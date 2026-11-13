package com.salmanmalvasi.studentcc.helpers;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Derives display totals from the same assessment snapshot that is persisted by sync. */
public final class CumulativeMarksCalculator {
    private CumulativeMarksCalculator() {}

    public static void normalizeWeightages(Collection<Mark> marks) {
        for (Mark mark : marks) {
            if (mark.weightage == null && valid(mark.score) && valid(mark.maxScore) && mark.maxScore > 0
                    && valid(mark.maxWeightage) && mark.maxWeightage > 0
                    && mark.score >= 0 && mark.score <= mark.maxScore + 0.01) {
                mark.weightage = mark.score * mark.maxWeightage / mark.maxScore;
            }
        }
    }

    /** Keep assessments missing from a partial response only within the same semester and class. */
    public static List<Mark> mergeMarks(Collection<Mark> incoming, Collection<Mark> cached, List<Course> courses) {
        return mergeMarks(incoming, cached, courses, courses);
    }

    public static List<Mark> mergeMarks(Collection<Mark> incoming, Collection<Mark> cached,
                                        List<Course> courses, List<Course> previousCourses) {
        Map<Integer, Course> byId = new HashMap<>();
        for (Course course : courses) byId.put(course.id, course);
        Map<String, Mark> snapshot = new LinkedHashMap<>();
        for (Mark mark : relinkCachedMarks(cached, previousCourses, courses)) snapshot.put(assessmentKey(mark), mark);
        for (Mark mark : incoming) {
            if (mark.courseId != null && byId.containsKey(mark.courseId)) snapshot.put(assessmentKey(mark), mark);
        }
        List<Mark> result = new ArrayList<>(snapshot.values());
        normalizeWeightages(result);
        for (Mark mark : result) mark.id = 0;
        return result;
    }

    /** Course IDs may be reused or reordered; cache ownership follows the actual class identity. */
    public static List<Mark> relinkCachedMarks(Collection<Mark> cached, List<Course> previousCourses,
                                              List<Course> currentCourses) {
        Map<Integer, Course> previousById = new HashMap<>();
        for (Course course : previousCourses) previousById.put(course.id, course);
        List<Mark> retained = new ArrayList<>();
        for (Mark mark : cached) {
            Course previous = mark.courseId == null ? null : previousById.get(mark.courseId);
            if (previous == null) continue;
            Course matched = null;
            boolean ambiguous = false;
            for (Course current : currentCourses) {
                if (!sameCourseIdentity(previous, current)) continue;
                if (matched != null) { ambiguous = true; break; }
                matched = current;
            }
            if (matched == null || ambiguous) continue;
            Mark copy = new Mark();
            copy.courseId = matched.id; copy.title = mark.title; copy.score = mark.score; copy.maxScore = mark.maxScore;
            copy.weightage = mark.weightage; copy.maxWeightage = mark.maxWeightage; copy.average = mark.average;
            copy.status = mark.status; copy.isRead = mark.isRead; copy.signature = mark.signature;
            retained.add(copy);
        }
        return retained;
    }

    public static List<CumulativeMark> retainTotalsForCourses(List<CumulativeMark> cached,
                                                             List<Course> currentCourses, List<Course> previousCourses) {
        List<CumulativeMark> retained = new ArrayList<>();
        for (CumulativeMark total : cached) {
            if (total.courseCode != null && sameCourseGroup(total.courseCode, currentCourses, previousCourses)) retained.add(total);
        }
        return retained;
    }

    public static List<CumulativeMark> calculate(List<Course> courses, Collection<Mark> marks) {
        Map<Integer, Course> byId = new HashMap<>();
        for (Course course : courses) byId.put(course.id, course);
        Map<String, Map<Integer, Component>> groups = new LinkedHashMap<>();
        Map<String, String> courseCodes = new HashMap<>();
        for (Mark mark : marks) {
            Course course = mark.courseId == null ? null : byId.get(mark.courseId);
            if (course == null || course.code == null || course.code.trim().isEmpty()) continue;
            if (!valid(mark.maxWeightage) || mark.maxWeightage <= 0) continue;
            String code = course.code.trim().toUpperCase(Locale.ROOT);
            courseCodes.put(code, course.code.trim());
            int type = componentType(course.type);
            Component component = groups.computeIfAbsent(code, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(type, ignored -> new Component());
            component.maximum += mark.maxWeightage;
            component.credits = course.credits;
            if (valid(mark.weightage) && mark.weightage >= 0 && mark.weightage <= mark.maxWeightage + 0.01) {
                component.total += mark.weightage;
                component.recorded = true;
            }
        }
        List<CumulativeMark> result = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Component>> entry : groups.entrySet()) {
            CumulativeMark cumulative = new CumulativeMark(0);
            cumulative.courseCode = courseCodes.get(entry.getKey());
            double weightedTotal = 0, weightedMaximum = 0, credits = 0;
            double total = 0, maximum = 0;
            int recordedComponents = 0;
            boolean allCreditsKnown = true;
            for (Map.Entry<Integer, Component> part : entry.getValue().entrySet()) {
                Component component = part.getValue();
                if (!component.recorded) continue;
                setComponent(cumulative, part.getKey(), component);
                total += component.total;
                maximum += component.maximum;
                recordedComponents++;
                if (!valid(component.credits) || component.credits <= 0) allCreditsKnown = false;
                else {
                    credits += component.credits;
                    weightedTotal += component.total * component.credits;
                    weightedMaximum += component.maximum * component.credits;
                }
            }
            if (recordedComponents > 0) {
                cumulative.grandTotal = allCreditsKnown ? weightedTotal / credits : total / recordedComponents;
                cumulative.grandMax = allCreditsKnown ? weightedMaximum / credits : maximum / recordedComponents;
            }
            result.add(cumulative);
        }
        return result;
    }

