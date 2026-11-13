package com.salmanmalvasi.studentcc.integrations.studentcc;

import com.salmanmalvasi.studentcc.models.Course;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** An explicit cohort. Different classes, semesters and assessment stages never share samples. */
public final class StudentCCClassScope {
    public static final String INTERNAL_STAGE = "INTERNAL_60";
    public static final String FINAL_STAGE = "FINAL_100";
    public final String campus;
    public final String semesterId;
    public final String classId;
    public final String stage;
    public final int marksBasis;

    public StudentCCClassScope(String campus, String semesterId, String classId) {
        this(campus, semesterId, classId, 60);
    }

    public StudentCCClassScope(String campus, String semesterId, String classId, int basis) {
        if (basis != 60 && basis != 100) throw new IllegalArgumentException("Unsupported assessment basis");
        marksBasis = basis;
        stage = basis == 60 ? INTERNAL_STAGE : FINAL_STAGE;
        this.campus = campus == null ? "" : campus.trim().toUpperCase(Locale.ROOT);
        this.semesterId = semesterId == null ? "" : semesterId.trim();
        this.classId = classId == null ? "" : classId.trim();
        if (!this.campus.matches("[A-Z]{2}") || !identifier(this.semesterId) || !identifier(this.classId)
                || !this.semesterId.toUpperCase(Locale.ROOT).startsWith(this.campus)
                || !this.classId.toUpperCase(Locale.ROOT).startsWith(this.campus)) {
            throw new IllegalArgumentException("Missing or inconsistent class and semester identifiers");
        }
    }

    public static StudentCCClassScope fromCourse(Course.AllData course) {
        return fromCourse(course, 60);
    }

    public static StudentCCClassScope fromCourse(Course.AllData course, int basis) {
        if (course == null || course.semesterId == null || course.semesterId.length() < 2) {
            throw new IllegalArgumentException("Refresh VTOP course information first");
        }
        return new StudentCCClassScope(course.semesterId.substring(0, 2), course.semesterId, course.vtopClassId, basis);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("campus", campus); object.put("semesterId", semesterId); object.put("classId", classId);
        object.put("stage", stage); object.put("marksBasis", marksBasis);
        return object;
    }

    String key() { return campus + ":" + semesterId.length() + ":" + semesterId + ":" + classId + ":" + stage; }
    static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"); }
}
