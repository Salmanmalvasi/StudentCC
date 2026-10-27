package com.salmanmalvasi.studentcc.sync;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.Slot;

import java.util.LinkedHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, streaming HTML parsers for VTOP responses using JSoup.
 * Completely replaces heavy offscreen WebView DOMParser execution.
 */
public class VTOPHtmlParsers {
    private static final String TAG = "VTOPHtmlParsers";

    public static class DetailedAttendanceLink {
        public final String courseCode;
        public final String courseTitle;
        public final String courseType;
        public final String slot;
        public final String classId;
        public final String slotName;

        public DetailedAttendanceLink(String courseCode, String courseTitle, String courseType,
                                      String slot, String classId, String slotName) {
            this.courseCode = courseCode;
            this.courseTitle = courseTitle;
            this.courseType = courseType;
            this.slot = slot;
            this.classId = classId;
            this.slotName = slotName;
        }
    }

    public static Map<String, String> parseSemesters(String html) {
        Map<String, String> semesters = new HashMap<>();
        if (html == null) return semesters;
        Document doc = Jsoup.parse(html);
        Element select = doc.getElementById("semesterSubId");
        if (select != null) {
            for (Element option : select.getElementsByTag("option")) {
                String val = option.val();
                String text = option.text().trim();
                if (!val.isEmpty() && !text.isEmpty()) {
                    semesters.put(text, val);
                }
            }
        }
        return semesters;
    }

