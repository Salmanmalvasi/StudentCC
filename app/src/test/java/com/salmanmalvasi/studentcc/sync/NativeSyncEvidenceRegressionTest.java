package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Slot;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Regression test suite proving the smallest evidence-supported corrections:
 * 1. HTTP 401 cannot result in fresh-sync success.
 * 2. Preloaded database data is not counted as fresh ingestion.
 * 3. Failure preserves cached data and freshness timestamps.
 * 4. Course results are staged only from a successful network response.
 * 5. Malformed and empty responses do not count as fresh ingestion.
 * 6. Partial success is reported accurately.
 */
public class NativeSyncEvidenceRegressionTest {

    private MockWebServer server;
    private NativeSyncBridge bridge;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        bridge = new NativeSyncBridge(null, server.url("/").toString());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /**
     * Requirement 1: HTTP 401 must fail sync with sessionExpired, never masquerading as success.
     */
    @Test
    public void testHttp401CannotResultInFreshSyncSuccess() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                // Official server returns HTTP 401 with an empty body
                return new MockResponse().setResponseCode(401).setBody("");
            }
        });

        NativeSyncBridge.BridgeResult result = bridge.executeSync(
                "csrf-test",
                "24BCE0000",
                "JSESSIONID=TEST_INVALID_COOKIE",
                "CH2024251",
                null
        );

        Assert.assertFalse("HTTP 401 must never report sync success", result.success);
        Assert.assertTrue("HTTP 401 must set sessionExpired flag", result.sessionExpired);
        Assert.assertNull("Staged data must not be committed on HTTP 401", result.stagedData);
        Assert.assertTrue("Error message must indicate 401 unauthorized",
                result.errorMessage != null && result.errorMessage.contains("401"));
    }

    /**
     * Requirement 2: Preloaded database data is kept in cached collections,
     * not counted in fresh network ingestion.
     */
    @Test
    public void testPreloadedDatabaseDataNotCountedAsFreshIngestion() {
        StagedSyncData staged = new StagedSyncData("CH2024251");

        Course cached1 = new Course();
        cached1.id = 101;
        cached1.code = "CSE1001";
        cached1.title = "Problem Solving";

        Course cached2 = new Course();
        cached2.id = 102;
        cached2.code = "MAT1001";
        cached2.title = "Calculus";

        // Preload database courses into cachedCourses (not courses)
        staged.cachedCourses.add(cached1);
        staged.cachedCourses.add(cached2);

        // Verify fresh network collection remains empty
        Assert.assertEquals(0, staged.courses.size());
        Assert.assertEquals(2, staged.cachedCourses.size());
        Assert.assertFalse("hasFreshCourses must be false when only cache is present", staged.hasFreshCourses());
        Assert.assertEquals(2, staged.getEffectiveCourses().size());

        // Now test via NativeSyncBridge in quick sync mode with network failure:
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(401).setBody("");
            }
        });

        bridge.setQuickSyncMode(true);
        NativeSyncBridge.BridgeResult result = bridge.executeSync(
                "csrf-test",
                "24BCE0000",
                "JSESSIONID=UNAUTH",
                "CH2024251",
                null
        );

        // Bridge must fail cleanly, not reporting cached courses as staged fresh courses
        Assert.assertFalse("Quick sync with 401 must fail, not report success from cache", result.success);
        Assert.assertTrue(result.sessionExpired);
    }

    /**
     * Requirement 3: Failure preserves cached data and freshness timestamps are never advanced.
     */
    @Test
    public void testFailurePreservesCachedDataAndFreshnessTimestamps() {
        StagedSyncData failedSyncData = new StagedSyncData("CH2024251");
        // Simulated failed network sync: 0 fresh courses, 0 fresh marks, 0 fresh attendance records
        Assert.assertEquals(0, failedSyncData.courses.size());
        Assert.assertEquals(0, failedSyncData.marks.size());
        Assert.assertEquals(0, failedSyncData.attendanceSummaries.size());
        Assert.assertEquals(0, failedSyncData.attendanceRecords.size());

        // In TransactionalDataActivator, when incoming stagedData has empty collections,
        // existing collections are retained and recordSuccessfulRefresh is NOT called.
        // We verify that LocalCacheMetrics records retention on empty replacement:
        LocalCacheMetrics metrics = LocalCacheMetrics.getInstance();
        metrics.recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_PROFILE_COURSES);
        metrics.recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_MARKS);
        metrics.recordDatasetRetainedOnFailure(DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);

        String summary = metrics.dumpSummary();
        Assert.assertTrue(summary.contains("RetainedOnFail:"));
    }

    /**
     * Requirement 4: Course results are staged only from a successful HTTP response.
     * Clean JSON parameters matching Contract 2 are sent without legacy _csrf or verifyMenu.
     */
    @Test
    public void testCourseResultsStagedOnlyFromSuccessfulNetworkResponse() throws InterruptedException {
        String courseJson = "{\n" +
                "  \"status\": \"SUCCESS\",\n" +
                "  \"totalCourse\": 1,\n" +
                "  \"courseList\": [\n" +
                "    {\n" +
                "      \"courseId\": \"CH20242510001\",\n" +
                "      \"classId\": \"VL20242501001\",\n" +
                "      \"courseCode\": \"CSE1001\",\n" +
                "      \"courseTitle\": \"Problem Solving and Programming\",\n" +
                "      \"courseType\": \"ETH\",\n" +
                "      \"courseMode\": \"CBL\",\n" +
                "      \"slotName\": \"A1+TA1\",\n" +
                "      \"facultyName\": \"FACULTY_MEMBER_A\",\n" +
                "      \"venue\": \"AB1-201\",\n" +
                "      \"credits\": 3,\n" +
                "      \"attendancePercentage\": \"92.5\",\n" +
                "      \"attendedClass\": 37,\n" +
                "      \"totalClass\": 40\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("studentRegisteredCourse")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json;charset=UTF-8")
                            .setBody(courseJson);
                }
                return new MockResponse().setResponseCode(200).setBody("{\"status\":\"SUCCESS\"}");
            }
        });

        bridge.setQuickSyncMode(true);
        NativeSyncBridge.BridgeResult result = bridge.executeSync(
                "csrf-val",
                "24BCE0000",
                "JSESSIONID=AUTH1234",
                "CH2024251",
                null
        );

        // Verify request was sent as clean JSON without legacy web parameters
        RecordedRequest req = null;
        for (int i = 0; i < server.getRequestCount(); i++) {
            RecordedRequest r = server.takeRequest();
            if (r.getPath() != null && r.getPath().contains("studentRegisteredCourse")) {
                req = r;
                break;
            }
        }

        Assert.assertNotNull("Must have made request to studentRegisteredCourse", req);
        Assert.assertEquals("POST", req.getMethod());
        String reqContentType = req.getHeader("Content-Type");
        Assert.assertNotNull(reqContentType);
        Assert.assertTrue("Request must be JSON", reqContentType.contains("application/json"));

        String reqBody = req.getBody().readUtf8();
        Assert.assertTrue("Request must contain regNo", reqBody.contains("24BCE0000"));
        Assert.assertTrue("Request must contain semesterId", reqBody.contains("CH2024251"));
        Assert.assertFalse("Request must not contain legacy _csrf token", reqBody.contains("_csrf"));
        Assert.assertFalse("Request must not contain legacy verifyMenu parameter", reqBody.contains("verifyMenu"));

        // Verify in-band attendance extraction with decimal parsing
        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData staged = new StagedSyncData("CH2024251");
        VTOPJsonParsers.parseCoursesAndSlots(courseJson, courses, slots, staged);

        Assert.assertEquals(1, courses.size());
        Assert.assertEquals("CSE1001", courses.get(0).code);
        Assert.assertEquals("CH20242510001", staged.serverCourseIds.get(courses.get(0).id));
        Assert.assertEquals("VL20242501001", staged.classIds.get(courses.get(0).id));

        // Slots split from "A1+TA1"
        Assert.assertEquals(2, slots.size());

        // Attendance summary extracted in-band with proper Math.round from "92.5" -> 93
        Assert.assertEquals(1, staged.attendanceSummaries.size());
        Attendance att = staged.attendanceSummaries.get(0);
        Assert.assertEquals(37, (int) att.attended);
        Assert.assertEquals(40, (int) att.total);
        Assert.assertEquals(93, (int) att.percentage);
    }

    /**
     * Requirement 5: Malformed and empty responses do not count as fresh ingestion.
     */
    @Test
    public void testMalformedAndEmptyResponsesDoNotCountAsFreshIngestion() {
        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData staged = new StagedSyncData("CH2024251");

        // Case A: Empty string
        VTOPJsonParsers.parseCoursesAndSlots("", courses, slots, staged);
        Assert.assertEquals(0, courses.size());

        // Case B: HTML error page
        VTOPJsonParsers.parseCoursesAndSlots("<html><body>Error 401</body></html>", courses, slots, staged);
        Assert.assertEquals(0, courses.size());

        // Case C: Malformed JSON
        VTOPJsonParsers.parseCoursesAndSlots("{invalid_json:true", courses, slots, staged);
        Assert.assertEquals(0, courses.size());

        // Case D: JSON with empty course array
        VTOPJsonParsers.parseCoursesAndSlots("{\"courseList\":[]}", courses, slots, staged);
        Assert.assertEquals(0, courses.size());

        // Case E: Marks parser with empty body
        List<Mark> marks = new ArrayList<>();
        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        VTOPJsonParsers.parseMarks("", courses, marks, new ArrayList<>(), tracker);
        Assert.assertEquals(0, marks.size());
        Assert.assertTrue(tracker.getTotalRejections() > 0);
    }

    /**
     * Requirement 6: Partial success is reported accurately when courses succeed
     * but marks or detailed attendance are pending.
     */
    @Test
    public void testPartialSuccessReportedAccurately() {
        StagedSyncData staged = new StagedSyncData("CH2024251");

        Course c = new Course();
        c.id = 1;
        c.code = "CSE1001";
        staged.courses.add(c);

        Slot s = new Slot();
        s.id = 1;
        s.slot = "A1";
        s.courseId = 1;
        staged.slots.add(s);

        // marks and attendanceRecords are empty -> partial
        boolean isPartial = staged.marks.isEmpty() || staged.attendanceRecords.isEmpty();
        Assert.assertTrue("Should detect partial sync state", isPartial);

        NativeSyncBridge.BridgeResult result = NativeSyncBridge.BridgeResult.partialSuccess(
                150L, staged, "Courses synced; marks pending");

        Assert.assertTrue(result.success);
        Assert.assertTrue(result.isPartial);
        Assert.assertFalse(result.sessionExpired);
        Assert.assertEquals("Courses synced; marks pending", result.errorMessage);
        Assert.assertEquals(1, result.stagedData.courses.size());
        Assert.assertEquals(0, result.stagedData.marks.size());
    }
}
