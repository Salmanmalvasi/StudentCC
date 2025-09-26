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

                // Compute per-course missed counts using timetable
                computePerCourseMissesAndPreview(total, attended);
            } catch (Exception e) {
                android.util.Log.e("AttendanceCalc", "Error calculating attendance", e);
                Toast.makeText(requireContext(), "Error calculating attendance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        buttonApply.setOnClickListener(v -> applyChanges());

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
            int calDay = c.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
            int dayIndex = calDay - 1; // 0..6
            // Map 0..6 to TimetableDao.get switch: 0->Sunday(default), 1->Monday(1), ... 6->Saturday(6)
            int daoDay = dayIndex; // matches switch mapping used in TimetableDao.get
            timetableRequests.add(timetableDao.get(daoDay));
            c.add(Calendar.DATE, 1);
        }

        // Process all timetable data together
        disposables.add(
                Single.zip(timetableRequests, objects -> {
                    // Combine all timetable data
                    for (Object obj : objects) {
                        @SuppressWarnings("unchecked")
                        List<Timetable.AllData> dayData = (List<Timetable.AllData>) obj;
                        for (Timetable.AllData item : dayData) {
                            if (item.courseCode == null) continue;
                            lastCourseMissed.put(item.courseCode, lastCourseMissed.getOrDefault(item.courseCode, 0) + 1);
                        }
                    }
                    return lastCourseMissed;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(courseMissedMap -> {
                    // Render per-course preview
                    coursePreview.removeAllViews();
                    for (Map.Entry<String, Integer> entry : lastCourseMissed.entrySet()) {
                        TextView tv = new TextView(requireContext());
                        tv.setText(entry.getKey() + ": -" + entry.getValue() + " classes");
                        coursePreview.addView(tv);
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
                    android.util.Log.e("AttendanceCalc", "Timetable error", throwable);
                    Toast.makeText(requireContext(), "Timetable error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
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

        // For each course code, fetch id and update totals: total += missed, percentage recomputed conservatively using existing attended
        for (Map.Entry<String, Integer> entry : lastCourseMissed.entrySet()) {
            String courseCode = entry.getKey();
            int missed = entry.getValue();
            totalMissedClasses[0] += missed;

            android.util.Log.d("AttendanceCalc", "Processing course: " + courseCode + " with " + missed + " missed classes");

            disposables.add(
                    coursesDao.getCourse(courseCode)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(list -> {
                                android.util.Log.d("AttendanceCalc", "Got course data for " + courseCode + ": " + (list != null ? list.size() : "null"));

                                if (list == null || list.isEmpty()) {
                                    android.util.Log.w("AttendanceCalc", "No course data found for " + courseCode);
                                    processedCourses[0]++;
                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                    return;
                                }

                                tk.therealsuji.vtopchennai.models.Course.AllData data = list.get(0);
                                android.util.Log.d("AttendanceCalc", "Course " + courseCode + " - Total: " + data.attendanceTotal + ", Attended: " + data.attendanceAttended);

                                if (data.attendanceTotal == null || data.attendanceAttended == null) {
                                    android.util.Log.w("AttendanceCalc", "Null attendance data for " + courseCode);
                                    processedCourses[0]++;
                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                    return;
                                }

                                int newTotal = Math.max(0, data.attendanceTotal + missed);
                                int newPercentage = newTotal == 0 ? 0 : (int) Math.ceil((data.attendanceAttended * 100.0) / newTotal);

                                android.util.Log.d("AttendanceCalc", "Course " + courseCode + " - New Total: " + newTotal + ", New Percentage: " + newPercentage);

                                // Always try the original course code first
                                final String courseCodeToTry = courseCode;

                                disposables.add(
                                        coursesDao.getCourseIdByCode(courseCodeToTry)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(courseId -> {
                                                    android.util.Log.d("AttendanceCalc", "Got course ID for " + courseCode + ": " + courseId);

                                                    if (courseId != null) {
                                                        disposables.add(
                                                                attendanceDao.updateTotals(courseId, newTotal, newPercentage)
                                                                        .subscribeOn(Schedulers.io())
                                                                        .observeOn(AndroidSchedulers.mainThread())
                                                                        .subscribe(() -> {
                                                                            android.util.Log.d("AttendanceCalc", "Successfully updated attendance for " + courseCode);
                                                                            processedCourses[0]++;
                                                                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                        }, err -> {
                                                                            android.util.Log.e("AttendanceCalc", "Error updating attendance for " + courseCode, err);
                                                                            processedCourses[0]++;
                                                                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                        })
                                                        );
                                                    } else {
                                                        // If course ID is null and we tried a modified code, try the original
                                                        if (!courseCodeToTry.equals(courseCode)) {
                                                            android.util.Log.d("AttendanceCalc", "Trying original course code: " + courseCode);
                                                            disposables.add(
                                                                    coursesDao.getCourseIdByCode(courseCode)
                                                                            .subscribeOn(Schedulers.io())
                                                                            .observeOn(AndroidSchedulers.mainThread())
                                                                            .subscribe(originalCourseId -> {
                                                                                if (originalCourseId != null) {
                                                                                    disposables.add(
                                                                                            attendanceDao.updateTotals(originalCourseId, newTotal, newPercentage)
                                                                                                    .subscribeOn(Schedulers.io())
                                                                                                    .observeOn(AndroidSchedulers.mainThread())
                                                                                                    .subscribe(() -> {
                                                                                                        android.util.Log.d("AttendanceCalc", "Successfully updated attendance for " + courseCode);
                                                                                                        processedCourses[0]++;
                                                                                                        checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                                                    }, err -> {
                                                                                                        android.util.Log.e("AttendanceCalc", "Error updating attendance for " + courseCode, err);
                                                                                                        processedCourses[0]++;
                                                                                                        checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                                                    })
                                                                                    );
                                                                                } else {
                                                                                    android.util.Log.w("AttendanceCalc", "Null course ID for both " + courseCodeToTry + " and " + courseCode);
                                                                                    processedCourses[0]++;
                                                                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                                }
                                                                            }, err -> {
                                                                                android.util.Log.e("AttendanceCalc", "Error getting course ID for " + courseCode, err);
                                                                                processedCourses[0]++;
                                                                                checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                                            })
                                                            );
                                                        } else {
                                                            android.util.Log.w("AttendanceCalc", "Null course ID for " + courseCode);
                                                            processedCourses[0]++;
                                                            checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                        }
                                                    }
                                                }, err -> {
                                                    android.util.Log.e("AttendanceCalc", "Error getting course ID for " + courseCode, err);
                                                    processedCourses[0]++;
                                                    checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
                                                })
                                );
                            }, err -> {
                                processedCourses[0]++;
                                checkCompletion(processedCourses[0], totalCourses, totalMissedClasses[0]);
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
