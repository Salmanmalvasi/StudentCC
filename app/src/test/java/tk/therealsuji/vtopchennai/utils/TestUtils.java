package tk.therealsuji.vtopchennai.utils;

import java.util.ArrayList;
import java.util.List;

import tk.therealsuji.vtopchennai.models.GPACourse;

/**
 * Utility class for creating test data.
 * Provides reusable helper methods for test classes.
 */
public class TestUtils {

    /**
     * Create a GPACourse with specified parameters.
     */
    public static GPACourse createGPACourse(double credits, String grade) {
        return new GPACourse(credits, grade);
    }

    /**
     * Create a list of GPACourses for testing.
     */
    public static List<GPACourse> createCourseList(double[][] creditsAndGrades) {
        List<GPACourse> courses = new ArrayList<>();
        for (double[] data : creditsAndGrades) {
            double credits = data[0];
            int gradeIndex = (int) data[1];
            String[] grades = {"S", "A", "B", "C", "D", "E", "F"};
            String grade = grades[Math.min(gradeIndex, grades.length - 1)];
            courses.add(new GPACourse(credits, grade));
        }
        return courses;
    }

    /**
     * Create a typical VIT semester with realistic courses.
     *
     * @return List of 6 courses representing a typical semester
     */
    public static List<GPACourse> createTypicalVITSemester() {
        List<GPACourse> courses = new ArrayList<>();
        courses.add(new GPACourse(4.0, "A")); // Theory course 1
        courses.add(new GPACourse(4.0, "B")); // Theory course 2
        courses.add(new GPACourse(3.0, "A")); // Theory course 3
        courses.add(new GPACourse(3.0, "S")); // Theory course 4
        courses.add(new GPACourse(2.0, "B")); // Lab course 1
        courses.add(new GPACourse(1.0, "S")); // Lab course 2
        return courses;
    }

    /**
     * Create a high-performing semester (all S and A grades).
     */
    public static List<GPACourse> createHighPerformingSemester() {
        List<GPACourse> courses = new ArrayList<>();
        courses.add(new GPACourse(4.0, "S"));
        courses.add(new GPACourse(4.0, "S"));
        courses.add(new GPACourse(3.0, "A"));
        courses.add(new GPACourse(3.0, "S"));
        courses.add(new GPACourse(2.0, "A"));
        return courses;
    }

    /**
     * Create a struggling semester (mix of low grades including F).
     */
    public static List<GPACourse> createStruggingSemester() {
        List<GPACourse> courses = new ArrayList<>();
        courses.add(new GPACourse(4.0, "C"));
        courses.add(new GPACourse(4.0, "D"));
        courses.add(new GPACourse(3.0, "E"));
        courses.add(new GPACourse(3.0, "F"));
        courses.add(new GPACourse(2.0, "D"));
        return courses;
    }

    /**
     * Assert that two double values are equal within a tolerance.
     *
     * @param expected Expected value
     * @param actual   Actual value
     * @param delta    Tolerance for comparison
     * @return true if values are equal within tolerance
     */
    public static boolean assertEqualsWithTolerance(double expected, double actual, double delta) {
        return Math.abs(expected - actual) <= delta;
    }

    /**
     * Calculate expected semester GPA manually for verification.
     */
    public static double calculateExpectedGPA(List<GPACourse> courses) {
        double totalGradePoints = 0;
        double totalCredits = 0;

        for (GPACourse course : courses) {
            totalGradePoints += course.getCourseGPA();
            totalCredits += course.getCreditHours();
        }

        return totalCredits > 0 ? totalGradePoints / totalCredits : 0.0;
    }

    /**
     * Get grade points for a grade letter.
     */
    public static double getGradePoints(String grade) {
        switch (grade) {
            case "S": return 10.0;
            case "A": return 9.0;
            case "B": return 8.0;
            case "C": return 7.0;
            case "D": return 6.0;
            case "E": return 5.0;
            case "F": return 0.0;
            default: return 0.0;
        }
    }

    /**
     * Verify attendance percentage calculation.
     */
    public static int calculateAttendancePercentage(int attended, int total) {
        if (total == 0) return 0;
        return (int) Math.floor((attended * 100.0) / total);
    }

    /**
     * Create test data for attendance scenarios.
     */
    public static class AttendanceScenario {
        public final int attended;
        public final int total;
        public final int expectedPercentage;

