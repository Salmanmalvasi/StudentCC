package com.salmanmalvasi.studentcc.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SemesterSummary implements Serializable {
    public String semesterId;
    public String semesterName;
    public int attendedClasses;
    public int totalClasses;
    public float attendancePercentage;
    public float averageMarksPercentage;
    public int courseCount;
    public String gpa;
    public boolean isCompleted;

    public String highestSubjectCode;
    public String highestSubjectName;
    public double highestSubjectScore;

    public String lowestSubjectCode;
    public String lowestSubjectName;
    public double lowestSubjectScore;

    public List<SubjectStat> subjects = new ArrayList<>();
    public long syncTimestamp;

    public SemesterSummary() {
    }

    public SemesterSummary(String semesterId, String semesterName) {
        this.semesterId = semesterId;
        this.semesterName = semesterName;
        this.syncTimestamp = System.currentTimeMillis();
    }

    public static class SubjectStat implements Serializable {
        public String courseCode;
        public String courseTitle;
        public String courseType; // Theory, Lab, Project
        public int attended;
        public int total;
        public float attendancePercentage;
        public double marksScore;
        public double marksMax;
        public float marksPercentage;
        public String grade;
        public int credits;

        public SubjectStat() {
        }

        public SubjectStat(String courseCode, String courseTitle, float attendancePercentage, float marksPercentage) {
            this.courseCode = courseCode;
            this.courseTitle = courseTitle;
            this.attendancePercentage = attendancePercentage;
            this.marksPercentage = marksPercentage;
        }
    }
}
