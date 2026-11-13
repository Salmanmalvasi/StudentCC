package com.salmanmalvasi.studentcc.integrations.studentcc;

import com.salmanmalvasi.studentcc.helpers.MarksCourseSummary;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds complete /60 internal and /100 final totals into separate class pools. */
public final class StudentCCContributionBuilder {
    /** Counts explain an empty queue without exposing any student's marks in diagnostics. */
    public static final class BuildResult {
        public final List<StudentCCContribution> contributions;
        public final int activeCourses, supportedCourses, completeTotals, missingMetadataTotals;
        BuildResult(List<StudentCCContribution> contributions, int activeCourses, int supportedCourses,
                    int completeTotals, int missingMetadataTotals) {
            this.contributions = contributions; this.activeCourses = activeCourses; this.supportedCourses = supportedCourses;
            this.completeTotals = completeTotals; this.missingMetadataTotals = missingMetadataTotals;
        }
        String emptyMessage() {
            if (activeCourses == 0) return "Run a full VTOP sync to load the selected semester’s classes before sharing.";
            if (supportedCourses == 0) return "There are no supported theory course totals to share for this semester.";
            if (missingMetadataTotals > 0) return "Complete totals are available, but their VTOP class or semester information is missing. Run a full VTOP sync, then retry sharing.";
            return "No complete totals are ready to share yet. All required internal marks (/60) or final marks (/100) must be released first.";
        }
    }
    private StudentCCContributionBuilder() {}
    public static List<StudentCCContribution> build(List<Course> registeredCourses, List<Mark.AllData> marks, String activeSemester) {
        return inspect(registeredCourses, marks, activeSemester).contributions;
    }
    public static BuildResult inspect(List<Course> registeredCourses, List<Mark.AllData> marks, String activeSemester) {
        List<Course.AllData> courses = new ArrayList<>(); Map<Integer, List<Mark.AllData>> byCourse = new HashMap<>();
        for (Course registered : registeredCourses) {
            if (activeSemester == null || !activeSemester.equals(registered.semesterId)) continue;
            Course.AllData course = new Course.AllData(); course.courseId = registered.id; course.courseCode = registered.code;
            course.courseTitle = registered.title; course.courseType = registered.type; course.credits = registered.credits;
            course.vtopClassId = registered.vtopClassId; course.semesterId = registered.semesterId; course.courseSystem = registered.courseSystem;
            courses.add(course); byCourse.put(course.courseId, new ArrayList<>());
        }
        for (Mark.AllData mark : marks) { List<Mark.AllData> list = byCourse.get(mark.courseId); if (list != null) list.add(mark); }
        Map<String, StudentCCContribution> contributions = new LinkedHashMap<>();
        int supportedCourses = 0, completeTotals = 0, missingMetadataTotals = 0;
        for (Course.AllData course : courses) {
            if (!MarksCourseSummary.isLab(course) && !MarksCourseSummary.isProject(course)
                    && course.courseType != null && course.courseType.toLowerCase(java.util.Locale.ROOT).contains("theory")) supportedCourses++;
            for (int basis : new int[] {60, 100}) {
                Double total = basis == 60 ? MarksCourseSummary.completeInternalScore(course, courses, byCourse)
                        : MarksCourseSummary.completeFinalScore(course, courses, byCourse);
                if (total == null) continue;
                completeTotals++;
                try {
                    StudentCCContribution contribution = new StudentCCContribution(StudentCCClassScope.fromCourse(course, basis), course.courseCode, total);
                    contributions.put(contribution.key(), contribution);
                } catch (IllegalArgumentException ignored) { missingMetadataTotals++; }
            }
        }
        return new BuildResult(new ArrayList<>(contributions.values()), courses.size(), supportedCourses,
                completeTotals, missingMetadataTotals);
    }
}
