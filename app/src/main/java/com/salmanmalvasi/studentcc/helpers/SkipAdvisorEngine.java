package com.salmanmalvasi.studentcc.helpers;

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

import com.salmanmalvasi.studentcc.interfaces.AttendanceDao;
import com.salmanmalvasi.studentcc.interfaces.CoursesDao;
import com.salmanmalvasi.studentcc.interfaces.ExamsDao;
import com.salmanmalvasi.studentcc.interfaces.TimetableDao;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.SkipRecommendation;
import com.salmanmalvasi.studentcc.models.Timetable;

/**
 * Skip Advisor Engine - Day-Grouped Skip Recommendations
 * 
 * Question: "Can I skip THIS ENTIRE DAY and stay above 75%/85%?"
 * 
 * Returns one recommendation PER DAY (next 7 days) instead of per-class.
 * Each recommendation shows all classes for that day and combined impact.
 *
 * Also provides semester-end projections:
 *   • How many classes remain until semester end (per course + overall)
 *   • Maximum achievable attendance if you attend every remaining class
 */
public class SkipAdvisorEngine {

    private static final String TAG = "SkipAdvisorEngine";

    // Safety thresholds (configurable)
    private float minOverallAttendance = 85.0f;
    private float minCourseAttendance = 75.0f;
    private float riskyThreshold = 73.0f; // Yellow warning if between 73-75%

    // Default semester end: ~18 weeks from the start of the current semester.
    // This is used when we can't determine the real end date from calendar events.
    private static final int DEFAULT_WEEKS_REMAINING = 16;

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