    public static void parseCoursesAndSlots(String html, List<Course> outCourses, List<Slot> outSlots) {
        if (html == null || html.trim().isEmpty()) {
            Log.w(TAG, "parseCoursesAndSlots: HTML is null or empty");
            return;
        }
        Document doc = Jsoup.parse(html);
        Element container = doc.getElementById("studentDetailsList");
        Element table = null;
        if (container != null) {
            table = container.tagName().equalsIgnoreCase("table") ? container : container.getElementsByTag("table").first();
        }
        if (table == null) {
            for (Element t : doc.getElementsByTag("table")) {
                String tText = t.text().toLowerCase(Locale.US);
                if (tText.contains("course") && (tText.contains("slot") || tText.contains("faculty"))) {
                    table = t;
                    break;
                }
            }
        }
        if (table == null) {
            Log.w(TAG, "parseCoursesAndSlots: No course table found in response");
            return;
        }

        Elements headings = table.getElementsByTag("th");
        int courseIndex = -1, courseCodeIndex = -1, courseTitleIndex = -1, courseTypeIndex = -1;
        int creditsIndex = -1, slotVenueIndex = -1, facultyIndex = -1;
        for (int i = 0; i < headings.size(); ++i) {
            String heading = headings.get(i).text().toLowerCase(Locale.US).trim();
            if (heading.equals("course")) {
                courseIndex = i;
            } else if (heading.equals("course code") || (heading.contains("code") && heading.contains("course"))) {
                courseCodeIndex = i;
            } else if (heading.equals("course title") || (heading.contains("title") && heading.contains("course"))) {
                courseTitleIndex = i;
            } else if (heading.equals("course type") || (heading.contains("type") && heading.contains("course"))) {
                courseTypeIndex = i;
            } else if (heading.equals("l t p j c") || heading.contains("credit")) {
                creditsIndex = i;
            } else if (heading.contains("slot")) {
                slotVenueIndex = i;
            } else if (heading.contains("faculty")) {
                facultyIndex = i;
            }
        }
        if (courseIndex < 0 && courseCodeIndex >= 0) {
            courseIndex = courseCodeIndex;
        }

        Elements cells = table.getElementsByTag("td");
        int courseIdCounter = 1;
        int slotIdCounter = 1;

        // Method 1: Robust Per-Row Extraction (Primary)
        Elements rows = table.getElementsByTag("tr");
        for (Element row : rows) {
            Elements ths = row.getElementsByTag("th");
            if (!ths.isEmpty()) continue; // Skip header row
            Elements rowCells = row.getElementsByTag("td");
            if (rowCells.size() < 3) continue;

            String rawCourse = "";
            String rawCode = "";
            String rawTitle = "";
            String rawType = "";
            String rawCredits = "";
            String rawSlotVenue = "";
            String rawFaculty = "";

            if (courseIndex >= 0 && courseIndex < rowCells.size()) rawCourse = rowCells.get(courseIndex).text().trim();
            if (courseCodeIndex >= 0 && courseCodeIndex < rowCells.size()) rawCode = rowCells.get(courseCodeIndex).text().trim();
            if (courseTitleIndex >= 0 && courseTitleIndex < rowCells.size()) rawTitle = rowCells.get(courseTitleIndex).text().trim();
            if (courseTypeIndex >= 0 && courseTypeIndex < rowCells.size()) rawType = rowCells.get(courseTypeIndex).text().trim();
            if (creditsIndex >= 0 && creditsIndex < rowCells.size()) rawCredits = rowCells.get(creditsIndex).text().trim();
            if (slotVenueIndex >= 0 && slotVenueIndex < rowCells.size()) rawSlotVenue = rowCells.get(slotVenueIndex).text().trim();
            if (facultyIndex >= 0 && facultyIndex < rowCells.size()) rawFaculty = rowCells.get(facultyIndex).text().trim();

            // Cell scanning heuristics within row if columns weren't identified
            if (rawCourse.isEmpty() && rawCode.isEmpty()) {
                for (Element cell : rowCells) {
                    String txt = cell.text().trim();
                    if (txt.matches("^[A-Za-z]{3,4}\\d{3,4}[A-Za-z]?$")) {
                        rawCode = txt;
                    } else if (txt.contains("-") && txt.matches(".*[A-Za-z]{3,4}\\d{3,4}.*")) {
                        rawCourse = txt;
                    }
                }
            }
            if (rawSlotVenue.isEmpty()) {
                for (Element cell : rowCells) {
                    String txt = cell.text().trim();
                    if ((txt.contains("+") || txt.matches(".*[A-Za-z]\\d+.*")) && !txt.matches(".*[A-Za-z]{3,4}\\d{3,4}.*")) {
                        rawSlotVenue = txt;
                        break;
                    }
                }
            }

            Course course = null;
            if (!rawCourse.isEmpty() && rawCourse.contains("-")) {
                course = parseCourseFromTokens(rawCourse, rawCredits, rawSlotVenue, rawFaculty, courseIdCounter++);
            } else if (!rawCode.isEmpty() || !rawCourse.isEmpty()) {
                String finalCode = !rawCode.isEmpty() ? rawCode : rawCourse.split("\\s+")[0].trim();
                String finalTitle = !rawTitle.isEmpty() ? rawTitle : (!rawCourse.isEmpty() ? rawCourse : finalCode);
                course = new Course();
                course.id = courseIdCounter++;
                course.code = finalCode;
                course.title = finalTitle;
                course.type = rawType.toLowerCase(Locale.US).contains("lab") ? "lab" : (rawType.toLowerCase(Locale.US).contains("project") ? "project" : "theory");
                try {
                    String[] credTokens = rawCredits.split("\\s+");
                    course.credits = Double.parseDouble(credTokens[credTokens.length - 1]);
                } catch (Exception ignored) {
                    course.credits = 3.0;
                }
                String[] slotVenueParts = rawSlotVenue.split("-");
                course.venue = slotVenueParts.length > 1 ? slotVenueParts[1].trim() : "";
                course.faculty = rawFaculty.split("-")[0].trim();
            }

            if (course != null && course.code != null && !course.code.isEmpty()) {
                outCourses.add(course);
                List<Slot> parsedSlots = parseSlotsForCourse(rawSlotVenue, course.id, slotIdCounter);
                outSlots.addAll(parsedSlots);
                slotIdCounter += parsedSlots.size();
            }
        }

        // Method 2: Flat cells striding fallback
        if (outCourses.isEmpty() && !cells.isEmpty() && !headings.isEmpty() && courseIndex >= 0 && creditsIndex >= 0 && slotVenueIndex >= 0 && facultyIndex >= 0) {
            int headingOffset = headings.get(0).text().toLowerCase(Locale.US).contains("invoice") ? -1 : 0;
            int cellOffset = cells.get(0).text().toLowerCase(Locale.US).contains("invoice") ? 1 : 0;
            int offset = headingOffset + cellOffset;
            int stride = headings.size() + headingOffset;

            int cIdx = courseIndex;
            int crIdx = creditsIndex;
            int svIdx = slotVenueIndex;
            int fIdx = facultyIndex;

            while (cIdx + offset < cells.size() && crIdx + offset < cells.size() && svIdx + offset < cells.size() && fIdx + offset < cells.size()) {
                String rawCourse = cells.get(cIdx + offset).text().replace("\t", "").replace("\n", " ").trim();
                String rawCredits = cells.get(crIdx + offset).text().replace("\t", "").replace("\n", " ").trim();
                String rawSlotVenue = cells.get(svIdx + offset).text().replace("\t", "").replace("\n", "").trim();
                String rawFaculty = cells.get(fIdx + offset).text().replace("\t", "").replace("\n", "").trim();

                if (!rawCourse.isEmpty()) {
                    Course course = parseCourseFromTokens(rawCourse, rawCredits, rawSlotVenue, rawFaculty, courseIdCounter++);
                    if (course != null) {
                        outCourses.add(course);
                        List<Slot> parsedSlots = parseSlotsForCourse(rawSlotVenue, course.id, slotIdCounter);
                        outSlots.addAll(parsedSlots);
                        slotIdCounter += parsedSlots.size();
                    }
                }

                cIdx += stride;
                crIdx += stride;
                svIdx += stride;
                fIdx += stride;
            }
        }

        // Method 3: Cell regex search fallback across entire table
        if (outCourses.isEmpty()) {
            for (int i = 0; i < cells.size(); i++) {
                String text = cells.get(i).text().trim();
                if (text.matches(".*[A-Za-z]{3,4}\\s*\\d{3,4}.*")) {
                    String rawCourse = text;
                    String rawCredits = (i + 1 < cells.size()) ? cells.get(i + 1).text().trim() : "3";
                    String rawSlotVenue = "";
                    String rawFaculty = "";
                    for (int j = i + 1; j < Math.min(i + 8, cells.size()); j++) {
                        String cText = cells.get(j).text().trim();
                        if (rawSlotVenue.isEmpty() && (cText.contains("+") || cText.matches(".*[A-Za-z]\\d+.*"))) {
                            rawSlotVenue = cText;
                        } else if (rawFaculty.isEmpty() && !cText.isEmpty() && !cText.matches("^[0-9.]+$")) {
                            rawFaculty = cText;
                        }
                    }
                    Course course = parseCourseFromTokens(rawCourse, rawCredits, rawSlotVenue, rawFaculty, courseIdCounter++);
                    if (course != null) {
                        outCourses.add(course);
                        List<Slot> parsedSlots = parseSlotsForCourse(rawSlotVenue, course.id, slotIdCounter);
                        outSlots.addAll(parsedSlots);
                        slotIdCounter += parsedSlots.size();
                    }
                }
            }
        }

        Log.d(TAG, "parseCoursesAndSlots parsed " + outCourses.size() + " courses and " + outSlots.size() + " slots (headings=" + headings.size() + ", cells=" + cells.size() + ")");
    }

