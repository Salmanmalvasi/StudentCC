package com.salmanmalvasi.studentcc.widgets;

import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.MarksCourseSummary;
import com.salmanmalvasi.studentcc.helpers.VitGradePredictor;
import com.salmanmalvasi.studentcc.integrations.unicc.UniCCClassStatistics;
import com.salmanmalvasi.studentcc.integrations.unicc.UniCCStatisticsRepository;
import com.salmanmalvasi.studentcc.integrations.unicc.UniCCStatisticsResult;
import com.salmanmalvasi.studentcc.models.Course;

import java.text.DecimalFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/** A lazily loaded panel; a recycled or collapsed course never retains an API observer. */
public final class UniCCInsightsBinder {
    private final View root;
    private final View loading;
    private final View metrics;
    private final TextView mean;
    private final TextView sd;
    private final TextView count;
    private final TextView message;
    private final TextView sample;
    private final TextView updated;
    private final MaterialButton refresh;
    private final VitGradePredictionBinder gradePrediction;
    private final UniCCStatisticsRepository repository;
    private Disposable request;
    private Course.AllData boundCourse;
    private Double personalTotal;
    private boolean embedded;
    private VitGradePredictor.PassStatus projectedPassStatus = VitGradePredictor.PassStatus.UNKNOWN;

    public UniCCInsightsBinder(View root) {
        this.root = root;
        loading = root.findViewById(R.id.progress_unicc_loading);
        metrics = root.findViewById(R.id.unicc_metrics);
        mean = root.findViewById(R.id.text_unicc_mean);
        sd = root.findViewById(R.id.text_unicc_sd);
        count = root.findViewById(R.id.text_unicc_count);
        message = root.findViewById(R.id.text_unicc_message);
        sample = root.findViewById(R.id.text_unicc_sample);
        updated = root.findViewById(R.id.text_unicc_updated);
        refresh = root.findViewById(R.id.button_unicc_refresh);
        gradePrediction = new VitGradePredictionBinder(root.findViewById(R.id.unicc_grade_prediction),
                R.id.text_unicc_predicted_grade, R.id.text_unicc_grade_assumptions, R.id.button_unicc_grade_ranges);
        repository = UniCCStatisticsRepository.getInstance(root.getContext());
        StatisticsMetricLayout.adapt(root, R.id.unicc_metrics);
        root.findViewById(R.id.button_unicc_info).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(root.getContext())
                        .setTitle(R.string.unicc_information)
                        .setMessage(R.string.unicc_help_content)
                        .setPositiveButton(R.string.got_it, null)
                        .show());
        refresh.setOnClickListener(v -> fetch(true));
    }

    public void bind(Course.AllData course, Double comparablePersonalTotal) {
        bind(course, comparablePersonalTotal, false);
    }

    public void bind(Course.AllData course, Double comparablePersonalTotal, boolean embedded) {
        bind(course, comparablePersonalTotal, embedded, VitGradePredictor.PassStatus.UNKNOWN);
    }

    public void bind(Course.AllData course, Double comparablePersonalTotal, boolean embedded,
                     VitGradePredictor.PassStatus projectedPassStatus) {
        dispose();
        boundCourse = course;
        personalTotal = comparablePersonalTotal;
        this.embedded = embedded;
        this.projectedPassStatus = projectedPassStatus;
        gradePrediction.reset();
        metrics.setVisibility(View.GONE);
        sample.setVisibility(View.GONE);
        updated.setVisibility(View.GONE);
        refresh.setVisibility(View.VISIBLE);
        if (MarksCourseSummary.isLab(course)) {
            showUnavailable(R.string.unicc_lab_component, false);
        } else if (MarksCourseSummary.isProject(course)) {
            showUnavailable(R.string.unicc_project_component, false);
        } else if (course.vtopClassId == null || course.vtopClassId.trim().isEmpty()
                || course.semesterId == null || course.semesterId.trim().isEmpty()) {
            showUnavailable(R.string.unicc_missing_class, false);
        } else {
            fetch(false);
        }
    }

    private void fetch(boolean forceRefresh) {
        if (boundCourse == null) return;
        if (request != null) request.dispose();
        loading.setVisibility(View.VISIBLE);
        refresh.setEnabled(false);
        if (metrics.getVisibility() != View.VISIBLE) message.setText(R.string.unicc_loading);
        request = repository.getStatistics(boundCourse.vtopClassId, boundCourse.semesterId, forceRefresh)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::render, error -> showUnavailable(R.string.unicc_unavailable, true));
    }

    private void render(UniCCStatisticsResult result) {
        loading.setVisibility(View.GONE);
        refresh.setEnabled(true);
        if (!result.hasStatistics()) {
            showUnavailable(result.status == UniCCStatisticsResult.Status.NO_DATA
                    ? R.string.unicc_no_data : R.string.unicc_unavailable, true);
            return;
        }
        UniCCClassStatistics stats = result.statistics;
        metrics.setVisibility(View.VISIBLE);
        mean.setText(number(stats.mean));
        sd.setText(number(stats.sd));
        count.setText(String.valueOf(stats.count));
        gradePrediction.bind(personalTotal, stats.marksBasis, stats.mean, stats.sd, stats.count,
                root.getContext().getString(R.string.unicc_insights_title), projectedPassStatus, embedded);
        if (personalTotal == null || stats.marksBasis != 60) {
            message.setText(R.string.unicc_incomplete_basis);
        } else {
            double difference = personalTotal - stats.mean;
            if (Math.abs(difference) < 0.01) message.setText(R.string.unicc_at_mean);
            else message.setText(root.getContext().getString(difference > 0
                    ? R.string.unicc_above_mean : R.string.unicc_below_mean, number(Math.abs(difference))));
        }
        sample.setVisibility(View.VISIBLE);
        sample.setText(stats.count < 30
                ? root.getContext().getString(R.string.unicc_small_sample, stats.count)
                : root.getContext().getString(R.string.unicc_sample_note));
        updated.setVisibility(View.VISIBLE);
        CharSequence age = DateUtils.getRelativeTimeSpanString(result.fetchedAtMillis,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        int freshness = result.isStale() ? R.string.unicc_stale
                : result.source == UniCCStatisticsResult.Source.CACHE ? R.string.unicc_cached : R.string.unicc_updated;
        updated.setText(root.getContext().getString(freshness, age));
    }

    private void showUnavailable(int resource, boolean canRefresh) {
        loading.setVisibility(View.GONE);
        metrics.setVisibility(View.GONE);
        gradePrediction.clear();
        sample.setVisibility(View.GONE);
        updated.setVisibility(View.GONE);
        message.setText(resource);
        refresh.setEnabled(canRefresh);
        refresh.setVisibility(canRefresh ? View.VISIBLE : View.GONE);
    }

    public void dispose() {
        if (request != null) {
            request.dispose();
            request = null;
        gradePrediction.clear();
        }
        boundCourse = null;
    }

    private static String number(double value) {
        return new DecimalFormat("0.##").format(value);
    }
}
