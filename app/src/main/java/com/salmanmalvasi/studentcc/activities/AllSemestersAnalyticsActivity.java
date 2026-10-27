package com.salmanmalvasi.studentcc.activities;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.FastScraper;
import com.salmanmalvasi.studentcc.helpers.MultiSemesterRepository;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.helpers.VTOPHelper;
import com.salmanmalvasi.studentcc.models.AllSemestersAnalytics;
import com.salmanmalvasi.studentcc.models.SemesterSummary;
import com.salmanmalvasi.studentcc.widgets.SemesterAnalyticsGraphView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllSemestersAnalyticsActivity extends AppCompatActivity {

    private MultiSemesterRepository repository;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageButton btnBack;
    private MaterialButton btnToolbarSync;
    private TextView textToolbarSubtitle;
    private LinearProgressIndicator progressSyncAllSemesters;

    private TextView textOverallAttendance;
    private TextView textOverallAttBadge;
    private TextView textAttendanceClasses;
    private ProgressBar progressOverallAttendance;

    private TextView textAverageMarks;
    private TextView textOverallMarksBadge;
    private TextView textSemestersCount;
    private ProgressBar progressAverageMarks;

    private View cardLowestSem;
    private TextView textHighestSemInfo;
    private TextView textLowestSemName;
    private TextView textLowestSemPct;

    private TextView textHighestSubjectScore;
    private TextView textHighestSubjectName;
    private TextView textHighestSubjectSem;

    private TextView textLowestSubjectScore;
    private TextView textLowestSubjectName;
    private TextView textLowestSubjectSem;

    private SemesterAnalyticsGraphView graphView;
    private LinearLayout containerSemesterCards;

    private View cardSyncBanner;
    private MaterialButton btnSyncAllSemesters;
    private TextView textSyncStatus;

    private boolean isSyncing = false;
    private ValueAnimator progressAnimator;
    private VTOPHelper vtopHelper;

    private final android.content.BroadcastReceiver syncProgressReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (intent == null || !"com.salmanmalvasi.studentcc.ALL_SEMESTERS_SYNC_PROGRESS".equals(intent.getAction())) return;
            int current = intent.getIntExtra("current", 0);
            int total = intent.getIntExtra("total", 1);
            String semName = intent.getStringExtra("semName");

            int pct = total > 0 ? (int) ((current * 100f) / total) : 25;
            animateProgressSmoothly(pct, 300);

            if (textSyncStatus != null) {
                textSyncStatus.setVisibility(View.VISIBLE);
                textSyncStatus.setText("Syncing " + current + " of " + total + ": " + (semName != null ? semName : ""));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_semesters_analytics);

        repository = MultiSemesterRepository.getInstance(this);

        initViews();
        setupListeners();
        loadAnalyticsData();

        android.content.IntentFilter filter = new android.content.IntentFilter("com.salmanmalvasi.studentcc.ALL_SEMESTERS_SYNC_PROGRESS");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(syncProgressReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(syncProgressReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalyticsData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back_analytics);
        btnToolbarSync = findViewById(R.id.btn_toolbar_sync);
        textToolbarSubtitle = findViewById(R.id.text_toolbar_subtitle);
        progressSyncAllSemesters = findViewById(R.id.progress_sync_all_semesters);

        textOverallAttendance = findViewById(R.id.text_overall_attendance);
        textOverallAttBadge = findViewById(R.id.text_overall_att_badge);
        textAttendanceClasses = findViewById(R.id.text_attendance_classes);
        progressOverallAttendance = findViewById(R.id.progress_overall_attendance);

        textAverageMarks = findViewById(R.id.text_average_marks);
        textOverallMarksBadge = findViewById(R.id.text_overall_marks_badge);
        textSemestersCount = findViewById(R.id.text_semesters_count);
        progressAverageMarks = findViewById(R.id.progress_average_marks);

        cardLowestSem = findViewById(R.id.card_lowest_sem);
        textHighestSemInfo = findViewById(R.id.text_highest_sem_info);
        textLowestSemName = findViewById(R.id.text_lowest_sem_name);
        textLowestSemPct = findViewById(R.id.text_lowest_sem_pct);

        textHighestSubjectScore = findViewById(R.id.text_highest_subject_score);
        textHighestSubjectName = findViewById(R.id.text_highest_subject_name);
        textHighestSubjectSem = findViewById(R.id.text_highest_subject_sem);

        textLowestSubjectScore = findViewById(R.id.text_lowest_subject_score);
        textLowestSubjectName = findViewById(R.id.text_lowest_subject_name);
        textLowestSubjectSem = findViewById(R.id.text_lowest_subject_sem);

        graphView = findViewById(R.id.graph_view_analytics);
        containerSemesterCards = findViewById(R.id.container_semester_cards);

        cardSyncBanner = findViewById(R.id.card_sync_banner);
        btnSyncAllSemesters = findViewById(R.id.btn_sync_all_semesters);
        textSyncStatus = findViewById(R.id.text_sync_status);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnToolbarSync.setOnClickListener(v -> startAllSemestersSync());
        if (btnSyncAllSemesters != null) {
            btnSyncAllSemesters.setOnClickListener(v -> startAllSemestersSync());
        }

        ChipGroup modeGroup = findViewById(R.id.chip_group_graph_mode);
        if (modeGroup != null) {
            modeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if (id == R.id.chip_mode_all) {
                    graphView.setMode(SemesterAnalyticsGraphView.MODE_COMBINED);
                } else if (id == R.id.chip_mode_att) {
                    graphView.setMode(SemesterAnalyticsGraphView.MODE_ATTENDANCE);
                } else if (id == R.id.chip_mode_marks) {
                    graphView.setMode(SemesterAnalyticsGraphView.MODE_MARKS);
                }
            });
        }
    }

    private void loadAnalyticsData() {
        AllSemestersAnalytics analytics = repository.computeAnalytics();

        // 1. Overall Attendance (2 decimal precision, no round off)
        float attPct = analytics.overallAttendancePercentage;
        animateFloatNumber(textOverallAttendance, 0f, attPct, "%");
        textAttendanceClasses.setText(analytics.totalAttendedClasses + " of " + analytics.totalConductedClasses + " classes attended");
        progressOverallAttendance.setProgress((int) Math.min(100, Math.max(0, Math.round(attPct))));

        if (attPct >= 85f) {
            textOverallAttBadge.setText("Safe Criteria");
            textOverallAttBadge.setTextColor(0xFF10B981);
        } else if (attPct >= 75f) {
            textOverallAttBadge.setText("Eligible");
            textOverallAttBadge.setTextColor(0xFF38BDF8);
        } else {
            textOverallAttBadge.setText("Short Attendance");
            textOverallAttBadge.setTextColor(0xFFF43F5E);
        }

        // 2. Average Marks
        if (analytics.overallAverageMarks > 0) {
            int roundedMarks = Math.round(analytics.overallAverageMarks);
            animateNumber(textAverageMarks, 0, roundedMarks, "%");
            progressAverageMarks.setProgress(roundedMarks);
            if (roundedMarks >= 85) {
                textOverallMarksBadge.setText("Distinction");
                textOverallMarksBadge.setTextColor(0xFFA855F7);
            } else if (roundedMarks >= 75) {
                textOverallMarksBadge.setText("Strong");
                textOverallMarksBadge.setTextColor(0xFF38BDF8);
            } else {
                textOverallMarksBadge.setText("Graded");
                textOverallMarksBadge.setTextColor(0xFFF59E0B);
            }
        } else {
            textAverageMarks.setText("--");
            progressAverageMarks.setProgress(0);
            textOverallMarksBadge.setText("Pending");
            textOverallMarksBadge.setTextColor(0xFF94A3B8);
        }

        int semCount = analytics.semesterSummaries.size();
        if (semCount == 1) {
            String semName = analytics.semesterSummaries.get(0).semesterName;
            textSemestersCount.setText(semName != null && !semName.isEmpty() ? semName : "1 Semester");
            textToolbarSubtitle.setText("1 Semester Analyzed");
            if (cardSyncBanner != null) cardSyncBanner.setVisibility(View.VISIBLE);
        } else if (semCount > 1) {
            textSemestersCount.setText(semCount + " Semesters Analyzed");
            textToolbarSubtitle.setText(semCount + " Semesters Analyzed");
            if (cardSyncBanner != null) cardSyncBanner.setVisibility(View.GONE);
        } else {
            textSemestersCount.setText("No Semesters Synced");
            textToolbarSubtitle.setText("Ready to Sync");
            if (cardSyncBanner != null) cardSyncBanner.setVisibility(View.VISIBLE);
        }

        // 3. Extremes
        if (semCount > 1 && analytics.lowestAttendancePercentage > 0) {
            if (cardLowestSem != null) cardLowestSem.setVisibility(View.VISIBLE);
            textLowestSemName.setText(analytics.lowestAttendanceSemesterName);
            textLowestSemPct.setText("(" + String.format(Locale.ENGLISH, "%.2f%%", analytics.lowestAttendancePercentage) + ")");
            textHighestSemInfo.setText(analytics.highestAttendanceSemesterName + " (" + String.format(Locale.ENGLISH, "%.2f%%", analytics.highestAttendancePercentage) + ")");
        } else {
            if (cardLowestSem != null) cardLowestSem.setVisibility(View.GONE);
        }

        // 4. Highest Subject
        if (analytics.highestSubjectScore > 0) {
            textHighestSubjectScore.setText(Math.round(analytics.highestSubjectScore) + "%");
            String display = analytics.highestSubjectCode;
            if (!analytics.highestSubjectName.isEmpty() && !analytics.highestSubjectName.equalsIgnoreCase(analytics.highestSubjectCode)) {
                display += " - " + analytics.highestSubjectName;
            }
            textHighestSubjectName.setText(display);
            textHighestSubjectSem.setText(analytics.highestSubjectSemester);
        } else {
            textHighestSubjectScore.setText("--");
            textHighestSubjectName.setText("Evaluations in progress");
            textHighestSubjectSem.setText("");
        }

        // 5. Lowest Subject
        if (analytics.lowestSubjectScore > 0 && analytics.lowestSubjectScore < 9000) {
            textLowestSubjectScore.setText(Math.round(analytics.lowestSubjectScore) + "%");
            String display = analytics.lowestSubjectCode;
            if (!analytics.lowestSubjectName.isEmpty() && !analytics.lowestSubjectName.equalsIgnoreCase(analytics.lowestSubjectCode)) {
                display += " - " + analytics.lowestSubjectName;
            }
            textLowestSubjectName.setText(display);
            textLowestSubjectSem.setText(analytics.lowestSubjectSemester);
        } else {
            textLowestSubjectScore.setText("--");
            textLowestSubjectName.setText("All subjects evaluated");
            textLowestSubjectSem.setText("");
        }

        // 6. Visual Graph View
        graphView.setData(analytics.semesterSummaries);

        // 7. Semester-by-semester breakdown list
        populateSemesterBreakdown(analytics.semesterSummaries);
    }

    private void animateNumber(TextView textView, int start, int end, String suffix) {
        if (textView == null) return;
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(700);
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            textView.setText(value + suffix);
        });
        animator.start();
    }

    private void animateFloatNumber(TextView textView, float start, float end, String suffix) {
        if (textView == null) return;
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(700);
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            textView.setText(String.format(Locale.ENGLISH, "%.2f%s", value, suffix));
        });
        animator.start();
    }

    private void populateSemesterBreakdown(List<SemesterSummary> list) {
        containerSemesterCards.removeAllViews();
        if (list == null || list.isEmpty()) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        for (SemesterSummary sem : list) {
            View card = inflater.inflate(R.layout.item_semester_breakdown, containerSemesterCards, false);

            TextView textName = card.findViewById(R.id.text_sem_breakdown_name);
            TextView textBadge = card.findViewById(R.id.text_sem_breakdown_badge);
            TextView textClasses = card.findViewById(R.id.text_sem_breakdown_classes);
            TextView textMarks = card.findViewById(R.id.text_sem_breakdown_marks);
            TextView textTopSub = card.findViewById(R.id.text_sem_breakdown_top_sub);
            TextView btnToggle = card.findViewById(R.id.btn_toggle_expand);
            LinearLayout containerSubs = card.findViewById(R.id.container_sub_list);

            String name = sem.semesterName != null ? sem.semesterName : "Semester";
            textName.setText(name);

            if (sem.totalClasses > 0) {
                textBadge.setText(String.format(Locale.ENGLISH, "%.2f%%", sem.attendancePercentage));
                if (sem.attendancePercentage >= 85f) {
                    textBadge.setTextColor(0xFF10B981);
                } else if (sem.attendancePercentage >= 75f) {
                    textBadge.setTextColor(0xFF38BDF8);
                } else {
                    textBadge.setTextColor(0xFFF43F5E);
                }
                textClasses.setText(sem.attendedClasses + " / " + sem.totalClasses + " classes");
            } else {
                String gpaStr = (sem.gpa != null && !sem.gpa.isEmpty()) ? sem.gpa + " GPA" : "Done";
                textBadge.setText(gpaStr);
                textBadge.setTextColor(0xFF10B981);
                textClasses.setText(sem.gpa != null && !sem.gpa.isEmpty() ? "Semester GPA: " + sem.gpa : (sem.courseCount > 0 ? sem.courseCount + " courses completed" : "Completed"));
            }

            textMarks.setText(sem.averageMarksPercentage > 0 ? Math.round(sem.averageMarksPercentage) + "%" : (sem.gpa != null ? "GPA " + sem.gpa : "In Progress"));
            textTopSub.setText(sem.highestSubjectScore > 0 ? (Math.round(sem.highestSubjectScore) + "% (" + sem.highestSubjectCode + ")") : "--");

            if (sem.subjects != null && !sem.subjects.isEmpty()) {
                containerSubs.removeAllViews();
                for (SemesterSummary.SubjectStat sub : sem.subjects) {
                    View subRow = inflater.inflate(R.layout.item_semester_subject_row, containerSubs, false);
                    TextView txtCode = subRow.findViewById(R.id.text_subject_code);
                    TextView txtTitle = subRow.findViewById(R.id.text_subject_title);
                    TextView txtAttBadge = subRow.findViewById(R.id.text_subject_att_badge);
                    TextView txtClasses = subRow.findViewById(R.id.text_subject_classes);
                    TextView txtMarksBadge = subRow.findViewById(R.id.text_subject_marks_badge);

                    String code = sub.courseCode != null && !sub.courseCode.isEmpty() ? sub.courseCode : "COURSE";
                    txtCode.setText(code);
                    txtTitle.setText(sub.courseTitle != null && !sub.courseTitle.isEmpty() ? sub.courseTitle : code);

                    if (sub.total > 0 || sub.attended > 0) {
                        txtAttBadge.setText(String.format(Locale.ENGLISH, "%.2f%%", sub.attendancePercentage));
                        if (sub.attendancePercentage >= 85f) {
                            txtAttBadge.setTextColor(0xFF10B981);
                        } else if (sub.attendancePercentage >= 75f) {
                            txtAttBadge.setTextColor(0xFF38BDF8);
                        } else {
                            txtAttBadge.setTextColor(0xFFF43F5E);
                        }
                        txtClasses.setText("Attended " + sub.attended + " of " + sub.total + " classes");
                    } else {
                        String gradeStr = (sub.grade != null && !sub.grade.isEmpty()) ? "Grade " + sub.grade : "Passed";
                        txtAttBadge.setText(gradeStr);
                        txtAttBadge.setTextColor(0xFF10B981);
                        txtClasses.setText(sub.credits > 0 ? "Credits: " + sub.credits : "Completed Course");
                    }

                    if (sub.marksPercentage > 0) {
                        txtMarksBadge.setText("Marks: " + Math.round(sub.marksPercentage) + "%");
                        txtMarksBadge.setVisibility(View.VISIBLE);
                    } else {
                        txtMarksBadge.setVisibility(View.GONE);
                    }

                    containerSubs.addView(subRow);
                }

                btnToggle.setVisibility(View.VISIBLE);
                btnToggle.setText("View Courses (" + sem.subjects.size() + ") ▼");
                btnToggle.setOnClickListener(v -> {
                    if (containerSubs.getVisibility() == View.VISIBLE) {
                        containerSubs.setVisibility(View.GONE);
                        btnToggle.setText("View Courses (" + sem.subjects.size() + ") ▼");
                    } else {
                        containerSubs.setVisibility(View.VISIBLE);
                        btnToggle.setText("Hide Courses ▲");
                    }
                });
            } else {
                btnToggle.setVisibility(View.GONE);
            }

            containerSemesterCards.addView(card);
        }
    }

    private void animateProgressSmoothly(int targetPercent, long durationMs) {
        mainHandler.post(() -> {
            if (progressAnimator != null && progressAnimator.isRunning()) {
                progressAnimator.cancel();
            }
            int current = progressSyncAllSemesters.getProgress();
            progressAnimator = ValueAnimator.ofInt(current, targetPercent);
            progressAnimator.setDuration(durationMs);
            progressAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            progressAnimator.addUpdateListener(anim -> {
                int val = (int) anim.getAnimatedValue();
                progressSyncAllSemesters.setProgressCompat(val, false);
            });
            progressAnimator.start();
        });
    }

    private void startAllSemestersSync() {
        if (isSyncing) return;

        isSyncing = true;
        setSyncUiState(true, "Connecting to VTOP and syncing all semesters...");
        animateProgressSmoothly(25, 800);

        if (vtopHelper != null) {
            try {
                vtopHelper.unbind();
            } catch (Exception ignored) {}
        }

        vtopHelper = new VTOPHelper(this, new VTOPHelper.Initiator() {
            @Override
            public void onLoading(boolean isLoading) {
            }

            @Override
            public void onForceSignOut() {
                mainHandler.post(() -> {
                    isSyncing = false;
                    setSyncUiState(false, null);
                    Toast.makeText(AllSemestersAnalyticsActivity.this, "Sign-in required", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onComplete() {
                mainHandler.post(() -> {
                    isSyncing = false;
                    animateProgressSmoothly(100, 300);
                    setSyncUiState(false, null);
                    loadAnalyticsData();
                    Toast.makeText(AllSemestersAnalyticsActivity.this, "✓ All semesters attendance & marks updated!", Toast.LENGTH_SHORT).show();
                });
            }
        });

        vtopHelper.startAllSemestersSync();
        vtopHelper.bind();
    }

    private void setSyncUiState(boolean syncing, String statusText) {
        btnToolbarSync.setEnabled(!syncing);
        if (btnSyncAllSemesters != null) btnSyncAllSemesters.setEnabled(!syncing);

        if (syncing) {
            progressSyncAllSemesters.setVisibility(View.VISIBLE);
            if (textSyncStatus != null) {
                textSyncStatus.setVisibility(View.VISIBLE);
                if (statusText != null) textSyncStatus.setText(statusText);
            }
            btnToolbarSync.setText("Syncing...");
        } else {
            progressSyncAllSemesters.setVisibility(View.GONE);
            if (textSyncStatus != null) textSyncStatus.setVisibility(View.GONE);
            btnToolbarSync.setText("Sync All");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(syncProgressReceiver);
        } catch (Exception ignored) {}
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        if (vtopHelper != null) {
            try {
                vtopHelper.unbind();
            } catch (Exception ignored) {}
        }
        executorService.shutdown();
    }
}
