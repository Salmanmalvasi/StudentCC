package com.salmanmalvasi.studentcc.sync;

import android.util.Log;

import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Leave;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Staff;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ultra-fast native JSON parsers for VTOP API endpoints discovered from
 * the official VITianCC app reverse-engineering report.
 * Provides direct O(1) deserialization into Room database entities.
 */
public final class VTOPJsonParsers {
    private static final String TAG = "VTOPJsonParsers";

    private VTOPJsonParsers() {
        // Static parser utility
    }

    public static boolean isJson(String response) {
        if (response == null) return false;
        String trimmed = response.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * Parses student profile JSON from /login/vitian/myProfileInfo
     */
    public static Map<String, String> parseProfile(String jsonStr) {
        Map<String, String> profile = new HashMap<>();
        if (!isJson(jsonStr)) return profile;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;

            String name = optString(data, "studentName", "name", "STUDENT_NAME", "userName");
            if (!name.isEmpty()) profile.put("name", name);

            String regNo = optString(data, "regNo", "registerNumber", "REG_NO", "userId");
            if (!regNo.isEmpty()) profile.put("regNo", regNo);

            String email = optString(data, "email", "studentEmail", "EMAIL_ID");
            if (!email.isEmpty()) profile.put("email", email);

            String phone = optString(data, "mobileNumber", "phone", "MOBILE_NO");
            if (!phone.isEmpty()) profile.put("mobileNumber", phone);

            String school = optString(data, "school", "schoolName", "SCHOOL");
            if (!school.isEmpty()) profile.put("school", school);

            String program = optString(data, "program", "programme", "degree", "BRANCH");
            if (!program.isEmpty()) profile.put("program", program);

        } catch (Exception e) {
            Log.w(TAG, "Error parsing profile JSON: " + e.getMessage());
        }
        return profile;
    }

    /**
     * Parses semester list from /login/vitian/getSemesterList
     */
    public static Map<String, String> parseSemesters(String jsonStr) {
        Map<String, String> semesters = new HashMap<>();
        if (!isJson(jsonStr)) return semesters;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("semesterList");
            if (array == null) array = root.optJSONArray("data");
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String id = optString(item, "semesterId", "semSubId", "id", "value");
                    String desc = optString(item, "semesterDesc", "description", "semDesc", "name");
                    if (!id.isEmpty() && !desc.isEmpty()) {
                        semesters.put(desc, id);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing semester list JSON: " + e.getMessage());
        }
        return semesters;
    }

