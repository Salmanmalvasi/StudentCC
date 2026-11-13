package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Slot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class UniCCMarksMetadataTest {
    @Test public void actualClassAndSemesterArePreservedFromRegisteredCourse() {
        StagedSyncData staged = new StagedSyncData("CH20262601");
        VTOPJsonParsers.parseCoursesAndSlots("{\"studentRegisteredCourse\":[{\"courseCode\":\"BCSE101L\","
                + "\"courseTitle\":\"Programming\",\"courseType\":\"Embedded Theory\",\"credits\":3,"
                + "\"classNbr\":\"CH2026260100834\",\"courseId\":\"backend-12\",\"courseSystem\":\"ACE\",\"slot\":\"A1\"}]}",
                staged.courses, staged.slots, staged);
        assertEquals(1, staged.courses.size());
        Course course = staged.courses.get(0);
        assertEquals("CH2026260100834", course.vtopClassId);
        assertEquals("CH20262601", course.semesterId);
        assertEquals("ACE", course.courseSystem);
        assertNotEquals(String.valueOf(course.id), course.vtopClassId);
    }

    @Test public void allocationIdentifierIsNotPassedOffAsUniversityClassNumber() {
        StagedSyncData staged = new StagedSyncData("CH20262601");
        VTOPJsonParsers.parseCoursesAndSlots("[{\"courseCode\":\"BCSE101L\",\"allocationId\":\"12345\",\"slot\":\"A1\"}]",
                staged.courses, staged.slots, staged);
        assertEquals(1, staged.courses.size());
        assertNull(staged.courses.get(0).vtopClassId);
    }

    @Test public void perCourseRawArrayPreservesWeightedMaximumAndClassNumber() {
        Course course = course(1, "theory");
        List<Mark> marks = new ArrayList<>();
        VTOPJsonParsers.parsePerCourseMarks("[{\"markTitle\":\"CAT 1\",\"maxMark\":50,\"marksGiven\":40,"
                + "\"weightageMarkView\":12,\"weightagePercent\":15,\"classNbr\":\"CH2026260100834\"}]", course, marks, null);
        assertEquals(1, marks.size());
        assertEquals(12, marks.get(0).weightage, 0);
        assertEquals(15, marks.get(0).maxWeightage, 0);
        assertEquals("CH2026260100834", course.vtopClassId);
    }

    @Test public void missingScoresStayMissingAndDoNotBecomeZero() {
        List<Mark> marks = new ArrayList<>();
        VTOPJsonParsers.parsePerCourseMarks("{\"marks\":[{\"markTitle\":\"FAT\",\"maxMark\":100,"
                + "\"marksGiven\":\"-\",\"weightageMarkView\":null,\"weightagePercent\":40}]}", course(1, "theory"), marks, null);
        assertEquals(1, marks.size());
        assertNull(marks.get(0).score);
        assertNull(marks.get(0).weightage);
        assertEquals(40, marks.get(0).maxWeightage, 0);
    }

    @Test public void weightageMaximumCanBeReadAlongsideExplicitWeightedScore() {
        List<Mark> marks = new ArrayList<>();
        VTOPJsonParsers.parsePerCourseMarks("{\"marks\":[{\"markTitle\":\"CAT 1\",\"marksGiven\":0,"
                + "\"weightageMark\":0,\"weightage\":15}]}", course(1, "theory"), marks, null);
        assertEquals(0, marks.get(0).score, 0);
        assertEquals(0, marks.get(0).weightage, 0);
        assertEquals(15, marks.get(0).maxWeightage, 0);
    }

    @Test public void embeddedComponentsWithSameCodeAreMatchedByTheirType() {
        Course theory = course(1, "Embedded Theory");
        Course lab = course(2, "Embedded Lab");
        List<Mark> marks = new ArrayList<>();
        VTOPJsonParsers.parseMarks("[{\"courseCode\":\"BCSE101L\",\"courseType\":\"Embedded Theory\","
                + "\"classNbr\":\"CH-THEORY\",\"assessments\":[{\"title\":\"CAT 1\",\"weightageMark\":12,\"weightagePercent\":15}]},"
                + "{\"courseCode\":\"BCSE101L\",\"courseType\":\"Embedded Lab\",\"classNbr\":\"CH-LAB\","
                + "\"assessments\":[{\"title\":\"Lab assessment\",\"weightageMark\":18,\"weightagePercent\":20}]}]",
                Arrays.asList(theory, lab), marks, new ArrayList<>());
        assertEquals(2, marks.size());
        assertEquals(Integer.valueOf(1), marks.get(0).courseId);
        assertEquals(Integer.valueOf(2), marks.get(1).courseId);
        assertEquals("CH-THEORY", theory.vtopClassId);
        assertEquals("CH-LAB", lab.vtopClassId);
        assertEquals(15, marks.get(0).maxWeightage, 0);
    }

    @Test public void ambiguousComponentsAreNotSilentlyAssignedToTheLastCourse() {
        List<Mark> marks = new ArrayList<>();
        VTOPJsonParsers.parseMarks("{\"marks\":[{\"courseCode\":\"BCSE101L\",\"markTitle\":\"CAT 1\",\"weightageMark\":12}]}",
                Arrays.asList(course(1, "theory"), course(2, "lab")), marks, new ArrayList<>());
        assertTrue(marks.isEmpty());
    }

    @Test public void htmlCourseHeadersPreserveUniversityClassNumber() {
        List<Course> courses = new ArrayList<>();
        VTOPHtmlParsers.parseCoursesAndSlots("<div id='studentDetailsList'><table><tr><th>Class Nbr</th><th>Course Code</th>"
                + "<th>Course Title</th><th>Course Type</th><th>Credits</th><th>Slot</th><th>Course System</th></tr>"
                + "<tr><td>CH2026260100834</td><td>BCSE101L</td><td>Programming</td><td>Embedded Theory</td>"
                + "<td>3</td><td>A1</td><td>ACE</td></tr></table></div>", courses, new ArrayList<Slot>());
        assertEquals(1, courses.size());
        assertEquals("CH2026260100834", courses.get(0).vtopClassId);
        assertEquals("ACE", courses.get(0).courseSystem);
        assertEquals("embedded theory", courses.get(0).type);
    }

    @Test public void htmlTheoryOnlyQualifierSurvivesAndDoesNotRequireAnUnregisteredLab() {
        List<Course> courses = new ArrayList<>();
        VTOPHtmlParsers.parseCoursesAndSlots("<div id='studentDetailsList'><table><tr><th>Class Nbr</th><th>Course Code</th>"
                + "<th>Course Title</th><th>Course Type</th><th>Credits</th><th>Slot</th><th>Course System</th></tr>"
                + "<tr><td>CH2026260100834</td><td>BCSE101L</td><td>Programming</td><td>Theory Only</td>"
                + "<td>3</td><td>A1</td><td>ACE</td></tr></table></div>", courses, new ArrayList<Slot>());
        assertEquals(1, courses.size());
        assertEquals("theory only", courses.get(0).type);
        courses.get(0).semesterId = "CH20262601";
        Mark.AllData mark = new Mark.AllData();
        mark.courseId = courses.get(0).id; mark.title = "Internal total"; mark.weightage = 47.75; mark.maxWeightage = 60.0;
        List<com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCContribution> contributions =
                com.salmanmalvasi.studentcc.integrations.studentcc.StudentCCContributionBuilder.build(
                        courses, Collections.singletonList(mark), "CH20262601");
        assertEquals(1, contributions.size());
        assertEquals(47.75, contributions.get(0).total, 0.0);
    }

    @Test public void combinedHtmlCourseLabelPreservesTheoryOnlyQualifier() {
        List<Course> courses = new ArrayList<>();
        VTOPHtmlParsers.parseCoursesAndSlots("<div id='studentDetailsList'><table><tr><th>Course</th><th>Credits</th><th>Slot</th>"
                + "<th>Faculty</th></tr><tr><td>BCSE101L - Programming (Theory Only)</td><td>3</td><td>A1</td>"
                + "<td>Test Faculty</td></tr></table></div>", courses, new ArrayList<Slot>());
        assertEquals(1, courses.size());
        assertEquals("theory only", courses.get(0).type);
    }

    @Test public void reversedEmbeddedAttendanceDoesNotReplaceTheoryClassWithLabClass() {
        Course theory = course(1, "Embedded Theory");
        Course lab = course(2, "Embedded Lab");
        theory.vtopClassId = "CH-THEORY";
        lab.vtopClassId = "CH-LAB";
        List<com.salmanmalvasi.studentcc.models.Attendance> attendance = VTOPJsonParsers.parseAttendanceSummary(
                "[{\"courseCode\":\"BCSE101L\",\"classId\":\"CH-THEORY\",\"present\":9,\"total\":10},"
                        + "{\"courseCode\":\"BCSE101L\",\"classId\":\"CH-LAB\",\"present\":8,\"total\":10}]",
                Arrays.asList(lab, theory), Collections.emptyList(), new StagedSyncData("CH20262601"));
        assertEquals(2, attendance.size());
        assertEquals(Integer.valueOf(1), attendance.get(0).courseId);
        assertEquals(Integer.valueOf(2), attendance.get(1).courseId);
        assertEquals("CH-THEORY", theory.vtopClassId);
        assertEquals("CH-LAB", lab.vtopClassId);
    }

    private static Course course(int id, String type) {
        Course course = new Course();
        course.id = id;
        course.code = "BCSE101L";
        course.type = type;
        course.credits = type.toLowerCase().contains("lab") ? 1.0 : 3.0;
        return course;
    }
}
