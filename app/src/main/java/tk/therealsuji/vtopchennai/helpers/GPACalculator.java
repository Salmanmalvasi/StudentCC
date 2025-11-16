package tk.therealsuji.vtopchennai.helpers;

import java.util.List;
import tk.therealsuji.vtopchennai.models.GPACourse;

/**
 * Helper class for GPA and CGPA calculations.
 * Extracted from business logic to improve testability and consistency.
 */
public class GPACalculator {

    /**
     * Calculate semester GPA from a list of courses.
     * Formula: Σ(gradePoints × credits) / Σcredits
     *
     * @param courses List of courses for the semester
     * @return Semester GPA (0.0-10.0), or 0.0 if no courses
     */
    public static double calculateSemesterGPA(List<GPACourse> courses) {
        if (courses == null || courses.isEmpty()) {
            return 0.0;
        }

        double totalGradePoints = 0;
        double totalCredits = 0;

        for (GPACourse course : courses) {
            totalGradePoints += course.getCourseGPA(); // gradePoints * creditHours
            totalCredits += course.getCreditHours();
        }

        if (totalCredits <= 0) {
            return 0.0;
        }

        return totalGradePoints / totalCredits;
    }

    /**
     * Calculate new CGPA after completing a semester.
     * Formula: (currentCGPA × currentCredits + semesterGPA × semesterCredits) / (currentCredits + semesterCredits)
     *
     * @param currentCGPA    Current cumulative GPA
     * @param currentCredits Total credits completed before this semester
     * @param semesterGPA    GPA for the new semester
     * @param semesterCredits Credits for the new semester
     * @return New CGPA
     * @throws IllegalArgumentException if any input is negative or credits are zero
     */
    public static double calculateNewCGPA(double currentCGPA, double currentCredits,
                                          double semesterGPA, double semesterCredits) {
        validateCGPAInputs(currentCGPA, currentCredits, "Current");
        validateCGPAInputs(semesterGPA, semesterCredits, "Semester");

        double totalPoints = (currentCGPA * currentCredits) + (semesterGPA * semesterCredits);
        double totalCredits = currentCredits + semesterCredits;

        if (totalCredits <= 0) {
            return 0.0;
        }

        return totalPoints / totalCredits;
    }

    /**
     * Calculate new CGPA after completing a semester with specific courses.
     * Combines semester GPA calculation and CGPA calculation.
     *
     * @param currentCGPA    Current cumulative GPA
     * @param currentCredits Total credits completed before this semester
     * @param semesterCourses List of courses for the new semester
     * @return New CGPA
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static double calculateNewCGPAWithCourses(double currentCGPA, double currentCredits,
                                                     List<GPACourse> semesterCourses) {
        double semesterGPA = calculateSemesterGPA(semesterCourses);
        double semesterCredits = calculateTotalCredits(semesterCourses);

        return calculateNewCGPA(currentCGPA, currentCredits, semesterGPA, semesterCredits);
    }

    /**
     * Calculate required semester GPA to achieve a target CGPA.
     * Formula: ((targetCGPA × totalCredits) - (currentCGPA × currentCredits)) / semesterCredits
     *
     * @param targetCGPA     Desired CGPA to achieve
     * @param currentCGPA    Current cumulative GPA
     * @param currentCredits Total credits completed so far
     * @param semesterCredits Credits for the upcoming semester
     * @return Required semester GPA, or -1 if target is impossible to achieve
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static double calculateRequiredGPA(double targetCGPA, double currentCGPA,
                                              double currentCredits, double semesterCredits) {
        validateCGPAInputs(targetCGPA, currentCredits, "Target");
        validateCGPAInputs(currentCGPA, currentCredits, "Current");

        if (semesterCredits <= 0) {
            throw new IllegalArgumentException("Semester credits must be greater than 0");
        }

        double totalCredits = currentCredits + semesterCredits;
        double requiredGPA = ((targetCGPA * totalCredits) - (currentCGPA * currentCredits)) / semesterCredits;

        return requiredGPA;
    }

    /**
     * Predict new CGPA based on expected semester GPA.
     * Same as calculateNewCGPA, but with clearer naming for prediction use case.
     *
     * @param currentCGPA    Current cumulative GPA
     * @param currentCredits Total credits completed so far
     * @param expectedGPA    Expected GPA for the upcoming semester
     * @param semesterCredits Credits for the upcoming semester
     * @return Predicted new CGPA
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static double predictCGPA(double currentCGPA, double currentCredits,
                                     double expectedGPA, double semesterCredits) {
        return calculateNewCGPA(currentCGPA, currentCredits, expectedGPA, semesterCredits);
    }

    /**
     * Calculate total credits from a list of courses.
     *
     * @param courses List of courses
     * @return Total credit hours
     */
    public static double calculateTotalCredits(List<GPACourse> courses) {
        if (courses == null || courses.isEmpty()) {
            return 0.0;
        }

        double totalCredits = 0;
        for (GPACourse course : courses) {
            totalCredits += course.getCreditHours();
        }

        return totalCredits;
    }