    private static Course parseCourseFromTokens(String rawCourse, String rawCredits, String rawSlotVenue, String rawFaculty, int id) {
        String[] courseParts = rawCourse.split("-");
        String code = courseParts[0].trim();
        StringBuilder titleBuilder = new StringBuilder();
        for (int p = 1; p < courseParts.length; p++) {
            if (p > 1) titleBuilder.append("-");
            titleBuilder.append(courseParts[p]);
        }
        String title = titleBuilder.toString().split("\\(")[0].trim();
        String rawCourseType = rawCourse.contains("(") ? rawCourse.substring(rawCourse.lastIndexOf('(')).toLowerCase(Locale.US) : "";
        String type = rawCourseType.contains("lab") ? "lab" : (rawCourseType.contains("project") ? "project" : "theory");

        double credits = 0.0;
        try {
            String[] credTokens = rawCredits.split("\\s+");
            credits = Double.parseDouble(credTokens[credTokens.length - 1]);
        } catch (Exception ignored) {}

        String[] slotVenueParts = rawSlotVenue.split("-");
        String slotsStr = slotVenueParts[0].trim();
        StringBuilder venueBuilder = new StringBuilder();
        for (int v = 1; v < slotVenueParts.length; v++) {
            if (v > 1) venueBuilder.append(" - ");
            venueBuilder.append(slotVenueParts[v]);
        }
        String venue = venueBuilder.toString().trim();
        String faculty = rawFaculty.split("-")[0].trim();

        Course course = new Course();
        course.id = id;
        course.code = code;
        course.title = title;
        course.type = type;
        course.credits = credits;
        course.venue = venue;
        course.faculty = faculty;
        return course;
    }

    private static List<Slot> parseSlotsForCourse(String rawSlotVenue, int courseId, int startSlotId) {
        List<Slot> slots = new ArrayList<>();
        String[] slotVenueParts = rawSlotVenue.split("-");
        String slotsStr = slotVenueParts[0].trim();
        String[] slotTokens = slotsStr.split("[+\\s]+");
        int currentId = startSlotId;
        for (String sToken : slotTokens) {
            String cleanSlot = sToken.trim();
            if (!cleanSlot.isEmpty()) {
                Slot slot = new Slot();
                slot.id = currentId++;
                slot.slot = cleanSlot;
                slot.courseId = courseId;
                slots.add(slot);
            }
        }
        return slots;
    }

    public static class AttendanceSummaryResult {
        public final List<Attendance> attendances;
        public final int attendedClasses;
        public final int totalClasses;
        public final float overallAttendance;

        public AttendanceSummaryResult(List<Attendance> attendances, int attendedClasses, int totalClasses, float overallAttendance) {
            this.attendances = attendances;
            this.attendedClasses = attendedClasses;
            this.totalClasses = totalClasses;
            this.overallAttendance = overallAttendance;
        }
    }

