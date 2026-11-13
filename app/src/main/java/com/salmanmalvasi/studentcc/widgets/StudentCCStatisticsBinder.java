package com.salmanmalvasi.studentcc.widgets;

import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.salmanmalvasi.studentcc.BuildConfig;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.MarksCourseSummary;
import com.salmanmalvasi.studentcc.helpers.VitGradePredictor;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCClassStatistics;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCClassScope;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCSharingManager;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCStatisticsRepository;
import com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCStatisticsResult;
import com.salmanmalvasi.studentcc.models.Course;

import java.text.DecimalFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/** Read-only display of the StudentCC pool, independently of the UniCC pool. */
public final class StudentCCStatisticsBinder {
    private final View root;
    private final View loading;
    private final View metrics;
    private final TextView mean;
    private final TextView sd;
    private final TextView count;
    private final TextView ninePlus;
    private final TextView message;
    private final TextView sample;
    private final TextView updated;
    private final MaterialButton refresh;
    private final VitGradePredictionBinder gradePrediction;
    private final StudentCCStatisticsRepository repository;
    private final StudentCCSharingManager sharingManager;
    private Disposable request;
    private Disposable sharingChanges;
    private StudentCCSharingManager.SharingState previousSharingState;
    private Course.AllData course;
    private Double personalTotal;
    private int marksBasis = 60;
    private VitGradePredictor.PassStatus passStatus = VitGradePredictor.PassStatus.UNKNOWN;
    private boolean embedded;

