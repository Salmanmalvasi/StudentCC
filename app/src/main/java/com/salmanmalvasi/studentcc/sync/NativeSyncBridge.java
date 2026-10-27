package com.salmanmalvasi.studentcc.sync;

import android.content.Context;
import android.util.Log;

import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Leave;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Spotlight;
import com.salmanmalvasi.studentcc.models.Staff;
import com.salmanmalvasi.studentcc.models.Timetable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production bridge adapter that orchestrates native asynchronous synchronization
 * using DeterministicTaskScheduler, VTOPOkHttpNetworkClient, VTOPHtmlParsers,
 * and TransactionalDataActivator.
 */
public class NativeSyncBridge {
    private static final String TAG = "NativeSyncBridge";
    public static final int TARGET_CONCURRENCY = 2; // Verified safe production concurrency bound

    public interface ProgressListener {
        void onProgress(int completedTasks, int totalTasks, String message);
    }

    public static class BridgeResult {
        public final boolean success;
        public final boolean sessionExpired;
        public final String errorMessage;
        public final long durationMs;
        public final StagedSyncData stagedData;
        public final boolean isPartial;

        public BridgeResult(boolean success, boolean sessionExpired, String errorMessage, long durationMs, StagedSyncData stagedData) {
            this(success, sessionExpired, errorMessage, durationMs, stagedData, false);
        }

        public BridgeResult(boolean success, boolean sessionExpired, String errorMessage, long durationMs, StagedSyncData stagedData, boolean isPartial) {
            this.success = success;
            this.sessionExpired = sessionExpired;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
            this.stagedData = stagedData;
            this.isPartial = isPartial;
        }

        public static BridgeResult success(long durationMs, StagedSyncData stagedData) {
            return new BridgeResult(true, false, null, durationMs, stagedData, false);
        }

        public static BridgeResult partialSuccess(long durationMs, StagedSyncData stagedData, String message) {
            return new BridgeResult(true, false, message, durationMs, stagedData, true);
        }

        public static BridgeResult sessionExpired(String message, long durationMs) {
            return new BridgeResult(false, true, message, durationMs, null, false);
        }

        public static BridgeResult failure(String message, long durationMs) {
            return new BridgeResult(false, false, message, durationMs, null, false);
        }
    }

    private final AppDatabase appDatabase;
    private final Context context;
    private final VTOPOkHttpNetworkClient networkClient;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile boolean isCancelled = false;

    public NativeSyncBridge(AppDatabase appDatabase) {
        this(appDatabase, VTOPEndpoints.BASE_URL, null, null);
    }

    public NativeSyncBridge(AppDatabase appDatabase, String baseUrl) {
        this(appDatabase, baseUrl != null ? baseUrl : VTOPEndpoints.BASE_URL, null, null);
    }

    public NativeSyncBridge(AppDatabase appDatabase, String baseUrl, String userAgent) {
        this(appDatabase, baseUrl, userAgent, null);
    }

    public NativeSyncBridge(AppDatabase appDatabase, String baseUrl, String userAgent, Context context) {
        this.appDatabase = appDatabase;
        this.context = context != null ? context.getApplicationContext() : null;
        this.networkClient = new VTOPOkHttpNetworkClient(baseUrl);
        if (userAgent != null && !userAgent.isEmpty()) {
            this.networkClient.setUserAgent(userAgent);
        }
    }

    private volatile boolean quickSyncOnly = false;

    public void setQuickSyncMode(boolean quickSyncOnly) {
        this.quickSyncOnly = quickSyncOnly;
    }

    public boolean isQuickSyncMode() {
        return quickSyncOnly;
    }

