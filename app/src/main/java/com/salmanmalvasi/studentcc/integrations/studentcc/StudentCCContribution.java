package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.json.JSONException;
import org.json.JSONObject;

/** Only the complete weighted course total and cohort identifiers are sent. */
public final class StudentCCContribution {
    public final StudentCCClassScope scope;
    public final String courseCode;
    public final double total;

    public StudentCCContribution(StudentCCClassScope scope, String courseCode, double total) {
        if (scope == null || !StudentCCClassScope.identifier(courseCode) || !Double.isFinite(total) || total < 0 || total > scope.marksBasis) {
            throw new IllegalArgumentException("Invalid complete course contribution");
        }
        this.scope = scope; this.courseCode = courseCode; this.total = total;
    }
    JSONObject toJson() throws JSONException {
        JSONObject object = scope.toJson(); object.put("courseCode", courseCode); object.put("total", total); return object;
    }
    static StudentCCContribution fromJson(JSONObject object) throws JSONException {
        int basis = object.getInt("marksBasis");
        String stage = object.getString("stage");
        if (!((basis == 60 && StudentCCClassScope.INTERNAL_STAGE.equals(stage))
                || (basis == 100 && StudentCCClassScope.FINAL_STAGE.equals(stage)))) {
            throw new JSONException("Incompatible contribution basis");
        }
        return new StudentCCContribution(new StudentCCClassScope(object.getString("campus"), object.getString("semesterId"),
                object.getString("classId"), basis), object.getString("courseCode"), object.getDouble("total"));
    }
    String key() { return scope.key(); }
}
