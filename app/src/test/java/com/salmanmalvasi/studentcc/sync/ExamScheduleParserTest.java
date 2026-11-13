package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Exam;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ExamScheduleParserTest {
    private final List<Course> courses = new ArrayList<>();
    private final Map<String, Integer> slots = new HashMap<>();

    @Before
    public void setUp() {
        courses.clear();
        courses.add(course(1, "BCSE101L", "theory"));
        courses.add(course(2, "BMAT201L", "theory"));
        courses.add(course(3, "BCSE101L", "lab"));
        slots.put("A1", 1);
        slots.put("TA1", 1);
        slots.put("B1", 2);
    }

    @Test
    public void rawArrayPreservesActualDateTimeAndSeat() {
        ExamScheduleParser.Result result = parse("[{\"slotName\":\"A1+TA1\",\"examTitle\":\"CAT1\",\"examDate\":\"13-NOV-2026\",\"examTime\":\"09:00 AM - 10:30 AM\",\"venue\":\"AB1-204\",\"seatNumber\":12}]");
        assertTrue(result.hasUsableSchedule());
        assertEquals(1, result.exams.size());
        Exam exam = result.exams.get(0);
        assertEquals(Integer.valueOf(1), exam.courseId);
        assertEquals("CAT 1", exam.title);
        assertEquals(Long.valueOf(1794540600000L), exam.startTime); // 09:00 India, 03:30 UTC
        assertEquals(90 * 60000L, exam.endTime - exam.startTime);
        assertEquals(Integer.valueOf(12), exam.seatNumber);
    }

    @Test
    public void nestedApiResponseMatchesCourseCodeBeforeSlot() {
        ExamScheduleParser.Result result = parse("{\"data\":{\"examScheduleView\":[{\"courseCode\":\"BMAT201L\",\"slot\":\"A1\",\"date\":\"2026-11-14\",\"startTime\":\"14:00\",\"endTime\":\"16:00\",\"examTitle\":\"FAT\"}]}}");
        assertEquals(1, result.exams.size());
        assertEquals(Integer.valueOf(2), result.exams.get(0).courseId);
        assertEquals(2 * 3600000L, result.exams.get(0).endTime - result.exams.get(0).startTime);
    }

    @Test
    public void explicitCourseTypeKeepsLabAndTheorySittingsSeparate() {
        Exam exam = parse("[{\"courseCode\":\"BCSE101L\",\"courseType\":\"Lab\",\"date\":\"2026-11-14\",\"examTitle\":\"FAT\"}]").exams.get(0);
        assertEquals(Integer.valueOf(3), exam.courseId);
    }

    @Test
    public void groupedPortalJsonSupportsSnakeCaseAndAlphanumericSeats() {
        ExamScheduleParser.Result result = parse("{\"CAT2\":[{\"slot\":\"A1\",\"date\":\"15/11/2026\",\"start_time\":\"9:00AM\",\"end_time\":\"10:30AM\",\"seat_number\":\"A12\",\"venue\":\"--\"}]}");
        assertEquals("CAT 2", result.exams.get(0).title);
        assertEquals("A12", result.exams.get(0).seatLocation);
        assertNull(result.exams.get(0).venue);
        assertEquals(90 * 60000L, result.exams.get(0).endTime - result.exams.get(0).startTime);
    }

    @Test
    public void thHeadersUseActualColumnsAndKeepVenueWithoutSeat() {
        String html = "<table><tr><th>Sl. No.</th><th>Course Code</th><th>Course Title</th><th>Exam Date</th><th>Exam Time</th><th>Slot</th><th>Venue</th></tr>"
                + "<tr><td colspan='7'>FAT</td></tr>"
                + "<tr><td>1</td><td>BMAT201L</td><td>Calculus</td><td>16-NOV-2026</td><td>14:00 – 17:00</td><td>B1</td><td>AB3-301</td></tr></table>";
        ExamScheduleParser.Result result = parse(html);
        assertEquals(1, result.exams.size());
        assertEquals(Integer.valueOf(2), result.exams.get(0).courseId);
        assertEquals("FAT", result.exams.get(0).title);
        assertEquals("AB3-301", result.exams.get(0).venue);
        assertNull(result.exams.get(0).seatLocation);
        assertEquals(3 * 3600000L, result.exams.get(0).endTime - result.exams.get(0).startTime);
    }

    @Test
    public void tdHeadersAndOptionalSeatColumnsDoNotDropRows() {
        String html = "<table><tr><td>Slot</td><td>Exam Date</td><td>Exam Time</td><td>Venue</td><td>Seat Location</td><td>Seat No.</td></tr>"
                + "<tr><th colspan='6'>CAT1</th></tr>"
                + "<tr><td>a1 + ta1</td><td>17-Nov-2026</td><td>09:00 to 10:30</td><td>DB-101</td><td>--</td><td>--</td></tr></table>";
        ExamScheduleParser.Result result = parse(html);
        assertEquals(1, result.exams.size());
        assertEquals("CAT 1", result.exams.get(0).title);
        assertNull(result.exams.get(0).seatNumber);
    }

    @Test
    public void dateOnlyDoesNotInventAnExamDuration() {
        Exam exam = parse("[{\"courseCode\":\"BCSE101L\",\"date\":\"2026-11-18\",\"examTitle\":\"FAT\"}]").exams.get(0);
        assertNotNull(exam.startTime);
        assertNull(exam.endTime);
        assertEquals(Integer.valueOf(1), exam.courseId);
    }

    @Test
    public void isoDateTimeUsesItsExplicitOffset() {
        Exam exam = parse("[{\"courseCode\":\"BCSE101L\",\"startDateTime\":\"2026-11-13T09:00:00+05:30\",\"endDateTime\":\"2026-11-13T10:30:00+05:30\",\"examTitle\":\"CAT 1\"}]").exams.get(0);
        assertEquals(Long.valueOf(1794540600000L), exam.startTime);
        assertEquals(90 * 60000L, exam.endTime - exam.startTime);
    }

    @Test
    public void invalidDateDoesNotBecomeToday() {
        ExamScheduleParser.Result result = parse("[{\"slot\":\"A1\",\"date\":\"31-Feb-2026\",\"examTime\":\"09:00 - 10:30\"}]");
        assertEquals(1, result.rejectedRows);
        assertFalse(result.hasUsableSchedule());
        assertTrue(result.exams.isEmpty());
    }

    @Test
    public void unknownCourseNeverCreatesForeignKeyZeroOrFallsBackToWrongSlot() {
        ExamScheduleParser.Result result = parse("[{\"courseCode\":\"UNKNOWN101L\",\"slot\":\"A1\",\"date\":\"2026-11-13\"}]");
        assertFalse(result.hasUsableSchedule());
        assertEquals(1, result.rejectedRows);
    }

    @Test
    public void ambiguousSlotNeedsCourseCode() {
        ExamScheduleParser.Result result = parse("[{\"slot\":\"A1+B1\",\"date\":\"2026-11-13\"}]");
        assertFalse(result.hasUsableSchedule());
        assertEquals(1, result.rejectedRows);
    }

    @Test
    public void emptyErrorAndLoginResponsesCannotReplaceCache() {
        for (String response : new String[]{"", "{}", "[]", "{\"data\":[]}", "not available", "{broken",
                "{\"status\":false,\"examScheduleView\":[{\"slot\":\"A1\",\"date\":\"2026-11-13\"}]}",
                "<form><input type='password'/></form>"}) {
            assertFalse(response, parse(response).hasUsableSchedule());
        }
    }

    @Test
    public void duplicateSittingsCollapseButDifferentDaysRemain() {
        String first = "{\"slot\":\"A1\",\"date\":\"2026-11-13\",\"examTitle\":\"FAT\"}";
        String second = "{\"slot\":\"A1\",\"date\":\"2026-11-14\",\"examTitle\":\"FAT\"}";
        assertEquals(2, parse("[" + first + "," + first + "," + second + "]").exams.size());
    }

    @Test
    public void cachedExamsRelinkAfterCourseReorderingAndExcludeRemovedCourses() {
        Exam theory = new Exam();
        theory.courseId = 1;
        theory.title = "CAT 1";
        theory.startTime = 1794540600000L;
        theory.venue = "AB1-204";
        Exam lab = new Exam();
        lab.courseId = 3;
        lab.title = "FAT";
        Exam removed = new Exam();
        removed.courseId = 2;
        removed.title = "FAT";
        List<Course> refreshed = Arrays.asList(course(20, "BCSE101L", "Theory"), course(30, "BCSE101L", "Lab"));
        List<Exam> retained = ExamScheduleParser.relinkCachedExams(Arrays.asList(theory, lab, removed), courses, refreshed);
        assertEquals(2, retained.size());
        assertEquals(Integer.valueOf(20), retained.get(0).courseId);
        assertEquals(Integer.valueOf(30), retained.get(1).courseId);
        assertEquals("AB1-204", retained.get(0).venue);
        assertEquals(theory.startTime, retained.get(0).startTime);
        assertEquals(Integer.valueOf(1), theory.courseId);
    }

    @Test
    public void cachedExamDoesNotCrossKnownSemesterOrClassChanges() {
        Course previous = courses.get(0);
        previous.semesterId = "CH2026271";
        previous.vtopClassId = "VL2026001";
        Exam exam = new Exam();
        exam.courseId = previous.id;
        Course next = course(10, previous.code, previous.type);
        next.semesterId = "CH2026275";
        next.vtopClassId = previous.vtopClassId;
        assertTrue(ExamScheduleParser.relinkCachedExams(Collections.singletonList(exam), courses, Collections.singletonList(next)).isEmpty());
        next.semesterId = previous.semesterId;
        next.vtopClassId = "VL2026002";
        assertTrue(ExamScheduleParser.relinkCachedExams(Collections.singletonList(exam), courses, Collections.singletonList(next)).isEmpty());
        previous.semesterId = null;
        previous.vtopClassId = null;
        assertEquals(1, ExamScheduleParser.relinkCachedExams(Collections.singletonList(exam), courses, Collections.singletonList(next)).size());
    }

    @Test
    public void compatibilityWrappersHandleRawArraysAndThTables() {
        assertEquals(1, VTOPJsonParsers.parseExamSchedule("[{\"slot\":\"A1\",\"date\":\"2026-11-13\"}]", slots).size());
        assertEquals(1, VTOPHtmlParsers.parseExamSchedule("<table><tr><th>Slot</th><th>Exam Date</th></tr><tr><td>A1</td><td>2026-11-13</td></tr></table>", slots).size());
    }

    private ExamScheduleParser.Result parse(String response) { return ExamScheduleParser.parse(response, courses, slots); }

    private static Course course(int id, String code, String type) {
        Course course = new Course();
        course.id = id;
        course.code = code;
        course.type = type;
        return course;
    }
}
