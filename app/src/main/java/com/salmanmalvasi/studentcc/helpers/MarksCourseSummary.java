package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Keeps complete internal and final totals distinct from partial display totals. */
public final class MarksCourseSummary {
    private static final Pattern FINAL_ASSESSMENT = Pattern.compile(
            "\\b(?:FAT|FINAL\\s+(?:ASSESSMENT|EXAM))\\b", Pattern.CASE_INSENSITIVE);
    public final double score;
    public final double maximum;
    public final int recordedAssessments;
    public final int assessments;
    public final int unreadAssessments;
    public final boolean complete;

    private MarksCourseSummary(double score, double maximum, int recorded, int assessments,
                               int unread, boolean complete) {
        this.score = score;
        this.maximum = maximum;
        this.recordedAssessments = recorded;
        this.assessments = assessments;
        this.unreadAssessments = unread;
        this.complete = complete;
    }

    public static MarksCourseSummary from(List<Mark.AllData> marks) {
        double total = 0;
        double maximum = 0;
        int recorded = 0;
        int unread = 0;
        boolean complete = !marks.isEmpty();
        for (Mark.AllData mark : marks) {
            if (Boolean.FALSE.equals(mark.isRead)) unread++;
            if (valid(mark.maxWeightage) && mark.maxWeightage > 0) maximum += mark.maxWeightage;
            boolean validScore = valid(mark.weightage) && valid(mark.maxWeightage)
                    && mark.maxWeightage > 0 && mark.weightage >= 0
                    && mark.weightage <= mark.maxWeightage + 0.01;
            if (validScore) {
                total += mark.weightage;
                recorded++;
            } else {
                complete = false;
            }
        }
        return new MarksCourseSummary(total, maximum, recorded, marks.size(), unread, complete);
    }

    public static boolean isLab(Course.AllData course) {
        return type(course).contains("lab");
    }

    public static boolean isProject(Course.AllData course) {
        return type(course).contains("project");
    }

    public static boolean isEmbedded(Course.AllData course, List<Course.AllData> courses) {
        if (course == null) return false;
        if (type(course).contains("embedded")) return true;
        for (Course.AllData other : courses) {
            if (isLab(other) && sameCode(course.courseCode, other.courseCode) && sameSemester(course, other)) return true;
        }
        return false;
    }

    /** Returns a rounded /60 value only when the same component rules as UniCC apply. */
    public static Double comparableInternalScore(Course.AllData course, List<Course.AllData> courses,
                                                 Map<Integer, List<Mark.AllData>> marks) {
        Double score = completeInternalScore(course, courses, marks);
        return score == null ? null : Math.ceil(score);
    }

    /** StudentCC keeps fractional totals; each pool must compare against its own rounding basis. */
    public static Double completeInternalScore(Course.AllData course, List<Course.AllData> courses,
                                               Map<Integer, List<Mark.AllData>> marks) {
        return completeScore(course, courses, marks, 60);
    }

    public static Double completeFinalScore(Course.AllData course, List<Course.AllData> courses,
                                            Map<Integer, List<Mark.AllData>> marks) {
        return completeScore(course, courses, marks, 100);
    }

    /** Unrounded internal total for one component, before theory/lab credit weighting. */
    public static Double completeComponentInternalScore(List<Mark.AllData> marks) {
        if (marks == null) return null;
        MarksCourseSummary summary = onBasis(marks, 60);
        return isCompleteOnBasis(summary, 60) ? summary.score : null;
    }

    private static MarksCourseSummary onBasis(List<Mark.AllData> marks, int basis) {
        if (basis == 100) return from(marks);
        List<Mark.AllData> internal = new ArrayList<>();
        for (Mark.AllData mark : marks) {
            String title = mark.title == null ? "" : mark.title;
            if (!FINAL_ASSESSMENT.matcher(title).find()) internal.add(mark);
        }
        return from(internal);
    }

    private static Double completeScore(Course.AllData course, List<Course.AllData> courses,
                                         Map<Integer, List<Mark.AllData>> marks, int basis) {
        if (course == null || course.courseId == null || isLab(course) || isProject(course)
                || !type(course).contains("theory")) return null;
        MarksCourseSummary theory = onBasis(marks.getOrDefault(course.courseId, Collections.emptyList()), basis);
        if (!isCompleteOnBasis(theory, basis)) return null;

        Course.AllData lab = null;
        for (Course.AllData other : courses) {
            if (isLab(other) && sameCode(course.courseCode, other.courseCode)
                    && sameSemester(course, other)) {
                if (lab != null) return null; // Ambiguous component pairing.
                lab = other;
            }
        }
        // ACE identifies a course system, not whether this course has a lab component.
        // Older caches retain only "theory"; require actual component metadata before pairing.
        boolean embedded = type(course).contains("embedded") || lab != null;
        if (!embedded) return theory.score;
        if (lab == null || lab.courseId == null || !"ACE".equalsIgnoreCase(course.courseSystem)
                || !"ACE".equalsIgnoreCase(lab.courseSystem)
                || !differentClasses(course, lab)
                || !valid(course.credits) || course.credits <= 0
                || !valid(lab.credits) || lab.credits <= 0) return null;
        MarksCourseSummary labSummary = onBasis(marks.getOrDefault(lab.courseId, Collections.emptyList()), basis);
        if (!isCompleteOnBasis(labSummary, basis)) return null;
        return (theory.score * course.credits + labSummary.score * lab.credits)
                / (course.credits + lab.credits);
    }

    private static boolean isCompleteOnBasis(MarksCourseSummary summary, int basis) {
        return summary.complete && Math.abs(summary.maximum - basis) <= 0.01;
    }

    private static String type(Course.AllData course) {
        return course == null || course.courseType == null ? ""
                : course.courseType.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameCode(String first, String second) {
        return first != null && second != null && normalizedCode(first).equals(normalizedCode(second));
    }

    private static boolean sameSemester(Course.AllData first, Course.AllData second) {
        return first.semesterId != null && !first.semesterId.trim().isEmpty()
                && second.semesterId != null && first.semesterId.trim().equals(second.semesterId.trim());
    }

    private static boolean differentClasses(Course.AllData theory, Course.AllData lab) {
        return theory.vtopClassId != null && !theory.vtopClassId.trim().isEmpty()
                && lab.vtopClassId != null && !lab.vtopClassId.trim().isEmpty()
                && !theory.vtopClassId.trim().equals(lab.vtopClassId.trim());
    }

    private static String normalizedCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("\\([TLP]\\)$", "");
    }

    private static boolean valid(Double value) {
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
