package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Leave;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Spotlight;
import com.salmanmalvasi.studentcc.models.Staff;
import com.salmanmalvasi.studentcc.models.Timetable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory staging container for synchronization data.
 * Protects database integrity by validating all relational invariants
 * BEFORE any database transactions are committed.
 */
public class StagedSyncData {
    public final long syncGenerationId;
    public final String semesterId;

    public final List<Course> courses = Collections.synchronizedList(new ArrayList<>());
    public final List<Slot> slots = Collections.synchronizedList(new ArrayList<>());
    public final List<Timetable> timetables = Collections.synchronizedList(new ArrayList<>());
    public final List<Attendance> attendanceSummaries = Collections.synchronizedList(new ArrayList<>());
    public final List<AttendanceRecord> attendanceRecords = Collections.synchronizedList(new ArrayList<>());
    public final List<Mark> marks = Collections.synchronizedList(new ArrayList<>());
    public final List<CumulativeMark> cumulativeMarks = Collections.synchronizedList(new ArrayList<>());
    public final List<Exam> exams = Collections.synchronizedList(new ArrayList<>());
    public final List<CalendarEvent> calendarEvents = Collections.synchronizedList(new ArrayList<>());
    public final List<Receipt> receipts = Collections.synchronizedList(new ArrayList<>());
    public final List<Spotlight> spotlights = Collections.synchronizedList(new ArrayList<>());
    public final List<Staff> staff = Collections.synchronizedList(new ArrayList<>());
    public final List<Leave> leaves = Collections.synchronizedList(new ArrayList<>());

    public final Map<String, String> profileAttributes = Collections.synchronizedMap(new LinkedHashMap<>());

    // Server-provided entity identifiers to bridge API requests that require backend courseId or classId
    public final Map<Integer, String> serverCourseIds = Collections.synchronizedMap(new HashMap<>());
    public final Map<Integer, String> classIds = Collections.synchronizedMap(new HashMap<>());
    public final Map<String, String> serverCourseIdByCode = Collections.synchronizedMap(new HashMap<>());
    public final Map<String, String> classIdByCode = Collections.synchronizedMap(new HashMap<>());
    public final Map<Integer, String> slotNames = Collections.synchronizedMap(new HashMap<>());

    // Preserves all server-provided metadata for each course (courseId -> Map<key, value>)
    public final Map<Integer, Map<String, String>> rawCourseMetadata = Collections.synchronizedMap(new HashMap<>());
    public final Map<String, Map<String, String>> rawCourseMetadataByCode = Collections.synchronizedMap(new HashMap<>());

    // Retained Room DB cache (populated for instant local-first display, kept strictly separate from fresh ingestion)
    public final List<Course> cachedCourses = Collections.synchronizedList(new ArrayList<>());
    public final List<Slot> cachedSlots = Collections.synchronizedList(new ArrayList<>());

    public enum Provenance {
        FRESH_NETWORK,
        RETAINED_CACHE
    }

    public boolean hasFreshCourses() {
        return !courses.isEmpty();
    }

    public List<Course> getEffectiveCourses() {
        return !courses.isEmpty() ? courses : cachedCourses;
    }

    public List<Slot> getEffectiveSlots() {
        return !slots.isEmpty() ? slots : cachedSlots;
    }

    public StagedSyncData(String semesterId) {
        this.syncGenerationId = System.currentTimeMillis();
        this.semesterId = semesterId;
    }

    /**
     * Validates that all staged attendance, slots, and exam entities reference
     * valid, existing Course IDs in this synchronization generation.
     *
     * @return null if validation passes; an error description if validation fails.
     */
    public synchronized String validateConsistency() {
        List<Course> effectiveCourses = getEffectiveCourses();
        if (effectiveCourses.isEmpty() && (!attendanceSummaries.isEmpty() || !slots.isEmpty())) {
            return "Validation failed: Attendance or slots staged without corresponding courses.";
        }

        Set<Integer> validCourseIds = new HashSet<>();
        for (Course c : effectiveCourses) {
            validCourseIds.add(c.id);
        }

        // Validate Slot course references
        for (Slot slot : slots) {
            if (slot.courseId == null || !validCourseIds.contains(slot.courseId)) {
                return "Validation failed: Slot '" + slot.slot + "' references missing course ID: " + slot.courseId;
            }
        }

        // Validate Attendance summary course references
        for (Attendance att : attendanceSummaries) {
            if (att.courseId == null || !validCourseIds.contains(att.courseId)) {
                return "Validation failed: Attendance summary references missing course ID: " + att.courseId;
            }
        }

        // Validate Detailed Attendance record course references
        for (AttendanceRecord record : attendanceRecords) {
            if (record.courseId == null || !validCourseIds.contains(record.courseId)) {
                return "Validation failed: AttendanceRecord references missing course ID: " + record.courseId;
            }
        }

        // Validate Exam course references
        for (Exam exam : exams) {
            if (exam.courseId != null && !validCourseIds.contains(exam.courseId)) {
                return "Validation failed: Exam references missing course ID: " + exam.courseId;
            }
        }

        // Validate Mark course references (preserves nullable relationship)
        for (Mark mark : marks) {
            if (mark.courseId != null && !validCourseIds.contains(mark.courseId)) {
                return "Validation failed: Mark references missing course ID: " + mark.courseId;
            }
        }

        return null; // Valid
    }
}
