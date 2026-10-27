package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Slot;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test suite verifying marks and detailed attendance data ingestion,
 * diagnostic response analysis, rejection tracking, and multi-format schema handling.
 */
public class MarksAndDetailedAttendanceIngestionTest {

    private List<Course> testCourses;
    private List<Slot> testSlots;
    private StagedSyncData stagedData;

    @Before
    public void setUp() {
        stagedData = new StagedSyncData("WS202324");
        testCourses = new ArrayList<>();
        testSlots = new ArrayList<>();

        Course c1 = new Course();
        c1.id = 1;
        c1.code = "BCSE101E";
        c1.title = "Computer Programming";
        c1.type = "Theory";
        testCourses.add(c1);

        Course c2 = new Course();
        c2.id = 2;
        c2.code = "BCSE102L";
        c2.title = "Data Structures Lab";
        c2.type = "Lab";
        testCourses.add(c2);

        stagedData.courses.addAll(testCourses);
        stagedData.serverCourseIds.put(1, "10452");
        stagedData.serverCourseIds.put(2, "10453");
        stagedData.classIds.put(1, "CH20232401");
        stagedData.classIds.put(2, "CH20232402");
    }

    @Test
    public void testPerCourseMarkViewJsonParsing() {
        String json = "{\n" +
                "  \"status\": true,\n" +
                "  \"markView\": [\n" +
                "    {\"markTitle\": \"Continuous Assessment Test 1\", \"maxMark\": 30.0, \"marksGiven\": 27.5, \"weightage\": 15.0},\n" +
                "    {\"markTitle\": \"Continuous Assessment Test 2\", \"maxMark\": 30.0, \"marksGiven\": 28.0, \"weightage\": 15.0},\n" +
                "    {\"markTitle\": \"Digital Assignment 1\", \"maxMark\": 10.0, \"marksGiven\": 10.0, \"weightage\": 10.0}\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();

        VTOPJsonParsers.parsePerCourseMarks(json, testCourses.get(0), outMarks, tracker);

        assertEquals("Should extract 3 marks for course 1", 3, outMarks.size());
        assertEquals("Course ID should match course 1", Integer.valueOf(1), outMarks.get(0).courseId);
        assertEquals("Title should match", "Continuous Assessment Test 1", outMarks.get(0).title);
        assertEquals(30.0, outMarks.get(0).maxScore, 0.01);
        assertEquals(27.5, outMarks.get(0).score, 0.01);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testAllCoursesStudentMarkListNestedParsing() {
        String json = "{\n" +
                "  \"status\": true,\n" +
                "  \"studentMarkList\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE101E\",\n" +
                "      \"markList\": [\n" +
                "        {\"markTitle\": \"CAT 1\", \"maxMark\": 50, \"marksGiven\": 44, \"weightage\": 15},\n" +
                "        {\"markTitle\": \"FAT\", \"maxMark\": 100, \"marksGiven\": 89, \"weightage\": 40}\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE102L\",\n" +
                "      \"marks\": [\n" +
                "        {\"markTitle\": \"Lab Assessment 1\", \"maxMark\": 100, \"marksGiven\": 95, \"weightage\": 50}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();

        VTOPJsonParsers.parseMarks(json, testCourses, outMarks, new ArrayList<>(), tracker);

        assertEquals("Should parse 3 total marks across both courses", 3, outMarks.size());
        assertEquals(Integer.valueOf(1), outMarks.get(0).courseId);
        assertEquals(Integer.valueOf(1), outMarks.get(1).courseId);
        assertEquals(Integer.valueOf(2), outMarks.get(2).courseId);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testDetailedAttendanceMultiShapeJsonParsing() {
        String json = "{\n" +
                "  \"status\": true,\n" +
                "  \"data\": {\n" +
                "    \"attendanceDetail\": [\n" +
                "      {\"calendarDate\": \"2024-01-15\", \"attendanceStatus\": \"Present\", \"slotName\": \"A1\", \"remarks\": \"Regular\"},\n" +
                "      {\"calendarDate\": \"2024-01-17\", \"attendanceStatus\": \"Absent\", \"slotName\": \"A1\", \"remarks\": \"Sick\"},\n" +
                "      {\"calendarDate\": \"2024-01-22\", \"attendanceStatus\": \"On Duty\", \"slotName\": \"A1\", \"remarks\": \"Tech fest\"}\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<AttendanceRecord> records = VTOPJsonParsers.parseAttendanceRecords(json, 1, "A1", tracker);

        assertEquals("Should extract 3 detailed attendance records", 3, records.size());
        assertEquals(Integer.valueOf(1), records.get(0).courseId);
        assertEquals("2024-01-15", records.get(0).dateString);
        assertEquals("Present", records.get(0).status);
        assertEquals("Absent", records.get(1).status);
        assertEquals("On Duty", records.get(2).status);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testUnexpectedEmptyAndAuthResponseClassification() {
        String authErrorHtml = "<html><head><title>VTOP: Login</title></head><body><h1>Session Expired</h1><input name='passwd'/></body></html>";

        DataIngestionDiagnostics.ResponseDiagnostic diag = DataIngestionDiagnostics.inspectResponse(
                "/login/vitian/markView", "POST", 200, "text/html", authErrorHtml);

        assertTrue("Should detect authentication or expired session page despite HTTP 200", diag.isAuthOrErrorPage);
        assertEquals("HTML", diag.detectedFormat);
        assertTrue("Summary should reflect table absence or title", diag.structureSummary.contains("VTOP: Login"));

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();
        VTOPJsonParsers.parseMarks(authErrorHtml, testCourses, outMarks, new ArrayList<>(), tracker);

        assertTrue("No marks should be extracted from auth error HTML by JSON parser", outMarks.isEmpty());
        assertTrue("Rejection tracker should record non-JSON format", tracker.getTotalRejections() > 0);
    }

    @Test
    public void testRejectionTrackingForUnknownCourseCode() {
        String json = "{\n" +
                "  \"markView\": [\n" +
                "    {\"courseCode\": \"NON_EXISTENT_999\", \"markTitle\": \"Quiz 1\", \"maxMark\": 10, \"marksGiven\": 8}\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();

        VTOPJsonParsers.parseMarks(json, testCourses, outMarks, new ArrayList<>(), tracker);

        assertEquals("No marks staged for unmatched course", 0, outMarks.size());
        assertEquals("Should record 1 rejection", 1, tracker.getTotalRejections());
        assertTrue(tracker.getSummary().contains("course_code_not_matched"));
    }

    @Test
    public void testCourseAndSlotMetadataCapture() {
        String json = "{\n" +
                "  \"courseList\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BMAT101L\",\n" +
                "      \"courseTitle\": \"Calculus\",\n" +
                "      \"courseType\": \"Theory\",\n" +
                "      \"courseId\": \"99012\",\n" +
                "      \"classId\": \"CH202399\",\n" +
                "      \"slotName\": \"B1+TB1\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData stage = new StagedSyncData("WS202324");

        VTOPJsonParsers.parseCoursesAndSlots(json, courses, slots, stage);

        assertEquals(1, courses.size());
        assertEquals("BMAT101L", courses.get(0).code);
        assertEquals(2, slots.size());

        // Verify captured server identifiers
        assertEquals("99012", stage.serverCourseIds.get(courses.get(0).id));
        assertEquals("CH202399", stage.classIds.get(courses.get(0).id));
        assertEquals("B1+TB1", stage.slotNames.get(courses.get(0).id));
    }

    @Test
    public void testAttendanceExtractionFromRegisteredCourseData() {
        String json = "{\n" +
                "  \"courseList\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE101E\",\n" +
                "      \"courseTitle\": \"Computer Programming\",\n" +
                "      \"courseType\": \"Theory\",\n" +
                "      \"courseId\": \"10452\",\n" +
                "      \"classId\": \"CH20232401\",\n" +
                "      \"slotName\": \"A1+TA1\",\n" +
                "      \"attendedClasses\": 24,\n" +
                "      \"totalClasses\": 28,\n" +
                "      \"attendancePercentage\": 86\n" +
                "    },\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE102L\",\n" +
                "      \"courseTitle\": \"Data Structures Lab\",\n" +
                "      \"courseType\": \"Lab\",\n" +
                "      \"courseId\": \"10453\",\n" +
                "      \"classId\": \"CH20232402\",\n" +
                "      \"slotName\": \"L1+L2\",\n" +
                "      \"attendedClasses\": 10,\n" +
                "      \"totalClasses\": 10,\n" +
                "      \"attendancePercentage\": 100\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData stage = new StagedSyncData("WS202324");

        VTOPJsonParsers.parseCoursesAndSlots(json, courses, slots, stage);

        assertEquals("Should parse 2 courses", 2, courses.size());
        assertEquals("Should extract 2 attendance summaries directly from registered courses", 2, stage.attendanceSummaries.size());

        com.salmanmalvasi.studentcc.models.Attendance att1 = stage.attendanceSummaries.get(0);
        assertEquals(Integer.valueOf(courses.get(0).id), att1.courseId);
        assertEquals(Integer.valueOf(24), att1.attended);
        assertEquals(Integer.valueOf(28), att1.total);
        assertEquals(Integer.valueOf(86), att1.percentage);

        com.salmanmalvasi.studentcc.models.Attendance att2 = stage.attendanceSummaries.get(1);
        assertEquals(Integer.valueOf(courses.get(1).id), att2.courseId);
        assertEquals(Integer.valueOf(10), att2.attended);
        assertEquals(Integer.valueOf(10), att2.total);
        assertEquals(Integer.valueOf(100), att2.percentage);

        // Verify aggregate calculation in profileAttributes: (24+10)/(28+10) = 34/38 = 89.47%
        assertEquals("34", stage.profileAttributes.get("attendedClasses"));
        assertEquals("38", stage.profileAttributes.get("totalClasses"));
        assertEquals("89.47", stage.profileAttributes.get("overallAttendance"));
    }

    @Test
    public void testLocalAttendanceCalculationsWithExclusions() {
        List<com.salmanmalvasi.studentcc.models.Attendance> list = new ArrayList<>();

        // Course 1: 20/25 (80%)
        com.salmanmalvasi.studentcc.models.Attendance a1 = new com.salmanmalvasi.studentcc.models.Attendance();
        a1.courseId = 1;
        a1.attended = 20;
        a1.total = 25;
        list.add(a1);

        // Course 2: 15/15 (100%)
        com.salmanmalvasi.studentcc.models.Attendance a2 = new com.salmanmalvasi.studentcc.models.Attendance();
        a2.courseId = 2;
        a2.attended = 15;
        a2.total = 15;
        list.add(a2);

        // Course 3: 0/0 (Project / not yet commenced / non-conducted - must be excluded)
        com.salmanmalvasi.studentcc.models.Attendance a3 = new com.salmanmalvasi.studentcc.models.Attendance();
        a3.courseId = 3;
        a3.attended = 0;
        a3.total = 0;
        list.add(a3);

        VTOPJsonParsers.AttendanceSummaryResult result = VTOPJsonParsers.calculateAggregateAttendance(list);

        assertEquals("Attended classes should be 20 + 15 = 35", 35, result.attendedClasses);
        assertEquals("Total classes should be 25 + 15 = 40 (excluding 0 total course)", 40, result.totalClasses);
        // 35 / 40 = 87.5%
        assertEquals(87.50f, result.overallAttendance, 0.01f);
    }

    @Test
    public void testActiveSemMarkViewJsonParsing() {
        // Representative response fixture for /login/vitian/activeSemMarkView
        String json = "{\n" +
                "  \"status\": true,\n" +
                "  \"activeSemMarkView\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE101E\",\n" +
                "      \"markTitle\": \"CAT 1\",\n" +
                "      \"markModeCode\": \"CBT\",\n" +
                "      \"maxMark\": 50.0,\n" +
                "      \"marksGiven\": 46.5,\n" +
                "      \"weightageMarkView\": 15.0\n" +
                "    },\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE101E\",\n" +
                "      \"markTitle\": \"DA 1\",\n" +
                "      \"markModeCode\": \"UPLOAD\",\n" +
                "      \"maxMark\": 10.0,\n" +
                "      \"marksGiven\": 10.0,\n" +
                "      \"weightageMarkView\": 10.0\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();

        VTOPJsonParsers.parseMarks(json, testCourses, outMarks, new ArrayList<>(), tracker);

        assertEquals("Should parse 2 marks from activeSemMarkView", 2, outMarks.size());
        assertEquals("BCSE101E course ID 1", Integer.valueOf(1), outMarks.get(0).courseId);
        assertEquals("CAT 1", outMarks.get(0).title);
        assertEquals(50.0, outMarks.get(0).maxScore, 0.01);
        assertEquals(46.5, outMarks.get(0).score, 0.01);
        assertEquals(15.0, outMarks.get(0).weightage, 0.01);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testGradePageMarkBySemIdCourseIdParsing() {
        // Representative fixture for per-course /login/vitian/gradePageMarkBySemIdCourseId
        String json = "{\n" +
                "  \"gradePageMarkBySemIdCourseId\": [\n" +
                "    {\"markTitle\": \"Mid Term Test\", \"maxMark\": 60.0, \"marksGiven\": 52.0, \"weightageMarkView\": 30.0},\n" +
                "    {\"markTitle\": \"Final Assessment Test\", \"maxMark\": 100.0, \"marksGiven\": 88.0, \"weightageMarkView\": 40.0}\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<Mark> outMarks = new ArrayList<>();

        VTOPJsonParsers.parsePerCourseMarks(json, testCourses.get(1), outMarks, tracker);

        assertEquals("Should parse 2 marks for course 2", 2, outMarks.size());
        assertEquals(Integer.valueOf(2), outMarks.get(0).courseId);
        assertEquals("Mid Term Test", outMarks.get(0).title);
        assertEquals(52.0, outMarks.get(0).score, 0.01);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testAttendanceCalDataOfficialApkTrace() {
        // Confirmed fields from libapp.so at 0x39672: attendanceCalData, attendanceDate, attendanceDay, attendanceSlot, attendanceStatus
        String json = "{\n" +
                "  \"attendanceCalData\": [\n" +
                "    {\n" +
                "      \"attendanceDate\": \"2024-02-05\",\n" +
                "      \"attendanceDay\": \"Monday\",\n" +
                "      \"attendanceSlot\": \"A1\",\n" +
                "      \"attendanceStatus\": \"Present\",\n" +
                "      \"attendanceStatusDescription\": \"Regular Instruction\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"attendanceDate\": \"2024-02-07\",\n" +
                "      \"attendanceDay\": \"Wednesday\",\n" +
                "      \"attendanceSlot\": \"A1\",\n" +
                "      \"attendanceStatus\": \"Absent\",\n" +
                "      \"attendanceStatusDescription\": \"Medical Leave\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        DataIngestionDiagnostics.RejectionTracker tracker = new DataIngestionDiagnostics.RejectionTracker();
        List<AttendanceRecord> records = VTOPJsonParsers.parseAttendanceRecords(json, 1, "A1", tracker);

        assertEquals("Should parse 2 attendance calendar punches", 2, records.size());
        assertEquals(Integer.valueOf(1), records.get(0).courseId);
        assertEquals("2024-02-05", records.get(0).dateString);
        assertEquals("Present", records.get(0).status);
        assertEquals("A1", records.get(0).session);
        assertEquals("Absent", records.get(1).status);
        assertEquals(0, tracker.getTotalRejections());
    }

    @Test
    public void testBridgeResultPartialSyncFlag() {
        StagedSyncData data = new StagedSyncData("WS202324");
        data.courses.addAll(testCourses);
        // Staged marks and attendanceRecords are empty

        NativeSyncBridge.BridgeResult partialResult = NativeSyncBridge.BridgeResult.partialSuccess(
                250L, data, "Courses staged; marks pending");

        assertTrue("Must report overall success so courses can be viewed", partialResult.success);
        assertTrue("Must accurately flag isPartial", partialResult.isPartial);
        assertFalse("Must not report session expired", partialResult.sessionExpired);
        assertEquals(2, partialResult.stagedData.courses.size());
    }

    @Test
    public void testBackendIdentifierPreservationNoAutoIncrementSubstitution() {
        StagedSyncData stage = new StagedSyncData("WS202324");
        Course c = new Course();
        c.id = 42; // Local auto-increment ID
        c.code = "BMAT102L";
        stage.courses.add(c);

        // Intentionally do NOT put serverCourseIds or classIds
        // Verify that absent backend IDs are safely detected
        String serverCourseId = stage.serverCourseIds.get(c.id);
        String classId = stage.classIds.get(c.id);
        boolean hasBackendId = (serverCourseId != null && !serverCourseId.isEmpty()) || (classId != null && !classId.isEmpty());

        assertFalse("Must not consider local auto-increment ID as valid backend identifier", hasBackendId);
        assertNull("Server course ID must be null when not provided by backend", serverCourseId);

        // When backend IDs are properly captured from studentRegisteredCourse:
        stage.serverCourseIds.put(c.id, "90210");
        stage.classIds.put(c.id, "CH202499");

        assertEquals("90210", stage.serverCourseIds.get(c.id));
        assertEquals("CH202499", stage.classIds.get(c.id));
    }

    @Test
    public void testPostAdaptiveNoRetryOn401() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        okhttp3.OkHttpClient mockClient = new okhttp3.OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    callCount.incrementAndGet();
                    return new okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(401)
                            .message("Unauthorized")
                            .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), ""))
                            .build();
                })
                .build();

