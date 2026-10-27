package com.salmanmalvasi.studentcc.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AllSemestersAnalytics implements Serializable {
    public int totalAttendedClasses;
    public int totalConductedClasses;
    public float overallAttendancePercentage;
    public float overallAverageMarks;

    public String highestSubjectCode;
    public String highestSubjectName;
    public double highestSubjectScore;
    public String highestSubjectSemester;

    public String lowestSubjectCode;
    public String lowestSubjectName;
    public double lowestSubjectScore;
    public String lowestSubjectSemester;

    public String lowestAttendanceSemesterName;
    public float lowestAttendancePercentage;

    public String highestAttendanceSemesterName;
    public float highestAttendancePercentage;

    public List<SemesterSummary> semesterSummaries = new ArrayList<>();

    public AllSemestersAnalytics() {
    }
}