    public void cancel() {
        this.isCancelled = true;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Executes the complete synchronization DAG, validates staging in memory,
     * and performs atomic transactional persistence.
     *
     * @param csrfToken        Extracted CSRF token (never logged)
     * @param authorizedId     Extracted student registration ID
     * @param cookieHeader     Session cookie string from CookieManager (never logged)
     * @param semesterId       Selected academic semester ID (e.g., "CH2024251")
     * @param progressListener Optional callback for progress updates
     * @return BridgeResult detailing outcome, timing, and errors
     */
    public BridgeResult executeSync(String csrfToken,
                                   String authorizedId,
                                   String cookieHeader,
                                   String semesterId,
                                   ProgressListener progressListener) {
        if (!isRunning.compareAndSet(false, true)) {
            return BridgeResult.failure("Synchronization already in progress", 0);
        }

        long startTime = System.currentTimeMillis();
        this.isCancelled = false;

        try {
            if (csrfToken == null || csrfToken.isEmpty() ||
                authorizedId == null || authorizedId.isEmpty() ||
                cookieHeader == null || cookieHeader.isEmpty() ||
                semesterId == null || semesterId.isEmpty()) {
                return BridgeResult.failure("Invalid or missing session authentication tokens", 0);
            }

            SyncContext context = new SyncContext(networkClient.getOkHttpClient(), semesterId, null);
            context.setSessionTokens(csrfToken, authorizedId, cookieHeader);

            // In Quick Sync mode, preload existing database courses and slots into cached collections
            // Keep stagedData.courses strictly for fresh network records
            if (quickSyncOnly) {
                try {
                    List<Course> existingCourses = appDatabase.coursesDao().getAllCourses().blockingGet();
                    List<Slot> existingSlots = appDatabase.coursesDao().getAllSlots().blockingGet();
                    if (existingCourses != null && !existingCourses.isEmpty()) {
                        context.getStagedData().cachedCourses.addAll(existingCourses);
                    }
                    if (existingSlots != null && !existingSlots.isEmpty()) {
                        context.getStagedData().cachedSlots.addAll(existingSlots);
                    }
                } catch (Exception ignored) {}
            }

            AtomicBoolean sessionExpiredFlag = new AtomicBoolean(false);
            AtomicInteger completedCounter = new AtomicInteger(0);

            List<SyncTask> tasks = buildSyncTasks(context, sessionExpiredFlag, completedCounter, progressListener);
            DeterministicTaskScheduler scheduler = new DeterministicTaskScheduler(TARGET_CONCURRENCY);

            DeterministicTaskScheduler.SchedulerResult schedulerResult = scheduler.execute(tasks, context);
            long schedulerDuration = System.currentTimeMillis() - startTime;

            if (isCancelled) {
                return BridgeResult.failure("Synchronization cancelled by user", schedulerDuration);
            }

            if (sessionExpiredFlag.get()) {
                Log.w(TAG, "Native API session unauthorized or expired (HTTP 401)");
                return BridgeResult.sessionExpired("Native API session unauthorized (HTTP 401): vtopcc5.vit.ac.in requires native session; legacy portal cookies rejected", schedulerDuration);
            }

            if (!schedulerResult.overallSuccess) {
                Log.w(TAG, "Sync DAG execution failed: one or more essential tasks failed");
                String specificError = null;
                if (schedulerResult.taskStates.get(SyncTaskId.COURSES) == DeterministicTaskScheduler.TaskState.FAILED) {
                    SyncTaskResult r = schedulerResult.taskResults.get(SyncTaskId.COURSES);
                    specificError = "Courses synchronization failed: " + (r != null ? r.errorMessage : "No courses found");
                } else {
                    for (Map.Entry<SyncTaskId, DeterministicTaskScheduler.TaskState> entry : schedulerResult.taskStates.entrySet()) {
                        if (entry.getValue() == DeterministicTaskScheduler.TaskState.FAILED) {
                            SyncTaskResult r = schedulerResult.taskResults.get(entry.getKey());
                            specificError = entry.getKey() + " failed: " + (r != null ? r.errorMessage : "unknown error");
                            break;
                        }
                    }
                }
                return BridgeResult.failure(specificError != null ? specificError : "Essential synchronization tasks failed", schedulerDuration);
            }

            // Phase 2: In-Memory Relational Consistency Validation
            StagedSyncData stagedData = context.getStagedData();
            if (!quickSyncOnly) {
                String consistencyError = stagedData.validateConsistency();
                if (consistencyError != null) {
                    Log.e(TAG, "Staged consistency check failed: " + consistencyError);
                    return BridgeResult.failure("Consistency check failed: " + consistencyError, schedulerDuration);
                }

                // Guard against empty responses masquerading as success
                if (stagedData.courses.isEmpty()) {
                    Log.e(TAG, "Sync produced zero courses. Aborting activation to prevent database corruption.");
                    return BridgeResult.failure("No courses found in timetable response", schedulerDuration);
                }
            } else {
                if (stagedData.courses.isEmpty() && stagedData.cachedCourses.isEmpty()) {
                    Log.e(TAG, "Quick sync produced zero fresh courses and no cached courses exist.");
                    return BridgeResult.failure("No courses found in timetable response or local cache", schedulerDuration);
                }
            }

            // Phase 3: Transactional Database Activation
            if (progressListener != null) {
                progressListener.onProgress(tasks.size(), tasks.size(), "Saving to database...");
            }

            TransactionalDataActivator activator = new TransactionalDataActivator(appDatabase, this.context);
            TransactionalDataActivator.ActivationResult activationResult = activator.activate(stagedData, quickSyncOnly);

            long totalDuration = System.currentTimeMillis() - startTime;
            if (!activationResult.success) {
                Log.e(TAG, "Transactional activation rolled back: " + activationResult.errorMessage);
                return BridgeResult.failure("Database transaction failed: " + activationResult.errorMessage, totalDuration);
            }

            boolean isPartial = stagedData.marks.isEmpty() || stagedData.attendanceRecords.isEmpty() || stagedData.courses.isEmpty();
            if (isPartial) {
                Log.i(TAG, "Native sync (" + (quickSyncOnly ? "QUICK" : "FULL") + ") completed partially in " + totalDuration +
                        " ms (freshCourses=" + stagedData.courses.size() + ", retainedCourses=" + stagedData.cachedCourses.size() +
                        ", attendanceSummaries=" + stagedData.attendanceSummaries.size() +
                        ", marks=" + stagedData.marks.size() + ", detailedAttendance=" + stagedData.attendanceRecords.size() + ")");
                return BridgeResult.partialSuccess(totalDuration, stagedData, "Courses and attendance synced; marks or attendance records pending");
            }

            Log.i(TAG, "Native sync (" + (quickSyncOnly ? "QUICK" : "FULL") + ") completed successfully in " + totalDuration + " ms");
            return BridgeResult.success(totalDuration, stagedData);

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in native sync: " + e.getMessage(), e);
            return BridgeResult.failure("Unexpected error: " + e.getMessage(), System.currentTimeMillis() - startTime);
        } finally {
            isRunning.set(false);
        }
    }

    private Map<String, String> buildApiParams(SyncContext context) {
        Map<String, String> params = new HashMap<>();
        params.put("regNo", context.getAuthorizedID());
        params.put("registerNumber", context.getAuthorizedID());
        params.put("authorizedID", context.getAuthorizedID());
        params.put("verifyMenu", "true");
        if (context.getSemesterID() != null) {
            params.put("semesterId", context.getSemesterID());
            params.put("semSubId", context.getSemesterID());
            params.put("semesterSubId", context.getSemesterID());
        }
        if (context.getCsrfToken() != null) {
            params.put("_csrf", context.getCsrfToken());
        }
        return params;
    }

    private List<SyncTask> buildSyncTasks(SyncContext ctx,
                                         AtomicBoolean sessionExpiredFlag,
                                         AtomicInteger completedCounter,
                                         ProgressListener progressListener) {
        List<SyncTask> tasks = new ArrayList<>();
        final int totalTasks = quickSyncOnly ? 4 : 12;

        if (!quickSyncOnly) {
            // 1. Profile Task (independent: /login/vitian/myProfileInfo)
            tasks.add(new NativeTask(SyncTaskId.PROFILE, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);
                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.PROFILE_INFO, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }

                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res.body)) {
                                Map<String, String> prof = VTOPJsonParsers.parseProfile(res.body);
                                context.getStagedData().profileAttributes.putAll(prof);
                            } else {
                                String name = VTOPHtmlParsers.parseStudentName(res.body);
                                if (name != null) {
                                    context.getStagedData().profileAttributes.put("name", name);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional PROFILE task encountered notice: " + e.getMessage());
                    }
                    return SyncTaskResult.success(10L);
                }
            });

            // 2. Staff Task (independent: Dean/HOD & Proctor via /login/vitian/hodDeanInfo & /login/vitian/proctorDetail)
            tasks.add(new NativeTask(SyncTaskId.PROCTOR, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);

                        VTOPOkHttpNetworkClient.HttpResponse res1 = networkClient.post(VTOPEndpoints.HOD_DEAN_INFO, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res1, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }
                        if (res1.isSuccessful && res1.body != null && !res1.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res1.body)) {
                                context.getStagedData().staff.addAll(VTOPJsonParsers.parseStaff(res1.body, "DeanHOD"));
                            } else {
                                context.getStagedData().staff.addAll(VTOPHtmlParsers.parseStaff(res1.body, "DeanHOD"));
                            }
                        }

                        VTOPOkHttpNetworkClient.HttpResponse res2 = networkClient.post(VTOPEndpoints.PROCTOR_DETAIL, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res2, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 20L);
                        }
                        if (res2.isSuccessful && res2.body != null && !res2.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res2.body)) {
                                context.getStagedData().staff.addAll(VTOPJsonParsers.parseStaff(res2.body, "Proctor"));
                            } else {
                                context.getStagedData().staff.addAll(VTOPHtmlParsers.parseStaff(res2.body, "Proctor"));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional PROCTOR task encountered notice: " + e.getMessage());
                    }

                    return SyncTaskResult.success(20L);
                }
            });

            // 3. Spotlight Task (independent: /login/vitian/myNotificationDt)
            tasks.add(new NativeTask(SyncTaskId.SPOTLIGHT, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.MY_NOTIFICATION_DT, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }
                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            context.getStagedData().spotlights.addAll(VTOPHtmlParsers.parseSpotlight(res.body));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional SPOTLIGHT task encountered notice: " + e.getMessage());
                    }
                    return SyncTaskResult.success(10L);
                }
            });

            // 4. Receipts Task (independent: /login/vitian/viewReceipt)
            tasks.add(new NativeTask(SyncTaskId.RECEIPTS, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.VIEW_RECEIPT, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }
                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res.body)) {
                                context.getStagedData().receipts.addAll(VTOPJsonParsers.parseReceipts(res.body));
                            } else {
                                context.getStagedData().receipts.addAll(VTOPHtmlParsers.parseReceipts(res.body));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional RECEIPTS task encountered notice: " + e.getMessage());
                    }
                    return SyncTaskResult.success(10L);
                }
            });

            // 5. Leave Task (independent: /login/vitian/studentHostelLeaveHistory)
            tasks.add(new NativeTask(SyncTaskId.LEAVE, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.STUDENT_HOSTEL_LEAVE_HISTORY, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }
                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res.body)) {
                                context.getStagedData().leaves.addAll(VTOPJsonParsers.parseLeaves(res.body));
                            } else {
                                context.getStagedData().leaves.addAll(VTOPHtmlParsers.parseLeave(res.body));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional LEAVE task encountered notice (day scholar or no hostel record): " + e.getMessage());
                    }
                    return SyncTaskResult.success(10L);
                }
            });

            // 6. Calendar Task (/login/vitian/academicCalendar)
            tasks.add(new NativeTask(SyncTaskId.CALENDAR, Collections.emptySet(), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    Calendar cal = Calendar.getInstance();
                    long totalLatency = 0;
                    SimpleDateFormat apiMonthFmt = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH);

                    try {
                        Map<String, String> params = buildApiParams(context);
                        params.put("yearMonth", apiMonthFmt.format(cal.getTime()));

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.ACADEMIC_CALENDAR, params, context.getCookieHeader());
                        totalLatency += 10L;

                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, totalLatency);
                        }
                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            if (VTOPJsonParsers.isJson(res.body)) {
                                context.getStagedData().calendarEvents.addAll(VTOPJsonParsers.parseCalendar(res.body));
                            } else {
                                SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                                context.getStagedData().calendarEvents.addAll(VTOPHtmlParsers.parseCalendarEvents(res.body, monthFmt.format(cal.getTime())));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional CALENDAR task encountered notice: " + e.getMessage());
                    }
                    return SyncTaskResult.success(totalLatency);
                }
            });
        }

        // 7. Courses Task (prerequisite for Timetable, Attendance, Marks, Exams: /login/vitian/studentRegisteredCourse)
        tasks.add(new NativeTask(SyncTaskId.COURSES, Collections.emptySet(), completedCounter, totalTasks, progressListener, true) {
            @Override
            public SyncTaskResult executeTask(SyncContext context) throws Exception {
                // Evidence-based clean JSON payload matching official VITianCC app Contract 2
                Map<String, String> cleanParams = new LinkedHashMap<>();
                cleanParams.put("regNo", context.getAuthorizedID());
                cleanParams.put("semesterId", context.getSemesterID());
                cleanParams.put("semesterSubId", context.getSemesterID());

                VTOPOkHttpNetworkClient.HttpResponse res = networkClient.postJson(
                        VTOPEndpoints.STUDENT_REGISTERED_COURSE, cleanParams, context.getCookieHeader());

                if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                    int retained = context.getStagedData().cachedCourses.size();
                    Log.w(TAG, "Task COURSES: Session unauthorized (HTTP " + res.code + "); retained " + retained + " cached courses from Room DB");
                    return SyncTaskResult.failure("Session Unauthorized (HTTP " + res.code + ")", res.code, 10L);
                }

                if (!res.isSuccessful) {
                    int retained = context.getStagedData().cachedCourses.size();
                    Log.w(TAG, "Task COURSES: HTTP error " + res.code + "; retained " + retained + " cached courses from Room DB");
                    return SyncTaskResult.failure("HTTP error: " + res.code, res.code, 10L);
                }

                if (res.body == null || res.body.trim().isEmpty()) {
                    int retained = context.getStagedData().cachedCourses.size();
                    Log.w(TAG, "Task COURSES: Empty response body; retained " + retained + " cached courses from Room DB");
                    return SyncTaskResult.failure("Empty response body from courses endpoint", res.code, 10L);
                }

                int prevCourses = context.getStagedData().courses.size();
                int prevSlots = context.getStagedData().slots.size();

                if (VTOPJsonParsers.isJson(res.body)) {
                    VTOPJsonParsers.parseCoursesAndSlots(res.body, context.getStagedData().courses, context.getStagedData().slots, context.getStagedData());
                } else {
                    VTOPHtmlParsers.parseCoursesAndSlots(res.body, context.getStagedData().courses, context.getStagedData().slots);
                }

                int freshCourses = context.getStagedData().courses.size() - prevCourses;
                int freshSlots = context.getStagedData().slots.size() - prevSlots;

                if (freshCourses == 0) {
                    int retained = context.getStagedData().cachedCourses.size();
                    Log.w(TAG, "Task COURSES: 0 courses parsed from response; retained " + retained + " cached courses from Room DB");
                    return SyncTaskResult.failure("Zero courses parsed from response", res.code, 10L);
                }

                Log.d(TAG, "Task COURSES: Freshly staged " + freshCourses + " courses and " + freshSlots + " slots (retained cache: " + context.getStagedData().cachedCourses.size() + ")");
                return SyncTaskResult.success(10L);
            }
        });

        if (!quickSyncOnly) {
            // 8. Timetable Generation Task (local computation, depends on COURSES)
            tasks.add(new NativeTask(SyncTaskId.TIMETABLE, setOf(SyncTaskId.COURSES), completedCounter, totalTasks, progressListener, true) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    List<Slot> slotsToUse = context.getStagedData().getEffectiveSlots();
                    List<Timetable> entries = generateLocalTimetable(slotsToUse);
                    context.getStagedData().timetables.addAll(entries);
                    Log.d(TAG, "Task TIMETABLE: Generated " + entries.size() + " periods from " + slotsToUse.size() + " slots");
                    return SyncTaskResult.success(0);
                }
            });
        }

        // In Quick Sync mode with cached courses present, marks and attendance summary can run independently in parallel
        boolean hasCachedCourses = !ctx.getStagedData().cachedCourses.isEmpty() || !ctx.getStagedData().courses.isEmpty();
        Set<SyncTaskId> marksAndAttPrereqs = (quickSyncOnly && hasCachedCourses) ? Collections.emptySet() : setOf(SyncTaskId.COURSES);

        // 9. Attendance Summary Task (computes local aggregate attendance from staged course response)
        // Official APK investigation confirms acadAttendanceCalculator is client-side math (calculateAttendancePlus),
        // not a server endpoint. Summary attendance is extracted directly from studentRegisteredCourse.
        tasks.add(new NativeTask(SyncTaskId.ATTENDANCE_SUMMARY, marksAndAttPrereqs, completedCounter, totalTasks, progressListener, true) {
            @Override
            public SyncTaskResult executeTask(SyncContext context) throws Exception {
                List<Attendance> summaries = context.getStagedData().attendanceSummaries;
                if (!summaries.isEmpty()) {
                    VTOPJsonParsers.AttendanceSummaryResult agg = VTOPJsonParsers.calculateAggregateAttendance(summaries);
                    context.getStagedData().profileAttributes.put("overallAttendance", String.valueOf(agg.overallAttendance));
                    context.getStagedData().profileAttributes.put("attendedClasses", String.valueOf(agg.attendedClasses));
                    context.getStagedData().profileAttributes.put("totalClasses", String.valueOf(agg.totalClasses));
                    Log.d(TAG, "Task ATTENDANCE_SUMMARY: Verified aggregate attendance " + agg.overallAttendance + "% (" +
                            agg.attendedClasses + "/" + agg.totalClasses + ") from " + summaries.size() + " courses");
                } else {
                    int freshCourseCount = context.getStagedData().courses.size();
                    int cachedCourseCount = context.getStagedData().cachedCourses.size();
                    if (freshCourseCount > 0) {
                        Log.i(TAG, "Task ATTENDANCE_SUMMARY: Course response contains no attendance fields across " +
                                freshCourseCount + " fresh courses; preserving existing cached metrics.");
                    } else {
                        Log.d(TAG, "Task ATTENDANCE_SUMMARY: Fresh course list is empty (retained " +
                                cachedCourseCount + " cached courses); preserving existing cached metrics.");
                    }
                }
                return SyncTaskResult.success(0L);
            }
        });

        // 10. Detailed Attendance Task (course-specific attendance calendar via /login/vitian/viewAttendanceCal)
        tasks.add(new NativeTask(SyncTaskId.DETAILED_ATTENDANCE, setOf(SyncTaskId.ATTENDANCE_SUMMARY), completedCounter, totalTasks, progressListener, false) {
            @Override
            public SyncTaskResult executeTask(SyncContext context) throws Exception {
                long totalLatency = 0;
                int initialRecordsCount = context.getStagedData().attendanceRecords.size();
                DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
                int consecutive401 = 0;

                List<Course> coursesToIterate = context.getStagedData().getEffectiveCourses();
                try {
                    for (Course c : coursesToIterate) {
                        if (isCancelled) break;

                        String serverCourseId = context.getStagedData().serverCourseIds.get(c.id);
                        String classId = context.getStagedData().classIds.get(c.id);
                        String slotName = context.getStagedData().slotNames.get(c.id);

                        // If not found in primary maps, also check rawCourseMetadata as fallback
                        if ((serverCourseId == null || serverCourseId.isEmpty()) && context.getStagedData().rawCourseMetadata.containsKey(c.id)) {
                            Map<String, String> meta = context.getStagedData().rawCourseMetadata.get(c.id);
                            if (meta != null) {
                                for (Map.Entry<String, String> e : meta.entrySet()) {
                                    String lk = e.getKey().toLowerCase(Locale.ROOT);
                                    if ((lk.contains("course") && lk.contains("id")) || lk.contains("allocation") || lk.equals("crsid")) {
                                        if (!e.getValue().isEmpty()) {
                                            serverCourseId = e.getValue();
                                            context.getStagedData().serverCourseIds.put(c.id, serverCourseId);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // Strict requirement: Never substitute local auto-increment IDs (e.g. 1, 2) for backend IDs!
                        if (serverCourseId == null || serverCourseId.isEmpty()) {
                            tracker.recordRejection("missing_backend_course_id_for_" + (c.code != null ? c.code : "course_" + c.id));
                            continue;
                        }

                        Map<String, String> attParams = new LinkedHashMap<>();
                        attParams.put("regNo", context.getAuthorizedID());
                        attParams.put("semesterSubId", context.getSemesterID());
                        attParams.put("courseId", serverCourseId);

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.postJson(
                                VTOPEndpoints.ATTENDANCE_CALENDAR, attParams, context.getCookieHeader());
                        totalLatency += 10L;

                        DataIngestionDiagnostics.inspectResponse("ATTENDANCE_CALENDAR[" + c.code + "]", "POST", res.code, res.contentType, res.body);

                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            Log.w(TAG, "DETAILED_ATTENDANCE unauthorized (HTTP " + res.code + "). Aborting.");
                            break;
                        }

                        if (res.code == 401) {
                            consecutive401++;
                            if (consecutive401 >= 2) {
                                Log.w(TAG, "DETAILED_ATTENDANCE received consecutive 401 responses. Aborting remaining requests to avoid redundant failures.");
                                tracker.recordRejection("aborted_consecutive_401");
                                break;
                            }
                            continue;
                        } else {
                            consecutive401 = 0;
                        }

                        if (!res.isSuccessful || res.body == null || res.body.trim().isEmpty()) {
                            tracker.recordRejection("http_err_" + res.code + "_for_" + c.code);
                            continue;
                        }

                        if (VTOPJsonParsers.isJson(res.body)) {
                            List<AttendanceRecord> records = VTOPJsonParsers.parseAttendanceRecords(res.body, c.id, slotName != null ? slotName : "", tracker);
                            context.getStagedData().attendanceRecords.addAll(records);
                        } else {
                            List<AttendanceRecord> records = VTOPHtmlParsers.parseDetailedAttendanceRecords(res.body, c.id, slotName != null ? slotName : "");
                            context.getStagedData().attendanceRecords.addAll(records);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Optional DETAILED_ATTENDANCE notice: " + e.getMessage());
                }

                int newlyStaged = context.getStagedData().attendanceRecords.size() - initialRecordsCount;
                DataIngestionDiagnostics.logTaskAudit("DETAILED_ATTENDANCE", newlyStaged, tracker.getTotalRejections(),
                        tracker.getSummary(), context.getStagedData().attendanceRecords.size());
                return SyncTaskResult.success(totalLatency);
            }
        });

        // 11. Marks Task (active semester assessment marks and course breakdown)
        tasks.add(new NativeTask(SyncTaskId.MARKS, marksAndAttPrereqs, completedCounter, totalTasks, progressListener, false) {
            @Override
            public SyncTaskResult executeTask(SyncContext context) throws Exception {
                DataIngestionDiagnostics.RejectionTracker marksTracker = new DataIngestionDiagnostics.RejectionTracker();
                List<Course> effectiveCourses = context.getStagedData().getEffectiveCourses();
                try {
                    // Strategy 1: Active semester mark view (/login/vitian/activeSemMarkView) - confirmed in libapp.so
                    Map<String, String> activeSemParams = new LinkedHashMap<>();
                    activeSemParams.put("regNo", context.getAuthorizedID());
                    activeSemParams.put("semesterId", context.getSemesterID());

                    VTOPOkHttpNetworkClient.HttpResponse activeSemRes = networkClient.postJson(
                            VTOPEndpoints.ACTIVE_SEM_MARK_VIEW, activeSemParams, context.getCookieHeader());

                    DataIngestionDiagnostics.inspectResponse("ACTIVE_SEM_MARK_VIEW", "POST", activeSemRes.code, activeSemRes.contentType, activeSemRes.body);

                    if (isSessionUnauthorizedOrExpired(activeSemRes, sessionExpiredFlag)) {
                        return SyncTaskResult.failure("Session Expired", 401, 10L);
                    }

                    if (activeSemRes.isSuccessful && activeSemRes.body != null && !activeSemRes.body.trim().isEmpty() && VTOPJsonParsers.isJson(activeSemRes.body)) {
                        VTOPJsonParsers.parseMarks(activeSemRes.body, effectiveCourses,
                                context.getStagedData().marks, context.getStagedData().cumulativeMarks, marksTracker);
                    }

                    // Strategy 2: Per-course breakdown (/login/vitian/gradePageMarkBySemIdCourseId) if activeSemMarkView staged 0
                    if (context.getStagedData().marks.isEmpty() && !effectiveCourses.isEmpty()) {
                        int consecutiveCourse401 = 0;
                        for (Course c : effectiveCourses) {
                            if (isCancelled) break;
                            String serverCourseId = context.getStagedData().serverCourseIds.get(c.id);

                            if ((serverCourseId == null || serverCourseId.isEmpty()) && context.getStagedData().rawCourseMetadata.containsKey(c.id)) {
                                Map<String, String> meta = context.getStagedData().rawCourseMetadata.get(c.id);
                                if (meta != null) {
                                    for (Map.Entry<String, String> e : meta.entrySet()) {
                                        String lk = e.getKey().toLowerCase(Locale.ROOT);
                                        if ((lk.contains("course") && lk.contains("id")) || lk.contains("allocation") || lk.equals("crsid")) {
                                            if (!e.getValue().isEmpty()) {
                                                serverCourseId = e.getValue();
                                                context.getStagedData().serverCourseIds.put(c.id, serverCourseId);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }

                            if (serverCourseId == null || serverCourseId.isEmpty()) {
                                marksTracker.recordRejection("missing_backend_course_id_for_" + c.code);
                                continue;
                            }

                            Map<String, String> courseParams = new LinkedHashMap<>();
                            courseParams.put("regNo", context.getAuthorizedID());
                            courseParams.put("semesterId", context.getSemesterID());
                            courseParams.put("courseId", serverCourseId);

                            try {
                                VTOPOkHttpNetworkClient.HttpResponse cRes = networkClient.postJson(
                                        VTOPEndpoints.GRADE_PAGE_MARK_BY_SEM_COURSE, courseParams, context.getCookieHeader());

                                DataIngestionDiagnostics.inspectResponse("GRADE_PAGE_MARK[" + c.code + "]", "POST", cRes.code, cRes.contentType, cRes.body);

                                if (isSessionUnauthorizedOrExpired(cRes, sessionExpiredFlag)) {
                                    return SyncTaskResult.failure("Session Expired", 401, 10L);
                                }

                                if (cRes.code == 401) {
                                    consecutiveCourse401++;
                                    if (consecutiveCourse401 >= 2) {
                                        marksTracker.recordRejection("course_marks_aborted_consecutive_401");
                                        break;
                                    }
                                    continue;
                                } else {
                                    consecutiveCourse401 = 0;
                                }

                                if (cRes.isSuccessful && cRes.body != null && !cRes.body.trim().isEmpty() && VTOPJsonParsers.isJson(cRes.body)) {
                                    VTOPJsonParsers.parsePerCourseMarks(cRes.body, c, context.getStagedData().marks, marksTracker);
                                }
                            } catch (Exception courseEx) {
                                marksTracker.recordRejection("course_marks_net_err: " + courseEx.getMessage());
                            }
                        }
                    }

                    // Strategy 3: Grade details by semester (/login/vitian/gradeDetailsBySemId) if marks still 0
                    if (context.getStagedData().marks.isEmpty()) {
                        try {
                            Map<String, String> gradeParams = new LinkedHashMap<>();
                            gradeParams.put("regNo", context.getAuthorizedID());
                            gradeParams.put("semesterId", context.getSemesterID());

                            VTOPOkHttpNetworkClient.HttpResponse gradeRes = networkClient.postJson(
                                    VTOPEndpoints.GRADE_DETAILS_BY_SEM_ID, gradeParams, context.getCookieHeader());

                            DataIngestionDiagnostics.inspectResponse("GRADE_DETAILS_BY_SEM_ID", "POST", gradeRes.code, gradeRes.contentType, gradeRes.body);

                            if (isSessionUnauthorizedOrExpired(gradeRes, sessionExpiredFlag)) {
                                return SyncTaskResult.failure("Session Expired", 401, 10L);
                            }

                            if (gradeRes.isSuccessful && gradeRes.body != null && !gradeRes.body.trim().isEmpty() && VTOPJsonParsers.isJson(gradeRes.body)) {
                                VTOPJsonParsers.parseMarks(gradeRes.body, effectiveCourses,
                                        context.getStagedData().marks, context.getStagedData().cumulativeMarks, marksTracker);
                            }
                        } catch (Exception ignored) {}
                    }

                    DataIngestionDiagnostics.logTaskAudit("MARKS", context.getStagedData().marks.size(),
                            marksTracker.getTotalRejections(), marksTracker.getSummary(), context.getStagedData().marks.size());
                } catch (Exception e) {
                    Log.w(TAG, "MARKS task notice: " + e.getMessage());
                }
                return SyncTaskResult.success(10L);
            }
        });

        if (!quickSyncOnly) {
            // 12. Exams Task (depends on COURSES: /login/vitian/examScheduleView)
            tasks.add(new NativeTask(SyncTaskId.EXAMS, setOf(SyncTaskId.COURSES), completedCounter, totalTasks, progressListener, false) {
                @Override
                public SyncTaskResult executeTask(SyncContext context) throws Exception {
                    try {
                        Map<String, String> params = buildApiParams(context);

                        VTOPOkHttpNetworkClient.HttpResponse res = networkClient.post(VTOPEndpoints.EXAM_SCHEDULE_VIEW, params, context.getCookieHeader());
                        if (isSessionUnauthorizedOrExpired(res, sessionExpiredFlag)) {
                            return SyncTaskResult.failure("Session Expired", 401, 10L);
                        }

                        if (res.isSuccessful && res.body != null && !res.body.trim().isEmpty()) {
                            Map<String, Integer> courseIdBySlot = new HashMap<>();
                            for (Slot s : context.getStagedData().getEffectiveSlots()) {
                                courseIdBySlot.put(s.slot, s.courseId);
                            }

                            if (VTOPJsonParsers.isJson(res.body)) {
                                List<Exam> exams = VTOPJsonParsers.parseExamSchedule(res.body, courseIdBySlot);
                                context.getStagedData().exams.addAll(exams);
                            } else {
                                List<Exam> exams = VTOPHtmlParsers.parseExamSchedule(res.body, courseIdBySlot);
                                context.getStagedData().exams.addAll(exams);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Optional EXAMS task notice: " + e.getMessage());
                    }
                    return SyncTaskResult.success(10L);
                }
            });
        }

        return tasks;
    }

    private static boolean isSessionUnauthorizedOrExpired(VTOPOkHttpNetworkClient.HttpResponse res, AtomicBoolean sessionExpiredFlag) {
        if (res == null) return false;
        if (res.code == 401 || res.code == 403 || res.isSessionExpired() || VTOPHtmlParsers.isSessionExpiredOrLoginPage(res.body)) {
            sessionExpiredFlag.set(true);
            return true;
        }
        return false;
    }

    private static boolean checkSession(String html, AtomicBoolean sessionExpiredFlag) {
        if (VTOPHtmlParsers.isSessionExpiredOrLoginPage(html)) {
            sessionExpiredFlag.set(true);
            return true;
        }
        return false;
    }

    private static Set<SyncTaskId> setOf(SyncTaskId... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static List<Timetable> generateLocalTimetable(List<Slot> slots) {
        return com.salmanmalvasi.studentcc.services.VTOPService.generateTimetableEntriesFromSlots(slots);
    }

    private abstract static class NativeTask extends SyncTask {
        private final AtomicInteger completedCounter;
        private final int totalTasks;
        private final ProgressListener listener;
        private final boolean essential;

        public NativeTask(SyncTaskId id, Set<SyncTaskId> prereqs,
                          AtomicInteger completedCounter, int totalTasks,
                          ProgressListener listener, boolean essential) {
            super(id, prereqs);
            this.completedCounter = completedCounter;
            this.totalTasks = totalTasks;
            this.listener = listener;
            this.essential = essential;
        }

        public NativeTask(SyncTaskId id, Set<SyncTaskId> prereqs,
                          AtomicInteger completedCounter, int totalTasks,
                          ProgressListener listener) {
            this(id, prereqs, completedCounter, totalTasks, listener, false);
        }

        @Override
        public boolean isEssential() {
            return essential;
        }

        private String getHumanReadableTaskName(SyncTaskId id) {
            switch (id) {
                case PROFILE: return "Downloading Profile";
                case PROCTOR: return "Downloading Staff Info";
                case SPOTLIGHT: return "Downloading Spotlight";
                case RECEIPTS: return "Downloading Receipts";
                case LEAVE: return "Downloading Leave History";
                case CALENDAR: return "Downloading Academic Calendar";
                case COURSES: return "Downloading Course Info";
                case TIMETABLE: return "Downloading Timetable";
                case ATTENDANCE_SUMMARY: return "Downloading Attendance";
                case DETAILED_ATTENDANCE: return "Downloading Detailed Attendance";
                case MARKS: return "Downloading Marks";
                case EXAMS: return "Downloading Exam Schedule";
                default: return "Syncing Data...";
            }
        }

        @Override
        public SyncTaskResult execute(SyncContext context) throws Exception {
            if (listener != null) {
                listener.onProgress(completedCounter.get(), totalTasks, getHumanReadableTaskName(getId()));
            }
            SyncTaskResult res = executeTask(context);
            int done = completedCounter.incrementAndGet();
            if (listener != null) {
                listener.onProgress(done, totalTasks, getHumanReadableTaskName(getId()));
            }
            return res;
        }

        public abstract SyncTaskResult executeTask(SyncContext context) throws Exception;
    }
}