    public StudentCCStatisticsBinder(View root) {
        this.root = root;
        loading = root.findViewById(R.id.progress_studentcc_statistics);
        metrics = root.findViewById(R.id.studentcc_statistics_metrics);
        mean = root.findViewById(R.id.text_studentcc_statistics_mean);
        sd = root.findViewById(R.id.text_studentcc_statistics_sd);
        count = root.findViewById(R.id.text_studentcc_statistics_count);
        ninePlus = root.findViewById(R.id.text_studentcc_statistics_nine_plus);
        message = root.findViewById(R.id.text_studentcc_statistics_message);
        sample = root.findViewById(R.id.text_studentcc_statistics_sample);
        updated = root.findViewById(R.id.text_studentcc_statistics_updated);
        refresh = root.findViewById(R.id.button_studentcc_statistics_refresh);
        gradePrediction = new VitGradePredictionBinder(root.findViewById(R.id.studentcc_grade_prediction),
                R.id.text_studentcc_predicted_grade, R.id.text_studentcc_grade_assumptions, R.id.button_studentcc_grade_ranges);
        repository = StudentCCStatisticsRepository.getInstance(root.getContext());
        sharingManager = StudentCCSharingManager.getInstance(root.getContext());
        StatisticsMetricLayout.adapt(root, R.id.studentcc_statistics_metrics);
        root.findViewById(R.id.button_studentcc_statistics_info).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(root.getContext()).setTitle(R.string.studentcc_statistics_information)
                        .setMessage(R.string.studentcc_statistics_help).setPositiveButton(R.string.got_it, null).show());
        refresh.setOnClickListener(v -> fetch(true));
    }

    public void bind(Course.AllData course, Double personalTotal) {
        bind(course, personalTotal, 60);
    }

    public void bind(Course.AllData course, Double personalTotal, int basis) {
        bind(course, personalTotal, basis, VitGradePredictor.PassStatus.UNKNOWN);
    }

    public void bind(Course.AllData course, Double personalTotal, int basis, VitGradePredictor.PassStatus passStatus) {
        bind(course, personalTotal, basis, passStatus, false);
    }

    public void bind(Course.AllData course, Double personalTotal, int basis, VitGradePredictor.PassStatus passStatus, boolean embedded) {
        dispose();
        this.course = course;
        this.personalTotal = personalTotal;
        this.marksBasis = basis;
        this.passStatus = passStatus;
        this.embedded = embedded;
        gradePrediction.reset();
        ((TextView) root.findViewById(R.id.text_studentcc_statistics_basis)).setText(root.getContext()
                .getString(basis == 100 ? R.string.studentcc_statistics_final_basis : R.string.studentcc_statistics_internal_basis));
        ((TextView) root.findViewById(R.id.text_studentcc_statistics_mean_label)).setText(basis == 100
                ? R.string.studentcc_statistics_final_mean_label : R.string.unicc_mean_label);
        metrics.setVisibility(View.GONE);
        ninePlus.setVisibility(View.GONE);
        sample.setVisibility(View.GONE);
        updated.setVisibility(View.GONE);
        if (BuildConfig.STUDENTCC_STATS_BASE_URL.trim().isEmpty()) {
            showState(R.string.studentcc_statistics_preparing, false);
        } else if (MarksCourseSummary.isLab(course)) {
            showState(R.string.unicc_lab_component, false);
        } else if (MarksCourseSummary.isProject(course)) {
            showState(R.string.unicc_project_component, false);
        } else if (course.vtopClassId == null || course.vtopClassId.trim().isEmpty()
                || course.semesterId == null || course.semesterId.trim().isEmpty()) {
            showState(R.string.unicc_missing_class, false);
        } else {
            fetch(false);
            observeSharingChanges();
        }
    }

    private void observeSharingChanges() {
        previousSharingState = sharingManager.getState();
        sharingChanges = sharingManager.observeState().observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    StudentCCSharingManager.SharingState previous = previousSharingState;
                    previousSharingState = state;
                    if (previous == null || course == null) return;
                    boolean finishedBatch = previous.working && !state.working;
                    boolean confirmedRemoval = previous.removalPending && !state.removalPending;
                    boolean recordedWithoutBatch = !state.working
                            && state.pendingContributions < previous.pendingContributions;
                    if (finishedBatch || confirmedRemoval || recordedWithoutBatch) {
                        // One refresh per finished operation, never per individual uploaded assessment.
                        fetch(finishedBatch || confirmedRemoval);
                    }
                }, error -> { });
    }

    private void fetch(boolean forceRefresh) {
        if (course == null) return;
        StudentCCClassScope scope;
        try { scope = StudentCCClassScope.fromCourse(course, marksBasis); }
        catch (IllegalArgumentException invalid) { showState(R.string.unicc_missing_class, false); return; }
        if (request != null) request.dispose();
        loading.setVisibility(View.VISIBLE);
        refresh.setVisibility(View.VISIBLE);
        refresh.setEnabled(false);
        if (metrics.getVisibility() != View.VISIBLE) message.setText(R.string.studentcc_statistics_loading);
        request = repository.getStatistics(scope, forceRefresh).observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::render, error -> showState(R.string.studentcc_statistics_unavailable, true));
    }

    private void render(StudentCCStatisticsResult result) {
        loading.setVisibility(View.GONE);
        refresh.setEnabled(true);
        if (result.status == StudentCCStatisticsResult.Status.NOT_CONFIGURED) {
            showState(R.string.studentcc_statistics_preparing, false);
            return;
        }
        if (result.status == StudentCCStatisticsResult.Status.INVALID_REQUEST) {
            showState(R.string.unicc_missing_class, false);
            return;
        }
        if (!result.hasStatistics() && result.statistics != null && result.statistics.count == 0) {
            metrics.setVisibility(View.GONE);
            gradePrediction.clear();
            updated.setVisibility(View.GONE);
            StudentCCClassStatistics statistics = result.statistics;
            showClassTally(statistics);
            message.setText(statistics == null ? root.getContext().getString(R.string.studentcc_statistics_waiting)
                    : root.getContext().getString(R.string.studentcc_statistics_empty_count, statistics.count));
            sample.setVisibility(View.GONE);
            showFreshness(result);
            return;
        }
        if (!result.hasStatistics()) {
            showState(R.string.studentcc_statistics_unavailable, true);
            return;
        }
        StudentCCClassStatistics statistics = result.statistics;
        showClassTally(statistics);
        metrics.setVisibility(View.VISIBLE);
        mean.setText(number(statistics.mean));
        sd.setText(number(statistics.sd));
        count.setText(String.valueOf(statistics.count));
        gradePrediction.bind(personalTotal, statistics.marksBasis, statistics.mean, statistics.sd, statistics.count,
                root.getContext().getString(R.string.studentcc_statistics_title), passStatus, embedded);
        sample.setVisibility(statistics.count < 30 ? View.VISIBLE : View.GONE);
        if (statistics.count < 30) sample.setText(root.getResources().getQuantityString(
                R.plurals.studentcc_statistics_sample_count, statistics.count, statistics.count));
        if (personalTotal == null || statistics.marksBasis != marksBasis) {
            message.setText(R.string.studentcc_statistics_no_comparison);
        } else {
            double difference = personalTotal - statistics.mean;
            if (Math.abs(difference) < 0.01) message.setText(R.string.studentcc_statistics_at_mean);
            else message.setText(root.getContext().getString(difference > 0
                    ? R.string.studentcc_statistics_above : R.string.studentcc_statistics_below, number(Math.abs(difference))));
        }
        showFreshness(result);
    }

    private void showClassTally(StudentCCClassStatistics statistics) {
        boolean available = statistics != null && statistics.cgpaAtLeastNineCount != null;
        ninePlus.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) ninePlus.setText(root.getContext().getString(
                R.string.studentcc_statistics_nine_plus, statistics.cgpaAtLeastNineCount));
    }

    private void showFreshness(StudentCCStatisticsResult result) {
        if (result.fetchedAtMillis <= 0) {
            updated.setVisibility(View.GONE);
            return;
        }
        updated.setVisibility(View.VISIBLE);
        CharSequence age = DateUtils.getRelativeTimeSpanString(result.fetchedAtMillis,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        int freshness = result.isStale() ? R.string.studentcc_statistics_stale
                : result.source == StudentCCStatisticsResult.Source.CACHE ? R.string.studentcc_statistics_cached
                : R.string.studentcc_statistics_updated;
        updated.setText(root.getContext().getString(freshness, age));
    }

    private void showState(int resource, boolean canRefresh) {
        loading.setVisibility(View.GONE);
        metrics.setVisibility(View.GONE);
        gradePrediction.clear();
        ninePlus.setVisibility(View.GONE);
        sample.setVisibility(View.GONE);
        updated.setVisibility(View.GONE);
        message.setText(resource);
        refresh.setEnabled(canRefresh);
        refresh.setVisibility(canRefresh ? View.VISIBLE : View.GONE);
    }

    public void dispose() {
        if (request != null) request.dispose();
        if (sharingChanges != null) sharingChanges.dispose();
        request = null;
        sharingChanges = null;
        previousSharingState = null;
        gradePrediction.clear();
        course = null;
    }

    private static String number(double value) {
        return new DecimalFormat("0.##").format(value);
    }
}
