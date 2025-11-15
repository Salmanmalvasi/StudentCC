package tk.therealsuji.vtopchennai.calculations;

import org.junit.Test;
import static org.junit.Assert.*;

import tk.therealsuji.vtopchennai.helpers.AttendanceCalculator;

/**
 * Unit tests for AttendanceCalculator helper class.
 * Tests attendance percentage calculations and various attendance scenarios.
 */
public class AttendanceCalculatorTest {

    @Test
    public void testCalculateAttendancePercentage_ExactPercentages() {
        // Test exact percentages
        assertEquals(75, AttendanceCalculator.calculateAttendancePercentage(3, 4));
        assertEquals(100, AttendanceCalculator.calculateAttendancePercentage(10, 10));
        assertEquals(50, AttendanceCalculator.calculateAttendancePercentage(5, 10));
        assertEquals(0, AttendanceCalculator.calculateAttendancePercentage(0, 10));
    }

    @Test
    public void testCalculateAttendancePercentage_FloorBehavior() {
        // Test that 9.99% becomes 9% (floor behavior, not ceiling)
        assertEquals(9, AttendanceCalculator.calculateAttendancePercentage(9, 100));
        assertEquals(9, AttendanceCalculator.calculateAttendancePercentage(99, 1000));
        assertEquals(9, AttendanceCalculator.calculateAttendancePercentage(9, 90));

        // Test that 10.0% becomes 10%
        assertEquals(10, AttendanceCalculator.calculateAttendancePercentage(10, 100));
        assertEquals(10, AttendanceCalculator.calculateAttendancePercentage(1, 10));
    }

    @Test
    public void testCalculateAttendancePercentage_EdgeCases() {
        // 85.71% should become 85%
        assertEquals(85, AttendanceCalculator.calculateAttendancePercentage(6, 7));

        // 75.00% should become 75%
        assertEquals(75, AttendanceCalculator.calculateAttendancePercentage(3, 4));

        // 99.99% should become 99%
        assertEquals(99, AttendanceCalculator.calculateAttendancePercentage(9999, 10000));
    }

