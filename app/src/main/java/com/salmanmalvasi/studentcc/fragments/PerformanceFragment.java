package com.salmanmalvasi.studentcc.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.adapters.EmptyStateAdapter;
import com.salmanmalvasi.studentcc.adapters.MarksAccordionAdapter;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.interfaces.MarksDao;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.sync.DataFreshnessManager;
import com.salmanmalvasi.studentcc.sync.LocalCacheMetrics;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Local marks appear immediately; independent statistics pools load only for expanded courses. */
public class PerformanceFragment extends Fragment {
    private final CompositeDisposable viewSubscriptions = new CompositeDisposable();
    private FirebaseAnalytics analytics;
    private AppBarLayout appBarLayout;
    private RecyclerView marksRecycler;
    private TextView screenSummary;
    private TextView lastUpdated;
    private MarksAccordionAdapter marksAdapter;
    private RecyclerView.Adapter<?> contentAdapter;
    private MarksDao marksDao;
    private Disposable marksRequest;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_performance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        analytics = FirebaseAnalytics.getInstance(requireContext());
        appBarLayout = view.findViewById(R.id.app_bar);
        marksRecycler = view.findViewById(R.id.recycler_view_marks);
        screenSummary = view.findViewById(R.id.text_view_marks_summary);
        lastUpdated = view.findViewById(R.id.text_view_marks_last_updated);
        LinearLayout header = view.findViewById(R.id.linear_layout_header);
        marksRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        marksRecycler.setItemAnimator(null);
        view.findViewById(R.id.marks_help_button).setOnClickListener(v -> showMarksHelpDialog());
        float density = getResources().getDisplayMetrics().density;
        getParentFragmentManager().setFragmentResultListener("customInsets", getViewLifecycleOwner(), (key, result) -> {
            appBarLayout.setPadding(result.getInt("systemWindowInsetLeft"), result.getInt("systemWindowInsetTop"),
                    result.getInt("systemWindowInsetRight"), 0);
            marksRecycler.setPadding(result.getInt("systemWindowInsetLeft"), 0,
                    result.getInt("systemWindowInsetRight"), (int) (result.getInt("bottomNavigationHeight") + 20 * density));
        });
        appBarLayout.addOnOffsetChangedListener((bar, offset) -> {
            if (header.getHeight() > 0) header.setAlpha(Math.max(0, 1f + (float) offset / header.getHeight()));
        });
        marksDao = AppDatabase.getInstance(requireContext().getApplicationContext()).marksDao();
        marksAdapter = new MarksAccordionAdapter();
        marksAdapter.setOnCourseExpandListener((course, displayedMarks) -> {
            markCourseAsRead(course, displayedMarks);
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, course.courseCode);
            analytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
        });
        contentAdapter = marksAdapter;
        marksRecycler.setAdapter(marksAdapter);
        getParentFragmentManager().setFragmentResultListener("dataUpdated", getViewLifecycleOwner(), (key, result) -> {
            loadMarksData();
            updateLastUpdated();
        });
        updateLastUpdated();
        loadMarksData();
    }

    private void loadMarksData() {
        if (marksRecycler == null) return;
        if (marksRequest != null) {
            viewSubscriptions.remove(marksRequest);
            marksRequest = null;
        }
        long openedAt = System.currentTimeMillis();
        marksRequest = Single.zip(marksDao.getCourses(), marksDao.getAllMarks(), marksDao.getAllCumulativeMarks(),
                        MarksSnapshot::new)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(snapshot -> {
                    if (marksRecycler == null) return;
                    screenSummary.setText(getString(R.string.marks_screen_summary, snapshot.courses.size(), snapshot.marks.size()));
                    screenSummary.setVisibility(snapshot.marks.isEmpty() ? View.GONE : View.VISIBLE);
                    if (snapshot.marks.isEmpty()) {
                        showContent(emptyState(EmptyStateAdapter.TYPE_NO_PERFORMANCE,
                                getString(R.string.marks_screen_empty), null));
                        return;
                    }
                    marksAdapter.setMarks(snapshot.marks, snapshot.courses, snapshot.cumulativeMarks);
                    showContent(marksAdapter);
                    LocalCacheMetrics.getInstance().recordScreenOpen("PerformanceFragment", System.currentTimeMillis() - openedAt, true);
                }, error -> {
                    if (marksRecycler == null) return;
                    screenSummary.setVisibility(View.GONE);
                    showContent(emptyState(EmptyStateAdapter.TYPE_ERROR,
                            getString(R.string.marks_screen_error), new EmptyStateAdapter.ButtonAttributes() {
                        @Override public void onClick() { loadMarksData(); }
                        @Override public int getButtonTextId() { return R.string.marks_retry; }
                    }));
                });
        viewSubscriptions.add(marksRequest);
    }

    private void updateLastUpdated() {
        if (lastUpdated == null || getContext() == null) return;
        String formatted = DataFreshnessManager.getInstance().getFormattedLastUpdated(requireContext(), DataFreshnessManager.DATASET_MARKS);
        if ("Not updated yet".equals(formatted)) {
            lastUpdated.setVisibility(View.GONE);
        } else {
            lastUpdated.setText(formatted);
            lastUpdated.setVisibility(View.VISIBLE);
        }
    }

    private void showContent(RecyclerView.Adapter<?> adapter) {
        if (marksRecycler == null || contentAdapter == adapter) return;
        marksRecycler.setAdapter(adapter);
        contentAdapter = adapter;
    }

    private EmptyStateAdapter emptyState(int type, String text, EmptyStateAdapter.ButtonAttributes action) {
        EmptyStateAdapter adapter = new EmptyStateAdapter(type, text, action) {
            @Override public long getItemId(int position) { return 0; }
        };
        adapter.setHasStableIds(true);
        return adapter;
    }

    public void refreshPerformanceDisplay() {
        loadMarksData();
    }

    private void markCourseAsRead(Course.AllData course, List<Mark.AllData> displayedMarks) {
        if (course.courseId == null || displayedMarks.isEmpty()) return;
        viewSubscriptions.add(marksDao.setMarksReadSnapshot(course.courseId, displayedMarks).subscribeOn(Schedulers.io())
                .subscribe(() -> course.unreadMarkCount = 0, error -> { }));
    }

    private void showMarksHelpDialog() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.marks_screen_title)
                .setMessage(R.string.marks_help_content_updated).setPositiveButton(R.string.got_it, null)
                .setNeutralButton(R.string.studentcc_sharing_manage_profile, (dialog, which) -> {
                    com.google.android.material.bottomnavigation.BottomNavigationView navigation =
                            requireActivity().findViewById(R.id.bottom_navigation);
                    if (navigation != null) navigation.setSelectedItemId(R.id.item_profile);
                }).show();
    }

    @Override
    public void onDestroyView() {
        viewSubscriptions.clear();
        marksRequest = null;
        if (marksAdapter != null) marksAdapter.release();
        if (marksRecycler != null) marksRecycler.setAdapter(null);
        marksAdapter = null;
        contentAdapter = null;
        marksRecycler = null;
        appBarLayout = null;
        screenSummary = null;
        lastUpdated = null;
        super.onDestroyView();
        getParentFragmentManager().setFragmentResult("getUnreadCount", new Bundle());
    }

    private static final class MarksSnapshot {
        final List<Course.AllData> courses;
        final List<Mark.AllData> marks;
        final List<CumulativeMark> cumulativeMarks;
        MarksSnapshot(List<Course.AllData> courses, List<Mark.AllData> marks, List<CumulativeMark> cumulativeMarks) {
            this.courses = courses;
            this.marks = marks;
            this.cumulativeMarks = cumulativeMarks;
        }
    }
}