    // ─────────────────────────────────────────────────────────────────────────
    // Semester-end projection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if a slot string or course type indicates a lab course.
     * Labs are slots like L1, L31+L32, L1+L2, or type containing lab/ela.
     */
    public static boolean isLabSlot(String slot, String courseType) {
        if (slot != null && slot.matches(".*(?i)(^|[^A-Z0-9])L\\d+.*")) {
            return true;
        }
        if (courseType != null) {
            String ct = courseType.trim().toLowerCase(Locale.ENGLISH);
            if (ct.contains("lab") || ct.equals("ela")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Holds the estimated semester-end projection data.
     */
    public static class SemesterProjection {
        /** Estimated number of teaching days remaining until semester end. */
        public int estimatedDaysRemaining;
        /** Estimated total classes remaining (sum across all courses). */
        public int totalClassesRemaining;
        /** Maximum overall attendance achievable if all remaining classes are attended. */
        public float maxAchievableOverall;
        /** Per-course: remaining classes and max achievable attendance. */
        public Map<String, CourseProjection> courseProjections = new HashMap<>();
        /** Approximate semester end date used for the estimate (LID). */
        public Date estimatedSemesterEnd;
        /** Approximate start date of FAT Lab week (1 week before LID or explicit event). */
        public Date fatLabStartDate;
        /** True if the current date is in or past the FAT Lab week. */
        public boolean isFatLabWeek;

        public static class CourseProjection {
            public String courseCode;
            public String courseTitle;
            public int currentAttended;
            public int currentTotal;
            public float currentAttendance;
            public int remainingClasses;
            public float maxAchievableAttendance; // if all remaining are attended
            public boolean isLab;
            public boolean isLocked; // true if lab classes are concluded/locked during FAT Lab week
        }
    }

    /**
     * Estimates the FAT Lab start date (typically 7 days before LID, or explicit calendar event).
     */
    private Date estimateFatLabStartDate(Date semesterEnd) {
        Calendar fatLabCal = Calendar.getInstance();
        if (semesterEnd != null) {
            fatLabCal.setTime(semesterEnd);
            fatLabCal.add(Calendar.DAY_OF_YEAR, -7);
        } else {
            fatLabCal.add(Calendar.WEEK_OF_YEAR, DEFAULT_WEEKS_REMAINING - 1);
        }

        try {
            List<com.salmanmalvasi.studentcc.models.CalendarEvent> calendarEvents =
                    database.calendarEventDao().getAll().blockingGet();
            if (calendarEvents != null && !calendarEvents.isEmpty()) {
                SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                Calendar candidate = null;

                for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : calendarEvents) {
                    if (ce.text == null || ce.month == null) continue;
                    String fullText = (ce.text + " " + (ce.category != null ? ce.category : "") + " " + (ce.type != null ? ce.type : "")).toLowerCase(Locale.ENGLISH);
                    if (fullText.matches(".*\\b(fat.*lab|lab.*fat)\\b.*") || fullText.contains("fat lab") || fullText.contains("lab fat")) {
                        try {
                            Date parsed = monthFmt.parse(ce.month);
                            if (parsed != null) {
                                Calendar eventCal = Calendar.getInstance();
                                eventCal.setTime(parsed);
                                eventCal.set(Calendar.DAY_OF_MONTH, ce.date);
                                if (candidate == null || eventCal.before(candidate)) {
                                    candidate = (Calendar) eventCal.clone();
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (candidate != null) {
                    return candidate.getTime();
                }
            }
        } catch (Exception ignored) {}

        return fatLabCal.getTime();
    }

    /**
     * Calculates semester-end projections for all enrolled courses.
     * Uses the timetable to count how many classes are scheduled from today
     * to the estimated semester end (LID).
     *
     * Rule: In FAT Lab week (locks a week before LID), lab classes are concluded
     * and do NOT get counted for future attendance.
     */
    public SemesterProjection calculateSemesterEndProjection() {
        SemesterProjection projection = new SemesterProjection();

        try {
            List<Attendance> attendanceRecords = database.attendanceDao().getAll().blockingGet();
            if (attendanceRecords == null || attendanceRecords.isEmpty()) {
                return projection;
            }

            // Determine semester end date (LID)
            Date semesterEnd = estimateSemesterEndDate();
            projection.estimatedSemesterEnd = semesterEnd;

            // Determine FAT Lab start date
            Date fatLabStart = estimateFatLabStartDate(semesterEnd);
            projection.fatLabStartDate = fatLabStart;

            Calendar today = Calendar.getInstance();
            Calendar fatLabCal = Calendar.getInstance();
            fatLabCal.setTime(fatLabStart);
            fatLabCal.set(Calendar.HOUR_OF_DAY, 0);
            fatLabCal.set(Calendar.MINUTE, 0);
            fatLabCal.set(Calendar.SECOND, 0);
            fatLabCal.set(Calendar.MILLISECOND, 0);

            projection.isFatLabWeek = !today.before(fatLabCal);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(semesterEnd);

            // Pre-map lab courses by slot and code
            Map<String, Boolean> isLabByCourseCode = new HashMap<>();
            try {
                List<com.salmanmalvasi.studentcc.models.Slot> allSlots = database.coursesDao().getAllSlots().blockingGet();
                List<Course> allCourses = database.coursesDao().getAllCourses().blockingGet();
                Map<Integer, String> codeById = new HashMap<>();
                for (Course c : allCourses) {
                    if (c != null && c.code != null) {
                        codeById.put(c.id, c.code);
                        if (isLabSlot(null, c.type)) {
                            isLabByCourseCode.put(c.code, true);
                        }
                    }
                }
                for (com.salmanmalvasi.studentcc.models.Slot s : allSlots) {
                    if (s != null && s.slot != null && s.courseId != null) {
                        String code = codeById.get(s.courseId);
                        if (code != null && isLabSlot(s.slot, null)) {
                            isLabByCourseCode.put(code, true);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Load calendar events to check which dates are Instructional Days
            // Only classes on "Instructional Day" dates count toward remaining classes
            Map<String, Boolean> calendarDayInstructional = new HashMap<>();
            Map<String, Boolean> calendarDayHoliday = new HashMap<>();
            try {
                List<com.salmanmalvasi.studentcc.models.CalendarEvent> calEvents =
                        database.calendarEventDao().getAll().blockingGet();
                if (calEvents != null) {
                    SimpleDateFormat monthFmtCal = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                    for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : calEvents) {
                        if (ce.month == null) continue;
                        try {
                            Date parsedMonth = monthFmtCal.parse(ce.month);
                            if (parsedMonth == null) continue;
                            Calendar evCal = Calendar.getInstance();
                            evCal.setTime(parsedMonth);
                            evCal.set(Calendar.DAY_OF_MONTH, ce.date);
                            evCal.set(Calendar.HOUR_OF_DAY, 0);
                            evCal.set(Calendar.MINUTE, 0);
                            evCal.set(Calendar.SECOND, 0);
                            evCal.set(Calendar.MILLISECOND, 0);
                            String dateKey = evCal.get(Calendar.YEAR) + "-" + evCal.get(Calendar.DAY_OF_YEAR);
                            String typeStr = ce.type != null ? ce.type.trim() : "";
                            String textStr = (ce.text != null ? ce.text : "").toLowerCase(Locale.ENGLISH);
                            String catStr = (ce.category != null ? ce.category : "").toLowerCase(Locale.ENGLISH);
                            boolean isHoliday = "Holiday".equalsIgnoreCase(typeStr)
                                    || textStr.contains("holiday") || textStr.contains("vacation")
                                    || textStr.contains("recess") || textStr.contains("no instructional")
                                    || textStr.contains("non-instructional") || textStr.contains("non instructional")
                                    || catStr.contains("holiday");
                            boolean isInstructional = "Instructional Day".equalsIgnoreCase(typeStr)
                                    || "Instructional".equalsIgnoreCase(typeStr)
                                    || textStr.contains("instructional");
                            if (isHoliday) {
                                calendarDayHoliday.put(dateKey, true);
                            }
                            if (isInstructional && !isHoliday) {
                                calendarDayInstructional.put(dateKey, true);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            // Whether calendar data is available: if no instructional days found, fall back to
            // counting all non-Sunday, non-holiday days (to avoid showing zero for everyone).
            boolean hasCalendarData = !calendarDayInstructional.isEmpty();

            // Count teaching days and classes per course from today → semester end
            Map<String, Integer> remainingPerCourse = new HashMap<>();
            int totalRemaining = 0;
            int days = 0;

            Calendar dayCursor = (Calendar) today.clone();
            // Start from tomorrow (today's classes already recorded or ongoing)
            dayCursor.add(Calendar.DAY_OF_YEAR, 1);

            SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.ENGLISH);

            while (!dayCursor.after(endCal) && days < 200) { // cap to avoid infinite loop
                int dow = dayCursor.get(Calendar.DAY_OF_WEEK);
                // Skip Sundays (VIT Chennai weekend)
                if (dow != Calendar.SUNDAY) {
                    // Check if this day is a holiday or not instructional per calendar
                    String dateKey = dayCursor.get(Calendar.YEAR) + "-" + dayCursor.get(Calendar.DAY_OF_YEAR);
                    boolean isHolidayDay = Boolean.TRUE.equals(calendarDayHoliday.get(dateKey));
                    boolean isInstructionalDay = !hasCalendarData || Boolean.TRUE.equals(calendarDayInstructional.get(dateKey));

                    // Skip holidays; skip non-instructional days only if we have calendar data
                    if (isHolidayDay || (hasCalendarData && !isInstructionalDay)) {
                        dayCursor.add(Calendar.DAY_OF_YEAR, 1);
                        continue;
                    }

                    String dayName = dayFmt.format(dayCursor.getTime());
                    List<Timetable.AllData> dayClasses = getClassesForDay(dayName);
                    if (dayClasses != null && !dayClasses.isEmpty()) {
                        days++;

                        Calendar cursorDayStart = (Calendar) dayCursor.clone();
                        cursorDayStart.set(Calendar.HOUR_OF_DAY, 0);
                        cursorDayStart.set(Calendar.MINUTE, 0);
                        cursorDayStart.set(Calendar.SECOND, 0);
                        cursorDayStart.set(Calendar.MILLISECOND, 0);
                        boolean isInOrAfterFatLabWeek = !cursorDayStart.before(fatLabCal);

                        // Group by course
                        for (Timetable.AllData tc : dayClasses) {
                            String code = tc.courseCode != null ? tc.courseCode : "UNKNOWN";
                            boolean isLab = isLabSlot(null, tc.courseType) || Boolean.TRUE.equals(isLabByCourseCode.get(code));

                            // FAT Lab week rule: lab attendance locks a week before LID;
                            // regular lab classes do NOT run or count during/after FAT Lab week.
                            if (isInOrAfterFatLabWeek && isLab) {
                                continue;
                            }

                            remainingPerCourse.put(code, remainingPerCourse.getOrDefault(code, 0) + 1);
                            totalRemaining++;
                        }
                    }
                }
                dayCursor.add(Calendar.DAY_OF_YEAR, 1);
            }

            projection.estimatedDaysRemaining = days;
            projection.totalClassesRemaining = totalRemaining;

            // Build per-course projections
            int sumAttended = 0;
            int sumTotal = 0;

            for (Attendance att : attendanceRecords) {
                if (att.attended == null || att.total == null) continue;
                sumAttended += att.attended;
                sumTotal += att.total;

                if (att.courseId == null || att.courseId <= 0) continue;
                try {
                    Course.AllData course = database.coursesDao().getCourse(att.courseId).blockingGet();
                    if (course == null || course.courseCode == null) continue;

                    String code = course.courseCode;
                    boolean isLab = isLabSlot(course.slot, course.courseType) || Boolean.TRUE.equals(isLabByCourseCode.get(code));
                    int rem = remainingPerCourse.getOrDefault(code, 0);

                    // If it is FAT Lab week, lab classes are strictly locked (0 remaining)
                    if (isLab && projection.isFatLabWeek) {
                        rem = 0;
                    }

                    SemesterProjection.CourseProjection cp = new SemesterProjection.CourseProjection();
                    cp.courseCode = code;
                    cp.courseTitle = course.courseTitle != null && !course.courseTitle.isEmpty()
                            ? course.courseTitle : code;
                    cp.isLab = isLab;
                    cp.isLocked = isLab && projection.isFatLabWeek;
                    cp.currentAttended = att.attended;
                    cp.currentTotal = att.total;
                    cp.currentAttendance = att.percentage != null ? att.percentage
                            : (att.total > 0 ? ((float) att.attended / att.total) * 100 : 0);
                    cp.remainingClasses = rem;
                    cp.maxAchievableAttendance = (att.total + rem) > 0
                            ? ((float) (att.attended + rem) / (att.total + rem)) * 100
                            : 0;

                    projection.courseProjections.put(code, cp);
                } catch (Exception e) {
                    Log.w(TAG, "Error building course projection: " + e.getMessage());
                }
            }

            // Also cover courses that might not have an attendance entry yet
            try {
                List<com.salmanmalvasi.studentcc.models.Course> allCourses = database.coursesDao().getAllCourses().blockingGet();
                if (allCourses != null) {
                    for (com.salmanmalvasi.studentcc.models.Course c : allCourses) {
                        if (c == null || c.code == null) continue;
                        String code = c.code.trim().toUpperCase(Locale.US);
                        if (!projection.courseProjections.containsKey(code)) {
                            boolean isLab = isLabSlot(null, c.type) || Boolean.TRUE.equals(isLabByCourseCode.get(code));
                            int rem = remainingPerCourse.getOrDefault(code, 0);
                            if (isLab && projection.isFatLabWeek) {
                                rem = 0;
                            }
                            SemesterProjection.CourseProjection cp = new SemesterProjection.CourseProjection();
                            cp.courseCode = code;
                            cp.courseTitle = c.title != null && !c.title.isEmpty() ? c.title : code;
                            cp.isLab = isLab;
                            cp.isLocked = isLab && projection.isFatLabWeek;
                            cp.currentAttended = 0;
                            cp.currentTotal = 0;
                            cp.currentAttendance = 0f;
                            cp.remainingClasses = rem;
                            cp.maxAchievableAttendance = 100f;
                            projection.courseProjections.put(code, cp);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Overall max achievable attendance
            int projectedTotalAttended = sumAttended + totalRemaining;
            int projectedTotalClasses = sumTotal + totalRemaining;
            projection.maxAchievableOverall = projectedTotalClasses > 0
                    ? ((float) projectedTotalAttended / projectedTotalClasses) * 100
                    : 0;

        } catch (Exception e) {
            Log.e(TAG, "Error calculating semester-end projection", e);
        }

        return projection;
    }

    public static SemesterProjection.CourseProjection findCourseProjection(
            SemesterProjection proj, String courseCode, String courseTitle) {
        if (proj == null || proj.courseProjections == null) return null;
        if (courseCode != null && !courseCode.trim().isEmpty()) {
            String cleanCode = courseCode.trim().toUpperCase(Locale.US);
            if (proj.courseProjections.containsKey(cleanCode)) {
                return proj.courseProjections.get(cleanCode);
            }
            String baseCode = cleanCode.replaceAll("[^A-Z0-9]", "");
            for (Map.Entry<String, SemesterProjection.CourseProjection> entry : proj.courseProjections.entrySet()) {
                String key = entry.getKey().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
                if (key.equals(baseCode) || baseCode.startsWith(key) || key.startsWith(baseCode)) {
                    return entry.getValue();
                }
            }
        }
        if (courseTitle != null && !courseTitle.trim().isEmpty()) {
            String cleanTitle = courseTitle.trim().toLowerCase(Locale.US);
            for (SemesterProjection.CourseProjection cp : proj.courseProjections.values()) {
                if (cp.courseTitle != null && cp.courseTitle.trim().toLowerCase(Locale.US).contains(cleanTitle)) {
                    return cp;
                }
            }
        }
        return null;
    }

    /**
     * Estimates how many total classes remain in the semester.
     * This is the sum across all courses until the estimated semester end date.
     */
    public int estimateTotalClassesRemainingInSemester() {
        return calculateSemesterEndProjection().totalClassesRemaining;
    }

    /**
     * Estimates the semester end date. First tries to find a "last working day" or
     * "end of semester" event in the academic calendar. Falls back to
     * DEFAULT_WEEKS_REMAINING weeks from today.
     */
    private Date estimateSemesterEndDate() {
        Calendar endCal = Calendar.getInstance();

        try {
            List<com.salmanmalvasi.studentcc.models.CalendarEvent> calendarEvents =
                    database.calendarEventDao().getAll().blockingGet();

            if (calendarEvents != null && !calendarEvents.isEmpty()) {
                SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                Calendar now = Calendar.getInstance();

                // Pass 1 (Top Priority): Explicit LID (Last Instructional Day)
                Calendar lidCandidate = null;
                for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : calendarEvents) {
                    if (ce.text == null || ce.month == null) continue;
                    String fullText = (ce.text + " " + (ce.category != null ? ce.category : "") + " " + (ce.type != null ? ce.type : "")).toLowerCase(Locale.ENGLISH);
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
                        } catch (Exception ignored) {}
                    }
                }

                if (lidCandidate != null) {
                    Log.i(TAG, "Identified Last Instructional Day (LID) for semester: " + lidCandidate.getTime());
                    return lidCandidate.getTime();
                }

                // Pass 2: Look for semester-end or study-holiday markers
                Calendar latestEndCandidate = null;

                for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : calendarEvents) {
                    if (ce.text == null || ce.month == null) continue;
                    String lower = (ce.text + " " + ce.category + " " + ce.type).toLowerCase(Locale.ENGLISH);
                    if (lower.contains("last working") || lower.contains("end of semester")
                            || lower.contains("semester end") || lower.contains("study holiday")
                            || lower.contains("revision") || lower.contains("pre-exam")) {
                        try {
                            // Reconstruct a date from ce.date (day) + ce.month (e.g. "November 2025")
                            Date parsed = monthFmt.parse(ce.month);
                            if (parsed != null) {
                                Calendar candidate = Calendar.getInstance();
                                candidate.setTime(parsed);
                                candidate.set(Calendar.DAY_OF_MONTH, ce.date);
                                if (candidate.after(now)) {
                                    if (latestEndCandidate == null || candidate.before(latestEndCandidate)) {
                                        latestEndCandidate = (Calendar) candidate.clone();
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (latestEndCandidate != null) {
                    return latestEndCandidate.getTime();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not find semester end from calendar events: " + e.getMessage());
        }

        // Fallback: DEFAULT_WEEKS_REMAINING weeks from today
        endCal.add(Calendar.WEEK_OF_YEAR, DEFAULT_WEEKS_REMAINING);
        return endCal.getTime();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Day-level skip recommendations (unchanged from original)
    // ─────────────────────────────────────────────────────────────────────────

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

            // Get calendar events to detect holidays and non-instructional days
            List<com.salmanmalvasi.studentcc.models.CalendarEvent> calendarEvents = new ArrayList<>();
            try {
                calendarEvents = database.calendarEventDao().getAll().blockingGet();
            } catch (Exception ignored) {
            }

            // Get next 7 days
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                Calendar targetDate = (Calendar) calendar.clone();
                targetDate.add(Calendar.DAY_OF_YEAR, dayOffset);

                Date date = targetDate.getTime();
                String dayOfWeek = dayFormat.format(date);
                int dayOfMonth = targetDate.get(Calendar.DAY_OF_MONTH);
                String monthKey = monthFormat.format(date);

                // Check if holiday or non-instructional
                boolean isHoliday = targetDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
                String holidayName = isHoliday ? "Sunday Weekend" : null;

                if (!isHoliday && calendarEvents != null) {
                    for (com.salmanmalvasi.studentcc.models.CalendarEvent ce : calendarEvents) {
                        if (ce.date == dayOfMonth && ce.month != null && ce.month.equalsIgnoreCase(monthKey)) {
                            String lower = (ce.text + " " + ce.category + " " + ce.type).toLowerCase(Locale.ENGLISH);
                            if (lower.contains("holiday") || lower.contains("no instructional") ||
                                    lower.contains("non-instructional") || lower.contains("non instructional") ||
                                    lower.contains("vacation") || lower.contains("recess")) {
                                isHoliday = true;
                                holidayName = ce.text;
                                break;
                            }
                        }
                    }
                }

                if (isHoliday) {
                    SkipRecommendation holidayRec = new SkipRecommendation();
                    holidayRec.date = date.getTime();
                    holidayRec.dayOfWeek = dayOfWeek;
                    holidayRec.currentOverall = currentOverall;
                    holidayRec.overallAfterSkip = currentOverall;
                    holidayRec.calculatedAt = System.currentTimeMillis();
                    holidayRec.isHoliday = true;
                    holidayRec.holidayName = holidayName;
                    holidayRec.canSkip = true;
                    holidayRec.hasClasses = false;
                    holidayRec.numClasses = 0;
                    recommendations.add(holidayRec);
                    continue;
                }

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
                            .append(String.format(Locale.ENGLISH, "%.1f%%", afterSkip)).append(". ");
                } else if (afterSkip < minCourseAttendance + 2) {
                    anySubjectRisky = true;
                    riskReasons.append(courseCode).append(" close (")
                            .append(String.format(Locale.ENGLISH, "%.1f%%", afterSkip)).append("). ");
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
