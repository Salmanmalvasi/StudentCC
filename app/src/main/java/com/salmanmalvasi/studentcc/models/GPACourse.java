package com.salmanmalvasi.studentcc.models;

public class GPACourse {
    private double creditHours;
    private String grade;
    private double gradePoints;
    private String courseCode;
    private String courseTitle;

    public GPACourse(double creditHours, String grade) {
        this.creditHours = creditHours;
        this.grade = grade;
        this.gradePoints = getGradePoints(grade);
    }

    public GPACourse(double creditHours, String grade, String courseCode, String courseTitle) {
        this.creditHours = creditHours;
        this.grade = grade;
        this.gradePoints = getGradePoints(grade);
        this.courseCode = courseCode;
        this.courseTitle = courseTitle;
    }

    public double getCreditHours() {
        return creditHours;
    }

    public void setCreditHours(double creditHours) {
        this.creditHours = creditHours;
    }

    public String getGrade() {
        return grade;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setGrade(String grade) {
        this.grade = grade;
        this.gradePoints = getGradePoints(grade);
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public double getGradePoints() {
        return gradePoints;
    }

    public double getCourseGPA() {
        return gradePoints * creditHours;
    }

    private double getGradePoints(String grade) {
        switch (grade) {
            case "S":
                return 10.0;
            case "A":
                return 9.0;
            case "B":
                return 8.0;
            case "C":
                return 7.0;
            case "D":
                return 6.0;
            case "E":
                return 5.0;
            case "F":
                return 0.0;
            case "N":
                return 0.0; // N grade for No Credit
            default:
                return 0.0;
        }
    }

    public static String[] getAvailableGrades() {
        return new String[]{"S", "A", "B", "C", "D", "E", "F", "N"};
    }

    public static String getGradeDisplayText(String grade) {
        switch (grade) {
            case "S":
                return "S";
            case "A":
                return "A";
            case "B":
                return "B";
            case "C":
                return "C";
            case "D":
                return "D";
            case "E":
                return "E";
            case "F":
                return "F";
            case "N":
                return "N";
            default:
                return grade;
        }
    }
}