        public AttendanceScenario(int attended, int total, int expectedPercentage) {
            this.attended = attended;
            this.total = total;
            this.expectedPercentage = expectedPercentage;
        }

        public static AttendanceScenario[] getStandardScenarios() {
            return new AttendanceScenario[]{
                    new AttendanceScenario(0, 10, 0),      // 0%
                    new AttendanceScenario(5, 10, 50),     // 50%
                    new AttendanceScenario(7, 10, 70),     // 70%
                    new AttendanceScenario(8, 10, 80),     // 80%
                    new AttendanceScenario(9, 10, 90),     // 90%
                    new AttendanceScenario(10, 10, 100),   // 100%
                    new AttendanceScenario(15, 20, 75),    // VIT requirement
                    new AttendanceScenario(9, 100, 9),     // Floor test
                    new AttendanceScenario(99, 1000, 9),   // Floor test
                    new AttendanceScenario(6, 7, 85)       // 85.71% -> 85%
            };
        }
    }

    /**
     * Test data for GPA scenarios.
     */
    public static class GPAScenario {
        public final double currentCGPA;
        public final double currentCredits;
        public final double targetCGPA;
        public final double semesterCredits;
        public final boolean isAchievable;

        public GPAScenario(double currentCGPA, double currentCredits,
                           double targetCGPA, double semesterCredits, boolean isAchievable) {
            this.currentCGPA = currentCGPA;
            this.currentCredits = currentCredits;
            this.targetCGPA = targetCGPA;
            this.semesterCredits = semesterCredits;
            this.isAchievable = isAchievable;
        }

        public static GPAScenario[] getStandardScenarios() {
            return new GPAScenario[]{
                    // Achievable targets
                    new GPAScenario(7.0, 60, 7.5, 20, true),   // Moderate improvement
                    new GPAScenario(8.0, 80, 8.5, 20, false),  // Difficult but realistic
                    new GPAScenario(6.0, 40, 7.0, 20, true),   // Good improvement

                    // Impossible targets
                    new GPAScenario(6.0, 100, 9.0, 20, false), // Requires >10 GPA
                    new GPAScenario(5.0, 120, 8.0, 20, false), // Not enough credits

                    // Easy targets (already achieved)
                    new GPAScenario(9.0, 80, 8.5, 20, true),   // Already there
                    new GPAScenario(8.5, 100, 8.0, 20, true)   // Already there
            };
        }
    }

    /**
     * Common test constants.
     */
    public static class Constants {
        public static final double GPA_DELTA = 0.001;
        public static final double MIN_GPA = 0.0;
        public static final double MAX_GPA = 10.0;
        public static final int VIT_MIN_ATTENDANCE = 75;
        public static final int MAX_PERCENTAGE = 100;
        public static final int MIN_PERCENTAGE = 0;

        // VIT-specific constants
        public static final double TYPICAL_SEMESTER_CREDITS = 17.0;
        public static final int TYPICAL_SEMESTER_COURSES = 6;
        public static final double MIN_CGPA_FOR_PLACEMENTS = 7.0;
        public static final double GOOD_CGPA_FOR_PLACEMENTS = 8.5;
    }

    /**
     * Boundary value test data generator.
     */
    public static class BoundaryValues {
        public static int[] getAttendanceBoundaries() {
            return new int[]{
                    0,      // Minimum
                    1,      // Just above minimum
                    74,     // Just below VIT requirement
                    75,     // VIT requirement
                    76,     // Just above VIT requirement
                    99,     // Just below maximum
                    100     // Maximum
            };
        }

        public static double[] getGPABoundaries() {
            return new double[]{
                    0.0,    // Minimum
                    0.1,    // Just above minimum
                    5.0,    // Pass mark
                    7.0,    // Good
                    8.5,    // Very good
                    9.0,    // Excellent
                    9.9,    // Just below maximum
                    10.0    // Maximum
            };
        }

        public static double[] getCreditBoundaries() {
            return new double[]{
                    0.0,    // Minimum
                    1.0,    // Minimum non-zero
                    2.0,    // Lab course
                    3.0,    // Theory course
                    4.0,    // Major theory course
                    6.0,    // Project course
                    20.0    // Full semester
            };
        }
    }
}
