package com.salmanmalvasi.studentcc.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.interfaces.AttendanceDao;
import com.salmanmalvasi.studentcc.interfaces.AttendanceRecordDao;
import com.salmanmalvasi.studentcc.interfaces.CalendarEventDao;
import com.salmanmalvasi.studentcc.interfaces.CoursesDao;
import com.salmanmalvasi.studentcc.interfaces.ExamsDao;
import com.salmanmalvasi.studentcc.interfaces.LeaveDao;
import com.salmanmalvasi.studentcc.interfaces.MarksDao;
import com.salmanmalvasi.studentcc.interfaces.ReceiptsDao;
import com.salmanmalvasi.studentcc.interfaces.SpotlightDao;
import com.salmanmalvasi.studentcc.interfaces.StaffDao;
import com.salmanmalvasi.studentcc.interfaces.TimetableDao;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Timetable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Executes atomic, transactional activation of validated staged synchronization data into Room SQLite.
 * Guarantees cache integrity: valid existing datasets are NEVER wiped if incoming data is empty,
 * malformed, or failed during network fetching.
 */
public class TransactionalDataActivator {
    private static final String TAG = "DataActivator";

    public static class ActivationResult {
        public final boolean success;
        public final String errorMessage;

        public ActivationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ActivationResult success() {
            return new ActivationResult(true, null);
        }

        public static ActivationResult failure(String errorMessage) {
            return new ActivationResult(false, errorMessage);
        }
    }

    private final AppDatabase appDatabase;
    private final Context context;

    public TransactionalDataActivator(AppDatabase appDatabase) {
        this(appDatabase, null);
    }

