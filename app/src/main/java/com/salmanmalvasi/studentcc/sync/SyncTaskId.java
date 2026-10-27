package com.salmanmalvasi.studentcc.sync;

/**
 * Unique identifiers for all synchronization tasks within StudentCC SyncEngine.
 */
public enum SyncTaskId {
    AUTH("Authentication & Session Handshake"),
    SEMESTERS("Semester Discovery"),
    PROFILE("Student Profile & Name"),
    CREDITS("Credits & Cumulative GPA"),
    COURSES("Course Catalog & Timetable Slots"),
    TIMETABLE("Weekly Timetable Generation"),
    ATTENDANCE_SUMMARY("Attendance Aggregate Summary"),
    DETAILED_ATTENDANCE("Detailed Course Attendance Records"),
    MARKS("Assessment Marks"),
    GRADES("Course Letter Grades"),
    EXAMS("Examination Schedule"),
    PROCTOR("Proctor Information"),
    DEAN_HOD("Dean & HOD Contacts"),
    SPOTLIGHT("Spotlight Campus Notices"),
    RECEIPTS("Finance Fee Receipts"),
    CALENDAR("Academic Calendar Events"),
    LEAVE("Hostel Leave History");

    private final String displayName;

    SyncTaskId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