    public static Integer resolveCourseId(List<Course> courses, List<Slot> slots, String slotName, int courseType, String courseCode) {
        if (slots != null && slotName != null && !slotName.isEmpty()) {
            String normalized = slotName.toUpperCase(Locale.US).trim();
            String canonical = com.salmanmalvasi.studentcc.services.VTOPService.SLOT_ALIASES.get(normalized);
            // 1. Try slot + course type exact match
            for (Slot s : slots) {
                if (s.slot != null) {
                    String sNorm = s.slot.toUpperCase(Locale.US).trim();
                    if (sNorm.equals(normalized) || (canonical != null && sNorm.equals(canonical))) {
                        if (courses != null) {
                            for (Course c : courses) {
                                if (c.id == s.courseId) {
                                    boolean isLab = "lab".equalsIgnoreCase(c.type);
                                    if ((courseType == Course.TYPE_LAB && isLab) || (courseType != Course.TYPE_LAB && !isLab)) {
                                        return c.id;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 2. Try slot match regardless of type
            for (Slot s : slots) {
                if (s.slot != null) {
                    String sNorm = s.slot.toUpperCase(Locale.US).trim();
                    if (sNorm.equals(normalized) || (canonical != null && sNorm.equals(canonical))) {
                        return s.courseId;
                    }
                }
            }
        }

        // 3. Try course code + course type match
        if (courses != null && courseCode != null && !courseCode.isEmpty()) {
            String cleanCode = courseCode.toUpperCase(Locale.US).trim();
            for (Course c : courses) {
                if (c.code != null) {
                    String cCode = c.code.toUpperCase(Locale.US).trim();
                    if (cCode.equals(cleanCode) || cCode.startsWith(cleanCode) || cleanCode.startsWith(cCode)) {
                        boolean isLab = "lab".equalsIgnoreCase(c.type);
                        if ((courseType == Course.TYPE_LAB && isLab) || (courseType != Course.TYPE_LAB && !isLab)) {
                            return c.id;
                        }
                    }
                }
            }
            // 4. Try course code match regardless of type
            for (Course c : courses) {
                if (c.code != null) {
                    String cCode = c.code.toUpperCase(Locale.US).trim();
                    if (cCode.equals(cleanCode) || cCode.startsWith(cleanCode) || cleanCode.startsWith(cCode)) {
                        return c.id;
                    }
                }
            }
        }
        return null;
    }

    public static AttendanceSummaryResult parseAttendanceSummary(String html, List<Course> courses, List<Slot> slots) {
        List<Attendance> attendances = new ArrayList<>();
        if (html == null) return new AttendanceSummaryResult(attendances, 0, 0, 0f);

        String unescaped = html.replace("\\u003C", "<").replace("\\u003E", ">")
                               .replace("\\u003c", "<").replace("\\u003e", ">")
                               .replace("\\r", "\r").replace("\\n", "\n")
                               .replace("\\\"", "\"").replace("\\\\", "\\");
        Document doc = Jsoup.parse(unescaped);

        Element container = doc.getElementById("getStudentDetails");
        Element table = (container != null) ? container.selectFirst("table") : null;
        if (table == null && container != null && "table".equalsIgnoreCase(container.tagName())) {
            table = container;
        }
        if (table == null) {
            table = doc.selectFirst("table");
        }
        if (table == null && container != null) {
            table = container;
        }
        if (table == null) {
            return new AttendanceSummaryResult(attendances, 0, 0, 0f);
        }

        Elements headers = table.select("th");
        int courseTypeIdx = -1, slotIdx = -1, attendedIdx = -1, totalIdx = -1, percentageIdx = -1, codeIdx = -1;

        for (int i = 0; i < headers.size(); i++) {
            String heading = headers.get(i).text().toLowerCase(Locale.US);
            if (heading.contains("course") && heading.contains("type")) {
                courseTypeIdx = i;
            } else if (heading.contains("course") && (heading.contains("code") || heading.contains("title"))) {
                if (heading.contains("code") && codeIdx < 0) codeIdx = i;
            } else if (heading.contains("slot")) {
                slotIdx = i;
            } else if (heading.contains("attended")) {
                attendedIdx = i;
            } else if (heading.contains("total")) {
                totalIdx = i;
            } else if (heading.contains("percentage") || heading.contains("%")) {
                percentageIdx = i;
            }
        }

        if (codeIdx < 0) codeIdx = 1;
        if (courseTypeIdx < 0) courseTypeIdx = 3;
        if (slotIdx < 0) slotIdx = 4;
        if (attendedIdx < 0) attendedIdx = 6;
        if (totalIdx < 0) totalIdx = 7;
        if (percentageIdx < 0) percentageIdx = 8;

        Elements rows = table.select("tr");
        int attendedClasses = 0;
        int totalClasses = 0;

        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements headerCells = row.select("th");
            if (!headerCells.isEmpty()) continue;

            Elements cells = row.select("td");
            if (cells.size() < 5) continue;

            String courseTypeText = (courseTypeIdx < cells.size()) ? cells.get(courseTypeIdx).text().trim() : "";
            int courseType = courseTypeText.toLowerCase(Locale.US).contains("lab") ? Course.TYPE_LAB : Course.TYPE_THEORY;

            String slotText = (slotIdx < cells.size()) ? cells.get(slotIdx).text().trim() : "";
            String[] slotParts = slotText.split("\\+");
            String slot = slotParts.length > 0 ? slotParts[0].trim() : slotText;

            String courseCode = (codeIdx < cells.size()) ? cells.get(codeIdx).text().trim() : "";

            Integer courseId = resolveCourseId(courses, slots, slot, courseType, courseCode);
            if (courseId == null) {
                if (courses != null && i - 1 < courses.size()) {
                    courseId = courses.get(i - 1).id;
                } else {
                    continue;
                }
            }

            Attendance att = new Attendance();
            att.id = attendances.size() + 1;
            att.courseId = courseId;

            String attendedStr = (attendedIdx < cells.size()) ? cells.get(attendedIdx).text().trim() : "";
            String totalStr = (totalIdx < cells.size()) ? cells.get(totalIdx).text().trim() : "";
            String pctStr = (percentageIdx < cells.size()) ? cells.get(percentageIdx).text().replaceAll("[^0-9]", "").trim() : "";

            try {
                att.attended = !attendedStr.isEmpty() ? Integer.parseInt(attendedStr.replaceAll("[^0-9]", "")) : null;
                att.total = !totalStr.isEmpty() ? Integer.parseInt(totalStr.replaceAll("[^0-9]", "")) : null;

                if (!pctStr.isEmpty()) {
                    att.percentage = Integer.parseInt(pctStr);
                } else if (att.attended != null && att.total != null && att.total > 0) {
                    att.percentage = (int) Math.round((att.attended * 10000.0) / att.total) / 100;
                } else {
                    att.percentage = 0;
                }

                if (att.attended != null) attendedClasses += att.attended;
                if (att.total != null) totalClasses += att.total;
            } catch (Exception e) {
                att.attended = 0;
                att.total = 0;
                att.percentage = 0;
            }

            attendances.add(att);
        }

        float overallAttendance = 0.0f;
        if (totalClasses > 0) {
            overallAttendance = Math.round((attendedClasses * 10000.0) / totalClasses) / 100.0f;
        }

        return new AttendanceSummaryResult(attendances, attendedClasses, totalClasses, overallAttendance);
    }

    public static List<Attendance> parseAttendanceSummary(String html, Map<String, Integer> courseIdByCode) {
        List<Course> mockCourses = new ArrayList<>();
        if (courseIdByCode != null) {
            for (Map.Entry<String, Integer> entry : courseIdByCode.entrySet()) {
                Course c = new Course();
                c.id = entry.getValue();
                c.code = entry.getKey();
                mockCourses.add(c);
            }
        }
        return parseAttendanceSummary(html, mockCourses, Collections.emptyList()).attendances;
    }

    public static List<DetailedAttendanceLink> parseDetailedAttendanceLinks(String html) {
        List<DetailedAttendanceLink> links = new ArrayList<>();
        if (html == null) return links;
        String unescaped = html.replace("\\u003C", "<").replace("\\u003E", ">")
                               .replace("\\u003c", "<").replace("\\u003e", ">")
                               .replace("\\r", "\r").replace("\\n", "\n")
                               .replace("\\\"", "\"").replace("\\\\", "\\");
        Document doc = Jsoup.parse(unescaped);
        Elements rows = doc.select("tr");

        for (Element row : rows) {
            Element viewLink = row.selectFirst("a[onclick*='processViewAttendanceDetail']");
            if (viewLink != null) {
                String onclick = viewLink.attr("onclick");
                Matcher m = Pattern.compile("processViewAttendanceDetail\\('([^']+)'\\s*,\\s*'([^']+)'\\)").matcher(onclick);
                if (m.find()) {
                    String classId = m.group(1);
                    String slotName = m.group(2);
                    Elements cells = row.select("td");
                    String courseCode = cells.size() > 1 ? cells.get(1).text().trim() : "";
                    String courseTitle = cells.size() > 2 ? cells.get(2).text().trim() : "";
                    String courseType = cells.size() > 3 ? cells.get(3).text().trim() : "";
                    String slot = cells.size() > 4 ? cells.get(4).text().split("\\+")[0].trim() : "";
                    links.add(new DetailedAttendanceLink(courseCode, courseTitle, courseType, slot, classId, slotName));
                }
            }
        }
        return links;
    }

    public static List<AttendanceRecord> parseDetailedAttendanceRecords(String html, int courseId, String slotName) {
        List<AttendanceRecord> records = new ArrayList<>();
        if (html == null) return records;
        String unescaped = html.replace("\\u003C", "<").replace("\\u003E", ">")
                               .replace("\\u003c", "<").replace("\\u003e", ">")
                               .replace("\\r", "\r").replace("\\n", "\n")
                               .replace("\\\"", "\"").replace("\\\\", "\\");
        Document doc = Jsoup.parse(unescaped);
        Elements tables = doc.getElementsByTag("table");
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

        String session = (slotName != null && slotName.replaceAll("\\s", "").matches(".*L\\d+.*")) ? "Lab" : "Class";

        for (Element table : tables) {
            Elements rows = table.getElementsByTag("tr");
            for (int i = 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).getElementsByTag("td");
                if (cells.size() >= 5) {
                    String dateStr = cells.get(1).text().trim();
                    String status = cells.get(4).text().trim();
                    if (!dateStr.isEmpty() && !status.isEmpty() && !dateStr.equalsIgnoreCase("Date")) {
                        AttendanceRecord record = new AttendanceRecord();
                        record.courseId = courseId;
                        record.dateString = dateStr;
                        record.status = status;
                        record.remarks = slotName;
                        record.session = session;
                        try {
                            Date parsedDate = dateFormat.parse(dateStr);
                            record.date = parsedDate != null ? parsedDate.getTime() : System.currentTimeMillis();
                        } catch (Exception e) {
                            record.date = System.currentTimeMillis();
                        }
                        records.add(record);
                    }
                }
            }
            if (!records.isEmpty()) break;
        }
        return records;
    }

    public static List<CalendarEvent> parseCalendarEvents(String html, String monthName) {
        List<CalendarEvent> events = new ArrayList<>();
        if (html == null) return events;
        Document doc = Jsoup.parse(html);
        Elements cells = doc.select("table.calendar-table tbody tr td");
        if (cells.isEmpty()) {
            cells = doc.select("table.calendar-table tr td");
        }
        if (cells.isEmpty()) {
            cells = doc.select("table tr td");
        }

        for (Element cell : cells) {
            Elements spans = cell.getElementsByTag("span");
            if (spans.isEmpty()) continue;
            String dateText = spans.first().text().trim();
            int day;
            try {
                day = Integer.parseInt(dateText);
            } catch (Exception e) {
                continue;
            }

            for (int i = 1; i < spans.size(); i++) {
                String text = spans.get(i).text().trim();
                if (text.isEmpty()) continue;

                String lower = text.toLowerCase(Locale.US);
                boolean isNonInstructional = lower.contains("no instructional") ||
                        lower.contains("non-instructional") ||
                        lower.contains("holiday") ||
                        lower.contains("vacation") ||
                        lower.contains("recess");
                boolean isInstructional = !isNonInstructional && lower.contains("instructional");
                String type = isNonInstructional ? "Holiday" : (isInstructional ? "Instructional Day" : "Other");

                events.add(new CalendarEvent(monthName, day, text, type, "", ""));
            }
        }
        return events;
    }

    /**
     * Checks if the returned HTML is an authentication gateway, login page, or session expired warning.
     */
    public static boolean isSessionExpiredOrLoginPage(String html) {
        if (html == null || html.trim().isEmpty()) return false;
        String lower = html.toLowerCase(Locale.US);
        return lower.contains("name=\"uname\"") ||
                lower.contains("id=\"uname\"") ||
                lower.contains("action=\"dologin\"") ||
                lower.contains("form id=\"vtoploginform\"") ||
                lower.contains("session timed out") ||
                lower.contains("session expired") ||
                lower.contains("you are not authorized") ||
                lower.contains("not authorized") ||
                lower.contains("login to your account");
    }

    /**
     * Parses the student's legal name from StudentProfileAllView.
     */
    public static String parseStudentName(String html) {
        if (html == null) return null;
        Document doc = Jsoup.parse(html);
        Elements cells = doc.getElementsByTag("td");
        for (int i = 0; i < cells.size(); i++) {
            String text = cells.get(i).text().toLowerCase(Locale.US);
            if (text.contains("name") && !text.contains("faculty") && !text.contains("proctor")) {
                if (i + 1 < cells.size()) {
                    String name = cells.get(i + 1).text().trim();
                    if (!name.isEmpty()) return name;
                }
            }
        }
        return null;
    }

    /**
     * Parses CAT/FAT/Assignment marks from doStudentMarkView HTML into Marks and CumulativeMarks.
     */
    public static void parseMarks(
            String html,
            List<Course> courses,
            List<Slot> slots,
            List<Mark> outMarks,
            List<CumulativeMark> outCumulativeMarks) {
        if (html == null || html.trim().isEmpty() || outMarks == null) return;
        if (html.toLowerCase(Locale.US).contains("no data found")) return;

        Document doc = Jsoup.parse(html);
        Element table = doc.getElementById("fixedTableContainer");
        if (table != null) {
            Element innerTableInDiv = table.selectFirst("table");
            if (innerTableInDiv != null) {
                table = innerTableInDiv;
            }
        }
        if (table == null) {
            table = doc.select("table").first();
        }
        if (table == null) return;

        // Direct child tr's prevent capturing nested table tr's directly into the outer list.
        Elements rows = table.select("> tbody > tr");
        if (rows.isEmpty()) {
            rows = table.select("> tr");
        }
        if (rows.isEmpty()) {
            rows = table.select("tr");
        }
        if (rows.isEmpty()) return;

        // Detect column indices in outer header
        int courseCodeIndex = -1;
        int courseTitleIndex = -1;
        int courseTypeIndex = -1;
        int slotIndex = -1;
        int creditIndex = -1;

        Elements headers = rows.first().select("th, td");
        for (int i = 0; i < headers.size(); i++) {
            String hText = headers.get(i).text().toLowerCase(Locale.US);
            if (hText.contains("course") && hText.contains("type")) {
                courseTypeIndex = i;
            } else if (hText.contains("slot")) {
                slotIndex = i;
            } else if (hText.contains("code")) {
                courseCodeIndex = i;
            } else if (hText.contains("title")) {
                courseTitleIndex = i;
            } else if (hText.contains("credit")) {
                creditIndex = i;
            }
        }

        Map<String, CumulativeMark> cumulativeMarksMap = new LinkedHashMap<>();
        int markId = 1;

        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cells = row.select("> td, > th");
            if (cells.isEmpty()) continue;

            // Extract course row details
            String rawCourseType = "";
            if (courseTypeIndex >= 0 && courseTypeIndex < cells.size()) {
                rawCourseType = cells.get(courseTypeIndex).text().trim().toLowerCase(Locale.US);
            }
            int courseType = Course.TYPE_THEORY;
            if (rawCourseType.contains("lab")) {
                courseType = Course.TYPE_LAB;
            } else if (rawCourseType.contains("project")) {
                courseType = Course.TYPE_PROJECT;
            }

            String slot = "";
            if (slotIndex >= 0 && slotIndex < cells.size()) {
                slot = cells.get(slotIndex).text().split("\\+")[0].trim();
            }

            String courseCode = "";
            if (courseCodeIndex >= 0 && courseCodeIndex < cells.size()) {
                courseCode = cells.get(courseCodeIndex).text().trim();
            }

            Double courseCredits = 0.0;
            if (creditIndex >= 0 && creditIndex < cells.size()) {
                courseCredits = parseDoubleSafe(cells.get(creditIndex).text());
                if (courseCredits == null) courseCredits = 0.0;
            }

            // Fallback heuristics for course code if not detected by header
            if (courseCode.isEmpty()) {
                for (Element cell : cells) {
                    String txt = cell.text().trim();
                    if (txt.matches("^[A-Z]{3,4}[0-9]{3,4}[A-Z]?$")) {
                        courseCode = txt;
                        break;
                    }
                }
            }

            // Resolve courseId
            Integer courseId = resolveCourseId(courses, slots, slot, courseType, courseCode);

            // Refine course code and credits from staged courses if available
            if (courses != null) {
                for (Course c : courses) {
                    if (courseId != null && c.id == courseId) {
                        if (courseCode.isEmpty() && c.code != null) {
                            courseCode = c.code;
                        }
                        if (c.credits != null && c.credits > 0) {
                            courseCredits = c.credits;
                        }
                        break;
                    } else if (!courseCode.isEmpty() && courseCode.equalsIgnoreCase(c.code)) {
                        if (courseId == null) {
                            courseId = c.id;
                        }
                        if (c.credits != null && c.credits > 0) {
                            courseCredits = c.credits;
                        }
                        break;
                    }
                }
            }

            // Locate the inner table containing marks
            // Case A: Inner table is inside the current row
            Element innerTable = row.selectFirst("table");
            // Case B: Inner table is inside the subsequent row (VTOP standard layout)
            if (innerTable == null && i + 1 < rows.size()) {
                innerTable = rows.get(i + 1).selectFirst("table");
                if (innerTable != null) {
                    i++; // Advance outer index past the nested table container row
                }
            }

            if (innerTable == null) continue;

            Elements innerRows = innerTable.select("tr");
            if (innerRows.isEmpty()) continue;

            // Analyze inner table header
            int titleIndex = -1;
            int scoreIndex = -1;
            int maxScoreIndex = -1;
            int weightageIndex = -1;
            int maxWeightageIndex = -1;
            int averageIndex = -1;
            int statusIndex = -1;

            Element innerHeader = innerRows.get(0);
            Elements innerHeaderCells = innerHeader.select("th, td");
            boolean hasHeader = false;

            for (int j = 0; j < innerHeaderCells.size(); j++) {
                String h = innerHeaderCells.get(j).text().toLowerCase(Locale.US);
                if (h.contains("title")) {
                    titleIndex = j;
                    hasHeader = true;
                } else if (h.contains("scored") || (h.contains("score") && !h.contains("max"))) {
                    scoreIndex = j;
                    hasHeader = true;
                } else if ((h.contains("max") && (h.contains("mark") || h.contains("score"))) ||
                        (h.contains("max") && !h.contains("weightage") && !h.contains("%"))) {
                    maxScoreIndex = j;
                    hasHeader = true;
                } else if (h.contains("weightage") && (h.contains("mark") || h.contains("score"))) {
                    weightageIndex = j;
                    hasHeader = true;
                } else if (h.contains("%") || (h.contains("weightage") && h.contains("max"))) {
                    maxWeightageIndex = j;
                    hasHeader = true;
                } else if (h.contains("average")) {
                    averageIndex = j;
                    hasHeader = true;
                } else if (h.contains("status")) {
                    statusIndex = j;
                    hasHeader = true;
                }
            }

            int startRow = hasHeader ? 1 : 0;

            for (int r = startRow; r < innerRows.size(); r++) {
                Elements iCells = innerRows.get(r).select("td");
                if (iCells.isEmpty()) continue;

                Mark m = new Mark();
                m.id = markId++;
                m.courseId = courseId;

                // Extract fields by detected index or default positional fallback
                if (titleIndex >= 0 && titleIndex < iCells.size()) {
                    m.title = iCells.get(titleIndex).text().trim();
                } else if (!hasHeader && iCells.size() >= 1) {
                    m.title = iCells.get(0).text().trim();
                } else if (iCells.size() >= 2) {
                    m.title = iCells.get(1).text().trim();
                } else {
                    m.title = iCells.get(0).text().trim();
                }

                if (scoreIndex >= 0 && scoreIndex < iCells.size()) {
                    m.score = parseDoubleSafe(iCells.get(scoreIndex).text());
                } else if (!hasHeader && iCells.size() >= 4) {
                    m.score = parseDoubleSafe(iCells.get(3).text());
                }

                if (maxScoreIndex >= 0 && maxScoreIndex < iCells.size()) {
                    m.maxScore = parseDoubleSafe(iCells.get(maxScoreIndex).text());
                } else if (!hasHeader && iCells.size() >= 2) {
                    m.maxScore = parseDoubleSafe(iCells.get(1).text());
                }

                if (weightageIndex >= 0 && weightageIndex < iCells.size()) {
                    m.weightage = parseDoubleSafe(iCells.get(weightageIndex).text());
                } else if (!hasHeader && iCells.size() >= 3) {
                    m.weightage = parseDoubleSafe(iCells.get(2).text());
                }

                if (maxWeightageIndex >= 0 && maxWeightageIndex < iCells.size()) {
                    m.maxWeightage = parseDoubleSafe(iCells.get(maxWeightageIndex).text());
                }

                if (averageIndex >= 0 && averageIndex < iCells.size()) {
                    m.average = parseDoubleSafe(iCells.get(averageIndex).text());
                }

                if (statusIndex >= 0 && statusIndex < iCells.size()) {
                    m.status = iCells.get(statusIndex).text().trim();
                } else if (!hasHeader && iCells.size() >= 5) {
                    m.status = iCells.get(4).text().trim();
                }

                // If score is null but weightage is available, or vice versa
                if (m.score == null && m.weightage != null) {
                    m.score = m.weightage;
                }

                // Unique signature for tracking read state
                String cTypeStr = (courseType == Course.TYPE_LAB) ? "lab" :
                        ((courseType == Course.TYPE_PROJECT) ? "project" : "theory");
                String codeForSig = !courseCode.isEmpty() ? courseCode : (slot != null ? slot : "COURSE");
                m.signature = (codeForSig + cTypeStr + (m.title != null ? m.title : "") +
                        (m.score != null ? m.score : 0.0)).hashCode();

                outMarks.add(m);

                // CumulativeMark calculation per course
                if (!courseCode.isEmpty()) {
                    CumulativeMark cm = cumulativeMarksMap.get(courseCode);
                    if (cm == null) {
                        cm = new CumulativeMark(cumulativeMarksMap.size() + 1);
                        cm.courseCode = courseCode;
                        cumulativeMarksMap.put(courseCode, cm);
                    }
                    cm.addWeightage(m.weightage != null ? m.weightage : m.score,
                            m.maxWeightage != null ? m.maxWeightage : m.maxScore,
                            courseType, courseCredits);
                }
            }
        }

        // Finalize CumulativeMarks with grand totals and averages
        if (outCumulativeMarks != null) {
            for (CumulativeMark cm : cumulativeMarksMap.values()) {
                double tTotal = cm.theoryTotal != null ? cm.theoryTotal : 0.0;
                double lTotal = cm.labTotal != null ? cm.labTotal : 0.0;
                double pTotal = cm.projectTotal != null ? cm.projectTotal : 0.0;

                double tMax = cm.theoryMax != null ? cm.theoryMax : 0.0;
                double lMax = cm.labMax != null ? cm.labMax : 0.0;
                double pMax = cm.projectMax != null ? cm.projectMax : 0.0;

                double tCred = cm.theoryCredits;
                double lCred = cm.labCredits;
                double pCred = cm.projectCredits;
                double totalCred = tCred + lCred + pCred;

                double grandTotal;
                double grandMax;

                if (totalCred > 0) {
                    grandTotal = (tTotal * tCred + lTotal * lCred + pTotal * pCred) / totalCred;
                    grandMax = (tMax * tCred + lMax * lCred + pMax * pCred) / totalCred;
                } else {
                    int parts = 0;
                    grandTotal = 0;
                    grandMax = 0;
                    if (cm.theoryTotal != null) { grandTotal += tTotal; grandMax += tMax; parts++; }
                    if (cm.labTotal != null) { grandTotal += lTotal; grandMax += lMax; parts++; }
                    if (cm.projectTotal != null) { grandTotal += pTotal; grandMax += pMax; parts++; }
                    if (parts > 0) {
                        grandTotal /= parts;
                        grandMax /= parts;
                    }
                }

                cm.grandTotal = Math.round(grandTotal * 100.0) / 100.0;
                cm.grandMax = Math.round(grandMax * 100.0) / 100.0;

                outCumulativeMarks.add(cm);
            }
        }
    }

    /**
     * Legacy/convenience overload for parseMarks.
     */
    public static void parseMarks(String html, Map<String, Integer> courseIdBySlot, List<Mark> outMarks) {
        List<Slot> tempSlots = new ArrayList<>();
        if (courseIdBySlot != null) {
            for (Map.Entry<String, Integer> entry : courseIdBySlot.entrySet()) {
                Slot s = new Slot();
                s.slot = entry.getKey();
                s.courseId = entry.getValue();
                tempSlots.add(s);
            }
        }
        parseMarks(html, Collections.emptyList(), tempSlots, outMarks, new ArrayList<>());
    }

    private static Double parseDoubleSafe(String text) {
        if (text == null) return null;
        try {
            String cleaned = text.trim().replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses examination schedule from doSearchExamScheduleForStudent.
     */
    public static List<Exam> parseExamSchedule(String html, Map<String, Integer> courseIdBySlot) {
        List<Exam> exams = new ArrayList<>();
        if (html == null) return exams;
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table tr");
        if (rows.isEmpty()) return exams;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH);

        String currentTitle = "Exam";
        for (Element row : rows) {
            Elements ths = row.getElementsByTag("th");
            if (!ths.isEmpty() && ths.first().hasAttr("colspan")) {
                currentTitle = ths.first().text().trim();
                continue;
            }

            Elements cells = row.getElementsByTag("td");
            if (cells.size() >= 6) {
                String slot = cells.get(1).text().split("\\+")[0].trim();
                Integer courseId = (courseIdBySlot != null) ? courseIdBySlot.get(slot) : null;
                if (courseId == null) continue;

                Exam exam = new Exam();
                exam.courseId = courseId;
                exam.title = currentTitle;
                String dateStr = cells.get(2).text().trim();
                String timingStr = cells.get(3).text().trim();
                exam.venue = cells.get(4).text().trim();
                exam.seatLocation = cells.get(5).text().trim();

                try {
                    String[] timeParts = timingStr.split("to|-");
                    if (timeParts.length >= 2) {
                        Date start = sdf.parse(dateStr + " " + timeParts[0].trim());
                        Date end = sdf.parse(dateStr + " " + timeParts[1].trim());
                        if (start != null) exam.startTime = start.getTime();
                        if (end != null) exam.endTime = end.getTime();
                    }
                } catch (Exception ignored) {
                }
                exams.add(exam);
            }
        }
        return exams;
    }

    /**
     * Parses proctor and HOD/Dean staff records.
     */
    public static List<com.salmanmalvasi.studentcc.models.Staff> parseStaff(String html, String staffType) {
        List<com.salmanmalvasi.studentcc.models.Staff> staffList = new ArrayList<>();
        if (html == null) return staffList;
        Document doc = Jsoup.parse(html);
        Elements cells = doc.getElementsByTag("td");

        for (int i = 0; i < cells.size() - 1; i += 2) {
            String key = cells.get(i).text().trim();
            String val = cells.get(i + 1).text().trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                com.salmanmalvasi.studentcc.models.Staff s = new com.salmanmalvasi.studentcc.models.Staff();
                s.type = staffType;
                s.key = key;
                s.value = val;
                staffList.add(s);
            }
        }
        return staffList;
    }

    /**
     * Parses finance receipts from StudentReceiptView.
     */
    public static List<Receipt> parseReceipts(String html) {
        List<Receipt> receipts = new ArrayList<>();
        if (html == null) return receipts;
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table tr");
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

        for (Element row : rows) {
            Elements cells = row.getElementsByTag("td");
            if (cells.size() >= 4) {
                try {
                    int receiptNo = Integer.parseInt(cells.get(1).text().replaceAll("[^0-9]", "").trim());
                    double amt = Double.parseDouble(cells.get(3).text().replaceAll("[^0-9.]", "").trim());
                    String dateStr = cells.get(2).text().trim();
                    Receipt r = new Receipt();
                    r.number = receiptNo;
                    r.amount = amt;
                    try {
                        Date d = sdf.parse(dateStr);
                        if (d != null) r.date = d.getTime();
                    } catch (Exception ignored) {
                    }
                    receipts.add(r);
                } catch (Exception ignored) {
                }
            }
        }
        return receipts;
    }

    /**
     * Parses circulars/spotlight from home page.
     */
    public static List<com.salmanmalvasi.studentcc.models.Spotlight> parseSpotlight(String html) {
        List<com.salmanmalvasi.studentcc.models.Spotlight> list = new ArrayList<>();
        if (html == null) return list;
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".news-ticker li, marquee a, #spotlight a");

        for (Element item : items) {
            String text = item.text().trim();
            String link = item.attr("href").trim();
            if (!text.isEmpty()) {
                com.salmanmalvasi.studentcc.models.Spotlight sp = new com.salmanmalvasi.studentcc.models.Spotlight();
                sp.announcement = text;
                sp.category = "General";
                sp.link = link;
                sp.signature = text.hashCode();
                list.add(sp);
            }
        }
        return list;
    }

    /**
     * Parses hostel leave records from hostels/student/leave/1.
     */
    public static List<com.salmanmalvasi.studentcc.models.Leave> parseLeave(String html) {
        List<com.salmanmalvasi.studentcc.models.Leave> leaves = new ArrayList<>();
        if (html == null) return leaves;
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table tr");

        for (Element row : rows) {
            Elements cells = row.getElementsByTag("td");
            if (cells.size() >= 7) {
                com.salmanmalvasi.studentcc.models.Leave l = new com.salmanmalvasi.studentcc.models.Leave();
                l.leaveId = cells.get(1).text().trim();
                l.appliedDate = cells.get(2).text().trim();
                l.leaveType = cells.get(3).text().trim();
                l.fromDate = cells.get(4).text().trim();
                l.toDate = cells.get(5).text().trim();
                l.status = cells.get(6).text().trim();
                if (!l.leaveId.isEmpty()) {
                    leaves.add(l);
                }
            }
        }
        return leaves;
    }
}
