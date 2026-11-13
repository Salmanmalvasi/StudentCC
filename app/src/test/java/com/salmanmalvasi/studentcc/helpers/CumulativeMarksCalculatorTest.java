package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.sync.VTOPJsonParsers;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CumulativeMarksCalculatorTest {
    private static Course course(int id, String code, String type, Double credits) {
        Course course = new Course(); course.id = id; course.code = code; course.type = type; course.credits = credits;
        return course;
    }
    private static Mark mark(int id, String title, Double weighted, Double maximum) {
        Mark mark = new Mark(); mark.courseId = id; mark.title = title; mark.weightage = weighted; mark.maxWeightage = maximum;
        return mark;
    }
    private static CumulativeMark total(String code, double score, String grade) {
        CumulativeMark total = new CumulativeMark(1); total.courseCode = code; total.grandTotal = score;
        total.grandMax = 60.0; total.grade = grade; return total;
    }

    @Test public void quickSyncIncludesNewAssessmentAndUpdatedWeightageWithoutDuplicateCounts() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory Only", 3.0));
        List<Mark> cached = Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0));
        List<Mark> incoming = Arrays.asList(mark(1, "CAT 1", 13.0, 15.0), mark(1, "CAT 2", 14.0, 15.0));
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(incoming, cached, courses);
        CumulativeMark total = CumulativeMarksCalculator.calculate(courses, snapshot).get(0);
        assertEquals(2, snapshot.size());
        assertEquals(27.0, total.theoryTotal, 0.00001);
        assertEquals(30.0, total.theoryMax, 0.00001);
        assertEquals(27.0, total.grandTotal, 0.00001);
    }

    @Test public void assessmentWhitespaceChangesReplaceOneRowInsteadOfDoublingTotals() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(Collections.singletonList(mark(1, "CAT   1", 13.0, 15.0)),
                Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0)), courses);
        assertEquals(1, snapshot.size());
        assertEquals(13.0, CumulativeMarksCalculator.calculate(courses, snapshot).get(0).grandTotal, 0.00001);
    }

    @Test public void unlinkedIncomingRowsKeepValidExistingAssessments() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(Collections.singletonList(mark(99, "CAT 2", 13.0, 15.0)),
                Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0)), courses);
        assertEquals(1, snapshot.size());
        assertEquals(12.0, CumulativeMarksCalculator.calculate(courses, snapshot).get(0).grandTotal, 0.00001);
    }

    @Test public void rawMarksScaleOnlyUsingKnownAssessmentWeightage() {
        Mark raw = mark(1, "CAT 1", null, 15.0); raw.score = 44.0; raw.maxScore = 50.0;
        CumulativeMarksCalculator.normalizeWeightages(Collections.singletonList(raw));
        assertEquals(13.2, raw.weightage, 0.00001);
        Mark zero = mark(1, "CAT 2", null, 15.0); zero.score = 0.0; zero.maxScore = 50.0;
        CumulativeMarksCalculator.normalizeWeightages(Collections.singletonList(zero));
        assertEquals(0.0, zero.weightage, 0);
        Mark unknown = mark(1, "FAT", null, null); unknown.score = 80.0; unknown.maxScore = 100.0;
        CumulativeMarksCalculator.normalizeWeightages(Collections.singletonList(unknown));
        assertNull(unknown.weightage);
    }

    @Test public void combinedComponentsUseCreditsAndAllCurrentAssessmentRows() {
        List<Course> courses = Arrays.asList(course(1, "BCSE101E", "Embedded Theory", 3.0),
                course(2, "BCSE101E", "Embedded Lab", 1.0), course(3, "BCSE101E", "Project", 2.0));
        List<Mark> marks = Arrays.asList(mark(1, "Internal", 54.0, 60.0), mark(2, "Lab", 48.0, 60.0),
                mark(3, "Project", 57.0, 60.0));
        CumulativeMark total = CumulativeMarksCalculator.calculate(courses, marks).get(0);
        assertEquals(54.0, total.grandTotal, 0.00001);
        assertEquals(60.0, total.grandMax, 0.00001);
        assertEquals(48.0, total.labTotal, 0.00001);
        assertEquals(57.0, total.projectTotal, 0.00001);
    }

    @Test public void partialResponseRetainsOtherCoursesAndEarlierAssessments() {
        List<Course> courses = Arrays.asList(course(1, "BCSE101L", "Theory", 3.0), course(2, "BMAT101L", "Theory", 4.0));
        Mark read = mark(1, "CAT 1", 12.0, 15.0); read.isRead = true; read.id = 1;
        Mark other = mark(2, "CAT 1", 14.0, 15.0); other.id = 2;
        Mark fresh = mark(1, "CAT 2", 13.0, 15.0); fresh.id = 1;
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(Collections.singletonList(fresh), Arrays.asList(read, other), courses);
        assertEquals(3, snapshot.size());
        assertTrue(snapshot.get(0).isRead);
        for (Mark mark : snapshot) assertEquals(0, mark.id);
        List<CumulativeMark> totals = CumulativeMarksCalculator.calculate(courses, snapshot);
        assertEquals(25.0, totals.get(0).grandTotal, 0.00001);
        assertEquals(14.0, totals.get(1).grandTotal, 0.00001);
    }

    @Test public void removedCourseIsNotRestoredAsAnOrphanAssessment() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(Collections.singletonList(mark(1, "CAT 2", 13.0, 15.0)),
                Collections.singletonList(mark(2, "Old course", 20.0, 30.0)), courses);
        assertEquals(1, snapshot.size());
        assertEquals(Integer.valueOf(1), snapshot.get(0).courseId);
    }

    @Test public void releasedGradeAndUntouchedCourseTotalsSurviveMarksOnlyRefresh() {
        CumulativeMark changed = total("BCSE101L", 42.0, null);
        List<CumulativeMark> merged = CumulativeMarksCalculator.mergeTotals(Collections.singletonList(changed),
                Arrays.asList(total("BCSE101L", 40.0, "A"), total("BMAT101L", 50.0, "S")));
        assertEquals(2, merged.size());
        assertEquals(42.0, merged.get(0).grandTotal, 0.00001);
        assertEquals("A", merged.get(0).grade);
        assertEquals(50.0, merged.get(1).grandTotal, 0.00001);
        assertEquals("S", merged.get(1).grade);
    }

    @Test public void zeroOrMissingCreditsNeverProduceNanTotals() {
        List<Course> courses = Arrays.asList(course(1, "BCSE101E", "Theory", 0.0), course(2, "BCSE101E", "Lab", null));
        CumulativeMark total = CumulativeMarksCalculator.calculate(courses,
                Arrays.asList(mark(1, "CAT", 40.0, 60.0), mark(2, "Lab", 50.0, 60.0))).get(0);
        assertEquals(45.0, total.grandTotal, 0.00001);
        assertTrue(Double.isFinite(total.grandTotal));
    }

    @Test public void missingFinalScoreStaysUnreleasedRatherThanBecomingZero() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        CumulativeMark total = CumulativeMarksCalculator.calculate(courses,
                Arrays.asList(mark(1, "Internal", 45.0, 60.0), mark(1, "FAT", null, 40.0))).get(0);
        assertEquals(45.0, total.grandTotal, 0.00001);
        assertEquals(100.0, total.grandMax, 0.00001);
        CumulativeMark unreleased = CumulativeMarksCalculator.calculate(courses,
                Collections.singletonList(mark(1, "FAT", null, 40.0))).get(0);
        assertNull(unreleased.grandTotal);
    }

    @Test public void nativeJsonAssessmentRowsProduceTotalsWithoutGradeDownload() {
        List<Course> courses = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        List<Mark> parsed = new ArrayList<>();
        VTOPJsonParsers.parseMarks("{\"marks\":[{\"courseCode\":\"BCSE101L\",\"title\":\"CAT 1\",\"score\":44,"
                + "\"maxScore\":50,\"weightageMarkView\":13.2,\"maxWeightage\":15},{\"courseCode\":\"BCSE101L\","
                + "\"title\":\"CAT 2\",\"score\":46,\"maxScore\":50,\"weightageMarkView\":13.8,\"maxWeightage\":15}]}",
                courses, parsed, new ArrayList<>());
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(parsed, Collections.emptyList(), courses);
        CumulativeMark total = CumulativeMarksCalculator.calculate(courses, snapshot).get(0);
        assertEquals(27.0, total.grandTotal, 0.00001);
        assertEquals(30.0, total.grandMax, 0.00001);
    }

    @Test public void reusedCourseIdCannotCarryMarksOrGradeIntoAnotherSemester() {
        Course previous = course(1, "BCSE101L", "Theory", 3.0);
        previous.semesterId = "CH-OLD"; previous.vtopClassId = "CH-CLASS-OLD";
        Course current = course(1, "BCSE101L", "Theory", 3.0);
        current.semesterId = "CH-NEW"; current.vtopClassId = "CH-CLASS-NEW";
        List<Course> oldCourses = Collections.singletonList(previous), newCourses = Collections.singletonList(current);
        List<Mark> snapshot = CumulativeMarksCalculator.mergeMarks(Collections.singletonList(mark(1, "CAT 2", 13.0, 15.0)),
                Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0)), newCourses, oldCourses);
        assertEquals(1, snapshot.size());
        assertEquals(13.0, CumulativeMarksCalculator.calculate(newCourses, snapshot).get(0).grandTotal, 0.00001);
        List<CumulativeMark> totals = CumulativeMarksCalculator.mergeTotals(Collections.singletonList(total("BCSE101L", 13.0, null)),
                Collections.singletonList(total("BCSE101L", 50.0, "S")), newCourses, oldCourses);
        assertEquals(1, totals.size());
        assertNull(totals.get(0).grade);
        assertTrue(CumulativeMarksCalculator.retainTotalsForCourses(Collections.singletonList(total("BCSE101L", 50.0, "S")),
                newCourses, oldCourses).isEmpty());
    }

    @Test public void sameSemesterButDifferentClassDoesNotRetainPreviousMarksOrGrades() {
        Course previous = course(1, "BCSE101L", "Theory", 3.0);
        previous.semesterId = "CH-SEM"; previous.vtopClassId = "CH-CLASS-A";
        Course current = course(1, "BCSE101L", "Theory", 3.0);
        current.semesterId = "CH-SEM"; current.vtopClassId = "CH-CLASS-B";
        assertTrue(CumulativeMarksCalculator.relinkCachedMarks(Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0)),
                Collections.singletonList(previous), Collections.singletonList(current)).isEmpty());
        assertTrue(CumulativeMarksCalculator.retainTotalsForCourses(Collections.singletonList(total("BCSE101L", 50.0, "S")),
                Collections.singletonList(current), Collections.singletonList(previous)).isEmpty());
    }

    @Test public void sameClassReorderedCourseIdRetainsReadStateAndReleasedGrade() {
        Course previous = course(1, "BCSE101L", "theory", 3.0);
        previous.semesterId = "CH-SEM"; previous.vtopClassId = "CH-CLASS-A";
        Course current = course(10, "BCSE101L", "Theory Only", 3.0);
        current.semesterId = "CH-SEM"; current.vtopClassId = "CH-CLASS-A";
        Mark read = mark(1, "CAT 1", 12.0, 15.0); read.isRead = true;
        List<Mark> retained = CumulativeMarksCalculator.relinkCachedMarks(Collections.singletonList(read),
                Collections.singletonList(previous), Collections.singletonList(current));
        assertEquals(1, retained.size());
        assertEquals(Integer.valueOf(10), retained.get(0).courseId);
        assertTrue(retained.get(0).isRead);
        assertEquals(Integer.valueOf(1), read.courseId);
        List<CumulativeMark> totals = CumulativeMarksCalculator.retainTotalsForCourses(Collections.singletonList(total("BCSE101L", 50.0, "S")),
                Collections.singletonList(current), Collections.singletonList(previous));
        assertEquals(1, totals.size());
        assertEquals("S", totals.get(0).grade);
    }

    @Test public void removedOrAmbiguousCourseDoesNotBringOldTotalsIntoActiveCache() {
        List<Course> previous = Collections.singletonList(course(1, "BCSE101L", "Theory", 3.0));
        List<Course> current = Collections.singletonList(course(2, "BMAT101L", "Theory", 4.0));
        assertTrue(CumulativeMarksCalculator.retainTotalsForCourses(Collections.singletonList(total("BCSE101L", 50.0, "S")), current, previous).isEmpty());
        List<Course> ambiguous = Arrays.asList(course(2, "BCSE101L", "Theory", 3.0), course(3, "BCSE101L", "Theory", 3.0));
        assertTrue(CumulativeMarksCalculator.relinkCachedMarks(Collections.singletonList(mark(1, "CAT 1", 12.0, 15.0)), previous, ambiguous).isEmpty());
    }
}
