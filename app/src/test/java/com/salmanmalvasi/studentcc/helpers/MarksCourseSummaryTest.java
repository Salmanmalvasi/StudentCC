package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class MarksCourseSummaryTest {
    @Test public void theoryComparisonUsesOnlyCompleteSixtyPointTotalAndUniCCRounding() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<Mark.AllData> marks = Arrays.asList(mark(12.3, 15.0), mark(13.2, 15.0), mark(22.2, 30.0));
        assertEquals(48.0, comparison(theory, Collections.singletonList(theory), marks), 0.0);
        Map<Integer, List<Mark.AllData>> byCourse = new HashMap<>();
        byCourse.put(theory.courseId, marks);
        assertEquals(47.7, MarksCourseSummary.completeInternalScore(theory, Collections.singletonList(theory), byCourse), 0.001);
    }

    @Test public void missingScoreIsNotTreatedAsZero() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<Mark.AllData> marks = Arrays.asList(mark(12.3, 15.0), mark(null, 45.0));
        MarksCourseSummary summary = MarksCourseSummary.from(marks);
        assertEquals(12.3, summary.score, 0.001);
        assertEquals(60.0, summary.maximum, 0.0);
        assertFalse(summary.complete);
        assertNull(comparison(theory, Collections.singletonList(theory), marks));
    }

    @Test public void finalMarksAndPartialInternalsCannotBeComparedToSixtyPointDistribution() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(84.0, 100.0))));
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(24.0, 30.0))));
    }

    @Test public void embeddedAceTotalUsesSeparateTheoryAndLabCredits() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course.AllData lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark(42.1, 60.0)));
        marks.put(2, Collections.singletonList(mark(55.5, 60.0)));
        assertEquals(46.0, MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks), 0.0);
        assertEquals(45.45, MarksCourseSummary.completeInternalScore(theory, Arrays.asList(theory, lab), marks), 0.001);
        assertNull(MarksCourseSummary.comparableInternalScore(lab, Arrays.asList(theory, lab), marks));
    }

    @Test public void embeddedComparisonRejectsUnknownCreditsAndUnsupportedCourseSystem() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course.AllData lab = course(2, "CSE1001", "Embedded Lab", null, "ACE");
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark(42.0, 60.0)));
        marks.put(2, Collections.singletonList(mark(54.0, 60.0)));
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
        lab.credits = 1.0;
        theory.courseSystem = "FFCS";
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
        theory.courseSystem = "ACE";
        lab.courseSystem = "FFCS";
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
    }

    @Test public void embeddedComparisonRejectsDifferentSemesterAndDuplicatedClassIdentity() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course.AllData lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark(42.0, 60.0)));
        marks.put(2, Collections.singletonList(mark(54.0, 60.0)));
        lab.semesterId = "CH2025";
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
        lab.semesterId = theory.semesterId;
        lab.vtopClassId = theory.vtopClassId;
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
        lab.vtopClassId = null;
        assertNull(MarksCourseSummary.comparableInternalScore(theory, Arrays.asList(theory, lab), marks));
    }

    @Test public void embeddedTheoryWithMissingLabHasNoComparison() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(45.0, 60.0))));
        theory.courseType = "theory"; // A generic legacy type and ACE alone do not prove an embedded component.
        assertEquals(45.0, comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(45.0, 60.0))), 0.0);
    }

    @Test public void ambiguousLabPairingHasNoComparison() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course.AllData firstLab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        Course.AllData secondLab = course(3, "CSE1001", "Embedded Lab", 1.0, "ACE");
        assertNull(comparison(theory, Arrays.asList(theory, firstLab, secondLab), Collections.singletonList(mark(45.0, 60.0))));
    }

    @Test public void invalidWeightedMarksAreNeverCompared() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(Double.NaN, 60.0))));
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(65.0, 60.0))));
        assertNull(comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(-1.0, 60.0))));
    }

    @Test public void projectsAndUnknownComponentsHaveNoComparison() {
        Course.AllData project = course(1, "CSE1001", "Project", 3.0, null);
        assertNull(comparison(project, Collections.singletonList(project), Collections.singletonList(mark(45.0, 60.0))));
        project.courseType = null;
        assertNull(comparison(project, Collections.singletonList(project), Collections.singletonList(mark(45.0, 60.0))));
    }

    @Test public void absentZeroIsAValidRecordedScore() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        assertEquals(0.0, comparison(theory, Collections.singletonList(theory), Collections.singletonList(mark(0.0, 60.0))), 0.0);
    }

    @Test public void unreleasedFatKeepsCompleteInternalsAvailableWithoutInventingFinalTotal() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Arrays.asList(mark(47.75, 60.0), finalMark("FAT", null, 40.0)));
        assertEquals(47.75, MarksCourseSummary.completeInternalScore(theory, Collections.singletonList(theory), marks), 0.0);
        assertEquals(48.0, MarksCourseSummary.comparableInternalScore(theory, Collections.singletonList(theory), marks), 0.0);
        assertNull(MarksCourseSummary.completeFinalScore(theory, Collections.singletonList(theory), marks));
    }

    @Test public void completedFatProducesFinalTotalAndPreservesTheOriginalInternalBasis() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Arrays.asList(mark(47.75, 60.0), finalMark("FAT", 31.125, 40.0)));
        assertEquals(78.875, MarksCourseSummary.completeFinalScore(theory, Collections.singletonList(theory), marks), 0.0);
        assertEquals(47.75, MarksCourseSummary.completeInternalScore(theory, Collections.singletonList(theory), marks), 0.0);
    }

    @Test public void finalTitleVariantsNeverLeakIntoTheInternalPool() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        for (String title : Arrays.asList("FAT (Theory)", "Final Assessment Test", "final\nexam")) {
            Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
            marks.put(1, Arrays.asList(mark(24.0, 30.0), finalMark(title, 25.0, 30.0)));
            assertNull(MarksCourseSummary.completeInternalScore(theory, Collections.singletonList(theory), marks));
            assertNull(MarksCourseSummary.completeFinalScore(theory, Collections.singletonList(theory), marks));
        }
    }

    @Test public void completedZeroFatIsARealFinalScore() {
        Course.AllData theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Arrays.asList(mark(42.0, 60.0), finalMark("FAT", 0.0, 40.0)));
        assertEquals(42.0, MarksCourseSummary.completeFinalScore(theory, Collections.singletonList(theory), marks), 0.0);
    }

    @Test public void embeddedFinalRequiresBothComponentsOnOneHundredPointBasis() {
        Course.AllData theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course.AllData lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        List<Course.AllData> courses = Arrays.asList(theory, lab);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Arrays.asList(mark(42.25, 60.0), finalMark("FAT", 30.0, 40.0)));
        marks.put(2, Arrays.asList(mark(55.75, 60.0), finalMark("Lab FAT", null, 40.0)));
        assertNull(MarksCourseSummary.completeFinalScore(theory, courses, marks));
        assertEquals(45.625, MarksCourseSummary.completeInternalScore(theory, courses, marks), 0.0);
        marks.put(2, Arrays.asList(mark(55.75, 60.0), finalMark("Lab FAT", 38.0, 40.0)));
        assertEquals(77.625, MarksCourseSummary.completeFinalScore(theory, courses, marks), 0.0);
    }

    private static Mark.AllData finalMark(String title, Double score, Double maximum) {
        Mark.AllData mark = mark(score, maximum);
        mark.title = title;
        return mark;
    }

    private static Double comparison(Course.AllData course, List<Course.AllData> courses, List<Mark.AllData> marks) {
        Map<Integer, List<Mark.AllData>> byCourse = new HashMap<>();
        byCourse.put(course.courseId, marks);
        return MarksCourseSummary.comparableInternalScore(course, courses, byCourse);
    }

    private static Course.AllData course(int id, String code, String type, Double credits, String system) {
        Course.AllData course = new Course.AllData();
        course.courseId = id;
        course.courseCode = code;
        course.courseType = type;
        course.credits = credits;
        course.courseSystem = system;
        course.semesterId = "CH2026";
        course.vtopClassId = "CH2026CLASS" + id;
        return course;
    }

    private static Mark.AllData mark(Double score, Double maximum) {
        Mark.AllData mark = new Mark.AllData();
        mark.weightage = score;
        mark.maxWeightage = maximum;
        mark.isRead = false;
        return mark;
    }
}