    /**
     * Check if a required GPA is achievable (between 0 and 10).
     *
     * @param requiredGPA The calculated required GPA
     * @return true if achievable, false otherwise
     */
    public static boolean isGPAAchievable(double requiredGPA) {
        return requiredGPA >= 0 && requiredGPA <= 10.0;
    }

    /**
     * Get a descriptive message about the required GPA.
     *
     * @param requiredGPA The calculated required GPA
     * @return A message describing the achievability
     */
    public static String getRequiredGPAMessage(double requiredGPA) {
        if (requiredGPA < 0) {
            return "Target CGPA is already achievable with current credits";
        } else if (requiredGPA > 10) {
            return "Target CGPA requires GPA > 10 (not possible)";
        } else if (requiredGPA > 9.5) {
            return "Target CGPA requires exceptional performance (GPA > 9.5)";
        } else if (requiredGPA > 8.5) {
            return "Target CGPA requires excellent performance (GPA > 8.5)";
        } else {
            return "Target CGPA is achievable";
        }
    }

    /**
     * Calculate the change in CGPA.
     *
     * @param oldCGPA Old CGPA value
     * @param newCGPA New CGPA value
     * @return Change in CGPA (positive for improvement, negative for decrease)
     */
    public static double calculateCGPAChange(double oldCGPA, double newCGPA) {
        return newCGPA - oldCGPA;
    }

    /**
     * Validate CGPA-related inputs.
     *
     * @param gpa GPA value to validate
     * @param credits Credits to validate
     * @param label Label for error messages
     * @throws IllegalArgumentException if inputs are invalid
     */
    private static void validateCGPAInputs(double gpa, double credits, String label) {
        if (gpa < 0 || gpa > 10) {
            throw new IllegalArgumentException(label + " GPA must be between 0 and 10");
        }
        if (credits < 0) {
            throw new IllegalArgumentException(label + " credits cannot be negative");
        }
    }

    /**
     * Result class for GPA calculations with metadata.
     */
    public static class GPAResult {
        private final double gpa;
        private final double totalCredits;
        private final boolean isValid;
        private final String message;

        public GPAResult(double gpa, double totalCredits, boolean isValid, String message) {
            this.gpa = gpa;
            this.totalCredits = totalCredits;
            this.isValid = isValid;
            this.message = message;
        }

        public double getGpa() {
            return gpa;
        }

        public double getTotalCredits() {
            return totalCredits;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Calculate semester GPA with validation and metadata.
     *
     * @param courses List of courses
     * @return GPAResult with GPA, credits, and validation info
     */
    public static GPAResult calculateSemesterGPAWithValidation(List<GPACourse> courses) {
        if (courses == null || courses.isEmpty()) {
            return new GPAResult(0.0, 0.0, false, "No courses added");
        }

        double gpa = calculateSemesterGPA(courses);
        double credits = calculateTotalCredits(courses);

        return new GPAResult(gpa, credits, true, "Semester GPA calculated successfully");
    }

    /**
     * Calculate required GPA with validation and metadata.
     *
     * @param targetCGPA Target CGPA
     * @param currentCGPA Current CGPA
     * @param currentCredits Current total credits
     * @param semesterCredits Semester credits
     * @return GPAResult with required GPA and achievability info
     */
    public static GPAResult calculateRequiredGPAWithValidation(double targetCGPA, double currentCGPA,
                                                               double currentCredits, double semesterCredits) {
        try {
            double requiredGPA = calculateRequiredGPA(targetCGPA, currentCGPA, currentCredits, semesterCredits);
            boolean achievable = isGPAAchievable(requiredGPA);
            String message = getRequiredGPAMessage(requiredGPA);

            return new GPAResult(requiredGPA, currentCredits + semesterCredits, achievable, message);
        } catch (IllegalArgumentException e) {
            return new GPAResult(0.0, 0.0, false, e.getMessage());
        }
    }
}
