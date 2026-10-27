package com.salmanmalvasi.studentcc.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.interfaces.AttendanceDao;
import com.salmanmalvasi.studentcc.interfaces.CoursesDao;
import com.salmanmalvasi.studentcc.interfaces.TimetableDao;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Timetable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AttendanceCalculatorFragment extends Fragment {

    private TextInputEditText inputStart;
    private TextInputEditText inputEnd;
    private ViewGroup coursePreview;
    private MaterialButton buttonApply;

    private TextView textOverallPct;
    private TextView textCurrentStats;
    private LinearProgressIndicator progressAttendanceBar;
    private TextView textAttendanceStatusPill;

    // LID Banner & note
    private View layoutLidBanner;
    private TextView textLidBanner;
    private TextView textDateRangeLidNote;
    private Long lidDateUtc = null;
    private Long fatLabStartDateUtc = null;
    private String lidDateFormatted = null;

    // Semester-End Forecast views
    private View cardSemesterProjection;
    private TextView textProjectionSubtitle;
    private View layoutFatLabBanner;
    private TextView textFatLabBanner;
    private TextView textProjectionRemainingClasses;
    private TextView textProjectionRemainingDays;
    private TextView textProjectionMaxOverall;
    private TextView btnToggleCourseForecasts;
    private LinearLayout containerCourseForecasts;

    // GitHub-Style Attendance Heatmap views
    private com.salmanmalvasi.studentcc.widgets.AttendanceHeatmapView attendanceHeatmapView;
    private TextView textHeatmapTotalBadge;
    private TextView textHeatmapSelectedDay;

    // Surplus Skip Buffer Badge
    private TextView textSurplusSkipBadge;

    // Calendar-aware Point of No Return alerts
    private TextView textSingleSkipRecoverWarning;
    private TextView textTargetSimPointOfNoReturn;

    // Calendar & Course metadata
    private final Map<String, Integer> remainingClassesMap = new HashMap<>();
    private int totalClassesRemainingOverall = 0;
    private boolean isFatLabWeekActive = false;
    private final Map<String, Boolean> isLabCourseMap = new HashMap<>();

    // Quick 1-Class Skip simulator
    private Spinner spinnerSingleSkipCourse;
    private MaterialButton buttonSingleSkipMinus;
    private MaterialButton buttonSingleSkipPlus;
    private TextView textSingleSkipCount;
    private TextView textSkipOneBadge;
    private LinearLayout layoutSingleSkipResult;
    private TextView textSingleSkipHeadline;
    private TextView textSingleSkipDropBadge;
    private TextView textSingleSkipDetail;
    private int singleSkipCount = 1;
    private ArrayAdapter<CourseItem> singleSkipCourseAdapter;

    // Leave mode chips
    private ChipGroup chipGroupLeaveMode;
    private Chip chipLeaveOnePerDay;

    private Spinner spinnerCourses;
    private ChipGroup chipGroupTargets;
    private TextInputEditText inputTarget;
    private LinearLayout layoutSimResult;
    private TextView textSimHeadline;
    private TextView textSimResult;

    private Long startDateUtc = null;
    private Long endDateUtc = null;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                refreshAllData();
            }
        }
    };

    private static class CourseItem {
        final String displayName;
        final String code;
        final int attended;
        final int total;

        CourseItem(String displayName, String code, int attended, int total) {
            this.displayName = displayName;
            this.code = code;
            this.attended = attended;
            this.total = total;
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }

    private final List<CourseItem> courseItemList = new ArrayList<>();
    private ArrayAdapter<CourseItem> courseSpinnerAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attendance_calculator, container, false);

        // Heatmap views
        attendanceHeatmapView = view.findViewById(R.id.attendance_heatmap_view);
        textHeatmapTotalBadge = view.findViewById(R.id.text_heatmap_total_badge);
        textHeatmapSelectedDay = view.findViewById(R.id.text_heatmap_selected_day);

        if (attendanceHeatmapView != null) {
            attendanceHeatmapView
                    .setOnDayClickListener((date, attendedCount, presentCount, odCount, totalConducted) -> {
                        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH);
                        if (textHeatmapSelectedDay != null) {
                            if (attendedCount > 0) {
                                textHeatmapSelectedDay.setText("📅 " + sdf.format(date.getTime()) + ": " + attendedCount
                                        + " classes attended (" + presentCount + " Present, " + odCount + " On Duty)");
                            } else if (totalConducted > 0) {
                                textHeatmapSelectedDay.setText("📅 " + sdf.format(date.getTime())
                                        + ": Missed / Absent (" + totalConducted + " conducted)");
                            } else {
                                textHeatmapSelectedDay.setText(
                                        "📅 " + sdf.format(date.getTime()) + ": No classes scheduled / attended");
                            }
                        }
                    });
            loadAttendanceHeatmapData();
        }

        // Header and Overview views (Current Standing card removed from top)
        textOverallPct = null;
        textCurrentStats = null;
        progressAttendanceBar = null;
        textAttendanceStatusPill = null;
        layoutLidBanner = null;
        textLidBanner = null;
        textDateRangeLidNote = view.findViewById(R.id.text_date_range_lid_note);

        // Semester-End Forecast views
        cardSemesterProjection = view.findViewById(R.id.card_semester_projection);
        textProjectionSubtitle = view.findViewById(R.id.text_projection_subtitle);
        layoutFatLabBanner = view.findViewById(R.id.layout_fat_lab_banner);
        textFatLabBanner = view.findViewById(R.id.text_fat_lab_banner);
        textProjectionRemainingClasses = view.findViewById(R.id.text_projection_remaining_classes);
        textProjectionRemainingDays = view.findViewById(R.id.text_projection_remaining_days);
        textProjectionMaxOverall = view.findViewById(R.id.text_projection_max_overall);
        btnToggleCourseForecasts = view.findViewById(R.id.btn_toggle_course_forecasts);
        containerCourseForecasts = view.findViewById(R.id.container_course_forecasts);

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

        // Quick 1-Class Skip views
        spinnerSingleSkipCourse = view.findViewById(R.id.spinner_single_skip_course);
        buttonSingleSkipMinus = view.findViewById(R.id.button_single_skip_minus);
        buttonSingleSkipPlus = view.findViewById(R.id.button_single_skip_plus);
        textSingleSkipCount = view.findViewById(R.id.text_single_skip_count);
        textSkipOneBadge = view.findViewById(R.id.text_skip_one_badge);
        layoutSingleSkipResult = view.findViewById(R.id.layout_single_skip_result);
        textSingleSkipHeadline = view.findViewById(R.id.text_single_skip_headline);
        textSingleSkipDropBadge = view.findViewById(R.id.text_single_skip_drop_badge);
        textSingleSkipDetail = view.findViewById(R.id.text_single_skip_detail);
        textSingleSkipRecoverWarning = view.findViewById(R.id.text_single_skip_recover_warning);

        View layoutSingleSkipCountClickable = view.findViewById(R.id.layout_single_skip_count_clickable);
        if (layoutSingleSkipCountClickable != null) {
            layoutSingleSkipCountClickable.setOnClickListener(v -> showCustomSkipCountDialog());
        }

        setupSkipPresetChip(view, R.id.chip_skip_1, 1);
        setupSkipPresetChip(view, R.id.chip_skip_2, 2);
        setupSkipPresetChip(view, R.id.chip_skip_3, 3);
        setupSkipPresetChip(view, R.id.chip_skip_5, 5);
        setupSkipPresetChip(view, R.id.chip_skip_10, 10);
        setupSkipPresetChip(view, R.id.chip_skip_15, 15);
        setupSkipPresetChip(view, R.id.chip_skip_20, 20);
        setupSkipPresetChip(view, R.id.chip_skip_25, 25);
        View chipCustom = view.findViewById(R.id.chip_skip_custom);
        if (chipCustom != null) {
            chipCustom.setOnClickListener(v -> showCustomSkipCountDialog());
        }

        // Target / Bunk simulator views
        spinnerCourses = view.findViewById(R.id.spinner_courses);
        chipGroupTargets = view.findViewById(R.id.chip_group_targets);
        inputTarget = view.findViewById(R.id.input_target_percentage);
        layoutSimResult = view.findViewById(R.id.layout_target_simulation_result);
        textSimHeadline = view.findViewById(R.id.text_sim_headline);
        textSimResult = view.findViewById(R.id.text_target_simulation_result);
        textSurplusSkipBadge = view.findViewById(R.id.text_surplus_skip_badge);
        textTargetSimPointOfNoReturn = view.findViewById(R.id.text_target_sim_point_of_no_return);

        // Date selection and mode views
        chipGroupLeaveMode = view.findViewById(R.id.chip_group_leave_mode);
        chipLeaveOnePerDay = view.findViewById(R.id.chip_leave_one_per_day);
        inputStart = view.findViewById(R.id.input_start_date);
        inputEnd = view.findViewById(R.id.input_end_date);
        coursePreview = view.findViewById(R.id.layout_course_preview);
        buttonApply = view.findViewById(R.id.button_apply);
        MaterialButton buttonCalculate = view.findViewById(R.id.button_calculate);

        inputStart.setOnClickListener(v -> showDatePicker(inputStart));
        inputEnd.setOnClickListener(v -> showDatePicker(inputEnd));

        setupOverviewAndCourses();
        setupSingleSkipSimulator();
        setupTargetListeners();
        loadLidDate();
        loadSemesterEndForecast();

        buttonCalculate.setOnClickListener(v -> calculateDateRangeImpact());
        buttonApply.setOnClickListener(v -> applyChanges());

        getParentFragmentManager().setFragmentResultListener("dataUpdated", getViewLifecycleOwner(), (requestKey, result) -> {
            if (isAdded()) {
                refreshAllData();
            }
        });

        return view;
    }

    private void loadAttendanceHeatmapData() {
        disposables.add(
                AppDatabase.getInstance(requireContext()).attendanceRecordDao().getAll()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(records -> {
                            if (attendanceHeatmapView != null) {
                                attendanceHeatmapView.setAttendanceData(records);
                            }
                            if (textHeatmapTotalBadge != null && records != null) {
                                int attended = 0;
                                for (AttendanceRecord r : records) {
                                    String s = r.status != null ? r.status.trim().toLowerCase() : "";
                                    if (s.contains("present") || s.equals("p") || s.contains("on duty")
                                            || s.contains("duty") || s.equals("od")) {
                                        attended++;
                                    }
                                }
                                textHeatmapTotalBadge.setText(attended + " Attended");
                            }
                        }, throwable -> android.util.Log.w("AttendanceCalc", "Could not load records for heatmap",
                                throwable)));
    }

    public void refreshAllData() {
        if (!isAdded() || getContext() == null) return;
        setupOverviewAndCourses();
        loadAttendanceHeatmapData();
        loadSemesterEndForecast();
        loadLidDate();
    }

    private void setupOverviewAndCourses() {
        SharedPreferences sp = SettingsRepository.getSharedPreferences(requireContext());
        int totalOverall = sp.getInt("totalClasses", 0);
        int attendedOverall = sp.getInt("attendedClasses", 0);
        float pctOverall = totalOverall > 0 ? ((float) attendedOverall / totalOverall) * 100f : 0f;

        if (textOverallPct != null)
            textOverallPct.setText(String.format(Locale.ENGLISH, "%.2f%%", pctOverall));
        if (textCurrentStats != null)
            textCurrentStats.setText(
                    String.format(Locale.ENGLISH, "%d attended • %d conducted", attendedOverall, totalOverall));
        if (progressAttendanceBar != null)
            progressAttendanceBar.setProgress((int) Math.min(100, Math.max(0, pctOverall)));

        if (textAttendanceStatusPill != null) {
            if (pctOverall >= 75f) {
                textAttendanceStatusPill.setText("SAFE (≥ 75%)");
                textAttendanceStatusPill.setTextColor(0xFF10B981);
            } else {
                textAttendanceStatusPill.setText("SHORT (< 75%)");
                textAttendanceStatusPill.setTextColor(0xFFF59E0B);
            }
        }

        if (courseSpinnerAdapter == null) {
            courseItemList.clear();
            courseItemList.add(new CourseItem("Overall (All Courses)", "ALL", attendedOverall, totalOverall));
            courseSpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                    courseItemList);
            spinnerCourses.setAdapter(courseSpinnerAdapter);
        }

        if (singleSkipCourseAdapter == null && spinnerSingleSkipCourse != null) {
            singleSkipCourseAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                    courseItemList);
            spinnerSingleSkipCourse.setAdapter(singleSkipCourseAdapter);
        }

        // Query course attendance from SQLite to populate individual course selections and compute true overall sum
        AppDatabase db = AppDatabase.getInstance(requireContext());
        disposables.add(
                Single.zip(db.attendanceDao().getAll(), db.coursesDao().getAllCourses(), (attList, courses) -> {
                    Map<Integer, Course> courseMap = new HashMap<>();
                    for (Course c : courses) {
                        if (c != null) {
                            courseMap.put(c.id, c);
                        }
                    }
                    List<CourseItem> items = new ArrayList<>();
                    int dbAttended = 0;
                    int dbTotal = 0;
                    for (Attendance a : attList) {
                        if (a != null && a.total != null && a.total > 0) {
                            int att = a.attended != null ? a.attended : 0;
                            int tot = a.total;
                            dbAttended += att;
                            dbTotal += tot;

                            Course c = courseMap.get(a.courseId);
                            String courseName = (c != null && c.title != null && !c.title.isEmpty())
                                    ? c.title
                                    : (c != null && c.code != null ? c.code : "Course " + a.courseId);
                            String courseCode = (c != null && c.code != null && !c.code.isEmpty())
                                    ? c.code
                                    : courseName;
                            int pct = a.percentage != null ? a.percentage : Math.round((att * 100f) / tot);
                            String label = courseName + " (" + pct + "%)";
                            items.add(new CourseItem(label, courseCode, att, tot));
                        }
                    }
                    return new Object[] { items, dbAttended, dbTotal };
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            @SuppressWarnings("unchecked")
                            List<CourseItem> items = (List<CourseItem>) ((Object[]) result)[0];
                            int dbAttended = (Integer) ((Object[]) result)[1];
                            int dbTotal = (Integer) ((Object[]) result)[2];

                            int finalAttended = dbTotal > 0 ? dbAttended : attendedOverall;
                            int finalTotal = dbTotal > 0 ? dbTotal : totalOverall;
                            float finalPct = finalTotal > 0 ? ((float) finalAttended / finalTotal) * 100f : 0f;

                            if (textOverallPct != null)
                                textOverallPct.setText(String.format(Locale.ENGLISH, "%.2f%%", finalPct));
                            if (textCurrentStats != null)
                                textCurrentStats.setText(
                                        String.format(Locale.ENGLISH, "%d attended • %d conducted", finalAttended, finalTotal));
                            if (progressAttendanceBar != null)
                                progressAttendanceBar.setProgress((int) Math.min(100, Math.max(0, finalPct)));

                            if (textAttendanceStatusPill != null) {
                                if (finalPct >= 75f) {
                                    textAttendanceStatusPill.setText("SAFE (≥ 75%)");
                                    textAttendanceStatusPill.setTextColor(0xFF10B981);
                                } else {
                                    textAttendanceStatusPill.setText("SHORT (< 75%)");
                                    textAttendanceStatusPill.setTextColor(0xFFF59E0B);
                                }
                            }

                            // Keep previous selections if possible
                            String prevSelectedCourse = null;
                            if (spinnerCourses != null && spinnerCourses.getSelectedItem() instanceof CourseItem) {
                                prevSelectedCourse = ((CourseItem) spinnerCourses.getSelectedItem()).code;
                            }
                            String prevSingleSkipCourse = null;
                            if (spinnerSingleSkipCourse != null && spinnerSingleSkipCourse.getSelectedItem() instanceof CourseItem) {
                                prevSingleSkipCourse = ((CourseItem) spinnerSingleSkipCourse.getSelectedItem()).code;
                            }

                            courseItemList.clear();
                            courseItemList.add(new CourseItem("Overall (All Courses)", "ALL", finalAttended, finalTotal));
                            courseItemList.addAll(items);

                            if (courseSpinnerAdapter != null) {
                                courseSpinnerAdapter.notifyDataSetChanged();
                            }
                            if (singleSkipCourseAdapter != null) {
                                singleSkipCourseAdapter.notifyDataSetChanged();
                            }

                            if (prevSelectedCourse != null && spinnerCourses != null) {
                                for (int i = 0; i < courseItemList.size(); i++) {
                                    if (prevSelectedCourse.equals(courseItemList.get(i).code)) {
                                        spinnerCourses.setSelection(i);
                                        break;
                                    }
                                }
                            }
                            if (prevSingleSkipCourse != null && spinnerSingleSkipCourse != null) {
                                for (int i = 0; i < courseItemList.size(); i++) {
                                    if (prevSingleSkipCourse.equals(courseItemList.get(i).code)) {
                                        spinnerSingleSkipCourse.setSelection(i);
                                        break;
                                    }
                                }
                            }

                            runSingleSkipSimulation();
                            runTargetSimulation();
                        }, throwable -> android.util.Log.w("AttendanceCalc", "Could not load per-course stats",
                                throwable)));

        if (spinnerCourses != null) {
            spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    runTargetSimulation();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    private void setupSingleSkipSimulator() {
        if (spinnerSingleSkipCourse != null) {
            spinnerSingleSkipCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    runSingleSkipSimulation();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        if (buttonSingleSkipMinus != null) {
            buttonSingleSkipMinus.setOnClickListener(v -> {
                if (singleSkipCount > 1) {
                    singleSkipCount--;
                    updateSingleSkipUI();
                }
            });
        }

        if (buttonSingleSkipPlus != null) {
            buttonSingleSkipPlus.setOnClickListener(v -> {
                if (singleSkipCount < 100) {
                    singleSkipCount++;
                    updateSingleSkipUI();
                }
            });
        }

        updateSingleSkipUI();
    }

    private void updateSingleSkipUI() {
        if (textSingleSkipCount != null) {
            textSingleSkipCount
                    .setText(singleSkipCount == 1 ? "Skipping 1 Class" : "Skipping " + singleSkipCount + " Classes");
        }
        if (textSkipOneBadge != null) {
            textSkipOneBadge.setText(singleSkipCount == 1 ? "1 CLASS" : singleSkipCount + " CLASSES");
        }
        runSingleSkipSimulation();
    }

    private void runSingleSkipSimulation() {
        if (spinnerSingleSkipCourse == null || textSingleSkipHeadline == null || textSingleSkipDetail == null)
            return;
        CourseItem selected = (CourseItem) spinnerSingleSkipCourse.getSelectedItem();
        if (selected == null && !courseItemList.isEmpty()) {
            selected = courseItemList.get(0);
        }
        if (selected == null || selected.total <= 0) {
            textSingleSkipHeadline.setText("No Data");
            textSingleSkipDetail.setText("Please sync your attendance data first.");
            if (textSingleSkipDropBadge != null)
                textSingleSkipDropBadge.setText("-0.00%");
            if (textSingleSkipRecoverWarning != null)
                textSingleSkipRecoverWarning.setVisibility(View.GONE);
            return;
        }

        int curAttended = selected.attended;
        int curTotal = selected.total;
        float curPct = ((float) curAttended / curTotal) * 100f;

        int newAttended = curAttended;
        int newTotal = curTotal + singleSkipCount;
        float newPct = ((float) newAttended / newTotal) * 100f;
        float drop = Math.max(0f, curPct - newPct);

        if (textSingleSkipDropBadge != null) {
            textSingleSkipDropBadge.setText(String.format(Locale.ENGLISH, "-%.2f%%", drop));
        }

        int remaining = "ALL".equals(selected.code) ? totalClassesRemainingOverall
                : remainingClassesMap.getOrDefault(selected.code, 0);
        boolean isLab = Boolean.TRUE.equals(isLabCourseMap.get(selected.code));

        if (isLab && isFatLabWeekActive) {
            if (layoutSingleSkipResult != null)
                layoutSingleSkipResult.setBackgroundResource(R.drawable.bg_sim_safe);
            textSingleSkipHeadline.setText("🔒 Lab Attendance Locked");
            textSingleSkipDetail.setText(
                    "This is a lab course and FAT Lab week is active. Lab attendance was locked a week before LID, so no more lab classes can be conducted or skipped.");
            if (textSingleSkipRecoverWarning != null) {
                textSingleSkipRecoverWarning.setVisibility(View.VISIBLE);
                textSingleSkipRecoverWarning.setText("ℹ️ Lab classes have already concluded for the semester.");
                textSingleSkipRecoverWarning.setTextColor(0xFF38BDF8);
            }
            return;
        }

        // Calendar-aware Point of No Return calculation:
        // Max achievable attendance at LID if skipping singleSkipCount classes and then
        // attending ALL remaining:
        float maxRecoverable = (newTotal + remaining) > 0
                ? ((float) (curAttended + remaining) / (newTotal + remaining)) * 100f
                : newPct;

        if (newPct >= 75f) {
            if (layoutSingleSkipResult != null)
                layoutSingleSkipResult.setBackgroundResource(R.drawable.bg_sim_safe);
            textSingleSkipHeadline.setText(String.format(Locale.ENGLISH, "SAFE: Projected %.2f%% (≥ 75%%)", newPct));
            int buffer = (int) Math.floor(((curAttended * 100.0) / 75.0) - newTotal);
            buffer = Math.max(0, buffer);
            textSingleSkipDetail.setText(String.format(Locale.ENGLISH,
                    "Skipping %d %s of %s drops attendance from %.2f%% (%d/%d) to %.2f%% (%d/%d). You can still miss %d more %s before dropping below 75%%.",
                    singleSkipCount, singleSkipCount == 1 ? "class" : "classes", selected.code, curPct, curAttended,
                    curTotal, newPct, newAttended, newTotal, buffer, buffer == 1 ? "class" : "classes"));

            if (textSingleSkipRecoverWarning != null) {
                if (remaining > 0) {
                    textSingleSkipRecoverWarning.setVisibility(View.VISIBLE);
                    textSingleSkipRecoverWarning.setText(String.format(Locale.ENGLISH,
                            "📅 Calendar Awareness: %d %s remaining till LID. Max achievable if you attend all remaining is %.2f%%.",
                            remaining, remaining == 1 ? "class" : "classes", maxRecoverable));
                    textSingleSkipRecoverWarning.setTextColor(0xFF10B981);
                } else {
                    textSingleSkipRecoverWarning.setVisibility(View.GONE);
                }
            }
        } else {
            if (layoutSingleSkipResult != null)
                layoutSingleSkipResult.setBackgroundResource(R.drawable.bg_sim_warning);
            textSingleSkipHeadline
                    .setText(String.format(Locale.ENGLISH, "⚠️ SHORT: Projected %.2f%% (< 75%%)", newPct));
            int mustAttend = (int) Math.ceil(((75.0 * newTotal) - (100.0 * newAttended)) / 25.0);
            mustAttend = Math.max(1, mustAttend);
            textSingleSkipDetail.setText(String.format(Locale.ENGLISH,
                    "Skipping %d %s of %s drops attendance from %.2f%% (%d/%d) to %.2f%% (%d/%d). This drops below the 75%% mandatory threshold!",
                    singleSkipCount, singleSkipCount == 1 ? "class" : "classes", selected.code, curPct, curAttended,
                    curTotal, newPct, newAttended, newTotal));

            if (textSingleSkipRecoverWarning != null) {
                textSingleSkipRecoverWarning.setVisibility(View.VISIBLE);
                if (remaining > 0 && maxRecoverable < 75.0f) {
                    // POINT OF NO RETURN REACHED!
                    textSingleSkipRecoverWarning.setText(String.format(Locale.ENGLISH,
                            "🚨 POINT OF NO RETURN: Skipping %d %s drops you so low that you CANNOT recover to 75%% before semester end (LID)! Even if you attend 100%% of all %d remaining %s, your maximum final attendance will be %.2f%%.",
                            singleSkipCount, singleSkipCount == 1 ? "class" : "classes", remaining,
                            remaining == 1 ? "class" : "classes", maxRecoverable));
                    textSingleSkipRecoverWarning.setTextColor(0xFFF43F5E);
                } else if (remaining > 0) {
                    textSingleSkipRecoverWarning.setText(String.format(Locale.ENGLISH,
                            "⚠️ Recovery Window: You must attend the next %d classes out of %d remaining before LID to climb back to 75%% (Max achievable: %.2f%%).",
                            mustAttend, remaining, maxRecoverable));
                    textSingleSkipRecoverWarning.setTextColor(0xFFF59E0B);
                } else {
                    textSingleSkipRecoverWarning.setText(String.format(Locale.ENGLISH,
                            "⚠️ You need to attend %d consecutive classes to recover to 75%%.", mustAttend));
                    textSingleSkipRecoverWarning.setTextColor(0xFFF43F5E);
                }
            }
        }
    }

    private void loadSemesterEndForecast() {
        disposables.add(
                io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                    com.salmanmalvasi.studentcc.helpers.SkipAdvisorEngine engine = new com.salmanmalvasi.studentcc.helpers.SkipAdvisorEngine(
                            requireContext());
                    return engine.calculateSemesterEndProjection();
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(projection -> {
                            if (projection == null || !isAdded())
                                return;

                            totalClassesRemainingOverall = projection.totalClassesRemaining;
                            isFatLabWeekActive = projection.isFatLabWeek;
                            if (projection.fatLabStartDate != null) {
                                fatLabStartDateUtc = projection.fatLabStartDate.getTime();
                            }
                            remainingClassesMap.clear();
                            isLabCourseMap.clear();

                            if (textProjectionRemainingClasses != null) {
                                textProjectionRemainingClasses.setText(projection.totalClassesRemaining + " classes");
                            }
                            if (textProjectionRemainingDays != null) {
                                textProjectionRemainingDays.setText(projection.estimatedDaysRemaining + " days");
                            }
                            if (textProjectionMaxOverall != null) {
                                textProjectionMaxOverall.setText(
                                        String.format(Locale.ENGLISH, "%.2f%%", projection.maxAchievableOverall));
                            }

                            if (layoutFatLabBanner != null) {
                                layoutFatLabBanner.setVisibility(projection.isFatLabWeek ? View.VISIBLE : View.GONE);
                            }

                            if (projection.estimatedSemesterEnd != null && textProjectionSubtitle != null) {
                                SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                                String lidStr = displayFmt.format(projection.estimatedSemesterEnd);
                                textProjectionSubtitle
                                        .setText("Calculated up to LID (" + lidStr + ") • Timetable cross-referenced");
                            }

                            if (containerCourseForecasts != null) {
                                containerCourseForecasts.removeAllViews();
                                LayoutInflater inflater = LayoutInflater.from(requireContext());
                                for (Map.Entry<String, com.salmanmalvasi.studentcc.helpers.SkipAdvisorEngine.SemesterProjection.CourseProjection> entry : projection.courseProjections
                                        .entrySet()) {
                                    com.salmanmalvasi.studentcc.helpers.SkipAdvisorEngine.SemesterProjection.CourseProjection cp = entry
                                            .getValue();
                                    if (cp == null)
                                        continue;

                                    remainingClassesMap.put(cp.courseCode, cp.remainingClasses);
                                    isLabCourseMap.put(cp.courseCode, cp.isLab);

                                    View row = inflater.inflate(R.layout.item_semester_subject_row,
                                            containerCourseForecasts, false);
                                    TextView txtCode = row.findViewById(R.id.text_subject_code);
                                    TextView txtTitle = row.findViewById(R.id.text_subject_title);
                                    TextView txtAttBadge = row.findViewById(R.id.text_subject_att_badge);
                                    TextView txtClasses = row.findViewById(R.id.text_subject_classes);
                                    TextView txtMarksBadge = row.findViewById(R.id.text_subject_marks_badge);

                                    String courseHeading = (cp.courseTitle != null && !cp.courseTitle.isEmpty())
                                            ? cp.courseTitle
                                            : cp.courseCode;
                                    txtCode.setText(courseHeading);
                                    if (cp.isLocked) {
                                        txtTitle.setText(cp.courseCode + " • 🔒 Lab Attendance Locked (FAT Lab week)");
                                    } else if (cp.isLab) {
                                        txtTitle.setText(cp.courseCode + " • Lab Course • Locks 1 week before LID");
                                    } else {
                                        txtTitle.setText(cp.courseCode + " • Theory Course • Runs till LID");
                                    }

                                    txtAttBadge.setText(String.format(Locale.ENGLISH, "%.2f%%", cp.currentAttendance));
                                    txtClasses.setText(String.format(Locale.ENGLISH, "%d/%d attended • %d classes left",
                                            cp.currentAttended, cp.currentTotal, cp.remainingClasses));

                                    txtMarksBadge.setText(
                                            String.format(Locale.ENGLISH, "Max: %.2f%%", cp.maxAchievableAttendance));
                                    txtMarksBadge.setTextColor(0xFF10B981);

                                    containerCourseForecasts.addView(row);
                                }
                            }

                            // Re-run simulations so they update with calendar-awareness immediately
                            runSingleSkipSimulation();
                            runTargetSimulation();
                        }, throwable -> android.util.Log.w("AttendanceCalc", "Could not load semester end forecast",
                                throwable)));
    }

    private void loadLidDate() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        disposables.add(
                db.calendarEventDao().getAll()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(events -> {
                            if (events == null || events.isEmpty())
                                return;
                            SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                            Calendar now = Calendar.getInstance();
                            Calendar lidCandidate = null;

                            for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : events) {
                                if (ce.text == null || ce.month == null)
                                    continue;
                                String fullText = (ce.text + " " + (ce.category != null ? ce.category : "") + " "
                                        + (ce.type != null ? ce.type : "")).toLowerCase(Locale.ENGLISH);
                                boolean isLid = fullText.matches(".*\\b(lid|l\\.i\\.d)\\b.*")
                                        || fullText.contains("last instructional day")
                                        || fullText.contains("last instruction day");
                                if (isLid) {
                                    try {
                                        Date parsed = monthFmt.parse(ce.month);
                                        if (parsed != null) {
                                            Calendar candidate = Calendar.getInstance();
                                            candidate.setTime(parsed);
                                            candidate.set(Calendar.DAY_OF_MONTH, ce.date);
                                            if (candidate.after(now)) {
                                                if (lidCandidate == null || candidate.before(lidCandidate)) {
                                                    lidCandidate = (Calendar) candidate.clone();
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }

                            if (lidCandidate != null) {
                                Calendar lidMidnight = (Calendar) lidCandidate.clone();
                                lidMidnight.set(Calendar.HOUR_OF_DAY, 23);
                                lidMidnight.set(Calendar.MINUTE, 59);
                                lidMidnight.set(Calendar.SECOND, 59);
                                lidMidnight.set(Calendar.MILLISECOND, 999);
                                lidDateUtc = lidMidnight.getTimeInMillis();
                                if (fatLabStartDateUtc == null) {
                                    Calendar fatLabCal = (Calendar) lidCandidate.clone();
                                    fatLabCal.add(Calendar.DATE, -7);
                                    fatLabCal.set(Calendar.HOUR_OF_DAY, 0);
                                    fatLabCal.set(Calendar.MINUTE, 0);
                                    fatLabCal.set(Calendar.SECOND, 0);
                                    fatLabCal.set(Calendar.MILLISECOND, 0);
                                    fatLabStartDateUtc = fatLabCal.getTimeInMillis();
                                }

                                SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                                lidDateFormatted = displayFmt.format(lidCandidate.getTime());

                                if (layoutLidBanner != null) {
                                    layoutLidBanner.setVisibility(View.VISIBLE);
                                }
                                if (textLidBanner != null) {
                                    textLidBanner.setText("📌 Semester Ends on LID: " + lidDateFormatted
                                            + " • Attendance counted only until then");
                                }
                                if (textDateRangeLidNote != null) {
                                    textDateRangeLidNote.setText(
                                            "* Attendance is strictly counted up to the Last Instructional Day (LID: "
                                                    + lidDateFormatted + ") and never beyond.");
                                }
                            }
                        }, throwable -> android.util.Log.w("AttendanceCalc", "Could not query calendar events for LID",
                                throwable)));
    }

    private void setupTargetListeners() {
        if (chipGroupTargets != null) {
            chipGroupTargets.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty())
                    return;
                int id = checkedIds.get(0);
                if (id == R.id.chip_target_75) {
                    inputTarget.setText("75");
                } else if (id == R.id.chip_target_80) {
                    inputTarget.setText("80");
                } else if (id == R.id.chip_target_85) {
                    inputTarget.setText("85");
                } else if (id == R.id.chip_target_90) {
                    inputTarget.setText("90");
                }
            });
        }

        inputTarget.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                runTargetSimulation();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        runTargetSimulation();
    }

    private void runTargetSimulation() {
        if (inputTarget == null || textSimHeadline == null || textSimResult == null)
            return;

        String targetStr = inputTarget.getText() != null ? inputTarget.getText().toString().trim() : "75";
        if (TextUtils.isEmpty(targetStr)) {
            textSimHeadline.setText("Enter a Target %");
            textSimResult.setText("Type a percentage above to calculate attendance feasibility.");
            if (textSurplusSkipBadge != null)
                textSurplusSkipBadge.setVisibility(View.GONE);
            if (textTargetSimPointOfNoReturn != null)
                textTargetSimPointOfNoReturn.setVisibility(View.GONE);
            return;
        }

        float targetPct;
        try {
            targetPct = Float.parseFloat(targetStr);
        } catch (Exception e) {
            return;
        }

        if (targetPct <= 0 || targetPct >= 100) {
            textSimHeadline.setText("Target out of bounds");
            textSimResult.setText("Target percentage must be between 1% and 99%.");
            if (textSurplusSkipBadge != null)
                textSurplusSkipBadge.setVisibility(View.GONE);
            if (textTargetSimPointOfNoReturn != null)
                textTargetSimPointOfNoReturn.setVisibility(View.GONE);
            return;
        }

        CourseItem selected = (CourseItem) spinnerCourses.getSelectedItem();
        if (selected == null && !courseItemList.isEmpty()) {
            selected = courseItemList.get(0);
        }
        if (selected == null || selected.total <= 0) {
            textSimHeadline.setText("No Data Found");
            textSimResult.setText("Please sync your attendance data first.");
            if (textSurplusSkipBadge != null)
                textSurplusSkipBadge.setVisibility(View.GONE);
            if (textTargetSimPointOfNoReturn != null)
                textTargetSimPointOfNoReturn.setVisibility(View.GONE);
            return;
        }

        int curAttended = selected.attended;
        int curTotal = selected.total;
        float curPct = ((float) curAttended / curTotal) * 100f;

        int remaining = "ALL".equals(selected.code) ? totalClassesRemainingOverall
                : remainingClassesMap.getOrDefault(selected.code, 0);
        boolean isLab = Boolean.TRUE.equals(isLabCourseMap.get(selected.code));

        if (isLab && isFatLabWeekActive) {
            remaining = 0;
        }

        // Show lab locked badge only if FAT Lab week is active
        if (textSurplusSkipBadge != null) {
            if (isLab && isFatLabWeekActive) {
                textSurplusSkipBadge.setVisibility(View.VISIBLE);
                textSurplusSkipBadge.setText("🔒 Lab Attendance Locked (FAT Lab Week)");
            } else {
                textSurplusSkipBadge.setVisibility(View.GONE);
            }
        }

        if (curPct >= targetPct) {
            int bunkable = (int) Math.floor(((curAttended * 100.0) / targetPct) - curTotal);
            bunkable = Math.max(0, bunkable);

            layoutSimResult.setBackgroundResource(R.drawable.bg_sim_safe);
            if (bunkable > 0) {
                textSimHeadline.setText(String.format(Locale.ENGLISH, "🎉 You can safely bunk %d %s!",
                        bunkable, bunkable == 1 ? "class" : "classes"));
                float afterBunkPct = (float) curAttended / (curTotal + bunkable) * 100f;
                textSimResult.setText(String.format(Locale.ENGLISH,
                        "Current is %.2f%% (%d/%d). Missing %d more %s keeps your standing at %.2f%% (Target: %.2f%%).",
                        curPct, curAttended, curTotal, bunkable, bunkable == 1 ? "class" : "classes", afterBunkPct,
                        targetPct));
            } else {
                textSimHeadline.setText("⚡ On the edge! Cannot miss any classes.");
                textSimResult.setText(String.format(Locale.ENGLISH,
                        "Current attendance is %.2f%%. Any absence will drop you below your %.2f%% target.",
                        curPct, targetPct));
            }

            if (textTargetSimPointOfNoReturn != null) {
                if (isLab && isFatLabWeekActive) {
                    textTargetSimPointOfNoReturn.setVisibility(View.VISIBLE);
                    textTargetSimPointOfNoReturn.setText(
                            "🔒 Lab Attendance Locked: FAT Lab week is active. Lab attendance was locked a week before LID. No future classes remain for this lab.");
                    textTargetSimPointOfNoReturn.setTextColor(0xFF38BDF8);
                } else {
                    textTargetSimPointOfNoReturn.setVisibility(View.GONE);
                }
            }
        } else {

            int mustAttend = (int) Math.ceil(((targetPct * curTotal) - (100.0 * curAttended)) / (100.0 - targetPct));
            mustAttend = Math.max(1, mustAttend);

            layoutSimResult.setBackgroundResource(R.drawable.bg_sim_warning);
            textSimHeadline.setText(String.format(Locale.ENGLISH, "⚠️ Attend next %d consecutive %s!",
                    mustAttend, mustAttend == 1 ? "class" : "classes"));
            float afterAttendPct = (float) (curAttended + mustAttend) / (curTotal + mustAttend) * 100f;
            textSimResult.setText(String.format(Locale.ENGLISH,
                    "Current is %.2f%% (%d/%d). Attend %d %s in a row without missing to recover to %.2f%%.",
                    curPct, curAttended, curTotal, mustAttend, mustAttend == 1 ? "class" : "classes", afterAttendPct));

            if (textTargetSimPointOfNoReturn != null) {
                if (isLab && isFatLabWeekActive) {
                    textTargetSimPointOfNoReturn.setVisibility(View.VISIBLE);
                    textTargetSimPointOfNoReturn.setText(
                            "🔒 Lab Attendance Locked: FAT Lab week is active. Lab attendance was locked a week before LID. It is impossible to attend more lab classes.");
                    textTargetSimPointOfNoReturn.setTextColor(0xFFF43F5E);
                } else {
                    textTargetSimPointOfNoReturn.setVisibility(View.GONE);
                }
            }
        }
    }

    private void calculateDateRangeImpact() {
        try {
            if (startDateUtc == null || endDateUtc == null) {
                String missingDate = startDateUtc == null
                        ? (endDateUtc == null ? "both start and end dates" : "start date")
                        : "end date";
                Toast.makeText(requireContext(), "Please pick " + missingDate, Toast.LENGTH_SHORT).show();
                return;
            }
            if (endDateUtc < startDateUtc) {
                Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                return;
            }

            if (lidDateUtc != null) {
                if (startDateUtc > lidDateUtc) {
                    Toast.makeText(requireContext(), "Selected start date is after Last Instructional Day ("
                            + lidDateFormatted + "). No attendance counted.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (endDateUtc > lidDateUtc) {
                    endDateUtc = lidDateUtc;
                    inputEnd.setText(lidDateFormatted);
                    Toast.makeText(requireContext(),
                            "Date range capped at Last Instructional Day (LID: " + lidDateFormatted + ")",
                            Toast.LENGTH_SHORT).show();
                }
            }

            long daysDiff = (endDateUtc - startDateUtc) / (1000 * 60 * 60 * 24) + 1;
            if (daysDiff > 365) {
                Toast.makeText(requireContext(), "Date range too large (max 365 days)", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences sp = SettingsRepository.getSharedPreferences(requireContext());
            int total = sp.getInt("totalClasses", 0);
            int attended = sp.getInt("attendedClasses", 0);

            buttonApply.setEnabled(false);
            computeSlotMissesAndPreview(total, attended);
        } catch (Exception e) {
            android.util.Log.e("AttendanceCalc", "Error calculating attendance", e);
            Toast.makeText(requireContext(), "Error calculating attendance: " + e.getMessage(), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private final Map<Integer, Integer> lastSlotMissed = new HashMap<>();
    private final List<SlotPreview> lastSlotPreview = new ArrayList<>();

    private void computeSlotMissesAndPreview(int overallTotal, int overallAttended) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        TimetableDao timetableDao = db.timetableDao();
        lastSlotMissed.clear();
        lastSlotPreview.clear();

        List<Single<DaySchedule>> timetableRequests = new ArrayList<>();
        List<com.salmanmalvasi.studentcc.models.CalendarEvent> allCalendarEvents = new ArrayList<>();
        try {
            allCalendarEvents = db.calendarEventDao().getAll().blockingGet();
        } catch (Exception ignored) {
        }

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startDateUtc);
        SimpleDateFormat mFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

        while (c.getTimeInMillis() <= endDateUtc) {
            if (lidDateUtc != null && c.getTimeInMillis() > lidDateUtc) {
                // Strictly stop counting at LID
                break;
            }

            int calDay = c.get(Calendar.DAY_OF_WEEK);
            if (calDay == Calendar.SUNDAY) {
                c.add(Calendar.DATE, 1);
                continue;
            }

            boolean isHoliday = false;
            int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
            String monthLabel = mFormat.format(c.getTime());

            if (allCalendarEvents != null) {
                for (com.salmanmalvasi.studentcc.models.CalendarEvent event : allCalendarEvents) {
                    if (event != null && event.date == dayOfMonth && monthLabel.equalsIgnoreCase(event.month)) {
                        String type = event.type != null ? event.type.toLowerCase(Locale.ENGLISH) : "";
                        String text = event.text != null ? event.text.toLowerCase(Locale.ENGLISH) : "";
                        if (type.contains("holiday") || text.contains("holiday") ||
                                text.contains("no instructional") || text.contains("non-instructional") ||
                                text.contains("vacation")) {
                            isHoliday = true;
                            break;
                        }
                    }
                }
            }

            if (!isHoliday) {
                int dayIndex = calDay - 1; // 0..6
                final boolean isFatLabDay = fatLabStartDateUtc != null && c.getTimeInMillis() >= fatLabStartDateUtc;
                timetableRequests.add(timetableDao.get(dayIndex).map(list -> new DaySchedule(list, isFatLabDay)));
            }
            c.add(Calendar.DATE, 1);
        }

        if (timetableRequests.isEmpty()) {
            Toast.makeText(requireContext(), "No classes scheduled in the selected range", Toast.LENGTH_SHORT).show();
            coursePreview.removeAllViews();
            buttonApply.setEnabled(false);
            return;
        }

        final boolean skipOnePerDay = chipLeaveOnePerDay != null && chipLeaveOnePerDay.isChecked();

        disposables.add(
                Single.zip(timetableRequests, objects -> {
                    Map<Integer, Integer> previewCounts = new HashMap<>();
                    Map<Integer, Integer> applyCounts = new HashMap<>();

                    for (Object obj : objects) {
                        DaySchedule daySchedule = (DaySchedule) obj;
                        List<Timetable.AllData> dayData = daySchedule.data;
                        if (dayData == null)
                            continue;

                        if (skipOnePerDay) {
                            for (Timetable.AllData item : dayData) {
                                if (item == null || item.slotId == 0)
                                    continue;
                                boolean isLab = isLabCourse(item.courseCode, item.courseType);
                                if (daySchedule.isFatLabDay && isLab)
                                    continue; // Lab attendance locked during FAT Lab week
                                int previewIncrement = isLab ? 2 : 1;
                                previewCounts.put(item.slotId,
                                        previewCounts.getOrDefault(item.slotId, 0) + previewIncrement);
                                applyCounts.put(item.slotId, applyCounts.getOrDefault(item.slotId, 0) + 1);
                                break; // Only 1 class skipped for this day
                            }
                        } else {
                            for (Timetable.AllData item : dayData) {
                                if (item == null || item.slotId == 0)
                                    continue;
                                boolean isLab = isLabCourse(item.courseCode, item.courseType);
                                if (daySchedule.isFatLabDay && isLab)
                                    continue; // Lab attendance locked during FAT Lab week
                                int previewIncrement = isLab ? 2 : 1;
                                previewCounts.put(item.slotId,
                                        previewCounts.getOrDefault(item.slotId, 0) + previewIncrement);
                                applyCounts.put(item.slotId, applyCounts.getOrDefault(item.slotId, 0) + 1);
                            }
                        }
                    }
                    return new Object[] { previewCounts, applyCounts };
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(pair -> {
                            @SuppressWarnings("unchecked")
                            Map<Integer, Integer> previewCounts = (Map<Integer, Integer>) pair[0];
                            @SuppressWarnings("unchecked")
                            Map<Integer, Integer> applyCounts = (Map<Integer, Integer>) pair[1];

                            lastSlotMissed.clear();
                            lastSlotMissed.putAll(applyCounts);
                            renderSlotPreview(db, previewCounts);
                        }, throwable -> {
                            android.util.Log.e("AttendanceCalc", "Error counting missed slots", throwable);
                            Toast.makeText(requireContext(), "Error computing missed slots", Toast.LENGTH_SHORT).show();
                        }));

    }

    private void renderSlotPreview(AppDatabase db, Map<Integer, Integer> slotMissedMap) {
        coursePreview.removeAllViews();
        buttonApply.setEnabled(false);

        if (slotMissedMap == null || slotMissedMap.isEmpty()) {
            Toast.makeText(requireContext(), "No classes found for the selected range", Toast.LENGTH_SHORT).show();
            return;
        }

        CoursesDao coursesDao = db.coursesDao();
        List<Single<SlotPreview>> previewSingles = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : slotMissedMap.entrySet()) {
            final int slotId = entry.getKey();
            final int missedCount = entry.getValue();

            previewSingles.add(
                    coursesDao.getCourse(slotId)
                            .onErrorReturn(throwable -> new Course.AllData())
                            .map(courseData -> {
                                String slotLabel = (courseData != null && !TextUtils.isEmpty(courseData.slot))
                                        ? courseData.slot
                                        : "Slot " + slotId;
                                String courseCode = courseData != null ? courseData.courseCode : null;
                                String courseTitle = courseData != null ? courseData.courseTitle : null;
                                return new SlotPreview(slotId, slotLabel, courseCode, courseTitle, missedCount);
                            }));
        }

        disposables.add(
                Single.zip(previewSingles, objects -> {
                    List<SlotPreview> previews = new ArrayList<>(objects.length);
                    for (Object obj : objects) {
                        previews.add((SlotPreview) obj);
                    }
                    Collections.sort(previews, (a, b) -> {
                        String codeA = a.courseCode != null ? a.courseCode : "";
                        String codeB = b.courseCode != null ? b.courseCode : "";
                        return codeA.compareToIgnoreCase(codeB);
                    });
                    return previews;
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(previewList -> {
                            lastSlotPreview.clear();
                            lastSlotPreview.addAll(previewList);

                            coursePreview.removeAllViews();
                            for (SlotPreview preview : previewList) {
                                Chip chip = new Chip(requireContext());
                                chip.setText(buildPreviewLabel(preview));
                                chip.setCheckable(false);
                                chip.setClickable(false);
                                coursePreview.addView(chip);
                            }

                            boolean hasResults = !previewList.isEmpty();
                            buttonApply.setEnabled(hasResults);
                            if (hasResults) {
                                Toast.makeText(requireContext(),
                                        "Found " + previewList.size() + " affected course slots", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }, throwable -> {
                            android.util.Log.e("AttendanceCalc", "Slot preview error", throwable);
                            Toast.makeText(requireContext(), "Error preparing preview: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }));
    }

    private String buildPreviewLabel(SlotPreview preview) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(preview.courseCode)) {
            sb.append(preview.courseCode);
        } else if (!TextUtils.isEmpty(preview.courseTitle)) {
            sb.append(preview.courseTitle);
        } else {
            sb.append("Course");
        }
        if (!TextUtils.isEmpty(preview.slotLabel)) {
            sb.append(" (").append(preview.slotLabel).append(")");
        }
        sb.append(" : -").append(preview.missedCount).append(preview.missedCount == 1 ? " class" : " classes");
        return sb.toString();
    }

    private void applyChanges() {
        if (lastSlotMissed.isEmpty()) {
            Toast.makeText(requireContext(), "No changes to apply", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        AttendanceDao attendanceDao = db.attendanceDao();

        List<Completable> updates = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : lastSlotMissed.entrySet()) {
            final int slotId = entry.getKey();
            final int missedCount = entry.getValue();
            if (missedCount <= 0)
                continue;

            updates.add(
                    attendanceDao.getAttendanceByCourseId(slotId)
                            .flatMapCompletable(att -> {
                                if (att == null)
                                    return Completable.complete();
                                int newTotal = (att.total != null ? att.total : 0) + missedCount;
                                int newPct = newTotal > 0
                                        ? (int) Math
                                                .round(((att.attended != null ? att.attended : 0) * 100.0) / newTotal)
                                        : 0;
                                return attendanceDao.updateTotals(att.courseId, newTotal, newPct);
                            })
                            .onErrorComplete());
        }

        disposables.add(
                Completable.merge(updates)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            Toast.makeText(requireContext(), "Attendance preview applied successfully",
                                    Toast.LENGTH_LONG).show();
                            buttonApply.setEnabled(false);
                            setupOverviewAndCourses();
                        }, throwable -> {
                            android.util.Log.e("AttendanceCalc", "Apply error", throwable);
                            Toast.makeText(requireContext(), "Error applying changes: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }));
    }

    private static class DaySchedule {
        final List<Timetable.AllData> data;
        final boolean isFatLabDay;

        DaySchedule(List<Timetable.AllData> data, boolean isFatLabDay) {
            this.data = data;
            this.isFatLabDay = isFatLabDay;
        }
    }

    private boolean isLabCourse(String courseCode, String type) {
        if (courseCode != null && Boolean.TRUE.equals(isLabCourseMap.get(courseCode))) {
            return true;
        }
        if (type != null) {
            String lower = type.toLowerCase(Locale.ENGLISH);
            return lower.contains("lab") || lower.contains("practical") || lower.contains("ela");
        }
        return false;
    }

    private static class SlotPreview {
        final int slotId;
        final String slotLabel;
        final String courseCode;
        final String courseTitle;
        final int missedCount;

        SlotPreview(int slotId, String slotLabel, String courseCode, String courseTitle, int missedCount) {
            this.slotId = slotId;
            this.slotLabel = slotLabel;
            this.courseCode = courseCode;
            this.courseTitle = courseTitle;
            this.missedCount = missedCount;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllData();
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
        disposables.clear();
    }

    private void showDatePicker(EditText editText) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .build();
        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            editText.setText(sdf.format(calendar.getTime()));

            long normalizedTime = normalizeToMidnight(selection);
            if (editText == inputStart) {
                startDateUtc = normalizedTime;
            } else if (editText == inputEnd) {
                endDateUtc = normalizedTime;
            }
        });
    }

    private long normalizeToMidnight(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void setupSkipPresetChip(View parent, int chipId, int count) {
        View chip = parent.findViewById(chipId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                singleSkipCount = count;
                updateSingleSkipUI();
            });
        }
    }

    private void showCustomSkipCountDialog() {
        if (!isAdded() || getContext() == null) return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 - 100");
        input.setText(String.valueOf(singleSkipCount));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Simulate Skipping Classes")
                .setMessage("Enter the number of classes you want to simulate skipping (1 to 100):")
                .setView(container)
                .setPositiveButton("Simulate", (dialog, which) -> {
                    String str = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(str)) {
                        try {
                            int count = Integer.parseInt(str);
                            if (count < 1) count = 1;
                            if (count > 100) count = 100;
                            singleSkipCount = count;
                            updateSingleSkipUI();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


}
