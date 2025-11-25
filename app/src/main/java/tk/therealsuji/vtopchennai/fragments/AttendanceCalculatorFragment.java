package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

import tk.therealsuji.vtopchennai.R;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.interfaces.AttendanceDao;
import tk.therealsuji.vtopchennai.models.Timetable;
import tk.therealsuji.vtopchennai.models.Course;

public class AttendanceCalculatorFragment extends Fragment {

    private TextInputEditText inputStart;
    private TextInputEditText inputEnd;

    private ViewGroup coursePreview;
    private MaterialButton buttonApply;

    private Long startDateUtc = null;
    private Long endDateUtc = null;

    private final CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attendance_calculator, container, false);

        inputStart = view.findViewById(R.id.input_start_date);
        inputEnd = view.findViewById(R.id.input_end_date);

        coursePreview = view.findViewById(R.id.layout_course_preview);
        buttonApply = view.findViewById(R.id.button_apply);

        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        inputStart.setOnClickListener(v -> showDatePicker(inputStart));
        inputEnd.setOnClickListener(v -> showDatePicker(inputEnd));

        MaterialButton calculate = view.findViewById(R.id.button_calculate);
        calculate.setOnClickListener(v -> {
            try {
                android.util.Log.d("AttendanceCalc", "Calculate button clicked. Start: " + startDateUtc + ", End: " + endDateUtc);

                if (startDateUtc == null || endDateUtc == null) {
                    String missingDate = startDateUtc == null ? (endDateUtc == null ? "both start and end dates" : "start date") : "end date";
                    Toast.makeText(requireContext(), "Please pick " + missingDate, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (endDateUtc < startDateUtc) {
                    Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Calculate the date range for user feedback
                long daysDiff = (endDateUtc - startDateUtc) / (1000 * 60 * 60 * 24) + 1;
                android.util.Log.d("AttendanceCalc", "Calculating for " + daysDiff + " days");

                if (daysDiff > 365) {
                    Toast.makeText(requireContext(), "Date range too large (max 365 days)", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Get current attendance data from shared preferences for calculation
                android.content.SharedPreferences sp = tk.therealsuji.vtopchennai.helpers.SettingsRepository.getSharedPreferences(requireContext());
                int total = sp.getInt("totalClasses", 0);
                int attended = sp.getInt("attendedClasses", 0);

                Toast.makeText(requireContext(), "Calculating attendance for " + daysDiff + " days...", Toast.LENGTH_SHORT).show();

                // Compute per-slot missed counts using timetable
                buttonApply.setEnabled(false);
                computeSlotMissesAndPreview(total, attended);
            } catch (Exception e) {
                android.util.Log.e("AttendanceCalc", "Error calculating attendance", e);
                Toast.makeText(requireContext(), "Error calculating attendance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        buttonApply.setOnClickListener(v -> applyChanges());

        return view;
    }

    private void renderSlotPreview(AppDatabase db, Map<Integer, Integer> slotMissedMap) {
        coursePreview.removeAllViews();
        buttonApply.setEnabled(false);

        if (slotMissedMap == null || slotMissedMap.isEmpty()) {
            android.util.Log.i("AttendanceCalc", "No slot misses found for preview");
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
                .onErrorReturn(throwable -> {
                android.util.Log.w("AttendanceCalc", "Course lookup failed for slot " + slotId, throwable);
                return new Course.AllData();
                })
                            .map(courseData -> {
                                String slotLabel = (courseData != null && !TextUtils.isEmpty(courseData.slot))
                                        ? courseData.slot
                                        : "Slot " + slotId;
                                String courseCode = courseData != null ? courseData.courseCode : null;
                                String courseTitle = courseData != null ? courseData.courseTitle : null;
                                return new SlotPreview(slotId, slotLabel, courseCode, courseTitle, missedCount);
                            })
            );
        }

        disposables.add(
                Single.zip(previewSingles, objects -> {
                    List<SlotPreview> previews = new ArrayList<>(objects.length);
                    for (Object obj : objects) {
                        previews.add((SlotPreview) obj);
                    }
                    Collections.sort(previews, (a, b) -> {
                        String labelA = TextUtils.isEmpty(a.slotLabel) ? "" : a.slotLabel;
                        String labelB = TextUtils.isEmpty(b.slotLabel) ? "" : b.slotLabel;
                        int labelCompare = labelA.compareToIgnoreCase(labelB);
                        if (labelCompare != 0) return labelCompare;
                        String codeA = TextUtils.isEmpty(a.courseCode) ? "" : a.courseCode;
                        String codeB = TextUtils.isEmpty(b.courseCode) ? "" : b.courseCode;
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
                        Toast.makeText(requireContext(), "Found " + previewList.size() + " slots", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "No classes found for the selected range", Toast.LENGTH_SHORT).show();
                    }
                }, throwable -> {
                    android.util.Log.e("AttendanceCalc", "Slot preview error", throwable);
                    Toast.makeText(requireContext(), "Error preparing preview: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                })
        );
    }



    private final Map<Integer, Integer> lastSlotMissed = new HashMap<>();
    private final List<SlotPreview> lastSlotPreview = new ArrayList<>();

    private void computeSlotMissesAndPreview(int overallTotal, int overallAttended) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        TimetableDao timetableDao = db.timetableDao();
        lastSlotMissed.clear();
        lastSlotPreview.clear();

        // Collect all timetable data for the date range first
        List<Single<List<Timetable.AllData>>> timetableRequests = new ArrayList<>();

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startDateUtc);
        while (c.getTimeInMillis() <= endDateUtc) {
            int calDay = c.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
            int dayIndex = calDay - 1; // 0..6
            // Map 0..6 to TimetableDao.get switch: 0->Sunday(default), 1->Monday(1), ... 6->Saturday(6)
            int daoDay = dayIndex; // matches switch mapping used in TimetableDao.get
            timetableRequests.add(timetableDao.get(daoDay));
            c.add(Calendar.DATE, 1);
        }

        if (timetableRequests.isEmpty()) {
            android.util.Log.w("AttendanceCalc", "No timetable requests generated for the selected range");
            Toast.makeText(requireContext(), "No classes scheduled in the selected range", Toast.LENGTH_SHORT).show();
            coursePreview.removeAllViews();
            buttonApply.setEnabled(false);
            return;
        }

        // Process all timetable data together
        disposables.add(
                Single.zip(timetableRequests, objects -> {
                    Map<Integer, Integer> previewCounts = new HashMap<>();
                    Map<Integer, Integer> applyCounts = new HashMap<>();

                    for (Object obj : objects) {
                        @SuppressWarnings("unchecked")
                        List<Timetable.AllData> dayData = (List<Timetable.AllData>) obj;
                        for (Timetable.AllData item : dayData) {
                            if (item == null || item.slotId == 0) continue;

                            boolean isLab = isLabCourseType(item.courseType);
                            int previewIncrement = isLab ? 2 : 1;
                            int applyIncrement = 1;

                            previewCounts.put(item.slotId, previewCounts.getOrDefault(item.slotId, 0) + previewIncrement);
                            applyCounts.put(item.slotId, applyCounts.getOrDefault(item.slotId, 0) + applyIncrement);
                        }
                    }
                    return new SlotAggregation(previewCounts, applyCounts);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(aggregation -> {
                    lastSlotMissed.clear();
                    lastSlotMissed.putAll(aggregation.applyCounts);
                    renderSlotPreview(db, aggregation.previewCounts);
                }, throwable -> {
                    android.util.Log.e("AttendanceCalc", "Timetable error", throwable);
                    Toast.makeText(requireContext(), "Timetable error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                })
        );
    }

    private boolean isLabCourseType(@Nullable String courseType) {
        if (courseType == null) return false;
        String normalized = courseType.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return false;
        return "lab".equals(normalized) || "l".equals(normalized) || normalized.contains("lab");
    }

    private void applyChanges() {
        if (lastSlotMissed.isEmpty()) {
            Toast.makeText(requireContext(), "Run a calculation first", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        AttendanceDao attendanceDao = db.attendanceDao();
        CoursesDao coursesDao = db.coursesDao();

        android.util.Log.d("AttendanceCalc", "Applying changes for slots: " + lastSlotMissed.keySet());

        final int totalSlots = lastSlotMissed.size();
        final int[] processedSlots = {0};
        final int[] totalMissedClasses = {0};

        for (Map.Entry<Integer, Integer> entry : lastSlotMissed.entrySet()) {
            final int slotId = entry.getKey();
            final int missedCount = entry.getValue();

            disposables.add(
                    coursesDao.getCourse(slotId)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(courseData -> {
                                if (courseData == null || courseData.courseId == null) {
                                    android.util.Log.w("AttendanceCalc", "No course data found for slot " + slotId);
                                    processedSlots[0]++;
                                    checkCompletion(processedSlots[0], totalSlots, totalMissedClasses[0]);
                                    return;
                                }

                                Integer attendanceTotal = courseData.attendanceTotal;
                                Integer attendanceAttended = courseData.attendanceAttended;

                                if (attendanceTotal == null || attendanceAttended == null) {
                                    android.util.Log.w("AttendanceCalc", "Null attendance data for slot " + slotId);
                                    processedSlots[0]++;
                                    checkCompletion(processedSlots[0], totalSlots, totalMissedClasses[0]);
                                    return;
                                }

                                int newTotal = Math.max(0, attendanceTotal + missedCount);
                                int newPercentage = newTotal == 0 ? 0 : (int) Math.ceil((attendanceAttended * 100.0) / newTotal);

                                disposables.add(
                                        attendanceDao.updateTotals(courseData.courseId, newTotal, newPercentage)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(() -> {
                                                    android.util.Log.d("AttendanceCalc", "Updated attendance for slot " + slotId + " (courseId=" + courseData.courseId + ")");
                                                    totalMissedClasses[0] += missedCount;
                                                    processedSlots[0]++;
                                                    checkCompletion(processedSlots[0], totalSlots, totalMissedClasses[0]);
                                                }, err -> {
                                                    android.util.Log.e("AttendanceCalc", "Error updating attendance for slot " + slotId, err);
                                                    processedSlots[0]++;
                                                    checkCompletion(processedSlots[0], totalSlots, totalMissedClasses[0]);
                                                })
                                );
                            }, err -> {
                                android.util.Log.e("AttendanceCalc", "Error loading course for slot " + slotId, err);
                                processedSlots[0]++;
                                checkCompletion(processedSlots[0], totalSlots, totalMissedClasses[0]);
                            })
            );
        }

        Toast.makeText(requireContext(), "Applied simulated changes", Toast.LENGTH_SHORT).show();
    }

    private void checkCompletion(int processedCourses, int totalCourses, int totalMissedClasses) {
        if (processedCourses >= totalCourses) {
            // All courses processed, update overall attendance
            updateOverallAttendance(totalMissedClasses);
        }
    }

    private void updateOverallAttendance(int missedClasses) {
        // Update shared preferences to reflect new total classes
        android.content.SharedPreferences sp = tk.therealsuji.vtopchennai.helpers.SettingsRepository.getSharedPreferences(requireContext());
        int currentTotal = sp.getInt("totalClasses", 0);
        int currentAttended = sp.getInt("attendedClasses", 0);

        int newTotal = currentTotal + missedClasses;
        int newOverallAttendance = newTotal == 0 ? 0 : (int) Math.ceil((currentAttended * 100.0) / newTotal);

        sp.edit()
                .putInt("totalClasses", newTotal)
                .putInt("overallAttendance", newOverallAttendance)
                .apply();        // Force refresh the home page attendance display
        refreshHomePageAttendance();
    }

    private void refreshHomePageAttendance() {
        // This will trigger a refresh of the home page attendance display
        android.util.Log.d("AttendanceCalc", "Requesting home page attendance refresh");
        if (getContext() != null) {
            android.widget.Toast.makeText(
                getContext(),
                "Attendance updated! Please refresh the home page to see changes.",
                android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    private String buildPreviewLabel(SlotPreview preview) {
        StringBuilder label = new StringBuilder();
        String slotSegment = TextUtils.isEmpty(preview.slotLabel) ? ("Slot " + preview.slotId) : preview.slotLabel;
        label.append(slotSegment);

        if (!TextUtils.isEmpty(preview.courseCode)) {
            label.append(" · ").append(preview.courseCode);
        } else if (!TextUtils.isEmpty(preview.courseTitle)) {
            label.append(" · ").append(preview.courseTitle);
        }

        label.append(" : Missed ").append(preview.missedCount).append(preview.missedCount == 1 ? " class" : " classes");
        return label.toString();
    }

    private static class SlotAggregation {
        final Map<Integer, Integer> previewCounts;
        final Map<Integer, Integer> applyCounts;

        SlotAggregation(Map<Integer, Integer> previewCounts, Map<Integer, Integer> applyCounts) {
            this.previewCounts = previewCounts;
            this.applyCounts = applyCounts;
        }
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

            // Update the internal date variables based on which EditText was clicked
            long normalizedTime = normalizeToMidnight(selection);
            if (editText == inputStart) {
                startDateUtc = normalizedTime;
                android.util.Log.d("AttendanceCalc", "Start date set to: " + new Date(startDateUtc));
            } else if (editText == inputEnd) {
                endDateUtc = normalizedTime;
                android.util.Log.d("AttendanceCalc", "End date set to: " + new Date(endDateUtc));
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


}