    public static List<CumulativeMark> mergeTotals(List<CumulativeMark> incoming, List<CumulativeMark> cached,
                                                  List<Course> currentCourses, List<Course> previousCourses) {
        List<CumulativeMark> active = new ArrayList<>();
        for (CumulativeMark total : incoming) {
            if (total.courseCode == null) continue;
            for (Course course : currentCourses) {
                if (codeKey(total.courseCode).equals(codeKey(course.code))) { active.add(total); break; }
            }
        }
        return mergeTotals(active, retainTotalsForCourses(cached, currentCourses, previousCourses));
    }

    /** Preserve released grades and courses omitted by a partial marks response. */
    public static List<CumulativeMark> mergeTotals(List<CumulativeMark> incoming, List<CumulativeMark> cached) {
        Map<String, CumulativeMark> merged = new LinkedHashMap<>();
        for (CumulativeMark total : cached) if (total.courseCode != null) merged.put(codeKey(total.courseCode), total);
        for (CumulativeMark total : incoming) {
            if (total.courseCode == null) continue;
            CumulativeMark previous = merged.get(codeKey(total.courseCode));
            if ((total.grade == null || total.grade.trim().isEmpty()) && previous != null) total.grade = previous.grade;
            merged.put(codeKey(total.courseCode), total);
        }
        List<CumulativeMark> result = new ArrayList<>(merged.values());
        for (CumulativeMark total : result) total.id = 0;
        return result;
    }

    private static String assessmentKey(Mark mark) {
        return mark.courseId + "\n" + (mark.title == null ? "" : mark.title.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT));
    }
    private static String codeKey(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }
    private static boolean sameCourseIdentity(Course first, Course second) {
        return !codeKey(first.code).isEmpty() && codeKey(first.code).equals(codeKey(second.code))
                && componentType(first.type) == componentType(second.type)
                && !differentKnown(first.semesterId, second.semesterId)
                && !differentKnown(first.vtopClassId, second.vtopClassId);
    }
    private static boolean differentKnown(String first, String second) {
        return !codeKey(first).isEmpty() && !codeKey(second).isEmpty() && !codeKey(first).equals(codeKey(second));
    }
    private static boolean sameCourseGroup(String code, List<Course> currentCourses, List<Course> previousCourses) {
        List<Course> current = new ArrayList<>(), previous = new ArrayList<>();
        for (Course course : currentCourses) if (codeKey(code).equals(codeKey(course.code))) current.add(course);
        for (Course course : previousCourses) if (codeKey(code).equals(codeKey(course.code))) previous.add(course);
        if (current.isEmpty() || current.size() != previous.size()) return false;
        for (Course candidate : current) {
            int matches = 0;
            for (Course old : previous) if (sameCourseIdentity(candidate, old)) matches++;
            if (matches != 1) return false;
        }
        return true;
    }
    private static int componentType(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return value.contains("lab") ? Course.TYPE_LAB : value.contains("project") ? Course.TYPE_PROJECT : Course.TYPE_THEORY;
    }
    private static boolean valid(Double value) { return value != null && Double.isFinite(value); }
    private static final class Component {
        double total, maximum;
        Double credits;
        boolean recorded;
    }
    private static void setComponent(CumulativeMark cumulative, int type, Component component) {
        if (type == Course.TYPE_LAB) { cumulative.labTotal = component.total; cumulative.labMax = component.maximum; }
        else if (type == Course.TYPE_PROJECT) { cumulative.projectTotal = component.total; cumulative.projectMax = component.maximum; }
        else { cumulative.theoryTotal = component.total; cumulative.theoryMax = component.maximum; }
    }
}
