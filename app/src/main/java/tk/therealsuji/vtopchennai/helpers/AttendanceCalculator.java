package tk.therealsuji.vtopchennai.helpers;

/**
 * Helper class for attendance-related calculations.
 * Extracted from business logic to improve testability and consistency.
 */
public class AttendanceCalculator {

    /**
     * Calculate attendance percentage using floor rounding.
     * This ensures consistent behavior across the app:
     * - 9.99% becomes 9%
     * - 75.5% becomes 75%
     * - 100.0% stays 100%
     *
     * @param attended Number of classes attended
     * @param total    Total number of classes
     * @return Attendance percentage (0-100) or 0 if total is 0
     */
    public static int calculateAttendancePercentage(int attended, int total) {
        if (total == 0) {
            return 0;
        }

        if (attended < 0 || total < 0) {
            throw new IllegalArgumentException("Attended and total must be non-negative");
        }

        double percentage = (attended * 100.0) / total;
        return (int) Math.floor(percentage);
    }

    /**
     * Calculate the minimum number of classes needed to achieve target percentage.
     *
     * @param currentAttended Current number of classes attended
     * @param currentTotal    Current total number of classes
     * @param targetPercentage Target attendance percentage (0-100)
     * @return Number of consecutive classes to attend to reach target, or -1 if already achieved
     */
    public static int calculateClassesToReachTarget(int currentAttended, int currentTotal, int targetPercentage) {
        if (targetPercentage < 0 || targetPercentage > 100) {
            throw new IllegalArgumentException("Target percentage must be between 0 and 100");
        }

        int currentPercentage = calculateAttendancePercentage(currentAttended, currentTotal);
        if (currentPercentage >= targetPercentage) {
            return 0; // Already at or above target
        }

        // Calculate classes needed: (attended + x) / (total + x) >= target/100
        // Solving: attended + x >= (target/100) * (total + x)
        // x >= (target * total - 100 * attended) / (100 - target)

        if (targetPercentage == 100) {
            // Special case: need perfect attendance from now on
            int missedClasses = currentTotal - currentAttended;
            if (missedClasses == 0) {
                return 0; // Already at 100%
            }
            return -1; // Impossible to reach 100% if any classes were missed
        }

        double numerator = (targetPercentage * currentTotal) - (100.0 * currentAttended);
        double denominator = 100.0 - targetPercentage;

        if (denominator == 0) {
            return -1; // Cannot reach 100% if classes were missed
        }

        int classesNeeded = (int) Math.ceil(numerator / denominator);
        return Math.max(0, classesNeeded);
    }

    /**
     * Calculate how many classes can be missed while staying above minimum percentage.
     *
     * @param currentAttended  Current number of classes attended
     * @param currentTotal     Current total number of classes
     * @param minimumPercentage Minimum acceptable attendance percentage
     * @return Maximum number of classes that can be missed
     */
    public static int calculateMaxClassesToMiss(int currentAttended, int currentTotal, int minimumPercentage) {
        if (minimumPercentage < 0 || minimumPercentage > 100) {
            throw new IllegalArgumentException("Minimum percentage must be between 0 and 100");
        }

        int currentPercentage = calculateAttendancePercentage(currentAttended, currentTotal);
        if (currentPercentage < minimumPercentage) {
            return 0; // Already below minimum
        }

        // Calculate: (attended) / (total + x) >= minimum/100
        // Solving: attended >= (minimum/100) * (total + x)
        // x <= (100 * attended / minimum) - total

        if (minimumPercentage == 0) {
            return Integer.MAX_VALUE; // Can miss all remaining classes
        }

        double maxTotal = (100.0 * currentAttended) / minimumPercentage;
        int canMiss = (int) Math.floor(maxTotal - currentTotal);

        return Math.max(0, canMiss);
    }

    /**
     * Calculate new attendance after simulating missed classes.
     *
     * @param currentAttended Current number of classes attended
     * @param currentTotal    Current total number of classes
     * @param missedClasses   Number of additional classes to simulate missing
     * @return New attendance percentage
     */
    public static int calculateAfterMissing(int currentAttended, int currentTotal, int missedClasses) {
        if (missedClasses < 0) {
            throw new IllegalArgumentException("Missed classes cannot be negative");
        }

        int newTotal = currentTotal + missedClasses;
        return calculateAttendancePercentage(currentAttended, newTotal);
    }

    /**
     * Calculate new attendance after simulating attended classes.
     *
     * @param currentAttended Current number of classes attended
     * @param currentTotal    Current total number of classes
     * @param additionalAttended Number of additional classes to simulate attending
     * @return New attendance percentage
     */
    public static int calculateAfterAttending(int currentAttended, int currentTotal, int additionalAttended) {
        if (additionalAttended < 0) {
            throw new IllegalArgumentException("Additional attended classes cannot be negative");
        }

        int newAttended = currentAttended + additionalAttended;
        int newTotal = currentTotal + additionalAttended;
        return calculateAttendancePercentage(newAttended, newTotal);
    }
}
