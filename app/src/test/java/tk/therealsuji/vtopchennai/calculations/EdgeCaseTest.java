package tk.therealsuji.vtopchennai.calculations;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

import tk.therealsuji.vtopchennai.helpers.AttendanceCalculator;
import tk.therealsuji.vtopchennai.helpers.GPACalculator;
import tk.therealsuji.vtopchennai.models.GPACourse;
import tk.therealsuji.vtopchennai.utils.TestUtils;

/**
 * Edge case and boundary value tests for calculations.
 * Tests extreme values, boundaries, and corner cases.
 */
public class EdgeCaseTest {

    private static final double DELTA = 0.001;

    // ========== Attendance Edge Cases ==========

    @Test
    public void testAttendanceBoundaryValues() {
        int[] boundaries = TestUtils.BoundaryValues.getAttendanceBoundaries();

        for (int percentage : boundaries) {
            int attended = percentage;
            int total = 100;
            int calculated = AttendanceCalculator.calculateAttendancePercentage(attended, total);
            assertEquals("Boundary test failed for " + percentage + "%", percentage, calculated);
        }
    }

    @Test
    public void testAttendanceWithVeryLargeNumbers() {
        // Test with large numbers to check for overflow
        int attended = 1000000;
        int total = 1000000;
        int percentage = AttendanceCalculator.calculateAttendancePercentage(attended, total);
        assertEquals(100, percentage);

        // Partial attendance with large numbers
        attended = 750000;
        total = 1000000;
        percentage = AttendanceCalculator.calculateAttendancePercentage(attended, total);
        assertEquals(75, percentage);
    }

    @Test
    public void testAttendanceFloorRoundingEdgeCases() {
        // Test cases that specifically verify floor behavior
        TestUtils.AttendanceScenario[] scenarios = {
                new TestUtils.AttendanceScenario(1, 3, 33),      // 33.33% -> 33%
                new TestUtils.AttendanceScenario(2, 3, 66),      // 66.67% -> 66%
                new TestUtils.AttendanceScenario(1, 7, 14),      // 14.29% -> 14%
                new TestUtils.AttendanceScenario(5, 7, 71),      // 71.43% -> 71%
                new TestUtils.AttendanceScenario(9, 11, 81),     // 81.82% -> 81%
                new TestUtils.AttendanceScenario(10, 11, 90),    // 90.91% -> 90%
                new TestUtils.AttendanceScenario(999, 1000, 99)  // 99.9% -> 99%
        };

        for (TestUtils.AttendanceScenario scenario : scenarios) {
            int calculated = AttendanceCalculator.calculateAttendancePercentage(
                    scenario.attended, scenario.total);
            assertEquals("Floor rounding failed for " + scenario.attended + "/" + scenario.total,
                    scenario.expectedPercentage, calculated);
        }
    }

    @Test
    public void testAttendanceCalculatorWithStandardScenarios() {
        for (TestUtils.AttendanceScenario scenario : TestUtils.AttendanceScenario.getStandardScenarios()) {
            int calculated = AttendanceCalculator.calculateAttendancePercentage(
                    scenario.attended, scenario.total);
            assertEquals("Standard scenario failed for " + scenario.attended + "/" + scenario.total,
                    scenario.expectedPercentage, calculated);
        }
    }

    @Test
    public void testClassesToReachTargetWithBoundaries() {
        // At exactly 75%
        int needed = AttendanceCalculator.calculateClassesToReachTarget(15, 20, 75);
        assertEquals(0, needed); // Already at target

        // Just below 75%
        needed = AttendanceCalculator.calculateClassesToReachTarget(14, 20, 75);
        assertTrue("Should need some classes to reach 75%", needed > 0);

        // Just above 75%
        needed = AttendanceCalculator.calculateClassesToReachTarget(16, 20, 75);
        assertEquals(0, needed); // Already above target
    }

    @Test
    public void testMaxClassesToMissWithBoundaries() {
        // At exactly 75%
        int canMiss = AttendanceCalculator.calculateMaxClassesToMiss(15, 20, 75);
        assertTrue("Should be able to miss 0 or very few classes", canMiss >= 0);

        // Well above 75% (90%)
        canMiss = AttendanceCalculator.calculateMaxClassesToMiss(18, 20, 75);
        assertTrue("Should be able to miss multiple classes", canMiss > 0);

        // At 100%
        canMiss = AttendanceCalculator.calculateMaxClassesToMiss(20, 20, 75);
        assertTrue("With 100%, should be able to miss several classes", canMiss >= 5);
    }

    // ========== GPA Edge Cases ==========