    /**
     * Parses CGPA and credit statistics from /login/vitian/cgpaView
     */
    public static Map<String, String> parseCgpa(String jsonStr) {
        Map<String, String> cgpaMap = new HashMap<>();
        if (!isJson(jsonStr)) return cgpaMap;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;

            String cgpa = optString(data, "cgpa", "cgpaView", "CGPA", "cumulativeGpa");
            if (!cgpa.isEmpty()) cgpaMap.put("cgpa", cgpa);

            String earned = optString(data, "creditEarned", "creditsEarned", "totalEarnedCredits");
            if (!earned.isEmpty()) cgpaMap.put("creditsEarned", earned);

            String registered = optString(data, "creditRegistered", "creditsRegistered", "totalUnits");
            if (!registered.isEmpty()) cgpaMap.put("creditsRegistered", registered);

        } catch (Exception e) {
            Log.w(TAG, "Error parsing CGPA JSON: " + e.getMessage());
        }
        return cgpaMap;
    }

    /**
     * Parses courses and slots from /login/vitian/studentRegisteredCourse or /login/vitian/timeTable
     */
    /**
     * Debug-only, privacy-safe structural inspection of the courses response (e.g. studentRegisteredCourse).
     * Identifies JSON structure, object/array hierarchy, field names, and field types.
     * Records candidate identifier and attendance fields (presence and type only, never values).
     */
    public static void logCourseResponseStructure(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            Log.d(TAG, "[CourseResponseStructure] Empty response payload");
            return;
        }
        try {
            String trimmed = jsonStr.trim();
            if (trimmed.startsWith("[")) {
                JSONArray rootArr = new JSONArray(trimmed);
                Log.d(TAG, "[CourseResponseStructure] Root is JSON ARRAY with " + rootArr.length() + " items");
                inspectCourseArrayItems(rootArr);
            } else if (trimmed.startsWith("{")) {
                JSONObject rootObj = new JSONObject(trimmed);
                List<String> rootKeys = new ArrayList<>();
                Iterator<String> it = rootObj.keys();
                while (it.hasNext()) {
                    rootKeys.add(it.next());
                }
                Collections.sort(rootKeys);
                StringBuilder rootKeySummary = new StringBuilder();
                for (String k : rootKeys) {
                    if (rootKeySummary.length() > 0) rootKeySummary.append(", ");
                    Object val = rootObj.opt(k);
                    String type = val == null ? "NULL" : val.getClass().getSimpleName();
                    if (val instanceof JSONArray) {
                        type = "ARRAY[" + ((JSONArray) val).length() + "]";
                    } else if (val instanceof JSONObject) {
                        type = "OBJECT";
                    }
                    rootKeySummary.append(k).append(": ").append(type);
                }
                Log.d(TAG, "[CourseResponseStructure] Root OBJECT keys: {" + rootKeySummary + "}");

                // Locate the course array
                JSONArray courseArray = rootObj.optJSONArray("courseList");
                if (courseArray == null) courseArray = rootObj.optJSONArray("studentRegisteredCourse");
                if (courseArray == null) courseArray = rootObj.optJSONArray("timeTable");
                if (courseArray == null) courseArray = rootObj.optJSONArray("data");
                if (courseArray == null && rootObj.optJSONObject("data") != null) {
                    JSONObject dataObj = rootObj.optJSONObject("data");
                    courseArray = dataObj.optJSONArray("courseList");
                    if (courseArray == null) courseArray = dataObj.optJSONArray("studentRegisteredCourse");
                }

                if (courseArray != null) {
                    Log.d(TAG, "[CourseResponseStructure] Found course array with " + courseArray.length() + " items");
                    inspectCourseArrayItems(courseArray);
                } else {
                    Log.d(TAG, "[CourseResponseStructure] No course array found among standard root keys");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "[CourseResponseStructure] Error analyzing structure: " + e.getMessage());
        }
    }

    private static void inspectCourseArrayItems(JSONArray arr) {
        int sampleLimit = Math.min(2, arr.length());
        for (int i = 0; i < sampleLimit; i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            List<String> keys = new ArrayList<>();
            Iterator<String> kit = item.keys();
            while (kit.hasNext()) {
                keys.add(kit.next());
            }
            Collections.sort(keys);

            StringBuilder schemaBuilder = new StringBuilder();
            List<String> candidateIdFields = new ArrayList<>();
            List<String> candidateAttFields = new ArrayList<>();

            for (String key : keys) {
                if (schemaBuilder.length() > 0) schemaBuilder.append(", ");
                Object v = item.opt(key);
                String vType = (v == null || item.isNull(key)) ? "NULL" : v.getClass().getSimpleName();
                if (v instanceof JSONArray) {
                    vType = "ARRAY[" + ((JSONArray) v).length() + "]";
                } else if (v instanceof JSONObject) {
                    JSONObject nestedObj = (JSONObject) v;
                    List<String> nKeys = new ArrayList<>();
                    Iterator<String> nit = nestedObj.keys();
                    while (nit.hasNext()) nKeys.add(nit.next());
                    vType = "OBJECT" + nKeys.toString();
                }
                schemaBuilder.append(key).append(": ").append(vType);

                String lowerKey = key.toLowerCase(Locale.ROOT);
                if (lowerKey.contains("id") || lowerKey.contains("code") || lowerKey.contains("nbr") ||
                    lowerKey.contains("crn") || lowerKey.contains("alloc") || lowerKey.contains("course") ||
                    lowerKey.contains("class") || lowerKey.contains("group") || lowerKey.contains("sem")) {
                    boolean hasVal = v != null && !item.isNull(key) && !v.toString().trim().isEmpty();
                    candidateIdFields.add(key + " (" + vType + ", present=" + hasVal + ")");
                }
                if (lowerKey.contains("att") || lowerKey.contains("pct") || lowerKey.contains("percent") ||
                    lowerKey.contains("present") || lowerKey.contains("total") || lowerKey.contains("conduct") ||
                    lowerKey.contains("absent") || lowerKey.contains("cal")) {
                    boolean hasVal = v != null && !item.isNull(key) && !v.toString().trim().isEmpty();
                    candidateAttFields.add(key + " (" + vType + ", present=" + hasVal + ")");
                }
            }

            Log.d(TAG, String.format(Locale.US,
                    "[CourseItem #%d Schema] Total Keys: %d | Fields: {%s}", i, keys.size(), schemaBuilder));
            Log.d(TAG, String.format(Locale.US,
                    "[CourseItem #%d Candidate IDs] %s", i, candidateIdFields.isEmpty() ? "none" : candidateIdFields.toString()));
            Log.d(TAG, String.format(Locale.US,
                    "[CourseItem #%d Candidate Attendance] %s", i, candidateAttFields.isEmpty() ? "none" : candidateAttFields.toString()));
        }
    }

    /**
     * Parses courses and slots from /login/vitian/studentRegisteredCourse or /login/vitian/timeTable.
     * Captures server-side course identifiers (courseId, classId) for subsequent API calls.
     */
    public static void parseCoursesAndSlots(String jsonStr, List<Course> outCourses, List<Slot> outSlots) {
        parseCoursesAndSlots(jsonStr, outCourses, outSlots, null);
    }

    public static void parseCoursesAndSlots(String jsonStr, List<Course> outCourses, List<Slot> outSlots, StagedSyncData stagedData) {
        if (!isJson(jsonStr)) return;

        // Perform privacy-safe structural inspection
        logCourseResponseStructure(jsonStr);

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray courseArray = root.optJSONArray("courseList");
            if (courseArray == null) courseArray = root.optJSONArray("studentRegisteredCourse");
            if (courseArray == null) courseArray = root.optJSONArray("timeTable");
            if (courseArray == null) courseArray = root.optJSONArray("data");
            if (courseArray == null && root.optJSONObject("data") != null) {
                JSONObject dataObj = root.optJSONObject("data");
                courseArray = dataObj.optJSONArray("courseList");
                if (courseArray == null) courseArray = dataObj.optJSONArray("studentRegisteredCourse");
            }
            if (courseArray == null && jsonStr.trim().startsWith("[")) {
                courseArray = new JSONArray(jsonStr);
            }

            if (courseArray == null) return;

            Map<String, Integer> courseIdMap = new HashMap<>();
            int nextCourseId = 1;

            for (int i = 0; i < courseArray.length(); i++) {
                JSONObject obj = courseArray.optJSONObject(i);
                if (obj == null) continue;

                String code = optString(obj, "courseCode", "code", "COURSE_CODE", "subCode", "subjectCode");
                String title = optString(obj, "courseTitle", "title", "COURSE_TITLE", "courseDescription", "subTitle");
                String typeStr = optString(obj, "courseType", "type", "COURSE_TYPE");
                String venue = optString(obj, "venue", "classRoom", "ROOM_NO", "classVenue");
                String faculty = optString(obj, "facultyName", "faculty", "empName", "STAFF_NAME");
                String slotStr = optString(obj, "slotName", "slot", "SLOT_NAME");

                // Capture raw metadata map to preserve all server fields
                Map<String, String> rawMeta = new HashMap<>();
                Iterator<String> keyIter = obj.keys();
                while (keyIter.hasNext()) {
                    String k = keyIter.next();
                    if (!obj.isNull(k)) {
                        rawMeta.put(k, obj.optString(k, ""));
                    }
                }

                // Check candidate server course ID fields
                String serverCourseId = optString(obj,
                        "courseId", "COURSE_ID", "course_id", "crsId",
                        "courseAllocationId", "allocationId", "courseRegistrationId", "regId",
                        "subId", "subjectId", "erpId", "referenceId",
                        "classId", "CLASS_ID", "class_id");

                // Check candidate class ID fields
                String classId = optString(obj,
                        "classId", "CLASS_ID", "class_id",
                        "classNbr", "classNumber", "classGroupId", "crn",
                        "courseAllocationId", "allocationId");

                // Fallback scan of raw metadata keys if specific keys not matched
                if (serverCourseId.isEmpty()) {
                    for (Map.Entry<String, String> e : rawMeta.entrySet()) {
                        String lk = e.getKey().toLowerCase(Locale.ROOT);
                        if ((lk.contains("course") && lk.contains("id")) || lk.contains("allocation") || lk.equals("crsid")) {
                            if (!e.getValue().isEmpty()) {
                                serverCourseId = e.getValue();
                                break;
                            }
                        }
                    }
                }
                if (classId.isEmpty()) {
                    for (Map.Entry<String, String> e : rawMeta.entrySet()) {
                        String lk = e.getKey().toLowerCase(Locale.ROOT);
                        if ((lk.contains("class") && lk.contains("id")) || lk.contains("classnbr") || lk.equals("crn")) {
                            if (!e.getValue().isEmpty()) {
                                classId = e.getValue();
                                break;
                            }
                        }
                    }
                }

                double credits = obj.optDouble("credits", obj.optDouble("courseCredits", 3.0));

                if (code.isEmpty()) continue;

                // Unique key per course code + type
                String courseKey = code + "_" + typeStr.toLowerCase(Locale.ROOT);
                int courseId;
                if (!courseIdMap.containsKey(courseKey)) {
                    courseId = nextCourseId++;
                    courseIdMap.put(courseKey, courseId);

                    Course course = new Course();
                    course.id = courseId;
                    course.code = code;
                    course.title = title.isEmpty() ? code : title;
                    course.type = typeStr.isEmpty() ? "Theory" : typeStr;
                    course.credits = credits;
                    course.venue = venue;
                    course.faculty = faculty;
                    outCourses.add(course);
                } else {
                    courseId = courseIdMap.get(courseKey);
                }

                if (stagedData != null) {
                    stagedData.rawCourseMetadata.put(courseId, rawMeta);
                    stagedData.rawCourseMetadataByCode.put(code, rawMeta);

                    if (!serverCourseId.isEmpty()) {
                        stagedData.serverCourseIds.put(courseId, serverCourseId);
                        stagedData.serverCourseIdByCode.put(code, serverCourseId);
                    }
                    if (!classId.isEmpty()) {
                        stagedData.classIds.put(courseId, classId);
                        stagedData.classIdByCode.put(code, classId);
                    }
                    if (!slotStr.isEmpty()) {
                        stagedData.slotNames.put(courseId, slotStr);
                    }

                    // Extract course-level attendance delivered directly in studentRegisteredCourse
                    // 1. Check flat keys
                    int attended = optInt(obj, -1,
                            "attendedClass", "attendedClasses", "classesAttended", "attended", "present", "presentCount");
                    int total = optInt(obj, -1,
                            "totalClass", "totalClasses", "classesConducted", "total", "totalCount", "conducted", "conductedClasses");
                    int percentage = optInt(obj, -1,
                            "attendancePercentage", "percentage", "attendancePercent", "attPercentage", "attPercent", "attPct");

                    // 2. Check nested attendance object if not found in flat keys
                    if (attended < 0 || total < 0) {
                        JSONObject attObj = obj.optJSONObject("attendance");
                        if (attObj == null) attObj = obj.optJSONObject("attendanceSummary");
                        if (attObj == null) attObj = obj.optJSONObject("attendanceData");
                        if (attObj == null) attObj = obj.optJSONObject("attDetails");

                        if (attObj != null) {
                            if (attended < 0) {
                                attended = optInt(attObj, -1, "attendedClass", "attendedClasses", "classesAttended", "attended", "present", "presentCount");
                            }
                            if (total < 0) {
                                total = optInt(attObj, -1, "totalClass", "totalClasses", "classesConducted", "total", "totalCount", "conducted", "conductedClasses");
                            }
                            if (percentage < 0) {
                                percentage = optInt(attObj, -1, "attendancePercentage", "percentage", "attendancePercent", "attPercentage", "attPercent", "attPct");
                            }
                        }
                    }

                    if (attended >= 0 || total >= 0 || percentage >= 0) {
                        int finalAttended = Math.max(0, attended);
                        int finalTotal = Math.max(0, total);
                        int finalPct = percentage >= 0 ? percentage : (finalTotal > 0 ? (int) Math.round((finalAttended * 100.0) / finalTotal) : 0);

                        boolean alreadyExists = false;
                        for (Attendance existing : stagedData.attendanceSummaries) {
                            if (existing.courseId != null && existing.courseId == courseId) {
                                alreadyExists = true;
                                break;
                            }
                        }
                        if (!alreadyExists) {
                            Attendance att = new Attendance();
                            att.courseId = courseId;
                            att.attended = finalAttended;
                            att.total = finalTotal;
                            att.percentage = finalPct;
                            stagedData.attendanceSummaries.add(att);
                        }
                    }

                    Log.d(TAG, String.format(Locale.US,
                            "Course [%s] -> serverCourseId=%s | classId=%s | attendance=%s | keys=%s",
                            code,
                            serverCourseId.isEmpty() ? "none" : "present",
                            classId.isEmpty() ? "none" : "present",
                            (attended >= 0 || total >= 0 || percentage >= 0) ? "staged" : "absent",
                            rawMeta.keySet()));
                }

                if (!slotStr.isEmpty()) {
                    String[] slots = slotStr.split("[+\\s/,]+");
                    for (String s : slots) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) {
                            Slot slotObj = new Slot();
                            slotObj.courseId = courseId;
                            slotObj.slot = trimmed;
                            outSlots.add(slotObj);
                        }
                    }
                }
            }

            // Calculate aggregate attendance from staged course attendance records
            if (stagedData != null && !stagedData.attendanceSummaries.isEmpty()) {
                AttendanceSummaryResult aggregate = calculateAggregateAttendance(stagedData.attendanceSummaries);
                stagedData.profileAttributes.put("overallAttendance", String.valueOf(aggregate.overallAttendance));
                stagedData.profileAttributes.put("attendedClasses", String.valueOf(aggregate.attendedClasses));
                stagedData.profileAttributes.put("totalClasses", String.valueOf(aggregate.totalClasses));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing courses/slots JSON: " + e.getMessage());
        }
    }

    /**
     * Parses attendance summary from /login/vitian/acadAttendanceCalculator
     */
    public static List<Attendance> parseAttendanceSummary(String jsonStr, List<Course> courses, List<Slot> slots) {
        return parseAttendanceSummary(jsonStr, courses, slots, null);
    }

    public static List<Attendance> parseAttendanceSummary(String jsonStr, List<Course> courses, List<Slot> slots, StagedSyncData stagedData) {
        List<Attendance> attendances = new ArrayList<>();
        if (!isJson(jsonStr)) return attendances;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("attendanceList");
            if (array == null) array = root.optJSONArray("courseList");
            if (array == null) array = root.optJSONArray("studentRegisteredCourse");
            if (array == null) array = root.optJSONArray("data");
            if (array == null) array = root.optJSONArray("acadAttendanceCalculator");
            if (array == null && root.optJSONObject("data") != null) {
                array = root.optJSONObject("data").optJSONArray("attendanceList");
                if (array == null) array = root.optJSONObject("data").optJSONArray("courseList");
            }
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) return attendances;

            Map<String, Course> courseByCode = new HashMap<>();
            for (Course c : courses) {
                if (c.code != null) courseByCode.put(c.code.toUpperCase(Locale.ROOT), c);
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                String code = optString(obj, "courseCode", "code", "subCode").toUpperCase(Locale.ROOT);
                Course matchedCourse = courseByCode.get(code);
                if (matchedCourse == null) continue;

                String serverCourseId = optString(obj, "courseId", "classId", "COURSE_ID");
                String classId = optString(obj, "classId", "CLASS_ID");
                String slotName = optString(obj, "slotName", "slot", "SLOT_NAME");

                if (stagedData != null) {
                    if (!serverCourseId.isEmpty()) {
                        stagedData.serverCourseIds.put(matchedCourse.id, serverCourseId);
                        stagedData.serverCourseIdByCode.put(code, serverCourseId);
                    }
                    if (!classId.isEmpty()) {
                        stagedData.classIds.put(matchedCourse.id, classId);
                        stagedData.classIdByCode.put(code, classId);
                    }
                    if (!slotName.isEmpty()) {
                        stagedData.slotNames.put(matchedCourse.id, slotName);
                    }
                }

                int attended = obj.optInt("present", obj.optInt("attendedClasses", obj.optInt("attended", 0)));
                int total = obj.optInt("total", obj.optInt("totalClasses", 0));
                int percentage = obj.optInt("percentage", obj.optInt("attendancePercentage", total > 0 ? (attended * 100 / total) : 0));

                Attendance att = new Attendance();
                att.courseId = matchedCourse.id;
                att.attended = attended;
                att.total = total;
                att.percentage = percentage;
                attendances.add(att);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing attendance summary JSON: " + e.getMessage());
        }
        return attendances;
    }

    /**
     * Parses detailed attendance records from /login/vitian/viewAttendanceCal.
     * Supports multiple JSON root shapes, nested data containers, and multi-format date parsing.
     */
    public static List<AttendanceRecord> parseAttendanceRecords(String jsonStr, int courseId, String defaultSlot) {
        return parseAttendanceRecords(jsonStr, courseId, defaultSlot, null);
    }

    public static List<AttendanceRecord> parseAttendanceRecords(String jsonStr, int courseId, String defaultSlot,
                                                               DataIngestionDiagnostics.RejectionTracker tracker) {
        List<AttendanceRecord> records = new ArrayList<>();
        if (!isJson(jsonStr)) {
            if (tracker != null) tracker.recordRejection("not_json_format");
            return records;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("attendanceCalData");
            if (array == null) array = root.optJSONArray("attendanceDetail");
            if (array == null) array = root.optJSONArray("attendanceDetails");
            if (array == null) array = root.optJSONArray("calendarEvents");
            if (array == null) array = root.optJSONArray("attendanceList");
            if (array == null) array = root.optJSONArray("attendanceCalList");
            if (array == null) array = root.optJSONArray("viewAttendanceCal");
            if (array == null) array = root.optJSONArray("data");

            if (array == null && root.optJSONObject("data") != null) {
                JSONObject dataObj = root.optJSONObject("data");
                array = dataObj.optJSONArray("attendanceCalData");
                if (array == null) array = dataObj.optJSONArray("attendanceDetail");
                if (array == null) array = dataObj.optJSONArray("attendanceDetails");
                if (array == null) array = dataObj.optJSONArray("calendarEvents");
                if (array == null) array = dataObj.optJSONArray("attendanceList");
                if (array == null) array = dataObj.optJSONArray("attendanceCalList");
            }

            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) {
                if (tracker != null) tracker.recordRejection("no_attendance_array_key_found");
                return records;
            }

            String[] datePatterns = new String[]{"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd", "dd-MMM-yyyy"};

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    if (tracker != null) tracker.recordRejection("null_json_item");
                    continue;
                }

                String dateStr = optString(obj, "attendanceDate", "calendarDate", "date", "classDate");
                String status = optString(obj, "attendanceStatus", "attendanceStatusDescription", "status", "statusDesc", "presentAbsent");
                String remarks = optString(obj, "attendanceStatusDescription", "remarks", "attendanceRemarks", "reason");
                String slot = optString(obj, "attendanceSlot", "slotName", "slot", defaultSlot);

                if (status.isEmpty() && obj.has("present")) {
                    status = obj.optBoolean("present") ? "Present" : "Absent";
                }

                if (dateStr.isEmpty() && status.isEmpty()) {
                    if (tracker != null) tracker.recordRejection("empty_date_and_status");
                    continue;
                }

                long epoch = 0;
                boolean parsedDate = false;
                for (String pattern : datePatterns) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
                        Date d = sdf.parse(dateStr);
                        if (d != null) {
                            epoch = d.getTime();
                            parsedDate = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                if (!parsedDate) {
                    epoch = System.currentTimeMillis();
                }

                AttendanceRecord record = new AttendanceRecord();
                record.courseId = courseId;
                record.date = epoch;
                record.dateString = dateStr.isEmpty() ? new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date(epoch)) : dateStr;
                record.status = status.isEmpty() ? "Present" : status;
                record.remarks = remarks;
                record.session = slot;
                records.add(record);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing attendance records JSON: " + e.getMessage());
            if (tracker != null) tracker.recordRejection("json_parse_exception: " + e.getMessage());
        }
        return records;
    }

    /**
     * Parses marks for a specific course when querying per-course endpoint /login/vitian/markView.
     * Maps all extracted assessments directly to the requested Course ID.
     */
    public static void parsePerCourseMarks(String jsonStr, Course course, List<Mark> outMarks,
                                           DataIngestionDiagnostics.RejectionTracker tracker) {
        if (!isJson(jsonStr) || course == null) {
            if (tracker != null) tracker.recordRejection("invalid_input_or_null_course");
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray markArray = root.optJSONArray("gradePageMarkBySemIdCourseId");
            if (markArray == null) markArray = root.optJSONArray("activeSemMarkView");
            if (markArray == null) markArray = root.optJSONArray("markView");
            if (markArray == null) markArray = root.optJSONArray("marks");
            if (markArray == null) markArray = root.optJSONArray("markList");
            if (markArray == null) markArray = root.optJSONArray("details");
            if (markArray == null) markArray = root.optJSONArray("studentMarkList");
            if (markArray == null) markArray = root.optJSONArray("data");

            if (markArray == null && root.optJSONObject("data") != null) {
                JSONObject dataObj = root.optJSONObject("data");
                markArray = dataObj.optJSONArray("gradePageMarkBySemIdCourseId");
                if (markArray == null) markArray = dataObj.optJSONArray("activeSemMarkView");
                if (markArray == null) markArray = dataObj.optJSONArray("markView");
                if (markArray == null) markArray = dataObj.optJSONArray("marks");
                if (markArray == null) markArray = dataObj.optJSONArray("markList");
                if (markArray == null) markArray = dataObj.optJSONArray("details");
            }

            if (markArray == null && jsonStr.trim().startsWith("[")) {
                markArray = new JSONArray(jsonStr);
            }

            if (markArray == null) {
                if (tracker != null) tracker.recordRejection("no_marks_array_found_for_course");
                return;
            }

            for (int i = 0; i < markArray.length(); i++) {
                JSONObject obj = markArray.optJSONObject(i);
                if (obj == null) {
                    if (tracker != null) tracker.recordRejection("null_mark_item");
                    continue;
                }

                String title = optString(obj, "markTitle", "title", "assessmentName", "titleDesc", "examType", "assessmentTitle", "componentName");
                if (title.isEmpty()) {
                    title = "Assessment " + (i + 1);
                }

                double maxMark = optDouble(obj, 100.0, "maxMark", "maximumMarks", "maxScore", "maxMarks");
                double marksGiven = optDouble(obj, 0.0, "marksGiven", "scoredMarks", "scoredMark", "marks", "score", "mark");
                double weightage = optDouble(obj, 0.0, "weightageMarkView", "weightageMark", "weightage", "weight");
                Double average = obj.has("average") ? obj.optDouble("average") : null;
                String status = optString(obj, "markModeCode", "status", "attendanceStatus", "Present");

                Mark mark = new Mark();
                mark.courseId = course.id;
                mark.title = title;
                mark.maxScore = maxMark;
                mark.score = marksGiven;
                mark.weightage = weightage;
                mark.average = average;
                mark.status = status;
                mark.signature = (course.id + "_" + title).hashCode();
                outMarks.add(mark);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing per-course marks JSON: " + e.getMessage());
            if (tracker != null) tracker.recordRejection("per_course_marks_exception: " + e.getMessage());
        }
    }

    /**
     * Parses marks from /login/vitian/markView or /login/vitian/studentMarkList across all courses.
     * Supports both flat item list and nested course groupings (course -> marks).
     */
    public static void parseMarks(String jsonStr, List<Course> courses, List<Mark> outMarks, List<CumulativeMark> outCumulativeMarks) {
        parseMarks(jsonStr, courses, outMarks, outCumulativeMarks, null);
    }

    public static void parseMarks(String jsonStr, List<Course> courses, List<Mark> outMarks,
                                  List<CumulativeMark> outCumulativeMarks,
                                  DataIngestionDiagnostics.RejectionTracker tracker) {
        if (!isJson(jsonStr)) {
            if (tracker != null) tracker.recordRejection("not_json_format");
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray markArray = root.optJSONArray("activeSemMarkView");
            if (markArray == null) markArray = root.optJSONArray("gradeDetailsBySemId");
            if (markArray == null) markArray = root.optJSONArray("gradeView");
            if (markArray == null) markArray = root.optJSONArray("markView");
            if (markArray == null) markArray = root.optJSONArray("studentMarkList");
            if (markArray == null) markArray = root.optJSONArray("marks");
            if (markArray == null) markArray = root.optJSONArray("markList");
            if (markArray == null) markArray = root.optJSONArray("data");

            if (markArray == null && root.optJSONObject("data") != null) {
                JSONObject dataObj = root.optJSONObject("data");
                markArray = dataObj.optJSONArray("activeSemMarkView");
                if (markArray == null) markArray = dataObj.optJSONArray("gradeDetailsBySemId");
                if (markArray == null) markArray = dataObj.optJSONArray("gradeView");
                if (markArray == null) markArray = dataObj.optJSONArray("studentMarkList");
                if (markArray == null) markArray = dataObj.optJSONArray("markView");
                if (markArray == null) markArray = dataObj.optJSONArray("marks");
            }

            if (markArray == null && jsonStr.trim().startsWith("[")) {
                markArray = new JSONArray(jsonStr);
            }

            if (markArray == null) {
                if (tracker != null) tracker.recordRejection("no_marks_array_in_root");
                return;
            }

            Map<String, Course> courseByCode = new HashMap<>();
            for (Course c : courses) {
                if (c.code != null) {
                    courseByCode.put(c.code.toUpperCase(Locale.ROOT), c);
                    // Also map normalized code without spaces/hyphens
                    String normalized = c.code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
                    courseByCode.put(normalized, c);
                }
            }

            for (int i = 0; i < markArray.length(); i++) {
                JSONObject obj = markArray.optJSONObject(i);
                if (obj == null) {
                    if (tracker != null) tracker.recordRejection("null_mark_item");
                    continue;
                }

                String code = optString(obj, "courseCode", "code", "subCode", "subjectCode", "COURSE_CODE").toUpperCase(Locale.ROOT);
                Course matched = courseByCode.get(code);
                if (matched == null && !code.isEmpty()) {
                    String normCode = code.replaceAll("[^A-Za-z0-9]", "");
                    matched = courseByCode.get(normCode);
                }

                // Check if this item is a COURSE container with nested marks
                JSONArray nestedMarks = obj.optJSONArray("markList");
                if (nestedMarks == null) nestedMarks = obj.optJSONArray("marks");
                if (nestedMarks == null) nestedMarks = obj.optJSONArray("markDetails");
                if (nestedMarks == null) nestedMarks = obj.optJSONArray("assessments");

                if (nestedMarks != null) {
                    if (matched == null) {
                        if (tracker != null) tracker.recordRejection("nested_course_not_found_" + code);
                        continue;
                    }
                    for (int j = 0; j < nestedMarks.length(); j++) {
                        JSONObject mObj = nestedMarks.optJSONObject(j);
                        if (mObj == null) continue;

                        String title = optString(mObj, "markTitle", "title", "assessmentName", "componentName");
                        if (title.isEmpty()) title = "Assessment " + (j + 1);

                        double maxMark = optDouble(mObj, 100.0, "maxMark", "maximumMarks", "maxScore", "maxMarks");
                        double marksGiven = optDouble(mObj, 0.0, "marksGiven", "scoredMarks", "scoredMark", "marks", "score", "mark");
                        double weightage = optDouble(mObj, 0.0, "weightageMarkView", "weightageMark", "weightage", "weight");

                        Mark mark = new Mark();
                        mark.courseId = matched.id;
                        mark.title = title;
                        mark.maxScore = maxMark;
                        mark.score = marksGiven;
                        mark.weightage = weightage;
                        mark.signature = (matched.id + "_" + title).hashCode();
                        outMarks.add(mark);
                    }
                    continue;
                }

                // Otherwise, flat mark item
                if (matched == null) {
                    if (tracker != null) tracker.recordRejection("course_code_not_matched_" + (code.isEmpty() ? "empty" : code));
                    continue;
                }

                String title = optString(obj, "markTitle", "title", "assessmentName", "componentName");
                if (title.isEmpty()) title = "Assessment " + (i + 1);

                double maxMark = optDouble(obj, 100.0, "maxMark", "maximumMarks", "maxScore", "maxMarks");
                double marksGiven = optDouble(obj, 0.0, "marksGiven", "scoredMarks", "scoredMark", "marks", "score", "mark");
                double weightage = optDouble(obj, 0.0, "weightageMarkView", "weightageMark", "weightage", "weight");

                String status = optString(obj, "markModeCode", "status", "attendanceStatus", "Present");

                Mark mark = new Mark();
                mark.courseId = matched.id;
                mark.title = title;
                mark.maxScore = maxMark;
                mark.score = marksGiven;
                mark.weightage = weightage;
                mark.status = status;
                mark.signature = (matched.id + "_" + title).hashCode();
                outMarks.add(mark);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing marks JSON: " + e.getMessage());
            if (tracker != null) tracker.recordRejection("marks_parse_exception: " + e.getMessage());
        }
    }

    /**
     * Parses exam schedule from /login/vitian/examScheduleView
     */
    public static List<Exam> parseExamSchedule(String jsonStr, Map<String, Integer> courseIdBySlot) {
        List<Exam> exams = new ArrayList<>();
        if (!isJson(jsonStr)) return exams;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("examScheduleView");
            if (array == null) array = root.optJSONArray("data");
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) return exams;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                String slot = optString(obj, "slotName", "slot");
                Integer courseId = courseIdBySlot.get(slot);
                if (courseId == null) courseId = 0;

                String dateStr = optString(obj, "examDate", "date");
                long epoch = 0;
                try {
                    Date d = sdf.parse(dateStr);
                    if (d != null) epoch = d.getTime();
                } catch (Exception ignored) {
                    epoch = System.currentTimeMillis();
                }

                Exam exam = new Exam();
                exam.courseId = courseId;
                exam.title = optString(obj, "examTitle", "examSession", "session", "Exam");
                exam.startTime = epoch;
                exam.endTime = epoch + 3 * 3600 * 1000L;
                exam.venue = optString(obj, "venue", "examVenue", "roomNo");
                exam.seatLocation = optString(obj, "seatLocation", "seatNo");
                exams.add(exam);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing exam schedule JSON: " + e.getMessage());
        }
        return exams;
    }

    /**
     * Parses proctor and staff information from /login/vitian/proctorDetail or /login/vitian/hodDeanInfo
     */
    public static List<Staff> parseStaff(String jsonStr, String role) {
        List<Staff> staffList = new ArrayList<>();
        if (!isJson(jsonStr)) return staffList;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;

            String name = optString(data, "proctorName", "empName", "staffName", "name");
            String email = optString(data, "proctorEmail", "empEmail", "email");
            String mobile = optString(data, "proctorMobile", "empMobile", "mobile", "phone");
            String cabin = optString(data, "cabin", "roomNo", "office");

            if (!name.isEmpty()) {
                Staff s1 = new Staff();
                s1.type = role;
                s1.key = "Name";
                s1.value = name;
                staffList.add(s1);
            }
            if (!email.isEmpty()) {
                Staff s2 = new Staff();
                s2.type = role;
                s2.key = "Email";
                s2.value = email;
                staffList.add(s2);
            }
            if (!mobile.isEmpty()) {
                Staff s3 = new Staff();
                s3.type = role;
                s3.key = "Mobile";
                s3.value = mobile;
                staffList.add(s3);
            }
            if (!cabin.isEmpty()) {
                Staff s4 = new Staff();
                s4.type = role;
                s4.key = "Cabin";
                s4.value = cabin;
                staffList.add(s4);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing staff JSON: " + e.getMessage());
        }
        return staffList;
    }

    /**
     * Parses fee receipts from /login/vitian/viewReceipt
     */
    public static List<Receipt> parseReceipts(String jsonStr) {
        List<Receipt> receipts = new ArrayList<>();
        if (!isJson(jsonStr)) return receipts;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("viewReceiptData");
            if (array == null) array = root.optJSONArray("data");
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) return receipts;

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                String numStr = optString(obj, "receiptNumber", "receiptNo", "number");
                String amtStr = optString(obj, "receiptAmount", "amount");
                String dateStr = optString(obj, "receiptDate", "date");

                int receiptNo = 0;
                try {
                    receiptNo = Integer.parseInt(numStr.replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {}

                double amt = 0.0;
                try {
                    amt = Double.parseDouble(amtStr.replaceAll("[^0-9.]", ""));
                } catch (Exception ignored) {}

                long dateEpoch = 0;
                try {
                    Date d = sdf.parse(dateStr);
                    if (d != null) dateEpoch = d.getTime();
                } catch (Exception ignored) {
                    dateEpoch = System.currentTimeMillis();
                }

                Receipt receipt = new Receipt();
                receipt.number = receiptNo;
                receipt.amount = amt;
                receipt.date = dateEpoch;
                receipts.add(receipt);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing receipts JSON: " + e.getMessage());
        }
        return receipts;
    }

    /**
     * Parses hostel leaves from /login/vitian/studentHostelLeaveHistory
     */
    public static List<Leave> parseLeaves(String jsonStr) {
        List<Leave> leaves = new ArrayList<>();
        if (!isJson(jsonStr)) return leaves;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("leaveHistory");
            if (array == null) array = root.optJSONArray("studentHostelLeaveHistory");
            if (array == null) array = root.optJSONArray("data");
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) return leaves;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                Leave leave = new Leave();
                leave.leaveId = optString(obj, "leaveId", "applicationId", "id", String.valueOf(i + 1));
                leave.leaveType = optString(obj, "leaveType", "type", "leaveCode");
                leave.status = optString(obj, "leaveStatus", "status");
                leave.fromDate = optString(obj, "leaveFromDate", "fromDate");
                leave.toDate = optString(obj, "leaveToDate", "toDate");
                leave.reason = optString(obj, "leaveReason", "reason");
                leave.lastUpdated = System.currentTimeMillis();
                leaves.add(leave);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing leaves JSON: " + e.getMessage());
        }
        return leaves;
    }

    /**
     * Parses academic calendar from /login/vitian/academicCalendar
     */
    public static List<CalendarEvent> parseCalendar(String jsonStr) {
        List<CalendarEvent> events = new ArrayList<>();
        if (!isJson(jsonStr)) return events;

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.optJSONArray("calendarEvents");
            if (array == null) array = root.optJSONArray("data");
            if (array == null && jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            }

            if (array == null) return events;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                String dateStr = optString(obj, "calendarDate", "date");
                String desc = optString(obj, "calendarEvents", "eventTitle", "description");
                String color = optString(obj, "color", "#000000");
                String category = optString(obj, "category", "Academic");

                int dayOfMonth = 1;
                String monthLabel = "Current Month";
                try {
                    Date d = sdf.parse(dateStr);
                    if (d != null) {
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTime(d);
                        dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH);
                        monthLabel = monthFmt.format(d).toUpperCase(Locale.ENGLISH);
                    }
                } catch (Exception ignored) {
                    dayOfMonth = i + 1;
                }

                boolean isHoliday = desc.toLowerCase(Locale.ROOT).contains("holiday") ||
                                    desc.toLowerCase(Locale.ROOT).contains("sunday");
                String type = isHoliday ? "Holiday" : "Instructional Day";

                CalendarEvent event = new CalendarEvent(monthLabel, dayOfMonth, desc, type, color, category);
                events.add(event);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing calendar JSON: " + e.getMessage());
        }
        return events;
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

    /**
     * Calculates aggregate attendance metrics across valid courses according to official VTOP rules.
     * Courses with 0 conducted classes are excluded from the overall conducted total.
     */
    public static AttendanceSummaryResult calculateAggregateAttendance(List<Attendance> attendances) {
        int totalAttended = 0;
        int totalConducted = 0;
        if (attendances != null) {
            for (Attendance att : attendances) {
                if (att.attended != null && att.total != null && att.total > 0) {
                    totalAttended += att.attended;
                    totalConducted += att.total;
                }
            }
        }
        float overall = 0.0f;
        if (totalConducted > 0) {
            overall = Math.round((totalAttended * 10000.0f) / totalConducted) / 100.0f;
        }
        return new AttendanceSummaryResult(attendances != null ? attendances : new ArrayList<>(),
                totalAttended, totalConducted, overall);
    }

    private static String optString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                String val = obj.optString(key, "").trim();
                if (!val.isEmpty()) return val;
            }
        }
        return "";
    }

    private static int optInt(JSONObject obj, int defaultValue, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                try {
                    return obj.getInt(key);
                } catch (Exception e) {
                    try {
                        String s = obj.optString(key, "").replaceAll("[^0-9.-]", "");
                        if (!s.isEmpty()) {
                            return (int) Math.round(Double.parseDouble(s));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return defaultValue;
    }

    private static double optDouble(JSONObject obj, double defaultValue, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                try {
                    return obj.getDouble(key);
                } catch (Exception e) {
                    try {
                        String s = obj.optString(key, "").replaceAll("[^0-9.-]", "");
                        if (!s.isEmpty()) return Double.parseDouble(s);
                    } catch (Exception ignored) {}
                }
            }
        }
        return defaultValue;
    }

    private static boolean hasAnyKey(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return true;
            }
        }
        return false;
    }
}