    @Test
    public void testCalculateAttendancePercentage_DivisionByZero() {
        // Should return 0 when total is 0 (not throw exception)
        assertEquals(0, AttendanceCalculator.calculateAttendancePercentage(5, 0));
        assertEquals(0, AttendanceCalculator.calculateAttendancePercentage(0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateAttendancePercentage_NegativeAttended() {
        AttendanceCalculator.calculateAttendancePercentage(-1, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateAttendancePercentage_NegativeTotal() {
        AttendanceCalculator.calculateAttendancePercentage(5, -10);
    }

    @Test
    public void testCalculateClassesToReachTarget_BasicScenarios() {
        // Currently at 70% (7/10), want to reach 80%
        // Need: (7 + x) / (10 + x) >= 0.80
        // Solving: 7 + x >= 8 + 0.8x => 0.2x >= 1 => x >= 5
        int needed = AttendanceCalculator.calculateClassesToReachTarget(7, 10, 80);
        assertEquals(5, needed);

        // Currently at 50% (5/10), want to reach 75%
        needed = AttendanceCalculator.calculateClassesToReachTarget(5, 10, 75);
        assertEquals(10, needed);
    }

    @Test
    public void testCalculateClassesToReachTarget_AlreadyAtTarget() {
        // Already at 80% (8/10), target is 75%
        int needed = AttendanceCalculator.calculateClassesToReachTarget(8, 10, 75);
        assertEquals(0, needed);

        // Already at exact target
        needed = AttendanceCalculator.calculateClassesToReachTarget(8, 10, 80);
        assertEquals(0, needed);
    }

    @Test
    public void testCalculateClassesToReachTarget_ImpossibleTarget() {
        // Missed classes, trying to reach 100%
        int needed = AttendanceCalculator.calculateClassesToReachTarget(8, 10, 100);
        assertEquals(-1, needed);
    }

    @Test
    public void testCalculateClassesToReachTarget_PerfectAttendance() {
        // Perfect attendance, want to stay at 100%
        int needed = AttendanceCalculator.calculateClassesToReachTarget(10, 10, 100);
        assertEquals(0, needed);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateClassesToReachTarget_InvalidPercentage() {
        AttendanceCalculator.calculateClassesToReachTarget(7, 10, 150);
    }

    @Test
    public void testCalculateMaxClassesToMiss_BasicScenarios() {
        // Currently at 80% (8/10), minimum is 75%
        // Can miss: (100 * 8 / 75) - 10 = 10.67 - 10 = 0.67 => 0 (floor)
        int canMiss = AttendanceCalculator.calculateMaxClassesToMiss(8, 10, 75);
        assertEquals(0, canMiss);

        // Currently at 90% (9/10), minimum is 75%
        // Can miss: (100 * 9 / 75) - 10 = 12 - 10 = 2
        canMiss = AttendanceCalculator.calculateMaxClassesToMiss(9, 10, 75);
        assertEquals(2, canMiss);
    }

    @Test
    public void testCalculateMaxClassesToMiss_BelowMinimum() {
        // Already below minimum
        int canMiss = AttendanceCalculator.calculateMaxClassesToMiss(6, 10, 75);
        assertEquals(0, canMiss);
    }

    @Test
    public void testCalculateMaxClassesToMiss_ZeroMinimum() {
        // Minimum is 0%, can miss unlimited
        int canMiss = AttendanceCalculator.calculateMaxClassesToMiss(8, 10, 0);
        assertEquals(Integer.MAX_VALUE, canMiss);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateMaxClassesToMiss_InvalidPercentage() {
        AttendanceCalculator.calculateMaxClassesToMiss(8, 10, -10);
    }

    @Test
    public void testCalculateAfterMissing() {
        // Start at 80% (8/10), miss 2 classes
        // New: 8/12 = 66.67% => 66% (floor)
        int newPercentage = AttendanceCalculator.calculateAfterMissing(8, 10, 2);
        assertEquals(66, newPercentage);

        // Start at 100% (10/10), miss 1 class
        // New: 10/11 = 90.91% => 90% (floor)
        newPercentage = AttendanceCalculator.calculateAfterMissing(10, 10, 1);
        assertEquals(90, newPercentage);
    }

    @Test
    public void testCalculateAfterMissing_Zero() {
        // Miss 0 classes, percentage stays same
        int newPercentage = AttendanceCalculator.calculateAfterMissing(8, 10, 0);
        assertEquals(80, newPercentage);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateAfterMissing_Negative() {
        AttendanceCalculator.calculateAfterMissing(8, 10, -1);
    }

    @Test
    public void testCalculateAfterAttending() {
        // Start at 70% (7/10), attend 3 more classes
        // New: 10/13 = 76.92% => 76% (floor)
        int newPercentage = AttendanceCalculator.calculateAfterAttending(7, 10, 3);
        assertEquals(76, newPercentage);

        // Start at 50% (5/10), attend 5 more classes
        // New: 10/15 = 66.67% => 66% (floor)
        newPercentage = AttendanceCalculator.calculateAfterAttending(5, 10, 5);
        assertEquals(66, newPercentage);
    }

    @Test
    public void testCalculateAfterAttending_ImprovesPercentage() {
        // Verify that attending classes always improves or maintains percentage
        int currentPercentage = AttendanceCalculator.calculateAttendancePercentage(6, 10);
        int newPercentage = AttendanceCalculator.calculateAfterAttending(6, 10, 5);
        assertTrue(newPercentage >= currentPercentage);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateAfterAttending_Negative() {
        AttendanceCalculator.calculateAfterAttending(8, 10, -1);
    }

    @Test
    public void testRealWorldScenario_VIT_75Percent() {
        // VIT requires 75% attendance
        // Student has 60% (18/30 classes)
        int current = AttendanceCalculator.calculateAttendancePercentage(18, 30);
        assertEquals(60, current);

        // How many consecutive classes to attend to reach 75%?
        int needed = AttendanceCalculator.calculateClassesToReachTarget(18, 30, 75);
        assertTrue(needed > 0);

        // Verify that attending those classes reaches the target
        int afterAttending = AttendanceCalculator.calculateAfterAttending(18, 30, needed);
        assertTrue(afterAttending >= 75);
    }

    @Test
    public void testRealWorldScenario_Maintaining75Percent() {
        // Student has 80% (24/30 classes)
        // How many can they miss while staying above 75%?
        int canMiss = AttendanceCalculator.calculateMaxClassesToMiss(24, 30, 75);

        // Verify that missing that many classes keeps them above 75%
        if (canMiss > 0) {
            int afterMissing = AttendanceCalculator.calculateAfterMissing(24, 30, canMiss);
            assertTrue(afterMissing >= 75);
        }

        // Verify that missing one more would drop below 75%
        int afterMissingMore = AttendanceCalculator.calculateAfterMissing(24, 30, canMiss + 1);
        assertTrue(afterMissingMore < 75 || canMiss == 0);
    }
}
