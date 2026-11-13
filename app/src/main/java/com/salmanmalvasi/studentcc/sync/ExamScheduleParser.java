package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Exam;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the native API and portal timetable without depending on a column order. */
public final class ExamScheduleParser {
    private static final TimeZone CAMPUS_TIME_ZONE = TimeZone.getTimeZone("Asia/Kolkata");
    private static final Pattern COURSE_CODE = Pattern.compile("(?i)\\b[A-Z]{3,5}\\s*\\d{3,5}[A-Z]?\\b");
    private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd", "dd-MMM-yyyy", "dd-MMM-yy", "dd/MM/yyyy", "dd-MM-yyyy",
            "yyyy/MM/dd", "dd MMM yyyy", "dd MMMM yyyy"
    };
    private static final String[] TIME_FORMATS = {"h:mm a", "H:mm", "H:mm:ss", "h.mm a", "h a"};

    private ExamScheduleParser() {}

    public static final class Result {
        public final List<Exam> exams = new ArrayList<>();
        public boolean recognized;
        public int rejectedRows;

        /** Empty, failed and unsupported responses must never erase a cached timetable. */
        public boolean hasUsableSchedule() {
            return recognized && !exams.isEmpty();
        }
    }

    public static Result parse(String response, List<Course> courses, Map<String, Integer> courseIdBySlot) {
        Result result = new Result();
        if (response == null || response.trim().isEmpty()) return result;
        String trimmed = response.trim();
        try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                Object root = new JSONTokener(trimmed).nextValue();
                readJson(root, "", courses, courseIdBySlot, result, 0);
            } else if (trimmed.startsWith("<")) {
                readHtml(trimmed, courses, courseIdBySlot, result);
            }
        } catch (Exception ignored) {
            result.exams.clear();
            result.recognized = false;
        }
        Set<String> seen = new HashSet<>();
        Iterator<Exam> iterator = result.exams.iterator();
        while (iterator.hasNext()) {
            Exam exam = iterator.next();
            String key = exam.courseId + "|" + exam.title + "|" + exam.startTime + "|" + exam.endTime;
            if (!seen.add(key)) iterator.remove();
        }
        Collections.sort(result.exams, Comparator.comparingLong(e -> e.startTime == null ? Long.MAX_VALUE : e.startTime));
        return result;
    }

    /** Keeps exam associations when the portal reorders its generated local course IDs. */
    public static List<Exam> relinkCachedExams(List<Exam> exams, List<Course> previousCourses, List<Course> refreshedCourses) {
        Map<Integer, String> previousKeys = new HashMap<>();
        Map<Integer, Course> previousById = new HashMap<>();
        for (Course course : previousCourses) {
            previousKeys.put(course.id, courseKey(course));
            previousById.put(course.id, course);
        }
        Map<String, Course> refreshedByKey = new HashMap<>();
        for (Course course : refreshedCourses) refreshedByKey.put(courseKey(course), course);
        List<Exam> retained = new ArrayList<>();
        for (Exam previous : exams) {
            Course refreshed = refreshedByKey.get(previousKeys.get(previous.courseId));
            Course previousCourse = previousById.get(previous.courseId);
            if (refreshed == null || previousCourse == null
                    || differentKnownValues(previousCourse.semesterId, refreshed.semesterId)
                    || differentKnownValues(previousCourse.vtopClassId, refreshed.vtopClassId)) continue;
            Exam exam = new Exam();
            exam.courseId = refreshed.id;
            exam.title = previous.title;
            exam.startTime = previous.startTime;
            exam.endTime = previous.endTime;
            exam.venue = previous.venue;
            exam.seatLocation = previous.seatLocation;
            exam.seatNumber = previous.seatNumber;
            retained.add(exam);
        }
        return retained;
    }

    private static boolean differentKnownValues(String previous, String refreshed) {
        return previous != null && !previous.trim().isEmpty() && refreshed != null && !refreshed.trim().isEmpty()
                && !previous.trim().equalsIgnoreCase(refreshed.trim());
    }

    private static String courseKey(Course course) {
        String type = course.type == null ? "" : course.type.toLowerCase(Locale.ENGLISH);
        return normalize(course.code) + "|" + (type.contains("lab") ? "lab" : type.contains("project") ? "project" : "theory");
    }

    private static void readJson(Object node, String inheritedTitle, List<Course> courses,
                                 Map<String, Integer> slots, Result result, int depth) throws Exception {
        if (depth > 6) return;
        if (node instanceof JSONArray) {
            result.recognized = true;
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) readJson(array.opt(i), inheritedTitle, courses, slots, result, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject obj = (JSONObject) node;
        if (obj.has("status") && (Boolean.FALSE.equals(obj.opt("status")) || "false".equalsIgnoreCase(obj.optString("status")))) return;
        Map<String, String> values = new HashMap<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = obj.opt(key);
            if (value != null && value != JSONObject.NULL && !(value instanceof JSONObject) && !(value instanceof JSONArray)) {
                values.put(normalize(key), clean(String.valueOf(value)));
            }
        }
        String title = first(values, "examTitle", "examName", "examType", "examTypeDescription", "assessmentName", "title", "examSession");
        if (title.isEmpty()) title = inheritedTitle;
        boolean isRow = !first(values, "examDate", "date", "examStartDate", "startDate", "startDateTime").isEmpty()
                || (!first(values, "courseCode", "subjectCode", "slotName", "slot").isEmpty()
                && !first(values, "examTime", "examTiming", "startTime", "examStartTime", "venue", "examVenue").isEmpty());
        if (isRow) {
            result.recognized = true;
            addExam(values, title, courses, slots, result);
            return;
        }
        keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = obj.opt(key);
            String normalized = normalize(key);
            boolean collection = normalized.equals("data") || normalized.equals("details") || normalized.equals("exams")
                    || normalized.equals("examlist") || normalized.equals("schedulelist") || normalized.equals("schedule")
                    || normalized.contains("examschedule") || normalized.matches("(cat|fat|sat|midterm|final).*?");
            if (collection && (value instanceof JSONArray || value instanceof JSONObject)) {
                String childTitle = normalized.matches("(cat|fat|sat|midterm|final).*?") ? key : title;
                readJson(value, childTitle, courses, slots, result, depth + 1);
            }
        }
    }

    private static void readHtml(String html, List<Course> courses, Map<String, Integer> slots, Result result) {
        Document doc = Jsoup.parse(html);
        if (!doc.select("input[type=password], input[name=passwd], input[name=password], #vtopLoginForm").isEmpty()) return;
        for (Element table : doc.select("table")) {
            Map<String, Integer> columns = new HashMap<>();
            String currentTitle = clean(table.select("caption").text());
            for (Element row : table.select("tr")) {
                Element owner = row.parent();
                while (owner != null && !"table".equals(owner.tagName())) owner = owner.parent();
                if (owner != table) continue;
                List<Element> cells = new ArrayList<>();
                for (Element child : row.children()) {
                    if ("th".equals(child.tagName()) || "td".equals(child.tagName())) cells.add(child);
                }
                if (cells.isEmpty()) continue;
                Map<String, Integer> headers = new HashMap<>();
                for (int i = 0; i < cells.size(); i++) {
                    String field = headerField(cells.get(i).text());
                    if (field != null) headers.put(field, i);
                }
                if (headers.containsKey("date") && (headers.containsKey("slot") || headers.containsKey("coursecode"))) {
                    columns = headers;
                    result.recognized = true;
                    continue;
                }
                if (cells.size() == 1 && cells.get(0).hasAttr("colspan")) {
                    String text = clean(cells.get(0).text());
                    if (!text.isEmpty() && !text.toLowerCase(Locale.ENGLISH).contains("no record")) currentTitle = text;
                    continue;
                }
                if (columns.isEmpty()) continue;
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, Integer> entry : columns.entrySet()) {
                    if (entry.getValue() < cells.size()) values.put(entry.getKey(), clean(cells.get(entry.getValue()).text()));
                }
                if (first(values, "courseCode", "slot").isEmpty()) continue;
                addExam(values, first(values, "examTitle").isEmpty() ? currentTitle : first(values, "examTitle"), courses, slots, result);
            }
        }
    }

    private static String headerField(String text) {
        String header = normalize(text);
        if (header.contains("coursecode") || header.contains("subjectcode")) return "coursecode";
        if (header.contains("coursetype") || header.contains("subjecttype")) return "coursetype";
        if (header.equals("course") || header.equals("subject")) return "coursecode";
        if (header.contains("slot")) return "slot";
        if (header.contains("date")) return "date";
        if (header.contains("start") && header.contains("time")) return "starttime";
        if (header.contains("end") && header.contains("time")) return "endtime";
        if (header.contains("time") || header.contains("timing")) return "examtime";
        if (header.contains("venue") || header.equals("room") || header.equals("roomnumber")) return "venue";
        if (header.contains("location") || header.equals("seatposition")) return "seatlocation";
        if (header.contains("seat") && (header.contains("no") || header.contains("number"))) return "seatnumber";
        if (header.contains("exam") && (header.contains("type") || header.contains("title") || header.contains("name"))) return "examtitle";
        return null;
    }

    private static void addExam(Map<String, String> values, String title, List<Course> courses,
                                Map<String, Integer> slots, Result result) {
        Integer courseId = findCourse(first(values, "courseCode", "subjectCode", "code", "course"),
                first(values, "slotName", "slot", "courseSlot"), first(values, "courseType", "subjectType"), courses, slots);
        if (courseId == null || courseId <= 0) {
            result.rejectedRows++;
            return;
        }
        Exam exam = new Exam();
        exam.courseId = courseId;
        exam.title = normalizeTitle(title);
        String date = first(values, "examDate", "date", "examStartDate", "startDate");
        String start = first(values, "examStartTime", "startTime", "startDateTime", "fromTime");
        String end = first(values, "examEndTime", "endTime", "endDateTime", "toTime");
        String range = first(values, "examTime", "examTiming", "timing", "timings", "time", "timeSlot");
        if (start.isEmpty() && !range.isEmpty()) {
            String[] parts = range.split("(?i)\\s+(?:to)\\s+|\\s*[-–—]\\s*", 2);
            start = parts[0].trim();
            if (parts.length == 2) end = parts[1].trim();
        }
        exam.startTime = parseDateTime(date, start);
        exam.endTime = end.isEmpty() ? null : parseDateTime(date, end);
        if ((!date.isEmpty() || !start.isEmpty()) && exam.startTime == null) {
            result.rejectedRows++;
            return;
        }
        if (!end.isEmpty() && (exam.endTime == null || (exam.startTime != null && exam.endTime <= exam.startTime))) {
            result.rejectedRows++;
            return;
        }
        exam.venue = nullable(first(values, "examVenue", "venue", "room", "roomNo", "roomNumber"));
        exam.seatLocation = nullable(first(values, "seatLocation", "seatPosition", "location"));
        String seat = first(values, "seatNumber", "seatNo");
        if (!seat.isEmpty()) {
            try {
                exam.seatNumber = Integer.valueOf(seat);
            } catch (NumberFormatException ignored) {
                exam.seatLocation = exam.seatLocation == null ? seat : exam.seatLocation + " · " + seat;
            }
        }
        result.exams.add(exam);
    }

    private static Integer findCourse(String rawCode, String slot, String requestedType, List<Course> courses, Map<String, Integer> slots) {
        if (!rawCode.isEmpty() && courses != null && !courses.isEmpty()) {
            Matcher matcher = COURSE_CODE.matcher(rawCode);
            String code = normalize(matcher.find() ? matcher.group() : rawCode);
            Course match = null;
            for (Course course : courses) {
                if (course.code != null && normalize(course.code).equals(code)) {
                    if (!requestedType.isEmpty() && course.type != null
                            && normalize(course.type).equals(normalize(requestedType))) return course.id;
                    if (match == null || (course.type != null && course.type.toLowerCase(Locale.ENGLISH).contains("theory"))) match = course;
                }
            }
            return match == null ? null : match.id;
        }
        if (slots == null || slot.isEmpty()) return null;
        Set<Integer> matches = new HashSet<>();
        for (String token : slot.split("[+,/\\s]+")) {
            for (Map.Entry<String, Integer> entry : slots.entrySet()) {
                if (!token.isEmpty() && normalize(entry.getKey()).equals(normalize(token)) && entry.getValue() != null) matches.add(entry.getValue());
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    public static Long parseDateTime(String date, String time) {
        String cleanedDate = clean(date);
        String cleanedTime = clean(time).replaceAll("(?i)(\\d)(AM|PM)$", "$1 $2");
        for (String candidate : new String[]{cleanedTime, cleanedDate}) {
            if (candidate.matches("\\d{13}")) return Long.valueOf(candidate);
            if (candidate.matches("\\d{10}")) return Long.valueOf(candidate) * 1000L;
            for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}) {
                Long parsed = parseStrict(candidate, pattern);
                if (parsed != null) return parsed;
            }
        }
        if (cleanedDate.isEmpty()) return null;
        for (String dateFormat : DATE_FORMATS) {
            if (cleanedTime.isEmpty()) {
                Long parsed = parseStrict(cleanedDate, dateFormat);
                if (parsed != null) return parsed;
            } else {
                for (String timeFormat : TIME_FORMATS) {
                    Long parsed = parseStrict(cleanedDate + " " + cleanedTime.toUpperCase(Locale.ENGLISH), dateFormat + " " + timeFormat);
                    if (parsed != null) return parsed;
                }
            }
        }
        return null;
    }

    private static Long parseStrict(String value, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
        format.setTimeZone(CAMPUS_TIME_ZONE);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(value, position);
        return date != null && position.getIndex() == value.length() ? date.getTime() : null;
    }

    private static String first(Map<String, String> values, String... aliases) {
        for (String alias : aliases) {
            String value = values.get(normalize(alias));
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String normalizeTitle(String value) {
        String title = clean(value);
        if (title.isEmpty()) return "Exam";
        return title.replaceAll("(?i)\\b(CAT|FAT|SAT)\\s*(\\d)", "$1 $2");
    }

    private static String nullable(String value) { return value.isEmpty() ? null : value; }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ENGLISH);
    }

    private static String clean(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.matches("[-–—]+") || cleaned.equalsIgnoreCase("null") || cleaned.equalsIgnoreCase("N/A") ? "" : cleaned;
    }
}
