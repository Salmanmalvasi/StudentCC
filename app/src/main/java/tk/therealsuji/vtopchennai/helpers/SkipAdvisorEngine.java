package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tk.therealsuji.vtopchennai.interfaces.AttendanceDao;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.models.Attendance;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.SkipRecommendation;
import tk.therealsuji.vtopchennai.models.Timetable;

/**
 * Skip Advisor Engine - Day-Grouped Skip Recommendations
 * 
 * Question: "Can I skip THIS ENTIRE DAY and stay above 75%/85%?"
 * 
 * Returns one recommendation PER DAY (next 7 days) instead of per-class.
 * Each recommendation shows all classes for that day and combined impact.
 */
public class SkipAdvisorEngine {

    private static final String TAG = "SkipAdvisorEngine";

    // Safety thresholds (configurable)
    private float minOverallAttendance = 85.0f;
    private float minCourseAttendance = 75.0f;
    private float riskyThreshold = 73.0f; // Yellow warning if between 73-75%

    private Context context;
    private AppDatabase database;

    public SkipAdvisorEngine(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
    }

    /**
     * Set custom attendance thresholds
     */
    public void setThresholds(float overallThreshold, float courseThreshold) {
        this.minOverallAttendance = overallThreshold;
        this.minCourseAttendance = courseThreshold;
        this.riskyThreshold = courseThreshold - 2.0f; // Yellow warning zone
    }

    /**
     * Calculate skip recommendations grouped by day (next 7 days)
     * Each recommendation represents a FULL DAY
     */
    public List<SkipRecommendation> calculateRecommendations() {
        List<SkipRecommendation> recommendations = new ArrayList<>();

        try {
            // Get all attendance records
            List<Attendance> attendanceRecords = database.attendanceDao().getAll().blockingGet();
            if (attendanceRecords == null || attendanceRecords.isEmpty()) {
                Log.w(TAG, "No attendance data available");
                return recommendations;
            }

            // Calculate current overall attendance
            float currentOverall = calculateOverallAttendance(attendanceRecords);

            // Get next 7 days
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);

            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                Calendar targetDate = (Calendar) calendar.clone();
                targetDate.add(Calendar.DAY_OF_YEAR, dayOffset);

                Date date = targetDate.getTime();
                String dayOfWeek = dayFormat.format(date);

                SkipRecommendation rec = calculateDayFeasibility(
                        date,
                        dayOfWeek,
                        attendanceRecords,
                        currentOverall);

                if (rec != null && rec.hasClasses) {
                    recommendations.add(rec);
                }
            }

