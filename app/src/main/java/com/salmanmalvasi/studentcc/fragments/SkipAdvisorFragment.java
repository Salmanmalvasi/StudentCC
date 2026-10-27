package com.salmanmalvasi.studentcc.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.SkipAdvisorEngine;
import com.salmanmalvasi.studentcc.models.SkipRecommendation;

/**
 * Smart Skip Advisor Fragment
 * Shows AI-powered recommendations for which classes can be safely skipped
 * with calendar view and customizable thresholds
 */
public class SkipAdvisorFragment extends Fragment {

    private RecyclerView recyclerView;
    private SkipAdapter adapter;
    private List<SkipRecommendation> recommendations = new ArrayList<>();
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private TextView emptyStateText;
    private LinearProgressIndicator loadingIndicator;
    private MaterialCardView emptyStateCard;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                refreshRecommendations();
            }
        }
    };

    // Threshold inputs
    private TextInputEditText overallThresholdInput;
    private TextInputEditText courseThresholdInput;
    private MaterialButton calculateButton;

    // Semester projection views
    private MaterialCardView cardSemesterProjection;
    private TextView textProjectionSubtitle;
    private TextView textProjectionRemainingClasses;
    private TextView textProjectionRemainingDays;
    private TextView textProjectionMaxOverall;
    private LinearLayout containerCourseForecasts;
    private TextView btnToggleCourseForecasts;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_skip_advisor, container, false);

        // Toolbar back navigation
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        // Initialize views
        recyclerView = view.findViewById(R.id.skip_recommendations_recycler);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        emptyStateCard = view.findViewById(R.id.empty_state_card);
        emptyStateText = view.findViewById(R.id.empty_state_text);

        overallThresholdInput = view.findViewById(R.id.overall_threshold_input);
        courseThresholdInput = view.findViewById(R.id.course_threshold_input);
        calculateButton = view.findViewById(R.id.calculate_button);

        // Semester projection views
        cardSemesterProjection = view.findViewById(R.id.card_semester_projection);
        textProjectionSubtitle = view.findViewById(R.id.text_projection_subtitle);
        textProjectionRemainingClasses = view.findViewById(R.id.text_projection_remaining_classes);
        textProjectionRemainingDays = view.findViewById(R.id.text_projection_remaining_days);
        textProjectionMaxOverall = view.findViewById(R.id.text_projection_max_overall);
        containerCourseForecasts = view.findViewById(R.id.container_course_forecasts);
        btnToggleCourseForecasts = view.findViewById(R.id.btn_toggle_course_forecasts);

        if (btnToggleCourseForecasts != null && containerCourseForecasts != null) {
            btnToggleCourseForecasts.setOnClickListener(v -> {
                if (containerCourseForecasts.getVisibility() == View.VISIBLE) {
                    containerCourseForecasts.setVisibility(View.GONE);
                    btnToggleCourseForecasts.setText("View course-wise max attendance ▼");
                } else {
                    containerCourseForecasts.setVisibility(View.VISIBLE);
                    btnToggleCourseForecasts.setText("Hide course-wise max attendance ▲");
                }
            });
        }

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SkipAdapter(recommendations);
        recyclerView.setAdapter(adapter);

        // Setup Calculate button
        calculateButton.setOnClickListener(v -> {
            String overallStr = overallThresholdInput.getText() != null
                    ? overallThresholdInput.getText().toString().trim()
                    : "85";
            String courseStr = courseThresholdInput.getText() != null ? courseThresholdInput.getText().toString().trim()
                    : "75";

            if (TextUtils.isEmpty(overallStr) || TextUtils.isEmpty(courseStr)) {
                Toast.makeText(getContext(), "Please enter both thresholds", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float overallThreshold = Float.parseFloat(overallStr);
                float courseThreshold = Float.parseFloat(courseStr);

                if (overallThreshold < 0 || overallThreshold > 100 ||
                        courseThreshold < 0 || courseThreshold > 100) {
                    Toast.makeText(getContext(), "Thresholds must be between 0 and 100", Toast.LENGTH_SHORT).show();
                    return;
                }

                loadRecommendations(overallThreshold, courseThreshold);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });

        // Initial load with default thresholds
        loadRecommendations(85.0f, 75.0f);

        getParentFragmentManager().setFragmentResultListener("dataUpdated", getViewLifecycleOwner(), (requestKey, result) -> {
            if (isAdded()) {
                refreshRecommendations();
            }
        });

        return view;
    }

    public void refreshRecommendations() {
        if (!isAdded() || getContext() == null) return;
        String overallStr = overallThresholdInput != null && overallThresholdInput.getText() != null
                ? overallThresholdInput.getText().toString().trim() : "85";
        String courseStr = courseThresholdInput != null && courseThresholdInput.getText() != null
                ? courseThresholdInput.getText().toString().trim() : "75";
        float overall = 85.0f;
        float course = 75.0f;
        try {
            if (!TextUtils.isEmpty(overallStr)) overall = Float.parseFloat(overallStr);
            if (!TextUtils.isEmpty(courseStr)) course = Float.parseFloat(courseStr);
        } catch (Exception ignored) {}
        loadRecommendations(overall, course);
    }

    private void loadRecommendations(float overallThreshold, float courseThreshold) {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.GONE);

        compositeDisposable.add(
                Observable.fromCallable(() -> {
                    SkipAdvisorEngine engine = new SkipAdvisorEngine(requireContext());
                    engine.setThresholds(overallThreshold, courseThreshold);
                    List<SkipRecommendation> recs = engine.calculateRecommendations();
                    SkipAdvisorEngine.SemesterProjection projection = engine.calculateSemesterEndProjection();
                    return new Object[] { recs, projection };
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                result -> {
                                    loadingIndicator.setVisibility(View.GONE);
                                    @SuppressWarnings("unchecked")
                                    List<SkipRecommendation> recs = (List<SkipRecommendation>) result[0];
                                    SkipAdvisorEngine.SemesterProjection projection = (SkipAdvisorEngine.SemesterProjection) result[1];

                                    recommendations.clear();
                                    recommendations.addAll(recs);
                                    adapter.notifyDataSetChanged();

                                    displaySemesterProjection(projection);

                                    if (recommendations.isEmpty()) {
                                        emptyStateCard.setVisibility(View.VISIBLE);
                                        emptyStateText.setText("No upcoming classes found. Sync your data to see recommendations.");
                                        recyclerView.setVisibility(View.GONE);
                                    } else {
                                        emptyStateCard.setVisibility(View.GONE);
                                        recyclerView.setVisibility(View.VISIBLE);
                                    }
                                },
                                error -> {
                                    loadingIndicator.setVisibility(View.GONE);
                                    emptyStateCard.setVisibility(View.VISIBLE);
                                    emptyStateText.setText("Error loading recommendations. Please try again.");
                                    recyclerView.setVisibility(View.GONE);
                                    Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT)
                                            .show();
                                }));
    }

    private void displaySemesterProjection(SkipAdvisorEngine.SemesterProjection projection) {
        if (projection == null || cardSemesterProjection == null || !isAdded()) return;

        if (projection.totalClassesRemaining > 0 || (projection.courseProjections != null && !projection.courseProjections.isEmpty())) {
            cardSemesterProjection.setVisibility(View.VISIBLE);
            textProjectionRemainingClasses.setText(String.valueOf(projection.totalClassesRemaining));
            textProjectionRemainingDays.setText(projection.estimatedDaysRemaining > 0
                    ? "~" + projection.estimatedDaysRemaining + " teaching days left"
                    : "Semester concluding soon");

            // Display max achievable attendance up to 2 decimal places (no round off)
            textProjectionMaxOverall.setText(String.format(Locale.ENGLISH, "%.2f%%", projection.maxAchievableOverall));

            if (projection.estimatedSemesterEnd != null) {
                SimpleDateFormat endFmt = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                textProjectionSubtitle.setText("Forecast till ~" + endFmt.format(projection.estimatedSemesterEnd));
            }

            containerCourseForecasts.removeAllViews();
            if (projection.courseProjections != null && !projection.courseProjections.isEmpty()) {
                for (Map.Entry<String, SkipAdvisorEngine.SemesterProjection.CourseProjection> entry : projection.courseProjections.entrySet()) {
                    SkipAdvisorEngine.SemesterProjection.CourseProjection cp = entry.getValue();

                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, 6, 0, 6);
                    row.setLayoutParams(rowParams);

                    TextView codeTv = new TextView(requireContext());
                    codeTv.setText(cp.courseTitle != null && !cp.courseTitle.isEmpty() ? cp.courseTitle : cp.courseCode);
                    codeTv.setTextSize(13);
                    codeTv.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
                    codeTv.setLayoutParams(lp1);

                    TextView remTv = new TextView(requireContext());
                    remTv.setText(cp.remainingClasses + " classes left");
                    remTv.setTextSize(12);
                    LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
                    remTv.setLayoutParams(lp2);

                    TextView maxTv = new TextView(requireContext());
                    maxTv.setText(String.format(Locale.ENGLISH, "Max: %.2f%%", cp.maxAchievableAttendance));
                    maxTv.setTextSize(12);
                    maxTv.setTypeface(null, android.graphics.Typeface.BOLD);
                    maxTv.setTextColor(ContextCompat.getColor(requireContext(),
                            cp.maxAchievableAttendance >= 75f ? R.color.attendance_high : R.color.attendance_low));
                    LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    maxTv.setLayoutParams(lp3);

                    row.addView(codeTv);
                    row.addView(remTv);
                    row.addView(maxTv);
                    containerCourseForecasts.addView(row);
                }
            }
        } else {
            cardSemesterProjection.setVisibility(View.GONE);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

    /**
     * RecyclerView Adapter for Day-Grouped Skip Recommendations
     */
    private static class SkipAdapter extends RecyclerView.Adapter<SkipAdapter.ViewHolder> {

        private List<SkipRecommendation> recommendations;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM dd", Locale.ENGLISH);
        private SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.ENGLISH);

        SkipAdapter(List<SkipRecommendation> recommendations) {
            this.recommendations = recommendations;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_skip_recommendation, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SkipRecommendation rec = recommendations.get(position);

            // Date Header
            try {
                holder.dateHeader.setText(dateFormat.format(new Date(rec.date)));
            } catch (Exception e) {
                holder.dateHeader.setText("Day " + (position + 1));
            }

            // Number of classes
            if (rec.isHoliday) {
                holder.numClasses.setText("Holiday");
                holder.statusChip.setText("🎉 " + (rec.holidayName != null ? rec.holidayName : "Day Off"));
                int holidayColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.holiday_indicator);
                holder.statusChip.setTextColor(holidayColor);
                holder.statusChip.setChipBackgroundColorResource(android.R.color.transparent);
                holder.card.setStrokeColor(holidayColor);
                holder.card.setStrokeWidth(2);
                holder.overallImpact.setText("Non-instructional day / Weekend. No classes to attend! ✨");
                holder.classesContainer.removeAllViews();
                holder.subjectImpactsContainer.removeAllViews();
                return;
            }

            holder.numClasses.setText(rec.numClasses + (rec.numClasses == 1 ? " class" : " classes"));

            // Status Chip with theme-aware colors
            String statusText;
            int statusColor;
            int strokeColor;

            if (rec.canSkip) {
                if (rec.isRisky) {
                    statusText = "⚠️ Risky";
                    statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_medium);
                    strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_medium);
                } else {
                    statusText = "✅ Safe to Skip";
                    statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_high);
                    strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_high);
                }
            } else {
                statusText = "❌ Don't Skip";
                statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_low);
                strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_low);
            }

            holder.statusChip.setText(statusText);
            holder.statusChip.setTextColor(statusColor);
            holder.statusChip.setChipBackgroundColorResource(android.R.color.transparent);
            holder.card.setStrokeColor(strokeColor);
            holder.card.setStrokeWidth(rec.canSkip && !rec.isRisky ? 3 : (rec.canSkip ? 2 : 1));

            // Classes List
            holder.classesContainer.removeAllViews();
            if (rec.classes != null) {
                for (SkipRecommendation.ClassInfo classInfo : rec.classes) {
                    TextView classView = new TextView(holder.itemView.getContext());
                    String classText = String.format(Locale.ENGLISH,
                            "• %s - %s (%s %s)",
                            classInfo.startTime, classInfo.endTime,
                            classInfo.courseCode, classInfo.courseType);
                    classView.setText(classText);
                    classView.setTextSize(14);
                    // Use same color as date header text (?attr/colorOnSurface)
                    TypedValue typedValue = new TypedValue();
                    holder.itemView.getContext().getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue, true);
                    classView.setTextColor(typedValue.data);
                    classView.setPadding(12, 8, 12, 8);
                    holder.classesContainer.addView(classView);
                }
            }

            // Overall attendance impact
            holder.overallImpact.setText(String.format(Locale.ENGLISH,
                    "Overall: %.1f%% → %.1f%% %s",
                    rec.currentOverall, rec.overallAfterSkip,
                    rec.overallAfterSkip >= 85 ? "✅" : "❌"));

            // Subject impacts in expandable section
            holder.subjectImpactsContainer.removeAllViews();
            if (rec.subjectImpacts != null) {
                for (SkipRecommendation.SubjectImpact impact : rec.subjectImpacts) {
                    TextView impactView = new TextView(holder.itemView.getContext());
                    String impactText = String.format(Locale.ENGLISH,
                            "%s: %.1f%% → %.1f%% %s",
                            impact.courseCode,
                            impact.currentAttendance,
                            impact.afterSkipAttendance,
                            impact.isSafe ? "✅" : "❌");
                    impactView.setText(impactText);
                    impactView.setTextSize(13);
                    // Use same color as date header text for base, with status colors
                    TypedValue baseColorValue = new TypedValue();
                    holder.itemView.getContext().getTheme().resolveAttribute(R.attr.colorOnSurface, baseColorValue, true);
                    // Keep status colors but ensure visibility
                    if (impact.isSafe) {
                        impactView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_high));
                    } else {
                        impactView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.attendance_low));
                    }
                    impactView.setPadding(12, 6, 12, 6);
                    holder.subjectImpactsContainer.addView(impactView);
                }
            }

            // Toggle details button
            holder.toggleDetails.setOnClickListener(v -> {
                if (holder.detailsSection.getVisibility() == View.GONE) {
                    holder.detailsSection.setVisibility(View.VISIBLE);
                    holder.toggleDetails.setText("Hide Details");
                } else {
                    holder.detailsSection.setVisibility(View.GONE);
                    holder.toggleDetails.setText("Show Details");
                }
            });
        }

        @Override
        public int getItemCount() {
            return recommendations.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView dateHeader;
            TextView numClasses;
            Chip statusChip;
            LinearLayout classesContainer;
            TextView overallImpact;
            LinearLayout detailsSection;
            LinearLayout subjectImpactsContainer;
            MaterialButton toggleDetails;

            ViewHolder(View view) {
                super(view);
                card = view.findViewById(R.id.recommendation_card);
                dateHeader = view.findViewById(R.id.date_header);
                numClasses = view.findViewById(R.id.num_classes);
                statusChip = view.findViewById(R.id.status_chip);
                classesContainer = view.findViewById(R.id.classes_container);
                overallImpact = view.findViewById(R.id.overall_impact);
                detailsSection = view.findViewById(R.id.details_section);
                subjectImpactsContainer = view.findViewById(R.id.subject_impacts_container);
                toggleDetails = view.findViewById(R.id.toggle_details);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRecommendations();
        IntentFilter filter = new IntentFilter("DATA_UPDATED");
        filter.addAction("OD_COUNT_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(syncReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(syncReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        compositeDisposable.clear();
    }
}
