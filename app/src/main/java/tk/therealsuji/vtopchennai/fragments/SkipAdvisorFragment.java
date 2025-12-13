package tk.therealsuji.vtopchennai.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SkipAdvisorEngine;
import tk.therealsuji.vtopchennai.models.SkipRecommendation;

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

    // Threshold inputs
    private TextInputEditText overallThresholdInput;
    private TextInputEditText courseThresholdInput;
    private MaterialButton calculateButton;

    // Calendar view
    private MaterialCardView calendarCard;
    private LinearLayout calendarGrid;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEE\nMMM dd", Locale.ENGLISH);

    // Skippable subjects view
    private MaterialCardView skippableSubjectsCard;
    private LinearLayout skippableSubjectsContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_skip_advisor, container, false);

        // Initialize views
        recyclerView = view.findViewById(R.id.skip_recommendations_recycler);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        loadingIndicator = view.findViewById(R.id.loading_indicator);

        overallThresholdInput = view.findViewById(R.id.overall_threshold_input);
        courseThresholdInput = view.findViewById(R.id.course_threshold_input);
        calculateButton = view.findViewById(R.id.calculate_button);

        calendarCard = view.findViewById(R.id.calendar_card);
        calendarGrid = view.findViewById(R.id.calendar_grid);

        skippableSubjectsCard = view.findViewById(R.id.skippable_subjects_card);
        skippableSubjectsContainer = view.findViewById(R.id.skippable_subjects_container);

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

        return view;
    }

    private void loadRecommendations(float overallThreshold, float courseThreshold) {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyStateText.setVisibility(View.GONE);
        calendarCard.setVisibility(View.GONE);
        skippableSubjectsCard.setVisibility(View.GONE);

        compositeDisposable.add(
                Observable.fromCallable(() -> {
                    SkipAdvisorEngine engine = new SkipAdvisorEngine(requireContext());
                    engine.setThresholds(overallThreshold, courseThreshold);
                    List<SkipRecommendation> recs = engine.calculateRecommendations();
                    java.util.Map<String, Integer> skippable = engine.calculateSkippableClasses();
                    return new android.util.Pair<>(recs, skippable);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                pair -> {
                                    loadingIndicator.setVisibility(View.GONE);
                                    List<SkipRecommendation> result = pair.first;
                                    java.util.Map<String, Integer> skippableMap = pair.second;

                                    recommendations.clear();
                                    recommendations.addAll(result);
                                    adapter.notifyDataSetChanged();

                                    if (recommendations.isEmpty() && (skippableMap == null || skippableMap.isEmpty())) {
                                        emptyStateText.setVisibility(View.VISIBLE);
                                        emptyStateText.setText(
                                                "No upcoming classes found. Sync your data to see recommendations.");
                                    } else {
                                        // Show skippable subjects
                                        if (skippableMap != null && !skippableMap.isEmpty()) {
                                            displaySkippableSubjects(skippableMap);
                                            skippableSubjectsCard.setVisibility(View.VISIBLE);
                                        }

                                        // Show calendar view
                                        if (!result.isEmpty()) {
                                            displayCalendar(result);
                                            calendarCard.setVisibility(View.VISIBLE);
                                            recyclerView.setVisibility(View.VISIBLE);
                                        }
                                    }
                                },
                                error -> {
                                    loadingIndicator.setVisibility(View.GONE);
                                    emptyStateText.setVisibility(View.VISIBLE);
                                    emptyStateText.setText("Error loading recommendations. Please try again.");
                                    Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT)
                                            .show();
                                }));
    }

    private void displaySkippableSubjects(java.util.Map<String, Integer> skippableMap) {
        skippableSubjectsContainer.removeAllViews();

        for (java.util.Map.Entry<String, Integer> entry : skippableMap.entrySet()) {
            String courseCode = entry.getKey();
            int count = entry.getValue();

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView courseText = new TextView(requireContext());
            courseText.setText(courseCode);
            courseText.setTextSize(16);
            courseText.setTextColor(Color.parseColor("#424242")); // Dark gray
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            courseText.setLayoutParams(params);

            TextView countText = new TextView(requireContext());
            countText.setText(count + " more");
            countText.setTextSize(16);
            countText.setTypeface(null, android.graphics.Typeface.BOLD);

            if (count > 2) {
                countText.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else if (count > 0) {
                countText.setTextColor(Color.parseColor("#FF9800")); // Orange
            } else {
                countText.setTextColor(Color.parseColor("#F44336")); // Red
            }

            row.addView(courseText);
            row.addView(countText);

            skippableSubjectsContainer.addView(row);

            // Add divider
            View divider = new View(requireContext());
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            skippableSubjectsContainer.addView(divider);
        }
    }

    private void displayCalendar(List<SkipRecommendation> recommendations) {
        calendarGrid.removeAllViews();

        for (SkipRecommendation rec : recommendations) {
            if (!rec.hasClasses)
                continue;

            // Create day card
            MaterialCardView dayCard = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 20);
            dayCard.setLayoutParams(cardParams);
            dayCard.setRadius(16);
            dayCard.setCardElevation(6);
            dayCard.setUseCompatPadding(true);

            // Determine status and colors
            String statusEmoji;
            String statusText;
            int strokeColor;
            int strokeWidth = 6;

            if (rec.canSkip && !rec.isRisky) {
                statusEmoji = "🟢";
                statusText = "Safe to skip!";
                strokeColor = Color.parseColor("#4CAF50"); // Green
            } else if (rec.canSkip && rec.isRisky) {
                statusEmoji = "�";
                statusText = "Risky - Be careful";
                strokeColor = Color.parseColor("#FF9800"); // Orange
            } else {
                statusEmoji = "�";
                statusText = "Don't skip!";
                strokeColor = Color.parseColor("#F44336"); // Red
            }

            // Set stroke instead of background color for better theme compatibility
            dayCard.setStrokeColor(strokeColor);
            dayCard.setStrokeWidth(strokeWidth);

            // Create main layout
            LinearLayout mainLayout = new LinearLayout(requireContext());
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(24, 20, 24, 20);

            // Top row: Date and Emoji
            LinearLayout topRow = new LinearLayout(requireContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            // Date text
            TextView dateText = new TextView(requireContext());
            try {
                SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE, MMM dd", Locale.ENGLISH);
                dateText.setText(fullDateFormat.format(new Date(rec.date)));
            } catch (Exception e) {
                dateText.setText(rec.dayOfWeek);
            }
            dateText.setTextSize(18);
            dateText.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            dateText.setLayoutParams(dateParams);

            // Status emoji
            TextView emojiText = new TextView(requireContext());
            emojiText.setText(statusEmoji);
            emojiText.setTextSize(40);
            emojiText.setPadding(16, 0, 0, 0);

            topRow.addView(dateText);
            topRow.addView(emojiText);

            // Middle row: Number of classes
            TextView classesText = new TextView(requireContext());
            classesText.setText(rec.numClasses + (rec.numClasses == 1 ? " class" : " classes"));
            classesText.setTextSize(14);
            classesText.setAlpha(0.7f);
            LinearLayout.LayoutParams classesParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            classesParams.setMargins(0, 8, 0, 8);
            classesText.setLayoutParams(classesParams);

            // Bottom row: Status text
            TextView statusTextView = new TextView(requireContext());
            statusTextView.setText(statusText);
            statusTextView.setTextSize(16);
            statusTextView.setTextColor(strokeColor);
            statusTextView.setTypeface(null, android.graphics.Typeface.BOLD);

            mainLayout.addView(topRow);
            mainLayout.addView(classesText);
            mainLayout.addView(statusTextView);

            dayCard.addView(mainLayout);
            calendarGrid.addView(dayCard);
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
            holder.numClasses.setText(rec.numClasses + (rec.numClasses == 1 ? " class" : " classes"));

            // Status Chip
            String statusText;
            int chipColor;

            if (rec.canSkip) {
                if (rec.isRisky) {
                    statusText = "⚠️ Risky (but possible)";
                    chipColor = Color.parseColor("#FFF3E0"); // Light orange
                } else {
                    statusText = "✅ Safe to Skip";
                    chipColor = Color.parseColor("#E8F5E9"); // Light green
                }
            } else {
                statusText = "❌ Don't Skip";
                chipColor = Color.parseColor("#FFEBEE"); // Light red
            }

            holder.statusChip.setText(statusText);
            holder.statusChip.setChipBackgroundColorResource(android.R.color.transparent);
            holder.card.setCardBackgroundColor(chipColor);

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
                    classView.setTextSize(13);
                    classView.setTextColor(Color.parseColor("#424242"));
                    classView.setPadding(8, 4, 8, 4);
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
                    impactView.setTextSize(12);
                    impactView.setTextColor(impact.isSafe ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
                    impactView.setPadding(8, 4, 8, 4);
                    holder.subjectImpactsContainer.addView(impactView);
                }
            }

            // Risk reason
            if (rec.riskReason != null && !rec.riskReason.isEmpty()) {
                holder.riskReason.setText(rec.riskReason);
                holder.riskReason.setVisibility(View.VISIBLE);
            } else {
                holder.riskReason.setVisibility(View.GONE);
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
            TextView riskReason;
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
                riskReason = view.findViewById(R.id.risk_reason);
                toggleDetails = view.findViewById(R.id.toggle_details);
            }
        }
    }
}