            Log.d(TAG, "Calculated " + recommendations.size() + " day recommendations");

        } catch (Exception e) {
            Log.e(TAG, "Error calculating recommendations", e);
        }

        return recommendations;
    }

    /**
     * Calculate if skipping THIS ENTIRE DAY is feasible
     */
    private SkipRecommendation calculateDayFeasibility(
            Date date,
            String dayOfWeek,
            List<Attendance> attendanceRecords,
            float currentOverall) {
        SkipRecommendation rec = new SkipRecommendation();
        rec.date = date.getTime();
        rec.dayOfWeek = dayOfWeek;
        rec.currentOverall = currentOverall;
        rec.calculatedAt = System.currentTimeMillis();

        // Get all classes for this day
        List<Timetable.AllData> dayClasses = getClassesForDay(dayOfWeek);

        if (dayClasses == null || dayClasses.isEmpty()) {
            rec.hasClasses = false;
            rec.canSkip = false;
            rec.numClasses = 0;
            return rec;
        }

        rec.hasClasses = true;
        rec.numClasses = dayClasses.size();

        // Check if Friday (potential long weekend)
        rec.isLongWeekend = dayOfWeek.equalsIgnoreCase("Friday");

        // Build class info list
        boolean hasLab = false;
        for (Timetable.AllData tClass : dayClasses) {
            SkipRecommendation.ClassInfo classInfo = new SkipRecommendation.ClassInfo();
            classInfo.courseCode = tClass.courseCode;
            classInfo.courseTitle = tClass.courseTitle;
            classInfo.startTime = tClass.startTime;
            classInfo.endTime = tClass.endTime;
            classInfo.courseType = tClass.courseType;
            rec.classes.add(classInfo);

            if ("Lab".equalsIgnoreCase(tClass.courseType)) {
                hasLab = true;
            }
        }
        rec.hasLabs = hasLab;

        // Calculate attendance impact for each subject
        boolean allSubjectsSafe = true;
        boolean anySubjectRisky = false;
        StringBuilder riskReasons = new StringBuilder();

        // Group classes by course code to count classes per subject this day
        Map<String, Integer> classesPerCourse = new HashMap<>();
        for (Timetable.AllData tClass : dayClasses) {
            classesPerCourse.put(tClass.courseCode,
                    classesPerCourse.getOrDefault(tClass.courseCode, 0) + 1);
        }

        // Check each subject's attendance after skipping
        for (Map.Entry<String, Integer> entry : classesPerCourse.entrySet()) {
            String courseCode = entry.getKey();
            int classesToSkip = entry.getValue();

            Attendance att = findAttendanceForCourse(courseCode, attendanceRecords);
            if (att != null && att.attended != null && att.total != null) {
                float currentAtt = att.percentage != null ? att.percentage : 0;
                float afterSkip = ((float) att.attended / (att.total + classesToSkip)) * 100;

                SkipRecommendation.SubjectImpact impact = new SkipRecommendation.SubjectImpact();
                impact.courseCode = courseCode;
                impact.courseTitle = courseCode; // Simplified
                impact.currentAttendance = currentAtt;
                impact.afterSkipAttendance = afterSkip;
                impact.isSafe = afterSkip >= minCourseAttendance;
                rec.subjectImpacts.add(impact);

                if (afterSkip < minCourseAttendance) {
                    allSubjectsSafe = false;
                    riskReasons.append(courseCode).append(" → ")
                            .append(String.format("%.0f%%", afterSkip)).append(". ");
                } else if (afterSkip < minCourseAttendance + 2) {
                    anySubjectRisky = true;
                    riskReasons.append(courseCode).append(" close (")
                            .append(String.format("%.0f%%", afterSkip)).append("). ");
                }
            }
        }

        // Calculate overall attendance after skipping all classes this day
        int totalAttended = 0;
        int totalClasses = 0;
        for (Attendance att : attendanceRecords) {
            if (att.attended != null && att.total != null) {
                totalAttended += att.attended;
                totalClasses += att.total;
            }
        }
        totalClasses += rec.numClasses; // Add classes we're skipping
        rec.overallAfterSkip = totalClasses > 0 ? ((float) totalAttended / totalClasses) * 100 : 0;

        // Final decision
        rec.canSkip = allSubjectsSafe && rec.overallAfterSkip >= minOverallAttendance;
        rec.isRisky = anySubjectRisky && rec.canSkip;
        rec.riskReason = riskReasons.length() > 0 ? riskReasons.toString().trim() : null;

        return rec;
    }

    /**
     * Get classes for specific day of week
     */
    private List<Timetable.AllData> getClassesForDay(String dayOfWeek) {
        try {
            switch (dayOfWeek.toLowerCase()) {
                case "sunday":
                    return database.timetableDao().getSunday().blockingGet();
                case "monday":
                    return database.timetableDao().getMonday().blockingGet();
                case "tuesday":
                    return database.timetableDao().getTuesday().blockingGet();
                case "wednesday":
                    return database.timetableDao().getWednesday().blockingGet();
                case "thursday":
                    return database.timetableDao().getThursday().blockingGet();
                case "friday":
                    return database.timetableDao().getFriday().blockingGet();
                case "saturday":
                    return database.timetableDao().getSaturday().blockingGet();
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching classes for " + dayOfWeek, e);
            return new ArrayList<>();
        }
    }

    /**
     * Find attendance record for a course by course code
     */
    private Attendance findAttendanceForCourse(String courseCode, List<Attendance> records) {
        for (Attendance att : records) {
            if (att.courseId != null && att.courseId > 0) {
                try {
                    Course.AllData course = database.coursesDao().getCourse(att.courseId).blockingGet();
                    if (course != null && course.courseCode != null && courseCode.equals(course.courseCode)) {
                        return att;
                    }
                } catch (Exception e) {
                    // Continue searching
                }
            }
        }
        return null;
    }

    /**
     * Calculate how many classes can be skipped for each subject
     * while maintaining minimum course attendance.
     * Returns Map: CourseCode -> Number of skippable classes
     */
    public Map<String, Integer> calculateSkippableClasses() {
        Map<String, Integer> skippableMap = new HashMap<>();

        try {
            List<Attendance> attendanceRecords = database.attendanceDao().getAll().blockingGet();
            if (attendanceRecords == null)
                return skippableMap;

            for (Attendance att : attendanceRecords) {
                if (att.courseId != null && att.courseId > 0 && att.attended != null && att.total != null) {
                    try {
                        Course.AllData course = database.coursesDao().getCourse(att.courseId).blockingGet();
                        if (course != null && course.courseCode != null) {
                            // Formula: (attended) / (total + x) >= min%
                            // x <= (attended / min%) - total

                            double maxTotal = (att.attended * 100.0) / minCourseAttendance;
                            int maxSkippable = (int) Math.floor(maxTotal - att.total);

                            if (maxSkippable < 0)
                                maxSkippable = 0;

                            skippableMap.put(course.courseCode, maxSkippable);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing course " + att.courseId, e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating skippable classes", e);
        }

        return skippableMap;
    }

    private float calculateOverallAttendance(List<Attendance> records) {
        int totalAttended = 0;
        int totalClasses = 0;

        for (Attendance att : records) {
            if (att.attended != null && att.total != null) {
                totalAttended += att.attended;
                totalClasses += att.total;
            }
        }

        if (totalClasses == 0)
            return 0;
        return ((float) totalAttended / totalClasses) * 100;
    }
}
