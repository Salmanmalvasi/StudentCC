package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salmanmalvasi.studentcc.helpers.VitGradePredictor.PassStatus.FAILED;
import static com.salmanmalvasi.studentcc.helpers.VitGradePredictor.PassStatus.PASSED;
import static com.salmanmalvasi.studentcc.helpers.VitGradePredictor.PassStatus.UNKNOWN;
import static org.junit.Assert.assertEquals;

public class VitGradePassRulesTest {
    @Test public void highInternalTotalCannotOverrideFailedTheoryFat() {
        Course.AllData theory = theory(false);
        assertEquals(FAILED, evaluate(theory, 60.0, fat("FAT", 39.99, 100.0, 15.996)));
        assertEquals(PASSED, evaluate(theory, 60.0, fat("Final Assessment Test", 40.0, 100.0, 16.0)));
    }

    @Test public void fatPassPercentageUsesItsActualMaximumRatherThanAssumingOneHundred() {
        Course.AllData theory = theory(false);
        assertEquals(PASSED, evaluate(theory, 55.0, fat("FAT (Theory)", 20.0, 50.0, 16.0)));
        assertEquals(FAILED, evaluate(theory, 55.0, fat("FAT (Theory)", 19.99, 50.0, 15.992)));
    }

    @Test public void explicitWeightedFatBasisWorksWhenRawScoreIsMissing() {
        Course.AllData theory = theory(false);
        assertEquals(PASSED, evaluate(theory, 55.0, fat("final\nexam", null, null, 16.0)));
        assertEquals(FAILED, evaluate(theory, 55.0, fat("final\nexam", null, null, 15.99)));
    }

