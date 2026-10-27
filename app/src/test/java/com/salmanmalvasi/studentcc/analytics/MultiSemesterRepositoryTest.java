package com.salmanmalvasi.studentcc.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.salmanmalvasi.studentcc.helpers.FastScraper;
import com.salmanmalvasi.studentcc.helpers.MultiSemesterRepository;
import com.salmanmalvasi.studentcc.models.AllSemestersAnalytics;
import com.salmanmalvasi.studentcc.models.SemesterSummary;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiSemesterRepositoryTest {

    @Test
    public void testAdmissionYearExtraction() {
        assertEquals(2023, MultiSemesterRepository.getStudentAdmissionYear("23BCE1234"));
        assertEquals(2022, MultiSemesterRepository.getStudentAdmissionYear("22BCI0045"));
        assertEquals(2024, MultiSemesterRepository.getStudentAdmissionYear("24MIS0012"));
        assertEquals(2021, MultiSemesterRepository.getStudentAdmissionYear("21BCE0456"));
        assertEquals(-1, MultiSemesterRepository.getStudentAdmissionYear("INVALID"));
        assertEquals(-1, MultiSemesterRepository.getStudentAdmissionYear(""));
        assertEquals(-1, MultiSemesterRepository.getStudentAdmissionYear(null));
    }

    @Test
    public void testSemesterStartYearExtraction() {
        assertEquals(2023, MultiSemesterRepository.extractSemesterStartYear("Fall Semester 2023-24"));
        assertEquals(2023, MultiSemesterRepository.extractSemesterStartYear("Winter Semester 2023-24"));
        assertEquals(2024, MultiSemesterRepository.extractSemesterStartYear("Fall Semester 2024-25"));
        assertEquals(2019, MultiSemesterRepository.extractSemesterStartYear("Fall Semester 2019-20"));
        assertEquals(2020, MultiSemesterRepository.extractSemesterStartYear("Winter Semester 2020-21"));
        assertEquals(-1, MultiSemesterRepository.extractSemesterStartYear("No Year Semester"));
    }

    @Test
    public void testGradeViewParsing() {
        String mockHtml = "<html><body>" +
                "<table>" +
                "<tr><th>Sl.No</th><th>Course Code</th><th>Course Title</th><th>Course Type</th><th>Credits</th><th>Grade</th></tr>" +
                "<tr><td>1</td><td>CSE1001</td><td>Problem Solving</td><td>Theory Only</td><td>3</td><td>S</td></tr>" +
                "<tr><td>2</td><td>MAT1011</td><td>Calculus</td><td>Theory Only</td><td>4</td><td>A</td></tr>" +
                "<tr><td>3</td><td>PHY1001</td><td>Engineering Physics</td><td>Lab Only</td><td>1</td><td>B</td></tr>" +
                "<tr><td colspan='6'>GPA : 8.85</td></tr>" +
                "</table>" +
                "</body></html>";

        SemesterSummary summary = new SemesterSummary("SEM_1", "Fall Semester 2023-24");
        FastScraper.parseGradeViewIntoSummary(mockHtml, summary);

        assertEquals("8.85", summary.gpa);
        assertEquals(3, summary.subjects.size());
        assertEquals(0, summary.totalClasses);
        assertEquals(0, summary.attendedClasses);
        assertTrue(summary.isCompleted);

        assertEquals("CSE1001", summary.subjects.get(0).courseCode);
        assertEquals("S", summary.subjects.get(0).grade);
        assertEquals(3, summary.subjects.get(0).credits);
        assertEquals(95.0f, summary.subjects.get(0).marksPercentage, 0.01f);

        assertEquals("MAT1011", summary.subjects.get(1).courseCode);
        assertEquals("A", summary.subjects.get(1).grade);
        assertEquals(4, summary.subjects.get(1).credits);
        assertEquals(85.0f, summary.subjects.get(1).marksPercentage, 0.01f);

        assertEquals("PHY1001", summary.subjects.get(2).courseCode);
        assertEquals("B", summary.subjects.get(2).grade);
        assertEquals(1, summary.subjects.get(2).credits);
        assertEquals(75.0f, summary.subjects.get(2).marksPercentage, 0.01f);
    }

    @Test
    public void testAttendanceDoesNotMultiplyAcrossSemesters() {
        // Active semester with 308 attended out of 351 classes
        SemesterSummary activeSem = new SemesterSummary("SEM_ACTIVE", "Fall Semester 2024-25");
        activeSem.attendedClasses = 308;
        activeSem.totalClasses = 351;
        activeSem.attendancePercentage = 87.75f;
        activeSem.isCompleted = false;

        // Past completed semester with grades and 0 total classes
        SemesterSummary pastSem = new SemesterSummary("SEM_PAST", "Fall Semester 2023-24");
        pastSem.attendedClasses = 0;
        pastSem.totalClasses = 0;
        pastSem.attendancePercentage = 0f;
        pastSem.isCompleted = true;
        pastSem.gpa = "8.85";

        List<SemesterSummary> list = new ArrayList<>();
        list.add(activeSem);
        list.add(pastSem);

        int totalAttended = 0;
        int totalConducted = 0;
        for (SemesterSummary sem : list) {
            if (sem.totalClasses > 0) {
                totalAttended += sem.attendedClasses;
                totalConducted += sem.totalClasses;
            }
        }

        // Must remain exactly 308 and 351, NEVER 3388
        assertEquals(308, totalAttended);
        assertEquals(351, totalConducted);
        float overallPct = (float) Math.round((totalAttended * 10000.0) / totalConducted) / 100.0f;
        assertEquals(87.75f, overallPct, 0.01f);
    }

    @Test
    public void testAdmissionFilteringAndEmptySemesterPruning() {
        int admissionYear = MultiSemesterRepository.getStudentAdmissionYear("23BCE1234");
        assertEquals(2023, admissionYear);

        // Simulated VTOP dropdown containing 11 semesters going back to 2019
        List<String> rawVtopDropdown = new ArrayList<>();
        rawVtopDropdown.add("Fall Semester 2019-20");
        rawVtopDropdown.add("Winter Semester 2019-20");
        rawVtopDropdown.add("Fall Semester 2020-21");
        rawVtopDropdown.add("Winter Semester 2020-21");
        rawVtopDropdown.add("Fall Semester 2021-22");
        rawVtopDropdown.add("Winter Semester 2021-22");
        rawVtopDropdown.add("Fall Semester 2022-23");
        rawVtopDropdown.add("Winter Semester 2022-23");
        rawVtopDropdown.add("Fall Semester 2023-24");
        rawVtopDropdown.add("Winter Semester 2023-24");
        rawVtopDropdown.add("Fall Semester 2024-25");

        List<String> eligible = new ArrayList<>();
        for (String sem : rawVtopDropdown) {
            int semYear = MultiSemesterRepository.extractSemesterStartYear(sem);
            if (admissionYear > 0 && semYear > 0 && semYear < admissionYear) {
                continue; // Reject pre-admission semesters
            }
            eligible.add(sem);
        }

        // Must ONLY keep 2023-24 and 2024-25 semesters (3 semesters instead of 11!)
        assertEquals(3, eligible.size());
        assertTrue(eligible.contains("Fall Semester 2023-24"));
        assertTrue(eligible.contains("Winter Semester 2023-24"));
        assertTrue(eligible.contains("Fall Semester 2024-25"));
        assertFalse(eligible.contains("Fall Semester 2022-23"));
        assertFalse(eligible.contains("Fall Semester 2019-20"));
    }
}
