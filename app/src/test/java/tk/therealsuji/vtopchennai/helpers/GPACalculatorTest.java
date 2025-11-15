package tk.therealsuji.vtopchennai.helpers;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tk.therealsuji.vtopchennai.models.GPACourse;

/**
 * Comprehensive unit tests for GPACalculator helper class.
 * Tests all GPA/CGPA calculation scenarios.
 */
public class GPACalculatorTest {

    private static final double DELTA = 0.001; // Tolerance for floating point comparisons

    // ========== Semester GPA Calculation Tests ==========

    @Test
    public void testCalculateSemesterGPA_SingleCourse() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "A") // 4 credits, 9.0 points = 36.0 total
        );

        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(9.0, gpa, DELTA); // 36.0 / 4.0 = 9.0
    }

    @Test
    public void testCalculateSemesterGPA_MultipleCourses() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "S"),  // 40.0 points
            new GPACourse(3.0, "A"),  // 27.0 points
            new GPACourse(3.0, "B"),  // 24.0 points
            new GPACourse(2.0, "C")   // 14.0 points
        );

        // Total: 105 points, 12 credits
        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(8.75, gpa, DELTA); // 105.0 / 12.0 = 8.75
    }

    @Test
    public void testCalculateSemesterGPA_WithFailedCourse() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "S"),  // 40.0 points
            new GPACourse(3.0, "F")   // 0.0 points
        );

        // Total: 40 points, 7 credits
        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(5.714, gpa, 0.01); // 40.0 / 7.0 ≈ 5.714
    }

    @Test
    public void testCalculateSemesterGPA_EmptyCourses() {
        List<GPACourse> courses = new ArrayList<>();
        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(0.0, gpa, DELTA);
    }

    @Test
    public void testCalculateSemesterGPA_NullCourses() {
        double gpa = GPACalculator.calculateSemesterGPA(null);
        assertEquals(0.0, gpa, DELTA);
    }

    @Test
    public void testCalculateSemesterGPA_AllSGrades() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "S"),
            new GPACourse(3.0, "S"),
            new GPACourse(2.0, "S")
        );

        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(10.0, gpa, DELTA); // Perfect GPA
    }

    // ========== New CGPA Calculation Tests ==========

    @Test
    public void testCalculateNewCGPA_BasicScenario() {
        // Current: 8.0 CGPA with 30 credits
        // Semester: 9.0 GPA with 15 credits
        // Expected: (8.0*30 + 9.0*15) / 45 = (240 + 135) / 45 = 8.333...
        double newCGPA = GPACalculator.calculateNewCGPA(8.0, 30.0, 9.0, 15.0);
        assertEquals(8.333, newCGPA, 0.01);
    }

    @Test
    public void testCalculateNewCGPA_ImprovingGPA() {
        // Current: 7.0 CGPA with 60 credits
        // Semester: 9.0 GPA with 20 credits
        double newCGPA = GPACalculator.calculateNewCGPA(7.0, 60.0, 9.0, 20.0);
        // Expected: (7.0*60 + 9.0*20) / 80 = (420 + 180) / 80 = 7.5
        assertEquals(7.5, newCGPA, DELTA);
    }

    @Test
    public void testCalculateNewCGPA_DecliningGPA() {
        // Current: 9.0 CGPA with 60 credits
        // Semester: 6.0 GPA with 20 credits
        double newCGPA = GPACalculator.calculateNewCGPA(9.0, 60.0, 6.0, 20.0);
        // Expected: (9.0*60 + 6.0*20) / 80 = (540 + 120) / 80 = 8.25
        assertEquals(8.25, newCGPA, DELTA);
    }

    @Test
    public void testCalculateNewCGPA_FirstSemester() {
        // Starting from 0.0 CGPA with 0 credits
        double newCGPA = GPACalculator.calculateNewCGPA(0.0, 0.0, 8.5, 20.0);
        assertEquals(8.5, newCGPA, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNewCGPA_InvalidCurrentGPA() {
        GPACalculator.calculateNewCGPA(11.0, 30.0, 9.0, 15.0); // GPA > 10
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateNewCGPA_NegativeCredits() {
        GPACalculator.calculateNewCGPA(8.0, -5.0, 9.0, 15.0);
    }

    // ========== Calculate New CGPA with Courses Tests ==========

    @Test
    public void testCalculateNewCGPAWithCourses() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "A"),  // 36.0 points
            new GPACourse(3.0, "S")   // 30.0 points
        );
        // Semester GPA: 66.0 / 7.0 ≈ 9.429

        // Current: 8.0 CGPA with 30 credits
        double newCGPA = GPACalculator.calculateNewCGPAWithCourses(8.0, 30.0, courses);
        // Expected: (8.0*30 + 9.429*7) / 37
        double semesterGPA = 66.0 / 7.0;
        double expected = (8.0 * 30 + semesterGPA * 7) / 37.0;
        assertEquals(expected, newCGPA, 0.01);
    }

    // ========== Required GPA Calculation Tests ==========

    @Test
    public void testCalculateRequiredGPA_BasicScenario() {
        // Want to achieve 8.0 CGPA
        // Current: 7.5 CGPA with 60 credits
        // Upcoming semester: 20 credits
        // Required: ((8.0*80) - (7.5*60)) / 20 = (640 - 450) / 20 = 9.5
        double requiredGPA = GPACalculator.calculateRequiredGPA(8.0, 7.5, 60.0, 20.0);
        assertEquals(9.5, requiredGPA, DELTA);
    }

    @Test
    public void testCalculateRequiredGPA_TargetAlreadyAchieved() {
        // Want to achieve 7.0 CGPA, already have 8.0
        double requiredGPA = GPACalculator.calculateRequiredGPA(7.0, 8.0, 60.0, 20.0);
        // Result will be negative, indicating target is already achieved
        assertTrue(requiredGPA < 0);
    }

    @Test
    public void testCalculateRequiredGPA_ImpossibleTarget() {
        // Want to achieve 9.5 CGPA
        // Current: 6.0 CGPA with 100 credits
        // Upcoming semester: 20 credits
        double requiredGPA = GPACalculator.calculateRequiredGPA(9.5, 6.0, 100.0, 20.0);
        // Required: ((9.5*120) - (6.0*100)) / 20 = (1140 - 600) / 20 = 27.0
        assertEquals(27.0, requiredGPA, DELTA);
        // This is > 10, so impossible
        assertFalse(GPACalculator.isGPAAchievable(requiredGPA));
    }

    @Test
    public void testCalculateRequiredGPA_ExactTarget() {
        // Want to achieve 8.0 CGPA, need exactly 8.0 in next semester
        double requiredGPA = GPACalculator.calculateRequiredGPA(8.0, 8.0, 60.0, 20.0);
        assertEquals(8.0, requiredGPA, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRequiredGPA_ZeroSemesterCredits() {
        GPACalculator.calculateRequiredGPA(8.0, 7.5, 60.0, 0.0);
    }

    // ========== Predict CGPA Tests ==========

    @Test
    public void testPredictCGPA_OptimisticScenario() {
        // Current: 7.0 CGPA with 60 credits
        // Expected: 9.0 GPA in upcoming 20 credits
        double predictedCGPA = GPACalculator.predictCGPA(7.0, 60.0, 9.0, 20.0);
        // Expected: (7.0*60 + 9.0*20) / 80 = 7.5
        assertEquals(7.5, predictedCGPA, DELTA);
    }

    @Test
    public void testPredictCGPA_PessimisticScenario() {
        // Current: 9.0 CGPA with 60 credits
        // Expected: 6.0 GPA in upcoming 20 credits
        double predictedCGPA = GPACalculator.predictCGPA(9.0, 60.0, 6.0, 20.0);
        assertEquals(8.25, predictedCGPA, DELTA);
    }

    // ========== Total Credits Calculation Tests ==========

    @Test
    public void testCalculateTotalCredits() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "S"),
            new GPACourse(3.0, "A"),
            new GPACourse(2.0, "B")
        );

        double totalCredits = GPACalculator.calculateTotalCredits(courses);
        assertEquals(9.0, totalCredits, DELTA);
    }

    @Test
    public void testCalculateTotalCredits_Empty() {
        double totalCredits = GPACalculator.calculateTotalCredits(new ArrayList<>());
        assertEquals(0.0, totalCredits, DELTA);
    }

    @Test
    public void testCalculateTotalCredits_Null() {
        double totalCredits = GPACalculator.calculateTotalCredits(null);
        assertEquals(0.0, totalCredits, DELTA);
    }

    // ========== GPA Achievability Tests ==========

    @Test
    public void testIsGPAAchievable_Valid() {
        assertTrue(GPACalculator.isGPAAchievable(0.0));
        assertTrue(GPACalculator.isGPAAchievable(5.0));
        assertTrue(GPACalculator.isGPAAchievable(9.5));
        assertTrue(GPACalculator.isGPAAchievable(10.0));
    }

    @Test
    public void testIsGPAAchievable_Invalid() {
        assertFalse(GPACalculator.isGPAAchievable(-0.1));
        assertFalse(GPACalculator.isGPAAchievable(10.1));
        assertFalse(GPACalculator.isGPAAchievable(15.0));
    }

    // ========== Required GPA Message Tests ==========

    @Test
    public void testGetRequiredGPAMessage_Negative() {
        String message = GPACalculator.getRequiredGPAMessage(-1.0);
        assertTrue(message.contains("already achievable"));
    }

    @Test
    public void testGetRequiredGPAMessage_Impossible() {
        String message = GPACalculator.getRequiredGPAMessage(12.0);
        assertTrue(message.contains("not possible"));
    }

    @Test
    public void testGetRequiredGPAMessage_Exceptional() {
        String message = GPACalculator.getRequiredGPAMessage(9.8);
        assertTrue(message.contains("exceptional"));
    }

    @Test
    public void testGetRequiredGPAMessage_Excellent() {
        String message = GPACalculator.getRequiredGPAMessage(9.0);
        assertTrue(message.contains("excellent"));
    }

    @Test
    public void testGetRequiredGPAMessage_Achievable() {
        String message = GPACalculator.getRequiredGPAMessage(7.5);
        assertTrue(message.contains("achievable"));
    }

    // ========== CGPA Change Calculation Tests ==========

    @Test
    public void testCalculateCGPAChange_Improvement() {
        double change = GPACalculator.calculateCGPAChange(7.0, 7.5);
        assertEquals(0.5, change, DELTA);
        assertTrue(change > 0); // Improvement
    }

    @Test
    public void testCalculateCGPAChange_Decrease() {
        double change = GPACalculator.calculateCGPAChange(8.5, 8.0);
        assertEquals(-0.5, change, DELTA);
        assertTrue(change < 0); // Decrease
    }

    @Test
    public void testCalculateCGPAChange_NoChange() {
        double change = GPACalculator.calculateCGPAChange(8.0, 8.0);
        assertEquals(0.0, change, DELTA);
    }

    // ========== GPA Result with Validation Tests ==========

    @Test
    public void testCalculateSemesterGPAWithValidation_Valid() {
        List<GPACourse> courses = Arrays.asList(
            new GPACourse(4.0, "A"),
            new GPACourse(3.0, "B")
        );

        GPACalculator.GPAResult result = GPACalculator.calculateSemesterGPAWithValidation(courses);
        assertTrue(result.isValid());
        assertEquals(7.0, result.getTotalCredits(), DELTA);
        assertTrue(result.getGpa() > 0);
    }

    @Test
    public void testCalculateSemesterGPAWithValidation_Empty() {
        GPACalculator.GPAResult result = GPACalculator.calculateSemesterGPAWithValidation(new ArrayList<>());
        assertFalse(result.isValid());
        assertEquals(0.0, result.getGpa(), DELTA);
        assertTrue(result.getMessage().contains("No courses"));
    }

    @Test
    public void testCalculateRequiredGPAWithValidation_Achievable() {
        GPACalculator.GPAResult result = GPACalculator.calculateRequiredGPAWithValidation(8.0, 7.5, 60.0, 20.0);
        assertTrue(result.isValid());
        assertTrue(GPACalculator.isGPAAchievable(result.getGpa()));
    }

    @Test
    public void testCalculateRequiredGPAWithValidation_Impossible() {
        GPACalculator.GPAResult result = GPACalculator.calculateRequiredGPAWithValidation(9.5, 6.0, 100.0, 20.0);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("not possible"));
    }

    // ========== Real-World VIT Scenarios ==========

    @Test
    public void testRealWorldScenario_VIT_FirstYear() {
        // First year student completed Fall semester with 7.5 GPA, 20 credits
        // Spring semester courses
        List<GPACourse> springCourses = Arrays.asList(
            new GPACourse(4.0, "A"),  // Math
            new GPACourse(4.0, "B"),  // Physics
            new GPACourse(3.0, "A"),  // Programming
            new GPACourse(3.0, "S"),  // English
            new GPACourse(2.0, "B")   // Lab
        );

        double newCGPA = GPACalculator.calculateNewCGPAWithCourses(7.5, 20.0, springCourses);
        assertTrue(newCGPA > 7.5); // Should improve
        assertTrue(newCGPA <= 10.0); // Should be valid
    }

    @Test
    public void testRealWorldScenario_VIT_TargetCGPA_8_5() {
        // Student wants 8.5 CGPA for placements
        // Current: 8.0 CGPA with 80 credits
        // Final semester: 20 credits
        double requiredGPA = GPACalculator.calculateRequiredGPA(8.5, 8.0, 80.0, 20.0);

        // Required: ((8.5*100) - (8.0*80)) / 20 = (850 - 640) / 20 = 10.5
        assertEquals(10.5, requiredGPA, DELTA);
        assertFalse(GPACalculator.isGPAAchievable(requiredGPA)); // Impossible
    }

    @Test
    public void testRealWorldScenario_VIT_MaintainCGPA() {
        // Student has 9.0 CGPA with 100 credits
        // Wants to maintain above 8.5 in final semester (20 credits)
        // What's the minimum GPA needed?
        double requiredGPA = GPACalculator.calculateRequiredGPA(8.5, 9.0, 100.0, 20.0);

        // Required: ((8.5*120) - (9.0*100)) / 20 = (1020 - 900) / 20 = 6.0
        assertEquals(6.0, requiredGPA, DELTA);
        assertTrue(GPACalculator.isGPAAchievable(requiredGPA)); // Easily achievable
    }

    @Test
    public void testRealWorldScenario_VIT_RecoveryFromBadSemester() {
        // Student had a bad semester: 5.0 GPA with 20 credits
        // Wants to know CGPA after next semester with 8.5 GPA
        double predictedCGPA = GPACalculator.predictCGPA(5.0, 20.0, 8.5, 20.0);

        // Expected: (5.0*20 + 8.5*20) / 40 = (100 + 170) / 40 = 6.75
        assertEquals(6.75, predictedCGPA, DELTA);
    }
}