        VTOPOkHttpNetworkClient netClient = new VTOPOkHttpNetworkClient(mockClient, "https://test.vtop.ac.in");
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("regNo", "24BCE1234");
        params.put("semesterId", "CH2024251");

        VTOPOkHttpNetworkClient.HttpResponse res = netClient.postAdaptive("/login/vitian/activeSemMarkView", params, "JSESSIONID=test");

        assertEquals(401, res.code);
        assertEquals("Must NOT perform duplicate/fallback retry on HTTP 401", 1, callCount.get());
    }

    @Test
    public void testPostAdaptiveFallbackOn415() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<String> contentTypes = new java.util.ArrayList<>();

        okhttp3.OkHttpClient mockClient = new okhttp3.OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int count = callCount.incrementAndGet();
                    okhttp3.Request req = chain.request();
                    if (req.body() != null && req.body().contentType() != null) {
                        contentTypes.add(req.body().contentType().toString());
                    }
                    if (count == 1) {
                        return new okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(415)
                                .message("Unsupported Media Type")
                                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"error\":\"Unsupported Media Type\"}"))
                                .build();
                    } else {
                        return new okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"status\":true}"))
                                .build();
                    }
                })
                .build();

        VTOPOkHttpNetworkClient netClient = new VTOPOkHttpNetworkClient(mockClient, "https://test.vtop.ac.in");
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("regNo", "24BCE1234");

        VTOPOkHttpNetworkClient.HttpResponse res = netClient.postAdaptive("/login/vitian/testEndpoint", params, "JSESSIONID=test");

        assertEquals(200, res.code);
        assertEquals("Must execute attempt 1 (JSON) and fallback attempt 2 (form) on HTTP 415", 2, callCount.get());
        assertTrue("Attempt 1 must be JSON", contentTypes.get(0).contains("application/json"));
        assertTrue("Attempt 2 must be form-urlencoded", contentTypes.get(1).contains("application/x-www-form-urlencoded"));
    }

    @Test
    public void testRawCourseMetadataPreservationAndCandidateResolution() {
        String json = "{\n" +
                "  \"courseList\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE307L\",\n" +
                "      \"courseTitle\": \"Compiler Design\",\n" +
                "      \"courseType\": \"Theory\",\n" +
                "      \"courseAllocationId\": \"ALLO-9876\",\n" +
                "      \"classNbr\": \"CLASS-5432\",\n" +
                "      \"slotName\": \"A1\",\n" +
                "      \"erpCustomToken\": \"XYZ-123\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData stage = new StagedSyncData("CH2024251");

        VTOPJsonParsers.parseCoursesAndSlots(json, courses, slots, stage);

        assertEquals(1, courses.size());
        Course c = courses.get(0);

        // Verify raw metadata was preserved
        assertNotNull(stage.rawCourseMetadata.get(c.id));
        assertEquals("XYZ-123", stage.rawCourseMetadata.get(c.id).get("erpCustomToken"));
        assertEquals("ALLO-9876", stage.rawCourseMetadata.get(c.id).get("courseAllocationId"));
        assertEquals("CLASS-5432", stage.rawCourseMetadata.get(c.id).get("classNbr"));

        // Verify candidate resolution mapped courseAllocationId and classNbr
        assertEquals("ALLO-9876", stage.serverCourseIds.get(c.id));
        assertEquals("CLASS-5432", stage.classIds.get(c.id));
    }

    @Test
    public void testNestedAttendanceObjectParsingFromCourse() {
        String json = "{\n" +
                "  \"courseList\": [\n" +
                "    {\n" +
                "      \"courseCode\": \"BCSE305L\",\n" +
                "      \"courseTitle\": \"Embedded Systems\",\n" +
                "      \"courseType\": \"Theory\",\n" +
                "      \"courseId\": \"12345\",\n" +
                "      \"classId\": \"CH202401\",\n" +
                "      \"attendance\": {\n" +
                "        \"attendedClass\": 22,\n" +
                "        \"totalClass\": 25,\n" +
                "        \"attendancePercentage\": 88\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<Course> courses = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        StagedSyncData stage = new StagedSyncData("CH2024251");

        VTOPJsonParsers.parseCoursesAndSlots(json, courses, slots, stage);

        assertEquals(1, courses.size());
        assertEquals("Should extract attendance from nested attendance object", 1, stage.attendanceSummaries.size());
        com.salmanmalvasi.studentcc.models.Attendance att = stage.attendanceSummaries.get(0);
        assertEquals(Integer.valueOf(22), att.attended);
        assertEquals(Integer.valueOf(25), att.total);
        assertEquals(Integer.valueOf(88), att.percentage);
    }

    @Test
    public void testStructuralLoggingSafety() {
        // Must handle valid object, valid array, empty string, null without throwing
        VTOPJsonParsers.logCourseResponseStructure("{\"status\":true,\"courseList\":[{\"code\":\"BCSE101\"}]}");
        VTOPJsonParsers.logCourseResponseStructure("[{\"code\":\"BCSE101\"}]");
        VTOPJsonParsers.logCourseResponseStructure("");
        VTOPJsonParsers.logCourseResponseStructure(null);
        VTOPJsonParsers.logCourseResponseStructure("invalid json {[[");
    }
}