    @Test public void authoritativeRawFatTakesPriorityAndInvalidRawDoesNotProveAPass() {
        Course.AllData theory = theory(false);
        assertEquals(FAILED, evaluate(theory, 60.0, fat("FAT", 39.0, 100.0, 36.0)));
        for (Double score : Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0)) {
            assertEquals(UNKNOWN, evaluate(theory, 60.0, fat("FAT", score, 100.0, 36.0)));
        }
        assertEquals(UNKNOWN, evaluate(theory, 60.0, fat("FAT", 90.0, null, 36.0)));
    }

    @Test public void incompleteFinalSnapshotDoesNotTurnAnInternalProjectionIntoAKnownFailure() {
        Course.AllData theory = theory(false);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark("CATs and assignments", 10.0, 60.0)));
        assertEquals(UNKNOWN, VitGradePassRules.evaluate(theory, Collections.singletonList(theory), marks));
        marks.put(1, Arrays.asList(mark("CATs and assignments", 10.0, 60.0),
                fat("FAT", 0.0, 100.0, null)));
        assertEquals(UNKNOWN, VitGradePassRules.evaluate(theory, Collections.singletonList(theory), marks));
    }

    @Test public void completeFinalTotalWithoutAnIdentifiableFatRemainsConditional() {
        Course.AllData theory = theory(false);
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark("Overall result", 98.0, 100.0)));
        assertEquals(UNKNOWN, VitGradePassRules.evaluate(theory, Collections.singletonList(theory), marks));
        marks.put(1, Arrays.asList(mark("Internal assessments", 55.0, 60.0),
                mark("FAT theory", 18.0, 20.0), mark("Final exam", 18.0, 20.0)));
        assertEquals(UNKNOWN, VitGradePassRules.evaluate(theory, Collections.singletonList(theory), marks));
    }

    @Test public void embeddedCourseRequiresLabTotalAndCombinedTotalToReachFifty() {
        Course.AllData theory = theory(true);
        Course.AllData lab = lab(2);
        List<Course.AllData> courses = Arrays.asList(theory, lab);
        assertEquals(FAILED, embedded(theory, courses, 60.0, 36.0, 49.99));
        // The lab passes, but (45 * 3 + 60) / 4 = 48.75 does not pass the combined requirement.
        assertEquals(FAILED, embedded(theory, courses, 29.0, 16.0, 60.0));
        assertEquals(PASSED, embedded(theory, courses, 31.0, 16.0, 59.0)); // Combined exactly 50.
        assertEquals(PASSED, embedded(theory, courses, 60.0, 36.0, 50.0)); // Lab exactly 50.
    }

    @Test public void embeddedCombinedRoundingEdgeStaysConditionalWhileLabIsUnrounded() {
        Course.AllData theory = theory(true);
        Course.AllData lab = lab(2);
        List<Course.AllData> courses = Arrays.asList(theory, lab);
        // Theory 47, lab 57 => combined 49.5, whose rounded-up grade total is 50.
        assertEquals(UNKNOWN, embedded(theory, courses, 31.0, 16.0, 57.0));
        assertEquals(FAILED, embedded(theory, courses, 60.0, 36.0, 49.01));
    }

    @Test public void embeddedLabCannotRescueFailedTheoryFat() {
        Course.AllData theory = theory(true);
        assertEquals(FAILED, embedded(theory, Arrays.asList(theory, lab(2)), 60.0, 15.99, 100.0));
    }

    @Test public void missingOrUnreleasedEmbeddedLabKeepsConditionsUnknown() {
        Course.AllData theory = theory(true);
        assertEquals(UNKNOWN, embedded(theory, Collections.singletonList(theory), 60.0, 0.0, 100.0));
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Arrays.asList(mark("Internals", 60.0, 60.0), fat("FAT", null, null, 0.0)));
        marks.put(2, Collections.singletonList(mark("Lab result", null, 100.0)));
        assertEquals(UNKNOWN, VitGradePassRules.evaluate(theory, Arrays.asList(theory, lab(2)), marks));
    }

    @Test public void invalidOrAmbiguousPairingCannotProduceAKnownFailedGrade() {
        Course.AllData theory = theory(true);
        Course.AllData lab = lab(2);
        assertEquals(UNKNOWN, embedded(theory, Arrays.asList(theory, lab, lab(3)), 60.0, 0.0, 100.0));
        lab.semesterId = "CH2025";
        assertEquals(UNKNOWN, embedded(theory, Arrays.asList(theory, lab), 60.0, 0.0, 100.0));
        lab.semesterId = theory.semesterId;
        lab.vtopClassId = theory.vtopClassId;
        assertEquals(UNKNOWN, embedded(theory, Arrays.asList(theory, lab), 60.0, 0.0, 100.0));
        lab.vtopClassId = "LAB_CLASS";
        lab.credits = null;
        assertEquals(UNKNOWN, embedded(theory, Arrays.asList(theory, lab), 60.0, 0.0, 100.0));
        lab.credits = 1.0;
        lab.courseSystem = "FFCS";
        assertEquals(UNKNOWN, embedded(theory, Arrays.asList(theory, lab), 60.0, 0.0, 100.0));
    }

    @Test public void normalizedComponentCodesUseTheSamePairingAsFinalTotals() {
        Course.AllData theory = theory(true);
        theory.courseCode = " CSE1001(T) ";
        Course.AllData lab = lab(2);
        lab.courseCode = "cse1001(L)";
        assertEquals(FAILED, embedded(theory, Arrays.asList(theory, lab), 60.0, 36.0, 49.0));
        assertEquals(PASSED, embedded(theory, Arrays.asList(theory, lab), 60.0, 36.0, 50.0));
    }

    @Test public void standaloneLabAndProjectAreOutsideTheoryFatPassRules() {
        Course.AllData course = lab(1);
        assertEquals(UNKNOWN, evaluate(course, 60.0, fat("FAT", 0.0, 100.0, 0.0)));
        course.courseType = "Project";
        assertEquals(UNKNOWN, evaluate(course, 60.0, fat("FAT", 0.0, 100.0, 0.0)));
    }

    @Test public void projectedTheoryFatFailureCannotBeHiddenByHighEmbeddedLabMarks() {
        Course.AllData theory = theory(true);
        List<Course.AllData> courses = Arrays.asList(theory, lab(2));
        Map<Integer, List<Mark.AllData>> marks = projectedMarks(23.0, 60.0);
        Double combined = MarksCourseSummary.completeInternalScore(theory, courses, marks);
        assertEquals(32.25, combined, 0.0);
        VitGradePredictor.PassStatus pass = VitGradePassRules.evaluateInternalProjection(theory, courses, marks);
        assertEquals(FAILED, pass);
        // Both the fractional StudentCC total and rounded UniCC total keep the component failure.
        assertEquals("F", VitGradePredictor.relative(combined, 60, 30, 6, 20, pass).grade);
        assertEquals("F", VitGradePredictor.relative(Math.ceil(combined), 60, 30, 6, 20, pass).grade);
    }

    @Test public void projectedLabFailureCannotBeHiddenByHighEmbeddedTheoryMarks() {
        Course.AllData theory = theory(true);
        List<Course.AllData> courses = Arrays.asList(theory, lab(2));
        Map<Integer, List<Mark.AllData>> marks = projectedMarks(60.0, 20.0);
        Double combined = MarksCourseSummary.completeInternalScore(theory, courses, marks);
        assertEquals(50.0, combined, 0.0);
        VitGradePredictor.PassStatus pass = VitGradePassRules.evaluateInternalProjection(theory, courses, marks);
        assertEquals(FAILED, pass);
        assertEquals("F", VitGradePredictor.relative(combined, 60, 30, 6, 20, pass).grade);
        assertEquals("F", VitGradePredictor.absolute(combined, 60, pass).grade);
    }

    @Test public void projectedComponentThresholdsStayUnroundedForBothPools() {
        Course.AllData theory = theory(true);
        List<Course.AllData> courses = Arrays.asList(theory, lab(2));
        assertEquals(FAILED, projection(theory, courses, 23.99, 60.0));
        assertEquals(FAILED, projection(theory, courses, 60.0, 29.99));
        assertEquals(PASSED, projection(theory, courses, 24.0, 48.0));
        assertEquals(PASSED, projection(theory, courses, 60.0, 30.0));
    }

    @Test public void projectedCombinedPassGateRetainsItsConditionalRoundingEdge() {
        Course.AllData theory = theory(true);
        List<Course.AllData> courses = Arrays.asList(theory, lab(2));
        assertEquals(FAILED, projection(theory, courses, 24.0, 30.0));
        // 29.75/60 projects 49.5833/100: grade rounding reaches 50, but the pass gate is unclear.
        assertEquals(UNKNOWN, projection(theory, courses, 24.0, 47.0));
        assertEquals(PASSED, projection(theory, courses, 24.0, 48.0));
    }

    @Test public void missingOrAmbiguousProjectedComponentsKeepPassConditionsUnknown() {
        Course.AllData theory = theory(true);
        Course.AllData lab = lab(2);
        assertEquals(UNKNOWN, projection(theory, Collections.singletonList(theory), 60.0, 60.0));
        assertEquals(UNKNOWN, projection(theory, Arrays.asList(theory, lab), 60.0, null));
        assertEquals(UNKNOWN, projection(theory, Arrays.asList(theory, lab, lab(3)), 60.0, 60.0));
        lab.credits = null;
        assertEquals(UNKNOWN, projection(theory, Arrays.asList(theory, lab), 60.0, 60.0));
    }

    @Test public void projectionUsesOriginalInternalComponentsEvenWhenFatIsPresent() {
        Course.AllData theory = theory(true);
        theory.courseCode = " CSE1001(T) ";
        Course.AllData lab = lab(2);
        lab.courseCode = "cse1001(L)";
        Map<Integer, List<Mark.AllData>> marks = projectedMarks(23.99, 60.0);
        marks.put(1, Arrays.asList(mark("Internal assessments", 23.99, 60.0),
                fat("FAT", 100.0, 100.0, 40.0)));
        marks.put(2, Arrays.asList(mark("Lab internal assessments", 60.0, 60.0),
                fat("Lab FAT", 100.0, 100.0, 40.0)));
        assertEquals(FAILED, VitGradePassRules.evaluateInternalProjection(theory, Arrays.asList(theory, lab), marks));
        // Recorded final conditions use the actual FAT instead of its internal projection.
        assertEquals(PASSED, VitGradePassRules.evaluate(theory, Arrays.asList(theory, lab), marks));
    }

    @Test public void theoryOnlyProjectionUsesItsOwnFortyPercentFatThreshold() {
        Course.AllData theory = theory(false);
        assertEquals(FAILED, projection(theory, Collections.singletonList(theory), 23.99, null));
        assertEquals(PASSED, projection(theory, Collections.singletonList(theory), 24.0, null));
    }

    private static VitGradePredictor.PassStatus projection(Course.AllData theory, List<Course.AllData> courses,
                                                           double internal, Double labInternal) {
        return VitGradePassRules.evaluateInternalProjection(theory, courses, projectedMarks(internal, labInternal));
    }

    private static Map<Integer, List<Mark.AllData>> projectedMarks(double theory, Double lab) {
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(1, Collections.singletonList(mark("Internal assessments", theory, 60.0)));
        marks.put(2, Collections.singletonList(mark("Lab internal assessments", lab, 60.0)));
        return marks;
    }

    private static VitGradePredictor.PassStatus evaluate(Course.AllData theory, double internal,
                                                        Mark.AllData fat) {
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(theory.courseId, Arrays.asList(mark("CATs and assignments", internal, 60.0), fat));
        return VitGradePassRules.evaluate(theory, Collections.singletonList(theory), marks);
    }

    private static VitGradePredictor.PassStatus embedded(Course.AllData theory, List<Course.AllData> courses,
                                                       double internal, double fat, double labTotal) {
        Map<Integer, List<Mark.AllData>> marks = new HashMap<>();
        marks.put(theory.courseId, Arrays.asList(mark("Internals", internal, 60.0),
                fat("FAT (Theory)", null, null, fat)));
        marks.put(2, Collections.singletonList(mark("Lab final total", labTotal, 100.0)));
        return VitGradePassRules.evaluate(theory, courses, marks);
    }

    private static Course.AllData theory(boolean embedded) {
        Course.AllData course = new Course.AllData();
        course.courseId = 1;
        course.courseCode = "CSE1001";
        course.courseType = embedded ? "Embedded Theory" : "Theory Only";
        course.credits = 3.0;
        course.courseSystem = "ACE";
        course.semesterId = "CH2026";
        course.vtopClassId = "THEORY_CLASS";
        return course;
    }

    private static Course.AllData lab(int id) {
        Course.AllData lab = theory(true);
        lab.courseId = id;
        lab.courseType = "Embedded Lab";
        lab.credits = 1.0;
        lab.vtopClassId = "LAB_CLASS_" + id;
        return lab;
    }

    private static Mark.AllData fat(String title, Double raw, Double maximum, Double weightage) {
        Mark.AllData mark = mark(title, weightage, 40.0);
        mark.score = raw;
        mark.maxScore = maximum;
        return mark;
    }

    private static Mark.AllData mark(String title, Double score, Double maximum) {
        Mark.AllData mark = new Mark.AllData();
        mark.title = title;
        mark.weightage = score;
        mark.maxWeightage = maximum;
        return mark;
    }
}