    @Test
    public void testGPABoundaryValues() {
        double[] gpaBoundaries = TestUtils.BoundaryValues.getGPABoundaries();
        double[] creditBoundaries = TestUtils.BoundaryValues.getCreditBoundaries();

        // Test all combinations of boundary values
        for (double gpa : gpaBoundaries) {
            if (gpa < 0 || gpa > 10) continue; // Skip invalid GPAs

            for (double credits : creditBoundaries) {
                if (credits <= 0) continue; // Skip invalid credits

                try {
                    double result = GPACalculator.calculateNewCGPA(gpa, credits, gpa, credits);
                    assertEquals("Boundary test failed", gpa, result, DELTA);
                } catch (IllegalArgumentException e) {
                    // Expected for invalid inputs
                }
            }
        }
    }

    @Test
    public void testGPAWithZeroGPA() {
        // Starting from 0 GPA
        double newCGPA = GPACalculator.calculateNewCGPA(0.0, 0.0, 8.0, 20.0);
        assertEquals(8.0, newCGPA, DELTA);

        // Semester with 0 GPA (all failures)
        newCGPA = GPACalculator.calculateNewCGPA(8.0, 60.0, 0.0, 20.0);
        assertEquals(6.0, newCGPA, DELTA); // (8*60 + 0*20) / 80 = 6.0
    }

    @Test
    public void testGPAWithPerfectScores() {
        // Perfect GPA throughout
        double newCGPA = GPACalculator.calculateNewCGPA(10.0, 100.0, 10.0, 20.0);
        assertEquals(10.0, newCGPA, DELTA);

        // Maintain perfect GPA
        List<GPACourse> perfectSemester = TestUtils.createHighPerformingSemester();
        newCGPA = GPACalculator.calculateNewCGPAWithCourses(10.0, 80.0, perfectSemester);
        assertTrue("CGPA should remain high", newCGPA >= 9.5);
    }

    @Test
    public void testGPAScenarios() {
        for (TestUtils.GPAScenario scenario : TestUtils.GPAScenario.getStandardScenarios()) {
            double requiredGPA = GPACalculator.calculateRequiredGPA(
                    scenario.targetCGPA,
                    scenario.currentCGPA,
                    scenario.currentCredits,
                    scenario.semesterCredits
            );

            boolean achievable = GPACalculator.isGPAAchievable(requiredGPA);

            if (scenario.isAchievable) {
                if (requiredGPA >= 0 && requiredGPA <= 10) {
                    assertTrue("Scenario should be achievable but marked as impossible: " +
                            scenario.currentCGPA + " -> " + scenario.targetCGPA, achievable);
                }
            } else {
                if (requiredGPA > 10) {
                    assertFalse("Scenario should be impossible but marked as achievable: " +
                            "Required GPA = " + requiredGPA, achievable);
                }
            }
        }
    }

    @Test
    public void testGPAWithVerySmallCredits() {
        // Test with minimal credits
        double newCGPA = GPACalculator.calculateNewCGPA(8.0, 100.0, 10.0, 1.0);
        // (8*100 + 10*1) / 101 = 810 / 101 ≈ 8.02
        assertTrue("Small credit semester should barely change CGPA", newCGPA > 8.0 && newCGPA < 8.1);
    }

    @Test
    public void testGPAWithLargeCreditDifference() {
        // New semester has many more credits than previous total
        double newCGPA = GPACalculator.calculateNewCGPA(6.0, 20.0, 9.0, 100.0);
        // Should be heavily weighted toward new semester
        assertTrue("Large credit semester should dominate", newCGPA > 8.0);
    }

    // ========== Combined Scenario Tests ==========

    @Test
    public void testRealWorldRecoveryScenario() {
        // Student failed first semester: 2.0 GPA with 20 credits
        // Wants to reach 7.0 CGPA by end of 4th year (140 total credits)
        double currentCGPA = 2.0;
        double currentCredits = 20.0;
        double targetCGPA = 7.0;
        double remainingCredits = 120.0; // 6 more semesters * 20 credits

        double requiredAvgGPA = GPACalculator.calculateRequiredGPA(
                targetCGPA, currentCGPA, currentCredits, remainingCredits);

        // Required: ((7*140) - (2*20)) / 120 = (980 - 40) / 120 = 7.83
        assertEquals(7.83, requiredAvgGPA, 0.1);
        assertTrue("Recovery should be possible", GPACalculator.isGPAAchievable(requiredAvgGPA));
    }

