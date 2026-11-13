package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Reading one version of an assessment never acknowledges a later change to its marks. */
public final class MarksReadState {
    private MarksReadState() {}

    public static void preserve(List<Mark> incoming, List<Mark> existing) {
        preserve(incoming, existing, null, null);
    }

    public static void preserve(List<Mark> incoming, List<Mark> existing,
                                List<Course> incomingCourses, List<Course> existingCourses) {
        Map<Integer, Course> freshCourses = byId(incomingCourses), oldCourses = byId(existingCourses);
        for (Mark fresh : incoming) {
            Mark previous = null;
            boolean ambiguous = false;
            for (Mark old : existing) {
                if (assessmentMatches(fresh, old, freshCourses, oldCourses)) {
                    if (previous != null) { ambiguous = true; break; }
                    previous = old;
                }
            }
            fresh.isRead = !ambiguous && previous != null && Boolean.TRUE.equals(previous.isRead)
                    && sameValues(fresh, previous);
        }
    }

    private static boolean assessmentMatches(Mark fresh, Mark old, Map<Integer, Course> freshCourses,
                                             Map<Integer, Course> oldCourses) {
        if (!clean(fresh.title).equals(clean(old.title)) || fresh.courseId == null || old.courseId == null) return false;
        Course freshCourse = freshCourses.get(fresh.courseId), oldCourse = oldCourses.get(old.courseId);
        if (freshCourse == null || oldCourse == null) return fresh.courseId.equals(old.courseId);
        if (!clean(freshCourse.code).equals(clean(oldCourse.code))
                || !component(freshCourse.type).equals(component(oldCourse.type))
                || differentKnown(freshCourse.semesterId, oldCourse.semesterId)
                || differentKnown(freshCourse.vtopClassId, oldCourse.vtopClassId)) return false;
        if (fresh.courseId.equals(old.courseId)) return true;
        // Course IDs can change when parsers gain better type metadata; a real class ID remains stable.
        return !clean(freshCourse.vtopClassId).isEmpty() && !clean(oldCourse.vtopClassId).isEmpty();
    }

    private static boolean sameValues(Mark fresh, Mark old) {
        return sameNumber(fresh.score, old.score) && sameNumber(fresh.maxScore, old.maxScore)
                && sameNumber(fresh.weightage, old.weightage) && sameNumber(fresh.maxWeightage, old.maxWeightage)
                && clean(fresh.status).equals(clean(old.status));
    }

    private static boolean sameNumber(Double first, Double second) {
        return Objects.equals(first, second) || (first != null && second != null && first.doubleValue() == second.doubleValue());
    }

    private static boolean differentKnown(String first, String second) {
        return !clean(first).isEmpty() && !clean(second).isEmpty() && !clean(first).equals(clean(second));
    }

    private static String component(String type) {
        return clean(type).contains("LAB") ? "lab" : clean(type).contains("PROJECT") ? "project" : "theory";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static Map<Integer, Course> byId(List<Course> courses) {
        Map<Integer, Course> result = new HashMap<>();
        if (courses != null) for (Course course : courses) result.put(course.id, course);
        return result;
    }
}
