package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks mandatory pass conditions for complete results and explicit internal projections. */
public final class VitGradePassRules {
    private static final Pattern FINAL_ASSESSMENT = Pattern.compile(
            "\\b(?:FAT|FINAL\\s+(?:ASSESSMENT|EXAM))\\b", Pattern.CASE_INSENSITIVE);

    private VitGradePassRules() {}

    /** Checks each component under the stated same-percentage FAT projection, without rounding it. */
    public static VitGradePredictor.PassStatus evaluateInternalProjection(Course.AllData course,
                                                                          List<Course.AllData> courses,
                                                                          Map<Integer, List<Mark.AllData>> marks) {
        if (courses == null || marks == null || course == null || course.courseId == null) {
            return VitGradePredictor.PassStatus.UNKNOWN;
        }
        // Validate the complete internal snapshot and the same pairing used by the displayed total.
        Double combined = MarksCourseSummary.completeInternalScore(course, courses, marks);
        if (combined == null) return VitGradePredictor.PassStatus.UNKNOWN;
        Double theory = MarksCourseSummary.completeComponentInternalScore(marks.get(course.courseId));
        if (theory == null) return VitGradePredictor.PassStatus.UNKNOWN;
        if (theory < 24.0) return VitGradePredictor.PassStatus.FAILED;

        Course.AllData lab = null;
        for (Course.AllData other : courses) {
            if (other != null && MarksCourseSummary.isLab(other) && sameCode(course.courseCode, other.courseCode)
                    && sameSemester(course.semesterId, other.semesterId)) {
                if (lab != null) return VitGradePredictor.PassStatus.UNKNOWN;
                lab = other;
            }
        }
        if (lab != null) {
            Double labInternal = MarksCourseSummary.completeComponentInternalScore(marks.get(lab.courseId));
            if (labInternal == null) return VitGradePredictor.PassStatus.UNKNOWN;
            if (labInternal < 30.0 || Math.ceil(combined * 100.0 / 60.0) < 50.0) {
                return VitGradePredictor.PassStatus.FAILED;
            }
            // Match the final-total rule: an unrounded combined result below 50 remains conditional.
            if (combined * 100.0 / 60.0 < 50.0) return VitGradePredictor.PassStatus.UNKNOWN;
        }
        return VitGradePredictor.PassStatus.PASSED;
    }

    public static VitGradePredictor.PassStatus evaluate(Course.AllData course,
                                                        List<Course.AllData> courses,
                                                        Map<Integer, List<Mark.AllData>> marks) {
        if (courses == null || marks == null || course == null || course.courseId == null) {
            return VitGradePredictor.PassStatus.UNKNOWN;
        }
        // This also validates component pairing, credits, course system and /100 completeness.
        Double combined = MarksCourseSummary.completeFinalScore(course, courses, marks);
        if (combined == null) return VitGradePredictor.PassStatus.UNKNOWN;
        Double fatPercentage = finalAssessmentPercentage(marks.getOrDefault(
                course.courseId, Collections.emptyList()));
        if (fatPercentage == null) return VitGradePredictor.PassStatus.UNKNOWN;
        if (fatPercentage < 40.0) return VitGradePredictor.PassStatus.FAILED;

        Course.AllData lab = null;
        for (Course.AllData other : courses) {
            if (other != null && MarksCourseSummary.isLab(other) && sameCode(course.courseCode, other.courseCode)
                    && sameSemester(course.semesterId, other.semesterId)) {
                if (lab != null) return VitGradePredictor.PassStatus.UNKNOWN;
                lab = other;
            }
        }
        if (lab != null) {
            MarksCourseSummary labSummary = MarksCourseSummary.from(marks.getOrDefault(
                    lab.courseId, Collections.emptyList()));
            if (labSummary.score < 50.0 || Math.ceil(combined) < 50.0) return VitGradePredictor.PassStatus.FAILED;
            // Components stay unrounded, but VIT rounds the grand total for grade bands. The
            // regulations do not clarify whether that rounding rescues this combined pass gate.
            // Keep this narrow edge conditional instead of asserting a pass or failure.
            if (combined < 50.0) return VitGradePredictor.PassStatus.UNKNOWN;
        }
        return VitGradePredictor.PassStatus.PASSED;
    }

    private static Double finalAssessmentPercentage(List<Mark.AllData> marks) {
        Mark.AllData finalAssessment = null;
        for (Mark.AllData mark : marks) {
            if (mark == null || !FINAL_ASSESSMENT.matcher(mark.title == null ? "" : mark.title).find()) continue;
            if (finalAssessment != null) return null; // Distinct FAT rows cannot prove one examination result.
            finalAssessment = mark;
        }
        if (finalAssessment == null) return null;
        // Raw examination marks take priority. Explicit weightage is equivalent when raw marks are absent.
        if (finalAssessment.score != null) {
            return percentage(finalAssessment.score, finalAssessment.maxScore);
        }
        return percentage(finalAssessment.weightage, finalAssessment.maxWeightage);
    }

    private static Double percentage(Double score, Double maximum) {
        if (score == null || maximum == null || !Double.isFinite(score) || !Double.isFinite(maximum)
                || maximum <= 0 || score < 0 || score > maximum) return null;
        return score / maximum * 100.0;
    }

    private static boolean sameCode(String first, String second) {
        return first != null && second != null && normalizeCode(first).equals(normalizeCode(second));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("\\([TLP]\\)$", "");
    }

    private static boolean sameSemester(String first, String second) {
        return first != null && !first.trim().isEmpty() && second != null && first.trim().equals(second.trim());
    }
}
