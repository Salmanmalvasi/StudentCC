package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import tk.therealsuji.vtopchennai.helpers.FirebaseAnalyticsHelper;

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

        // Track screen view
        FirebaseAnalyticsHelper.trackScreenView(requireContext(), "AttendanceCalculator", "AttendanceCalculatorFragment");

        inputStart = view.findViewById(R.id.input_start_date);
        inputEnd = view.findViewById(R.id.input_end_date);

        coursePreview = view.findViewById(R.id.layout_course_preview);
        buttonApply = view.findViewById(R.id.button_apply);

        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        inputStart.setOnClickListener(v -> showDatePicker(true));
        inputEnd.setOnClickListener(v -> showDatePicker(false));

        MaterialButton calculate = view.findViewById(R.id.button_calculate);
        calculate.setOnClickListener(v -> {
            try {
                FirebaseAnalyticsHelper.trackButtonPress(requireContext(), "Calculate Attendance", "AttendanceCalculator");

                if (startDateUtc == null || endDateUtc == null) {
                    Toast.makeText(requireContext(), "Please select both start and end dates", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (endDateUtc < startDateUtc) {
                    Toast.makeText(requireContext(), "End date must be after or equal to start date", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Calculate number of days
                long daysDiff = (endDateUtc - startDateUtc) / (1000 * 60 * 60 * 24) + 1;
                
                // Check for reasonable date range
                if (daysDiff > 365) {
                    Toast.makeText(requireContext(), "Date range cannot exceed 1 year", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (daysDiff > 365) {
                    Toast.makeText(requireContext(), "Date range too large. Please select a range within one year.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Get current attendance data from shared preferences for calculation
                android.content.SharedPreferences sp = tk.therealsuji.vtopchennai.helpers.SettingsRepository.getSharedPreferences(requireContext());
                int total = sp.getInt("totalClasses", 0);
                int attended = sp.getInt("attendedClasses", 0);

                // Compute per-course missed counts using timetable
                computePerCourseMissesAndPreview(total, attended);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error calculating attendance", Toast.LENGTH_SHORT).show();
            }
        });

        buttonApply.setOnClickListener(v -> {
            FirebaseAnalyticsHelper.trackButtonPress(requireContext(), "Apply Attendance Changes", "AttendanceCalculator");
            applyChanges();
        });

        return view;
    }



    private Map<String, Integer> lastCourseMissed = new HashMap<>();
    private void computePerCourseMissesAndPreview(int overallTotal, int overallAttended) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        TimetableDao timetableDao = db.timetableDao();
        lastCourseMissed.clear();

        // Collect all timetable data for the date range first
        List<Single<List<Timetable.AllData>>> timetableRequests = new ArrayList<>();

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startDateUtc);
        while (c.getTimeInMillis() <= endDateUtc) {
            int calDay = c.get(Calendar.DAY_OF_WEEK); // 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
            // Convert Calendar day to TimetableDao day mapping:
            // Calendar: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
            // TimetableDao: 0=Sun(default), 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
            int daoDay;
            if (calDay == Calendar.SUNDAY) {
                daoDay = 0; // Sunday -> default case in TimetableDao
            } else {
                daoDay = calDay - 1; // Mon(2)->1, Tue(3)->2, Wed(4)->3, Thu(5)->4, Fri(6)->5, Sat(7)->6
            }
            timetableRequests.add(timetableDao.get(daoDay));
            c.add(Calendar.DATE, 1);
        }

        // Process all timetable data together
        disposables.add(
                Single.zip(timetableRequests, objects -> {
                    // Combine all timetable data - use courseCode like original
                    for (Object obj : objects) {
                        @SuppressWarnings("unchecked")
                        List<Timetable.AllData> dayData = (List<Timetable.AllData>) obj;
                        if (dayData != null) {
                            for (Timetable.AllData item : dayData) {
                                // Use courseCode for grouping (original behavior)
                                String courseKey = item.courseCode;
                                if (courseKey != null && !courseKey.trim().isEmpty()) {
                                    lastCourseMissed.put(courseKey, lastCourseMissed.getOrDefault(courseKey, 0) + 1);
                                }
                            }
                        }
                    }
                    return lastCourseMissed;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(courseMissedMap -> {
                    // Render per-course preview
                    coursePreview.removeAllViews();
                    if (lastCourseMissed.isEmpty()) {
                        TextView emptyView = new TextView(requireContext());
                        emptyView.setText("No classes found for the selected date range");
                        emptyView.setTextSize(14);
                        emptyView.setTextColor(requireContext().getColor(android.R.color.secondary_text_dark));
                        emptyView.setPadding(16, 16, 16, 16);
                        coursePreview.addView(emptyView);
                    } else {
                        for (Map.Entry<String, Integer> entry : lastCourseMissed.entrySet()) {
                            TextView tv = new TextView(requireContext());
                            tv.setText(entry.getKey() + ": " + entry.getValue() + " missed classes");
                            tv.setTextSize(16);
                            tv.setPadding(16, 8, 16, 8);
                            tv.setBackground(requireContext().getDrawable(R.drawable.background_course_item));
                            android.view.ViewGroup.MarginLayoutParams params = new android.view.ViewGroup.MarginLayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            params.setMargins(0, 8, 0, 8);
                            tv.setLayoutParams(params);
                            coursePreview.addView(tv);
                        }
                    }
                    buttonApply.setEnabled(!lastCourseMissed.isEmpty());

                    // Log for debugging
                    android.util.Log.d("AttendanceCalc", "Found courses: " + lastCourseMissed.keySet());

                    if (lastCourseMissed.isEmpty()) {
                        Toast.makeText(requireContext(), "No classes found for selected date range", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Found " + lastCourseMissed.size() + " courses with classes", Toast.LENGTH_SHORT).show();
                    }
                }, throwable -> {
                    android.util.Log.e("AttendanceCalc", "Error fetching timetable data", throwable);
                    Toast.makeText(requireContext(), "Unable to fetch timetable data. Please try syncing your data first.", Toast.LENGTH_LONG).show();
                    
                    // Show empty state
                    coursePreview.removeAllViews();
                    TextView errorView = new TextView(requireContext());
                    errorView.setText("Error loading timetable data. Please sync your data and try again.");
                    errorView.setTextSize(14);
                    errorView.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
                    errorView.setPadding(16, 16, 16, 16);
                    coursePreview.addView(errorView);
                    buttonApply.setEnabled(false);
                })
        );
    }

    private void applyChanges() {
        if (lastCourseMissed.isEmpty()) return;
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AttendanceDao attendanceDao = db.attendanceDao();
        CoursesDao coursesDao = db.coursesDao();

        android.util.Log.d("AttendanceCalc", "Applying changes for courses: " + lastCourseMissed.keySet());

        // Track completion and total missed classes
        final int[] processedCourses = {0};
        final int totalCourses = lastCourseMissed.size();
        final int[] totalMissedClasses = {0};

        // For each course code, directly update attendance
        for (Map.Entry<String, Integer> entry : lastCourseMissed.entrySet()) {
            String courseCode = entry.getKey();
            int missed = entry.getValue();
            totalMissedClasses[0] += missed;

            android.util.Log.d("AttendanceCalc", "Processing course: " + courseCode + " with " + missed + " missed classes");

            // Process course directly using course code
            processCourseByCode(courseCode, missed, processedCourses, totalCourses, totalMissedClasses[0], db);


        }
        Toast.makeText(requireContext(), "Applied simulated changes", Toast.LENGTH_SHORT).show();
    }

    private void processCourseByCode(String courseCode, int missed, int[] processedCourses, int totalCourses, int totalMissedClasses, AppDatabase db) {
        android.util.Log.d("AttendanceCalc", "Processing course code: " + courseCode + " with " + missed + " missed classes");

        disposables.add(
                db.coursesDao().getCourse(courseCode)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(courseDataList -> {
                            if (courseDataList != null && !courseDataList.isEmpty()) {
                                tk.therealsuji.vtopchennai.models.Course.AllData data = courseDataList.get(0);

                                if (data.attendanceTotal == null || data.attendanceAttended == null) {
                                    android.util.Log.w("AttendanceCalc", "Null attendance data for " + courseCode);
                                    processedCourses[0]++;
                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                                    return;
                                }

                                int newTotal = Math.max(0, data.attendanceTotal + missed);
                                int newPercentage = newTotal == 0 ? 0 : calculateAttendancePercentage(data.attendanceAttended, newTotal);

                                android.util.Log.d("AttendanceCalc", "Course " + courseCode + " - New Total: " + newTotal + ", New Percentage: " + newPercentage);

                                // Update attendance using the course code
                                disposables.add(
                                        db.coursesDao().getCourseIdByCode(courseCode)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(courseId -> {
                                                    if (courseId != null) {
                                                        disposables.add(
                                                                db.attendanceDao().updateTotals(courseId, newTotal, newPercentage)
                                                                        .subscribeOn(Schedulers.io())
                                                                        .observeOn(AndroidSchedulers.mainThread())
                                                                        .subscribe(() -> {
                                                                            android.util.Log.d("AttendanceCalc", "Successfully updated attendance for " + courseCode);
                                                                            processedCourses[0]++;
                                                                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                                                                        }, err -> {
                                                                            android.util.Log.e("AttendanceCalc", "Error updating attendance for " + courseCode, err);
                                                                            processedCourses[0]++;
                                                                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                                                                        })
                                                        );
                                                    } else {
                                                        android.util.Log.w("AttendanceCalc", "Null course ID for " + courseCode);
                                                        processedCourses[0]++;
                                                        checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                                                    }
                                                }, err -> {
                                                    android.util.Log.e("AttendanceCalc", "Error getting course ID for " + courseCode, err);
                                                    processedCourses[0]++;
                                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                                                })
                                );
                            } else {
                                android.util.Log.w("AttendanceCalc", "No course data found for code: " + courseCode);
                                processedCourses[0]++;
                                checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                            }
                        }, err -> {
                            android.util.Log.e("AttendanceCalc", "Error getting course data for code: " + courseCode, err);
                            processedCourses[0]++;
                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses);
                        })
        );
    }

    private void checkCompletion(int processedCourses, int totalCourses, int totalMissedClasses) {
        if (processedCourses >= totalCourses) {
            // All courses processed, update overall attendance
            updateOverallAttendance(totalMissedClasses);
        }
    }

    // Debug helper removed

    private void updateOverallAttendance(int missedClasses) {
        // Update shared preferences to reflect new total classes
        android.content.SharedPreferences sp = tk.therealsuji.vtopchennai.helpers.SettingsRepository.getSharedPreferences(requireContext());
        int currentTotal = sp.getInt("totalClasses", 0);
        int currentAttended = sp.getInt("attendedClasses", 0);

        int newTotal = currentTotal + missedClasses;
        int newOverallAttendance = newTotal == 0 ? 0 : calculateAttendancePercentage(currentAttended, newTotal);

        sp.edit()
                .putInt("totalClasses", newTotal)
                .putInt("overallAttendance", newOverallAttendance)
                .apply();

        android.util.Log.d("AttendanceCalc", "Updated shared preferences - Total: " + currentTotal + " -> " + newTotal +
                          ", Overall: " + sp.getInt("overallAttendance", 0) + "%");

        // Force refresh the home page attendance display
        refreshHomePageAttendance();
    }

    private void refreshHomePageAttendance() {
        // This will trigger a refresh of the home page attendance display
        android.util.Log.d("AttendanceCalc", "Requesting home page attendance refresh");
        if (getContext() != null) {
            // Calculate total courses affected
            int totalCourses = lastCourseMissed.size();
            int totalMissed = lastCourseMissed.values().stream().mapToInt(Integer::intValue).sum();
            
            String message = String.format("Updated %d course%s with %d missed class%s. " +
                                          "Please refresh the home page to see changes.",
                                          totalCourses, totalCourses != 1 ? "s" : "",
                                          totalMissed, totalMissed != 1 ? "es" : "");
            
            android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_LONG).show();
        }
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private boolean isValidDateRange() {
        if (startDateUtc == null || endDateUtc == null) {
            return false;
        }
        
        // Check if start date is before or equal to end date
        if (endDateUtc < startDateUtc) {
            Toast.makeText(getContext(), "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        // Check if the range is reasonable (not more than 1 year)
        long daysDiff = (endDateUtc - startDateUtc) / (1000 * 60 * 60 * 24) + 1;
        
        if (daysDiff > 365) {
            Toast.makeText(getContext(), "Date range cannot exceed 1 year", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        return true;
    }

    private void showDatePicker(boolean isStart) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? R.string.start_date : R.string.end_date)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (isStart) {
                startDateUtc = normalizeToMidnight(selection);
                inputStart.setText(picker.getHeaderText());
            } else {
                endDateUtc = normalizeToMidnight(selection);
                inputEnd.setText(picker.getHeaderText());
            }
        });
        picker.show(getParentFragmentManager(), isStart ? "startDate" : "endDate");
    }

    private long normalizeToMidnight(long utcMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(utcMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Calculate attendance percentage with proper ceiling logic.
     * 9.xx becomes 9 (floor behavior), not 10 (ceiling behavior).
     * This matches the original StudentCC logic.
     */
    private int calculateAttendancePercentage(int attended, int total) {
        if (total == 0) return 0;
        
        double percentage = (attended * 100.0) / total;
        // Use floor instead of ceiling - 9.99 becomes 9, not 10
        return (int) Math.floor(percentage);
    }


}