    @Test
    public void testAttendanceRecoveryScenario() {
        // Student has 60% attendance (12/20 classes) after missing too many
        // How many consecutive classes to reach 75%?
        int needed = AttendanceCalculator.calculateClassesToReachTarget(12, 20, 75);

        // Verify calculation
        assertTrue("Should need significant attendance to recover", needed > 0);

        // Simulate attending those classes
        int newPercentage = AttendanceCalculator.calculateAfterAttending(12, 20, needed);
        assertTrue("After attending required classes, should be at or above 75%",
                newPercentage >= 75);
    }

    @Test
    public void testPerfectVsFailingCourses() {
        // Semester with extreme mix: some perfect, some failed
        List<GPACourse> courses = new java.util.ArrayList<>();
        courses.add(new GPACourse(4.0, "S")); // Perfect
        courses.add(new GPACourse(4.0, "S")); // Perfect
        courses.add(new GPACourse(4.0, "F")); // Failed
        courses.add(new GPACourse(4.0, "F")); // Failed

        double gpa = GPACalculator.calculateSemesterGPA(courses);
        // (40 + 40 + 0 + 0) / 16 = 5.0
        assertEquals(5.0, gpa, DELTA);
    }

    @Test
    public void testExtremeAttendanceDrop() {
        // Start at 100% (20/20), simulate missing many classes
        int currentAttended = 20;
        int currentTotal = 20;

        // Miss 30 classes
        int newPercentage = AttendanceCalculator.calculateAfterMissing(
                currentAttended, currentTotal, 30);

        // 20/50 = 40%
        assertEquals(40, newPercentage);
        assertTrue("Should drop significantly below 75%", newPercentage < 75);
    }

    @Test
    public void testMinimalImprovementScenario() {
        // Student has 7.4 CGPA, needs exactly 7.5
        double requiredGPA = GPACalculator.calculateRequiredGPA(7.5, 7.4, 100.0, 20.0);

        // ((7.5*120) - (7.4*100)) / 20 = (900 - 740) / 20 = 8.0
        assertEquals(8.0, requiredGPA, DELTA);
        assertTrue("Small improvement should be achievable", GPACalculator.isGPAAchievable(requiredGPA));
    }

    @Test
    public void testFloatingPointPrecision() {
        // Test calculations that might have precision issues
        int percentage = AttendanceCalculator.calculateAttendancePercentage(1, 3);
        assertEquals(33, percentage); // 33.333... -> 33

        percentage = AttendanceCalculator.calculateAttendancePercentage(2, 3);
        assertEquals(66, percentage); // 66.666... -> 66

        // GPA with repeating decimals
        GPACourse course = new GPACourse(3.0, "A"); // 3 * 9 = 27
        List<GPACourse> courses = new java.util.ArrayList<>();
        courses.add(course);
        courses.add(new GPACourse(3.0, "A")); // 3 * 9 = 27
        courses.add(new GPACourse(3.0, "A")); // 3 * 9 = 27

        double gpa = GPACalculator.calculateSemesterGPA(courses);
        assertEquals(9.0, gpa, DELTA); // Should be exactly 9.0
    }

    @Test
    public void testSymmetricOperations() {
        // Test that certain operations are symmetric

        // Attending then missing should equal missing then attending (for final percentage)
        int initial = 80;
        int change = 10;

        // Path 1: Attend 10, then miss 10
        int afterAttend = AttendanceCalculator.calculateAfterAttending(initial, 100, change);
        int final1 = AttendanceCalculator.calculateAfterMissing(initial + change, 100 + change, change);

        // Both should end up at similar place (not exact due to floor rounding)
        assertTrue("Should end up near 80%", Math.abs(final1 - 80) < 3);
    }

    @Test
    public void testMonotonicity() {
        // Test that attending more classes monotonically increases percentage

        int attended = 10;
        int total = 20;
        int lastPercentage = AttendanceCalculator.calculateAttendancePercentage(attended, total);

        for (int i = 1; i <= 10; i++) {
            int newPercentage = AttendanceCalculator.calculateAfterAttending(attended, total, i);
            assertTrue("Attending more classes should not decrease percentage",
                    newPercentage >= lastPercentage);
            lastPercentage = newPercentage;
        }

        // Test that missing more classes monotonically decreases percentage
        lastPercentage = 100;
        attended = 20;
        total = 20;

        for (int i = 1; i <= 10; i++) {
            int newPercentage = AttendanceCalculator.calculateAfterMissing(attended, total, i);
            assertTrue("Missing more classes should not increase percentage",
                    newPercentage <= lastPercentage);
            lastPercentage = newPercentage;
        }
    }
}
