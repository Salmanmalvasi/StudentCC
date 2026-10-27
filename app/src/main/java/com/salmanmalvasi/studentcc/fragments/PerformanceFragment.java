package com.salmanmalvasi.studentcc.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.EmptyStateAdapter;
import com.salmanmalvasi.studentcc.adapters.MarksAccordionAdapter;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.RelativePerformanceCalculator;
import com.salmanmalvasi.studentcc.interfaces.ClassStatsDao;
import com.salmanmalvasi.studentcc.interfaces.MarksDao;
import com.salmanmalvasi.studentcc.models.ClassStats;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.widgets.PerformanceCard;

public class PerformanceFragment extends Fragment {
    private FirebaseAnalytics mFirebaseAnalytics;
    AppBarLayout appBarLayout;
    RecyclerView marksRecycler;
    List<Course.AllData> courses;
    MarksAccordionAdapter marksAccordionAdapter;
    int selectedCoursePosition = -1;
    View activePerformanceCardsRoot;

    public PerformanceFragment() {
        // Required empty public constructor
    }

    private void displayEmptyState(int type, String message) {
        this.marksRecycler.setAdapter(new EmptyStateAdapter(type, message));
        this.marksRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);

        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) this.appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) layoutParams.getBehavior();

        if (behavior != null) {
            behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
                @Override
                public boolean canDrag(@androidx.annotation.NonNull AppBarLayout appBarLayout) {
                    return false;
                }
            });
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());
        View performanceFragment = inflater.inflate(R.layout.fragment_performance, container, false);

        this.appBarLayout = performanceFragment.findViewById(R.id.app_bar);
        this.marksRecycler = performanceFragment.findViewById(R.id.recycler_view_marks);
        this.marksRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        LinearLayout header = performanceFragment.findViewById(R.id.linear_layout_header);

        com.google.android.material.button.MaterialButton helpButton = performanceFragment.findViewById(R.id.marks_help_button);
        if (helpButton != null) {
            helpButton.setOnClickListener(v -> showMarksHelpDialog());
        }

        float pixelDensity = this.getResources().getDisplayMetrics().density;

        getParentFragmentManager().setFragmentResultListener("customInsets", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");

            this.appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            this.marksRecycler.setPadding(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)
            );
        });

        this.appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            float alpha = 1 - ((float) (-1 * verticalOffset) / header.getHeight());
            header.setAlpha(alpha);
        });

        AppDatabase appDatabase = AppDatabase.getInstance(requireActivity().getApplicationContext());
        MarksDao marksDao = appDatabase.marksDao();

        marksAccordionAdapter = new MarksAccordionAdapter();
        marksAccordionAdapter.setOnCourseExpandListener((course, position, performanceCardsRoot) -> {
            selectedCoursePosition = position;
            activePerformanceCardsRoot = performanceCardsRoot;
            markCourseAsRead(marksDao, course);
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, course.courseCode);
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
            updateCumulativeCards(performanceCardsRoot, marksDao, course.courseCode, position);
        });
        marksRecycler.setAdapter(marksAccordionAdapter);

        TextView marksLastUpdated = performanceFragment.findViewById(R.id.text_view_marks_last_updated);
        updateMarksLastUpdatedIndicator(marksLastUpdated);

        getParentFragmentManager().setFragmentResultListener("dataUpdated", this, (requestKey, result) -> {
            if (isAdded()) {
                loadMarksData(marksDao);
                updateMarksLastUpdatedIndicator(marksLastUpdated);
            }
        });

        long startNs = System.currentTimeMillis();
        loadMarksData(marksDao);
        com.salmanmalvasi.studentcc.sync.LocalCacheMetrics.getInstance()
                .recordScreenOpen("PerformanceFragment", System.currentTimeMillis() - startNs, true);
        return performanceFragment;
    }

    private void updateMarksLastUpdatedIndicator(android.widget.TextView textView) {
        if (textView == null || getContext() == null) return;
        String formatted = com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance()
                .getFormattedLastUpdated(requireContext(), com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_MARKS);
        if (!"Not updated yet".equals(formatted)) {
            textView.setText(formatted);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    private void loadMarksData(MarksDao marksDao) {
        io.reactivex.rxjava3.core.Single.zip(
                marksDao.getCourses(),
                marksDao.getAllMarks(),
                (courseList, allMarks) -> new Object[]{courseList, allMarks})
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Object[]>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull Object[] result) {
                        @SuppressWarnings("unchecked")
                        List<Course.AllData> courseList = (List<Course.AllData>) result[0];
                        @SuppressWarnings("unchecked")
                        List<Mark.AllData> allMarks = (List<Mark.AllData>) result[1];

                        PerformanceFragment.this.courses = courseList;
                        if (courseList.isEmpty()) {
                            displayEmptyState(EmptyStateAdapter.TYPE_NO_PERFORMANCE, null);
                            return;
                        }

                        marksAccordionAdapter.setMarks(allMarks, courseList);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        displayEmptyState(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage());
                    }
                });
    }

    public void refreshPerformanceDisplay() {
        if (activePerformanceCardsRoot != null
                && courses != null
                && selectedCoursePosition >= 0
                && selectedCoursePosition < courses.size()) {
            Course.AllData course = courses.get(selectedCoursePosition);
            MarksDao marksDao = AppDatabase.getInstance(requireContext()).marksDao();
            updateCumulativeCards(activePerformanceCardsRoot, marksDao, course.courseCode, selectedCoursePosition);
        }
    }

    private void markCourseAsRead(MarksDao marksDao, Course.AllData course) {
        if (course.unreadMarkCount != 0 && course.courseId != null) {
            marksDao.setMarksRead(course.courseId)
                    .subscribeOn(Schedulers.single())
                    .subscribe();
        }
    }

    private void updateCumulativeCards(View root, MarksDao marksDao, String courseCode, int position) {
        PerformanceCard overall = root.findViewById(R.id.performance_card_overall);
        PerformanceCard theory = root.findViewById(R.id.performance_card_theory);
        PerformanceCard project = root.findViewById(R.id.performance_card_project);
        PerformanceCard lab = root.findViewById(R.id.performance_card_lab);

        overall.setIndeterminate(true);
        theory.setIndeterminate(true);
        project.setIndeterminate(true);
        lab.setIndeterminate(true);

        marksDao.getCumulativeMark(courseCode)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<CumulativeMark>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull CumulativeMark cumulativeMark) {
                        if (cumulativeMark.grandTotal != null) {
                            overall.show();
                            overall.setIndeterminate(false);
                            overall.setScore(cumulativeMark.grandTotal, cumulativeMark.grandMax);
                        } else {
                            overall.hide();
                        }

                        if (cumulativeMark.theoryTotal != null) {
                            theory.show();
                            theory.setIndeterminate(false);
                            theory.setScore(cumulativeMark.theoryTotal, cumulativeMark.theoryMax);
                        } else {
                            theory.hide();
                        }

                        updatePerformanceCards(root, position);

                        if (cumulativeMark.projectTotal != null) {
                            project.show();
                            project.setIndeterminate(false);
                            project.setScore(cumulativeMark.projectTotal, cumulativeMark.projectMax);
                        } else {
                            project.hide();
                        }

                        if (cumulativeMark.labTotal != null) {
                            lab.show();
                            lab.setIndeterminate(false);
                            lab.setScore(cumulativeMark.labTotal, cumulativeMark.labMax);
                        } else {
                            lab.hide();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Toast.makeText(getContext(), "Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updatePerformanceCards(View root, int position) {
        if (courses == null || position >= courses.size()) {
            return;
        }

        Course.AllData selectedCourse = courses.get(position);
        String courseCode = selectedCourse.courseCode;
        PerformanceCard average = root.findViewById(R.id.performance_card_average);

        AppDatabase database = AppDatabase.getInstance(getContext());
        ClassStatsDao classStatsDao = database.classStatsDao();
        MarksDao marksDao = database.marksDao();

        if (RelativePerformanceCalculator.isTheoryCourse(courseCode)) {
            classStatsDao.getAllClassStatsForCourse(courseCode)
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleObserver<List<ClassStats>>() {
                        @Override
                        public void onSubscribe(@NonNull Disposable d) {
                        }

                        @Override
                        public void onSuccess(@NonNull List<ClassStats> classStatsList) {
                            if (classStatsList != null && !classStatsList.isEmpty() && selectedCourse.courseId != null) {
                                marksDao.getMarks(selectedCourse.courseId)
                                        .subscribeOn(Schedulers.single())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(new SingleObserver<List<Mark.AllData>>() {
                                            @Override
                                            public void onSubscribe(@NonNull Disposable d) {
                                            }

                                            @Override
                                            public void onSuccess(@NonNull List<Mark.AllData> marks) {
                                                Double weightageAheadValue =
                                                        RelativePerformanceCalculator.getWeightageMarksAheadValue(marks, classStatsList);

                                                if (weightageAheadValue != null) {
                                                    average.show();
                                                    String formattedValue = (weightageAheadValue >= 0 ? "+" : "") +
                                                            new java.text.DecimalFormat("#.##").format(weightageAheadValue);
                                                    average.setRelativePerformance(formattedValue);
                                                } else {
                                                    average.hide();
                                                }
                                            }

                                            @Override
                                            public void onError(@NonNull Throwable e) {
                                                average.hide();
                                            }
                                        });
                            } else {
                                average.hide();
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            average.hide();
                        }
                    });
        } else {
            average.hide();
        }
    }

    private void showMarksHelpDialog() {
        if (!isAdded()) {
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.help_marks_title))
                .setMessage(getString(R.string.help_marks_content))
                .setPositiveButton(getString(R.string.got_it), null)
                .setIcon(R.drawable.ic_info)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getParentFragmentManager().setFragmentResult("getUnreadCount", new Bundle());
    }
}
