package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.Mark;

/**
 * Enhanced Marks Adapter with better course organization and assessment display
 */
public class EnhancedMarksAdapter extends RecyclerView.Adapter<EnhancedMarksAdapter.ViewHolder> {
    List<Observable<List<Mark.AllData>>> markObservables;

    public EnhancedMarksAdapter(List<Observable<List<Mark.AllData>>> markObservables) {
        this.markObservables = markObservables;
    }

    @NonNull
    @Override
    public EnhancedMarksAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_enhanced_marks, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EnhancedMarksAdapter.ViewHolder holder, int position) {
        this.markObservables.get(position)
                .subscribe(new Observer<List<Mark.AllData>>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                    }

                    @Override
                    public void onNext(@io.reactivex.rxjava3.annotations.NonNull List<Mark.AllData> marks) {
                        holder.organizeAndDisplayMarks(marks);
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        holder.displayError("Error loading marks: " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    @Override
    public int getItemCount() {
        return this.markObservables.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout coursesContainer;
        private Context context;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.coursesContainer = itemView.findViewById(R.id.courses_container);
            this.context = itemView.getContext();
        }

        public void organizeAndDisplayMarks(List<Mark.AllData> marks) {
            coursesContainer.removeAllViews();

            if (marks.isEmpty()) {
                displayEmptyState();
                return;
            }

            // Group marks by course
            Map<String, CourseMarks> courseMarksMap = new HashMap<>();

            for (Mark.AllData mark : marks) {
                String courseKey = mark.courseCode + "_" + mark.courseType;

                if (!courseMarksMap.containsKey(courseKey)) {
                    CourseMarks courseMarks = new CourseMarks();
                    courseMarks.courseCode = mark.courseCode;
                    courseMarks.courseTitle = mark.courseTitle;
                    courseMarks.courseType = mark.courseType;
                    courseMarks.assessments = new ArrayList<>();
                    courseMarksMap.put(courseKey, courseMarks);
                }

                courseMarksMap.get(courseKey).assessments.add(mark);
            }

            // Create course cards
            for (CourseMarks courseMarks : courseMarksMap.values()) {
                createCourseCard(courseMarks);
            }
        }

        private void createCourseCard(CourseMarks courseMarks) {
            // Create main course card
            MaterialCardView courseCard = new MaterialCardView(context);
            courseCard.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            courseCard.setCardElevation(8);
            courseCard.setRadius(16);
            courseCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background));

            LinearLayout cardContent = new LinearLayout(context);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            cardContent.setPadding(24, 24, 24, 24);

            // Course header
            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            // Course info section
            LinearLayout courseInfoLayout = new LinearLayout(context);
            courseInfoLayout.setOrientation(LinearLayout.VERTICAL);
            courseInfoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            ));

            // Course title
            TextView courseTitleText = new TextView(context);
            courseTitleText.setText(courseMarks.courseCode + " - " + courseMarks.courseTitle);
            courseTitleText.setTextSize(16);
            courseTitleText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            courseTitleText.setTypeface(null, android.graphics.Typeface.BOLD);
            courseTitleText.setMaxLines(2);
            courseInfoLayout.addView(courseTitleText);

            // Course type chip
            Chip courseTypeChip = new Chip(context);
            courseTypeChip.setText(courseMarks.courseType.toUpperCase());
            courseTypeChip.setChipBackgroundColor(ContextCompat.getColorStateList(context, R.color.chip_background));
            courseTypeChip.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            courseTypeChip.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            courseTypeChip.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            courseInfoLayout.addView(courseTypeChip);

            headerLayout.addView(courseInfoLayout);

            // Progress indicator section
            LinearLayout progressLayout = new LinearLayout(context);
            progressLayout.setOrientation(LinearLayout.VERTICAL);
            progressLayout.setGravity(android.view.Gravity.CENTER);

            // Calculate totals
            double totalScored = 0, totalMax = 0, totalWeighted = 0, totalMaxWeighted = 0;
            for (Mark.AllData assessment : courseMarks.assessments) {
                if (assessment.score != null) totalScored += assessment.score;
                if (assessment.maxScore != null) totalMax += assessment.maxScore;
                if (assessment.weightage != null) totalWeighted += assessment.weightage;
                if (assessment.maxWeightage != null) totalMaxWeighted += assessment.maxWeightage;
            }

            // Circular progress indicator
            CircularProgressIndicator progressIndicator = new CircularProgressIndicator(context);
            progressIndicator.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            progressIndicator.setMax(100);
            progressIndicator.setProgress((int) (totalMaxWeighted > 0 ? (totalWeighted / totalMaxWeighted * 100) : 0));
            progressIndicator.setIndicatorColor(ContextCompat.getColor(context, R.color.colorAccent));
            progressIndicator.setTrackColor(ContextCompat.getColor(context, R.color.progress_track));
            progressLayout.addView(progressIndicator);

            // Progress text
            TextView progressText = new TextView(context);
            DecimalFormat df = new DecimalFormat("#.#");
            progressText.setText(df.format(totalWeighted) + "/" + df.format(totalMaxWeighted));
            progressText.setTextSize(12);
            progressText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            progressText.setGravity(android.view.Gravity.CENTER);
            progressLayout.addView(progressText);

            headerLayout.addView(progressLayout);
            cardContent.addView(headerLayout);

            // Assessments section (collapsible)
            LinearLayout assessmentsLayout = new LinearLayout(context);
            assessmentsLayout.setOrientation(LinearLayout.VERTICAL);
            assessmentsLayout.setVisibility(View.GONE);

            // Add assessments
            for (Mark.AllData assessment : courseMarks.assessments) {
                createAssessmentItem(assessmentsLayout, assessment);
            }

            cardContent.addView(assessmentsLayout);

            // Make header clickable to toggle assessments
            headerLayout.setOnClickListener(v -> {
                boolean isVisible = assessmentsLayout.getVisibility() == View.VISIBLE;
                assessmentsLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            });

            courseCard.addView(cardContent);
            coursesContainer.addView(courseCard);
        }

        private void createAssessmentItem(LinearLayout parent, Mark.AllData assessment) {
            LinearLayout assessmentLayout = new LinearLayout(context);
            assessmentLayout.setOrientation(LinearLayout.HORIZONTAL);
            assessmentLayout.setPadding(16, 12, 16, 12);
            assessmentLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.assessment_background));

            // Assessment info
            LinearLayout infoLayout = new LinearLayout(context);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            ));

            TextView titleText = new TextView(context);
            titleText.setText(assessment.title);
            titleText.setTextSize(14);
            titleText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            infoLayout.addView(titleText);

            TextView statusText = new TextView(context);
            if (assessment.status != null) {
                statusText.setText(Html.fromHtml(context.getString(R.string.status, assessment.status), Html.FROM_HTML_MODE_LEGACY));
                statusText.setTextSize(12);
                statusText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                infoLayout.addView(statusText);
            }

            parent.addView(infoLayout);

            // Score display
            LinearLayout scoreLayout = new LinearLayout(context);
            scoreLayout.setOrientation(LinearLayout.VERTICAL);
            scoreLayout.setGravity(android.view.Gravity.END);

            TextView scoreText = new TextView(context);
            if (assessment.score != null && assessment.maxScore != null) {
                DecimalFormat df = new DecimalFormat("#.#");
                scoreText.setText(df.format(assessment.score) + "/" + df.format(assessment.maxScore));
                scoreText.setTextSize(14);
                scoreText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                scoreText.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            scoreLayout.addView(scoreText);

            TextView weightText = new TextView(context);
            if (assessment.weightage != null && assessment.maxWeightage != null) {
                DecimalFormat df = new DecimalFormat("#.#");
                weightText.setText(df.format(assessment.weightage) + "/" + df.format(assessment.maxWeightage));
                weightText.setTextSize(12);
                weightText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }
            scoreLayout.addView(weightText);

            parent.addView(scoreLayout);
            parent.addView(assessmentLayout);
        }

        private void displayEmptyState() {
            TextView emptyText = new TextView(context);
            emptyText.setText("No marks data available");
            emptyText.setTextSize(16);
            emptyText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            emptyText.setGravity(android.view.Gravity.CENTER);
            emptyText.setPadding(32, 64, 32, 64);
            coursesContainer.addView(emptyText);
        }

        public void displayError(String errorMessage) {
            coursesContainer.removeAllViews();
            TextView errorText = new TextView(context);
            errorText.setText(errorMessage);
            errorText.setTextSize(14);
            errorText.setTextColor(ContextCompat.getColor(context, R.color.error_color));
            errorText.setGravity(android.view.Gravity.CENTER);
            errorText.setPadding(32, 64, 32, 64);
            coursesContainer.addView(errorText);
        }

        private static class CourseMarks {
            String courseCode;
            String courseTitle;
            String courseType;
            List<Mark.AllData> assessments;
        }
    }
}
