package com.salmanmalvasi.studentcc.integrations.studentcc;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StudentCCContributionBuilderTest {
    private static final String SEMESTER = "CH20262601";

    @Test public void completeTheoryPreservesFractionalTotalAndExplicitScope() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<StudentCCContribution> contributions = build(Collections.singletonList(theory),
                Arrays.asList(mark(1, 12.25, 15.0), mark(1, 11.125, 15.0), mark(1, 20.5, 30.0)));
        assertEquals(1, contributions.size());
        StudentCCContribution contribution = contributions.get(0);
        assertEquals(43.875, contribution.total, 0.000001);
        assertEquals("CSE1001", contribution.courseCode);
        assertEquals("CH", contribution.scope.campus);
        assertEquals(SEMESTER, contribution.scope.semesterId);
        assertEquals(theory.vtopClassId, contribution.scope.classId);
        assertEquals("INTERNAL_60", contribution.scope.stage);
        assertEquals(60, contribution.scope.marksBasis);
    }

    @Test public void compatibleEmbeddedCourseUsesBothCreditsWithoutCeiling() {
        Course theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        List<StudentCCContribution> contributions = build(Arrays.asList(theory, lab),
                Arrays.asList(mark(1, 42.25, 60.0), mark(2, 55.75, 60.0)));
        assertEquals(1, contributions.size());
        assertEquals(45.625, contributions.get(0).total, 0.000001);
        assertEquals(theory.vtopClassId, contributions.get(0).scope.classId);
    }

    @Test public void incompleteWeightedMarksNeverProducePartialOrZeroContribution() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        assertTrue(build(Collections.singletonList(theory), Collections.emptyList()).isEmpty());
        assertTrue(build(Collections.singletonList(theory),
                Arrays.asList(mark(1, 12.0, 15.0), mark(1, null, 45.0))).isEmpty());
        assertTrue(build(Collections.singletonList(theory), Collections.singletonList(mark(1, 42.0, null))).isEmpty());
    }

    @Test public void embeddedCourseRequiresRegisteredAndCompleteLab() {
        Course theory = course(1, "CSE1001", "Embedded Theory", 3.0, "ACE");
        Course lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        assertTrue(build(Collections.singletonList(theory), Collections.singletonList(mark(1, 42.0, 60.0))).isEmpty());
        assertTrue(build(Arrays.asList(theory, lab), Collections.singletonList(mark(1, 42.0, 60.0))).isEmpty());
        assertTrue(build(Arrays.asList(theory, lab),
                Arrays.asList(mark(1, 42.0, 60.0), mark(2, 20.0, 30.0))).isEmpty());
    }

    @Test public void embeddedCourseRequiresKnownPositiveCreditsForBothComponents() {
        Course theory = course(1, "CSE1001", "Embedded Theory", null, "ACE");
        Course lab = course(2, "CSE1001", "Embedded Lab", 1.0, "ACE");
        List<Mark.AllData> marks = Arrays.asList(mark(1, 42.0, 60.0), mark(2, 55.0, 60.0));
        assertTrue(build(Arrays.asList(theory, lab), marks).isEmpty());
        theory.credits = 3.0;
        lab.credits = 0.0;
        assertTrue(build(Arrays.asList(theory, lab), marks).isEmpty());
        lab.credits = null;
        assertTrue(build(Arrays.asList(theory, lab), marks).isEmpty());
    }

    @Test public void inactiveSemesterRecordsAndTheirMarksAreSuppressed() {
        Course old = course(1, "CSE1001", "Theory Only", 3.0, null);
        old.semesterId = "CH20252501";
        Course current = course(2, "CSE1002", "Theory Only", 3.0, null);
        List<StudentCCContribution> contributions = build(Arrays.asList(old, current),
                Arrays.asList(mark(1, 51.0, 60.0), mark(2, 42.5, 60.0)));
        assertEquals(1, contributions.size());
        assertEquals("CSE1002", contributions.get(0).courseCode);
        assertEquals(42.5, contributions.get(0).total, 0.0);
        assertTrue(StudentCCContributionBuilder.build(Collections.singletonList(current),
                Collections.singletonList(mark(2, 42.5, 60.0)), null).isEmpty());
    }

    @Test public void missingMismatchedAndMalformedClassMetadataAreSuppressed() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<Mark.AllData> marks = Collections.singletonList(mark(1, 42.0, 60.0));
        theory.vtopClassId = null;
        assertTrue(build(Collections.singletonList(theory), marks).isEmpty());
        theory.vtopClassId = "VL2026260100001";
        assertTrue(build(Collections.singletonList(theory), marks).isEmpty());
        theory.vtopClassId = "CH20262601?other_class=1";
        assertTrue(build(Collections.singletonList(theory), marks).isEmpty());
        theory.vtopClassId = "CH2026260100001";
        theory.code = "CSE 1001";
        assertTrue(build(Collections.singletonList(theory), marks).isEmpty());
    }

    @Test public void actualZeroMarkIsContributed() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<StudentCCContribution> contributions = build(Collections.singletonList(theory),
                Collections.singletonList(mark(1, 0.0, 60.0)));
        assertEquals(1, contributions.size());
        assertEquals(0.0, contributions.get(0).total, 0.0);
    }

    @Test public void finalAndPartialAssessmentBasesCannotEnterTheInternalPool() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        List<StudentCCContribution> finalOnly = build(Collections.singletonList(theory), Collections.singletonList(mark(1, 84.0, 100.0)));
        assertEquals(1, finalOnly.size());
        assertEquals(100, finalOnly.get(0).scope.marksBasis);
        assertEquals("FINAL_100", finalOnly.get(0).scope.stage);
        assertEquals(84.0, finalOnly.get(0).total, 0.0);
        assertTrue(build(Collections.singletonList(theory), Collections.singletonList(mark(1, 24.0, 30.0))).isEmpty());
    }

    @Test public void releasedFinalsCreateTwoIndependentContributionsWithoutRounding() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        Mark.AllData fat = mark(1, 31.125, 40.0);
        fat.title = "FAT";
        List<StudentCCContribution> contributions = build(Collections.singletonList(theory),
                Arrays.asList(mark(1, 47.75, 60.0), fat));
        assertEquals(2, contributions.size());
        assertEquals(60, contributions.get(0).scope.marksBasis);
        assertEquals(47.75, contributions.get(0).total, 0.0);
        assertEquals(100, contributions.get(1).scope.marksBasis);
        assertEquals(78.875, contributions.get(1).total, 0.0);
        org.junit.Assert.assertNotEquals(contributions.get(0).key(), contributions.get(1).key());
        assertEquals(contributions.get(0).scope.classId, contributions.get(1).scope.classId);
    }

    @Test public void unreleasedFinalsDoNotBlockTheExistingInternalContribution() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        Mark.AllData fat = mark(1, null, 40.0);
        fat.title = "FAT";
        List<StudentCCContribution> contributions = build(Collections.singletonList(theory),
                Arrays.asList(mark(1, 47.75, 60.0), fat));
        assertEquals(1, contributions.size());
        assertEquals(60, contributions.get(0).scope.marksBasis);
        assertEquals(47.75, contributions.get(0).total, 0.0);
    }

    @Test public void marksWithoutARegisteredCourseDoNotBecomeContributions() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        assertTrue(build(Collections.singletonList(theory), Collections.singletonList(mark(99, 45.0, 60.0))).isEmpty());
    }

    @Test public void genericAceTheoryWithoutAnActualLabIsNotSilentlySuppressed() {
        Course theory = course(1, "CSE1001", "theory", 3.0, "ACE");
        List<StudentCCContribution> contributions = build(Collections.singletonList(theory),
                Collections.singletonList(mark(1, 47.75, 60.0)));
        assertEquals(1, contributions.size());
        assertEquals(47.75, contributions.get(0).total, 0.0);
    }

    @Test public void emptyEligibilityDistinguishesPartialMarksFromMissingClassMetadata() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        StudentCCContributionBuilder.BuildResult partial = StudentCCContributionBuilder.inspect(
                Collections.singletonList(theory), Collections.singletonList(mark(1, 24.0, 30.0)), SEMESTER);
        assertTrue(partial.contributions.isEmpty());
        assertEquals(1, partial.activeCourses);
        assertEquals(1, partial.supportedCourses);
        assertEquals(0, partial.completeTotals);
        assertEquals(0, partial.missingMetadataTotals);
        assertTrue(partial.emptyMessage().contains("must be released"));
        theory.vtopClassId = null;
        StudentCCContributionBuilder.BuildResult metadata = StudentCCContributionBuilder.inspect(
                Collections.singletonList(theory), Collections.singletonList(mark(1, 47.75, 60.0)), SEMESTER);
        assertTrue(metadata.contributions.isEmpty());
        assertEquals(1, metadata.completeTotals);
        assertEquals(1, metadata.missingMetadataTotals);
        assertTrue(metadata.emptyMessage().contains("class or semester information"));
    }

    @Test public void absentActiveSemesterCoursesRequestAFullSync() {
        Course theory = course(1, "CSE1001", "Theory Only", 3.0, null);
        theory.semesterId = null;
        StudentCCContributionBuilder.BuildResult result = StudentCCContributionBuilder.inspect(
                Collections.singletonList(theory), Collections.singletonList(mark(1, 47.75, 60.0)), SEMESTER);
        assertTrue(result.contributions.isEmpty());
        assertEquals(0, result.activeCourses);
        assertTrue(result.emptyMessage().contains("full VTOP sync"));
    }

    private static List<StudentCCContribution> build(List<Course> courses, List<Mark.AllData> marks) {
        return StudentCCContributionBuilder.build(courses, marks, SEMESTER);
    }

    private static Course course(int id, String code, String type, Double credits, String system) {
        Course course = new Course();
        course.id = id;
        course.code = code;
        course.title = code;
        course.type = type;
        course.credits = credits;
        course.courseSystem = system;
        course.semesterId = SEMESTER;
        course.vtopClassId = "CH202626010000" + id;
        return course;
    }

    private static Mark.AllData mark(int courseId, Double total, Double maximum) {
        Mark.AllData mark = new Mark.AllData();
        mark.courseId = courseId;
        mark.weightage = total;
        mark.maxWeightage = maximum;
        return mark;
    }
}