    public TransactionalDataActivator(AppDatabase appDatabase, Context context) {
        this.appDatabase = appDatabase;
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public ActivationResult activate(StagedSyncData stagedData) {
        return activate(stagedData, false);
    }

    public ActivationResult activate(StagedSyncData stagedData, boolean quickSyncOnly) {
        if (stagedData == null) {
            return ActivationResult.failure("Staged data container is null");
        }

        // Phase 1: Validate relational consistency before database interaction
        if (!quickSyncOnly) {
            String validationError = stagedData.validateConsistency();
            if (validationError != null) {
                Log.e(TAG, "Activation rejected due to consistency violation: " + validationError);
                LocalCacheMetrics.getInstance().recordValidationFailure("staged_consistency", validationError);
                return ActivationResult.failure(validationError);
            }
        }

        // Phase 2: Transactional database activation with cache integrity protection
        final int[] beforeCounts = new int[4]; // 0: marks, 1: detailed attendance, 2: attendance, 3: courses
        try {
            appDatabase.runInTransaction(() -> {
                CoursesDao coursesDao = appDatabase.coursesDao();
                AttendanceDao attendanceDao = appDatabase.attendanceDao();
                AttendanceRecordDao attendanceRecordDao = appDatabase.attendanceRecordDao();
                MarksDao marksDao = appDatabase.marksDao();
                ExamsDao examsDao = appDatabase.examsDao();
                CalendarEventDao calendarDao = appDatabase.calendarEventDao();
                ReceiptsDao receiptsDao = appDatabase.receiptsDao();
                SpotlightDao spotlightDao = appDatabase.spotlightDao();
                TimetableDao timetableDao = appDatabase.timetableDao();
                StaffDao staffDao = appDatabase.staffDao();
                LeaveDao leaveDao = appDatabase.leaveDao();

                // Snapshot existing database state to protect non-empty datasets against empty overwrites
                List<Course> existingCourses = new ArrayList<>();
                List<Attendance> existingAttendance = new ArrayList<>();
                List<AttendanceRecord> existingRecords = new ArrayList<>();
                List<Mark> existingMarks = new ArrayList<>();
                List<Timetable> existingTimetables = new ArrayList<>();

                try {
                    List<Course> c = coursesDao.getAllCourses().blockingGet();
                    if (c != null) existingCourses.addAll(c);
                } catch (Exception ignored) {}

                try {
                    List<Attendance> a = attendanceDao.getAll().blockingGet();
                    if (a != null) existingAttendance.addAll(a);
                } catch (Exception ignored) {}

                try {
                    List<AttendanceRecord> r = attendanceRecordDao.getAll().blockingGet();
                    if (r != null) existingRecords.addAll(r);
                } catch (Exception ignored) {}

                try {
                    List<Mark> m = marksDao.getRawMarks().blockingGet();
                    if (m != null) existingMarks.addAll(m);
                } catch (Exception ignored) {}

                try {
                    List<Timetable> t = timetableDao.getTimetable().blockingGet();
                    if (t != null) existingTimetables.addAll(t);
                } catch (Exception ignored) {}

                beforeCounts[0] = existingMarks.size();
                beforeCounts[1] = existingRecords.size();
                beforeCounts[2] = existingAttendance.size();
                beforeCounts[3] = existingCourses.size();

                // Build stable course ID mapping based on (code, type) to preserve foreign keys
                Map<String, Integer> existingCourseKeyToId = new HashMap<>();
                int maxCourseId = 0;
                for (Course ec : existingCourses) {
                    if (ec.code != null) {
                        String key = (ec.code.trim().toUpperCase(Locale.ROOT) + "_" +
                                (ec.type != null ? ec.type.trim().toLowerCase(Locale.ROOT) : "")).trim();
                        existingCourseKeyToId.put(key, ec.id);
                        if (ec.id > maxCourseId) maxCourseId = ec.id;
                    }
                }

                Map<Integer, Integer> oldToNewCourseIdMap = new HashMap<>();
                int nextAssignedId = maxCourseId + 1;

                if (!stagedData.courses.isEmpty()) {
                    for (Course sc : stagedData.courses) {
                        if (sc.code == null) continue;
                        String key = (sc.code.trim().toUpperCase(Locale.ROOT) + "_" +
                                (sc.type != null ? sc.type.trim().toLowerCase(Locale.ROOT) : "")).trim();

                        int oldId = sc.id;
                        int resolvedId;
                        if (existingCourseKeyToId.containsKey(key)) {
                            resolvedId = existingCourseKeyToId.get(key);
                        } else {
                            resolvedId = nextAssignedId++;
                            existingCourseKeyToId.put(key, resolvedId);
                        }
                        sc.id = resolvedId;
                        oldToNewCourseIdMap.put(oldId, resolvedId);
                    }

                    // Re-link staged slots, attendance, records, marks, and exams with stable course IDs
                    for (Slot s : stagedData.slots) {
                        if (s.courseId != null && oldToNewCourseIdMap.containsKey(s.courseId)) {
                            s.courseId = oldToNewCourseIdMap.get(s.courseId);
                        }
                    }
                    for (Attendance a : stagedData.attendanceSummaries) {
                        if (a.courseId != null && oldToNewCourseIdMap.containsKey(a.courseId)) {
                            a.courseId = oldToNewCourseIdMap.get(a.courseId);
                        }
                    }
                    for (AttendanceRecord r : stagedData.attendanceRecords) {
                        if (r.courseId != null && oldToNewCourseIdMap.containsKey(r.courseId)) {
                            r.courseId = oldToNewCourseIdMap.get(r.courseId);
                        }
                    }
                    for (Mark m : stagedData.marks) {
                        if (m.courseId != null && oldToNewCourseIdMap.containsKey(m.courseId)) {
                            m.courseId = oldToNewCourseIdMap.get(m.courseId);
                        }
                    }
                    for (com.salmanmalvasi.studentcc.models.Exam e : stagedData.exams) {
                        if (e.courseId != null && oldToNewCourseIdMap.containsKey(e.courseId)) {
                            e.courseId = oldToNewCourseIdMap.get(e.courseId);
                        }
                    }
                }

                // 1. Courses and Slots (Safely update only when fresh network courses are present)
                if (!stagedData.courses.isEmpty()) {
                    coursesDao.deleteAll().blockingAwait();
                    coursesDao.insertCourses(stagedData.courses).blockingAwait();
                    if (!stagedData.slots.isEmpty()) {
                        coursesDao.insertSlots(stagedData.slots).blockingAwait();
                    }
                    if (context != null) {
                        DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_PROFILE_COURSES);
                    }
                } else if (!existingCourses.isEmpty()) {
                    LocalCacheMetrics.getInstance().recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_PROFILE_COURSES);
                    Log.i(TAG, "Cache integrity preserved: retained " + existingCourses.size() + " existing courses from cache");
                }

                // 2. Timetables (Full Sync ONLY)
                if (!quickSyncOnly) {
                    if (!stagedData.timetables.isEmpty()) {
                        timetableDao.deleteAll().blockingAwait();
                        timetableDao.insert(stagedData.timetables).blockingAwait();
                        if (context != null) {
                            DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_TIMETABLE);
                        }
                    } else if (!existingTimetables.isEmpty()) {
                        // Preserve previous timetables if replacement was empty
                        timetableDao.deleteAll().blockingAwait();
                        timetableDao.insert(existingTimetables).blockingAwait();
                        LocalCacheMetrics.getInstance().recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_TIMETABLE);
                    }
                }

                // 3. Attendance summaries (Cache Integrity: never delete existing if replacement is empty)
                if (!stagedData.attendanceSummaries.isEmpty()) {
                    attendanceDao.delete().blockingAwait();
                    attendanceDao.insert(stagedData.attendanceSummaries).blockingAwait();
                    if (context != null) {
                        DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
                    }
                } else if (!existingAttendance.isEmpty()) {
                    // Retain valid existing attendance records
                    attendanceDao.delete().blockingAwait();
                    attendanceDao.insert(existingAttendance).blockingAwait();
                    LocalCacheMetrics.getInstance().recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
                    Log.i(TAG, "Cache integrity preserved: retained " + existingAttendance.size() + " existing attendance summaries");
                }

                // 4. Detailed Attendance records (Cache Integrity: never delete existing if replacement is empty)
                if (!stagedData.attendanceRecords.isEmpty()) {
                    attendanceRecordDao.clearAll().blockingAwait();
                    attendanceRecordDao.insertAll(stagedData.attendanceRecords).blockingAwait();
                    if (context != null) {
                        DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_DETAILED_ATTENDANCE);
                    }
                } else if (!existingRecords.isEmpty()) {
                    attendanceRecordDao.clearAll().blockingAwait();
                    attendanceRecordDao.insertAll(existingRecords).blockingAwait();
                    LocalCacheMetrics.getInstance().recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_DETAILED_ATTENDANCE);
                    Log.i(TAG, "Cache integrity preserved: retained " + existingRecords.size() + " existing detailed attendance records");
                }

                // 5. Marks and Cumulative Marks (Cache Integrity: never delete existing if replacement is empty)
                if (!stagedData.marks.isEmpty()) {
                    try {
                        List<Mark> readMarks = marksDao.getMarksRead().blockingGet();
                        if (readMarks != null && !readMarks.isEmpty()) {
                            Set<Integer> readSigs = new HashSet<>();
                            for (Mark rm : readMarks) {
                                if (rm.signature != null) readSigs.add(rm.signature);
                            }
                            for (Mark m : stagedData.marks) {
                                if (m.signature != null && readSigs.contains(m.signature)) {
                                    m.isRead = true;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    marksDao.deleteMarks().blockingAwait();
                    marksDao.insertMarks(stagedData.marks).blockingAwait();
                    if (context != null) {
                        DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_MARKS);
                    }
                } else if (!existingMarks.isEmpty()) {
                    marksDao.deleteMarks().blockingAwait();
                    marksDao.insertMarks(existingMarks).blockingAwait();
                    LocalCacheMetrics.getInstance().recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_MARKS);
                    Log.i(TAG, "Cache integrity preserved: retained " + existingMarks.size() + " existing marks");
                }

                if (!stagedData.cumulativeMarks.isEmpty()) {
                    marksDao.deleteCumulativeMarks().blockingAwait();
                    marksDao.insertCumulativeMarks(stagedData.cumulativeMarks).blockingAwait();
                }

                if (!quickSyncOnly) {
                    // 6. Activate Exams (Full Sync ONLY)
                    if (!stagedData.exams.isEmpty()) {
                        examsDao.deleteAll().blockingAwait();
                        examsDao.insert(stagedData.exams).blockingAwait();
                    }

                    // 7. Activate Calendar events (Full Sync ONLY)
                    if (!stagedData.calendarEvents.isEmpty()) {
                        calendarDao.clearAll();
                        calendarDao.insertAll(stagedData.calendarEvents);
                        if (context != null) {
                            DataFreshnessManager.getInstance().recordSuccessfulRefresh(context, DataFreshnessManager.DATASET_CALENDAR);
                        }
                    }

                    // 8. Activate Receipts (Full Sync ONLY)
                    if (!stagedData.receipts.isEmpty()) {
                        receiptsDao.deleteAll().blockingAwait();
                        receiptsDao.insert(stagedData.receipts).blockingAwait();
                    }

                    // 9. Activate Spotlight (Full Sync ONLY)
                    if (!stagedData.spotlights.isEmpty()) {
                        spotlightDao.delete().blockingAwait();
                        spotlightDao.insert(stagedData.spotlights).blockingAwait();
                    }

                    // 10. Activate Staff (Full Sync ONLY)
                    if (!stagedData.staff.isEmpty()) {
                        staffDao.deleteAll().blockingAwait();
                        staffDao.insert(stagedData.staff).blockingAwait();
                    }

                    // 11. Activate Leaves (Full Sync ONLY)
                    if (!stagedData.leaves.isEmpty()) {
                        leaveDao.insertAll(stagedData.leaves);
                    }
                }

                // Phase 3: Synchronize profile attributes (overall attendance, attended, total)
                if (context != null && stagedData.profileAttributes != null) {
                    SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
                    SharedPreferences.Editor editor = sp.edit();

                    if (stagedData.profileAttributes.containsKey("name")) {
                        editor.putString("name", stagedData.profileAttributes.get("name"));
                    }
                    if (stagedData.profileAttributes.containsKey("overallAttendance")) {
                        try {
                            float overall = Float.parseFloat(stagedData.profileAttributes.get("overallAttendance"));
                            int attended = Integer.parseInt(stagedData.profileAttributes.get("attendedClasses"));
                            int total = Integer.parseInt(stagedData.profileAttributes.get("totalClasses"));
                            editor.putFloat("overallAttendance", overall);
                            editor.putInt("attendedClasses", attended);
                            editor.putInt("totalClasses", total);
                        } catch (Exception ignored) {}
                    }
                    editor.apply();
                }
            });

            // Phase 4: Persistence Verification & DAO Read-back Audit
            int afterMarks = 0;
            int afterDetailedAttendance = 0;
            int afterAttendance = 0;
            int afterCourses = 0;
            try {
                List<Mark> m = appDatabase.marksDao().getRawMarks().blockingGet();
                if (m != null) afterMarks = m.size();
                List<AttendanceRecord> r = appDatabase.attendanceRecordDao().getAll().blockingGet();
                if (r != null) afterDetailedAttendance = r.size();
                List<Attendance> a = appDatabase.attendanceDao().getAll().blockingGet();
                if (a != null) afterAttendance = a.size();
                List<Course> c = appDatabase.coursesDao().getAllCourses().blockingGet();
                if (c != null) afterCourses = c.size();
            } catch (Exception auditEx) {
                Log.w(TAG, "Persistence audit query warning: " + auditEx.getMessage());
            }

            Log.i(TAG, String.format(Locale.US,
                    "[Persistence Proof] Ingestion Audit -> Marks: before=%d, staged=%d, persisted=%d | DetailedAttendance: before=%d, staged=%d, persisted=%d | Courses: before=%d, staged=%d, persisted=%d",
                    beforeCounts[0], stagedData.marks.size(), afterMarks,
                    beforeCounts[1], stagedData.attendanceRecords.size(), afterDetailedAttendance,
                    beforeCounts[3], stagedData.courses.size(), afterCourses));

            Log.i(TAG, "Transactional activation succeeded for generation: " + stagedData.syncGenerationId);
            if (context != null) {
                try {
                    com.salmanmalvasi.studentcc.widgets.TodayTimetableWidget.updateAllWidgets(context);
                } catch (Exception widgetEx) {
                    Log.w(TAG, "Widget update notification failed: " + widgetEx.getMessage());
                }
            }
            return ActivationResult.success();
        } catch (Exception e) {
            Log.e(TAG, "Transactional activation rolled back due to error: " + e.getMessage(), e);
            if (context != null) {
                DataFreshnessManager.getInstance().recordRefreshFailure(context, "database_transaction", e.getMessage());
            }
            return ActivationResult.failure(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
