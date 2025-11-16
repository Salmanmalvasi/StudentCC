package tk.therealsuji.vtopchennai.models;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for GPACourse model.
 * Tests grade point conversion and GPA calculations.
 */
public class GPACourseTest {

    private static final double DELTA = 0.001; // Tolerance for floating point comparisons

    @Test
    public void testGradePointConversion_S_Grade() {
        GPACourse course = new GPACourse(3.0, "S");
        assertEquals(10.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_A_Grade() {
        GPACourse course = new GPACourse(3.0, "A");
        assertEquals(9.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_B_Grade() {
        GPACourse course = new GPACourse(3.0, "B");
        assertEquals(8.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_C_Grade() {
        GPACourse course = new GPACourse(3.0, "C");
        assertEquals(7.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_D_Grade() {
        GPACourse course = new GPACourse(3.0, "D");
        assertEquals(6.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_E_Grade() {
        GPACourse course = new GPACourse(3.0, "E");
        assertEquals(5.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_F_Grade() {
        GPACourse course = new GPACourse(3.0, "F");
        assertEquals(0.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testGradePointConversion_InvalidGrade() {
        GPACourse course = new GPACourse(3.0, "X");
        assertEquals(0.0, course.getGradePoints(), DELTA);
    }

    @Test
    public void testCourseGPA_Calculation() {
        // Course with 4 credits and A grade (9.0 points)
        GPACourse course = new GPACourse(4.0, "A");
        // Expected: 4.0 * 9.0 = 36.0
        assertEquals(36.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testCourseGPA_SGrade() {
        // Course with 3 credits and S grade (10.0 points)
        GPACourse course = new GPACourse(3.0, "S");
        // Expected: 3.0 * 10.0 = 30.0
        assertEquals(30.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testCourseGPA_FGrade() {
        // Course with 4 credits and F grade (0.0 points)
        GPACourse course = new GPACourse(4.0, "F");
        // Expected: 4.0 * 0.0 = 0.0
        assertEquals(0.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testCourseGPA_DecimalCredits() {
        // Course with 1.5 credits and B grade (8.0 points)
        GPACourse course = new GPACourse(1.5, "B");
        // Expected: 1.5 * 8.0 = 12.0
        assertEquals(12.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testCourseGPA_ZeroCredits() {
        // Course with 0 credits
        GPACourse course = new GPACourse(0.0, "S");
        // Expected: 0.0 * 10.0 = 0.0
        assertEquals(0.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testSetGrade_UpdatesGradePoints() {
        GPACourse course = new GPACourse(3.0, "F");
        assertEquals(0.0, course.getGradePoints(), DELTA);

        course.setGrade("S");
        assertEquals(10.0, course.getGradePoints(), DELTA);
        assertEquals(30.0, course.getCourseGPA(), DELTA);
    }

    @Test
    public void testSetCreditHours_UpdatesGPA() {
        GPACourse course = new GPACourse(3.0, "A");
        assertEquals(27.0, course.getCourseGPA(), DELTA); // 3 * 9 = 27

        course.setCreditHours(4.0);
        assertEquals(36.0, course.getCourseGPA(), DELTA); // 4 * 9 = 36
    }

    @Test
    public void testGetAvailableGrades() {
        String[] expectedGrades = {"S", "A", "B", "C", "D", "E", "F"};
        String[] actualGrades = GPACourse.getAvailableGrades();

        assertArrayEquals(expectedGrades, actualGrades);
    }

    @Test
    public void testGetGradeDisplayText_AllGrades() {
        assertEquals("S (10.0)", GPACourse.getGradeDisplayText("S"));
        assertEquals("A (9.0)", GPACourse.getGradeDisplayText("A"));
        assertEquals("B (8.0)", GPACourse.getGradeDisplayText("B"));
        assertEquals("C (7.0)", GPACourse.getGradeDisplayText("C"));
        assertEquals("D (6.0)", GPACourse.getGradeDisplayText("D"));
        assertEquals("E (5.0)", GPACourse.getGradeDisplayText("E"));
        assertEquals("F (0.0)", GPACourse.getGradeDisplayText("F"));
    }

    @Test
    public void testGetGradeDisplayText_InvalidGrade() {
        assertEquals("X", GPACourse.getGradeDisplayText("X"));
    }

    @Test
    public void testGetters() {
        GPACourse course = new GPACourse(3.5, "B");
        assertEquals(3.5, course.getCreditHours(), DELTA);
        assertEquals("B", course.getGrade());
    }

    @Test
    public void testMultipleCourses_DifferentScenarios() {
        // Simulate a semester with multiple courses
        GPACourse course1 = new GPACourse(4.0, "S"); // 40 points
        GPACourse course2 = new GPACourse(3.0, "A"); // 27 points
        GPACourse course3 = new GPACourse(3.0, "B"); // 24 points
        GPACourse course4 = new GPACourse(2.0, "C"); // 14 points
        GPACourse course5 = new GPACourse(1.0, "D"); // 6 points

        double totalGradePoints = course1.getCourseGPA() +
                                  course2.getCourseGPA() +
                                  course3.getCourseGPA() +
                                  course4.getCourseGPA() +
                                  course5.getCourseGPA();

        double totalCredits = 4.0 + 3.0 + 3.0 + 2.0 + 1.0; // 13 credits
        double semesterGPA = totalGradePoints / totalCredits;

        // Expected: (40 + 27 + 24 + 14 + 6) / 13 = 111 / 13 = 8.538...
        assertEquals(8.538, semesterGPA, 0.01);
    }
}
