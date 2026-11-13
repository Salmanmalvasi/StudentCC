package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.json.JSONObject;

/** Resolves the same selected semester label and name-to-ID map used by VTOPService. */
public final class StudentCCSemesterResolver {
    private StudentCCSemesterResolver() {}

    public static String resolve(String selected, String semestersMapJson, String explicitId) {
        String selection = clean(selected);
        if (semesterId(selection)) return selection;
        String map = clean(semestersMapJson);
        if (!map.isEmpty()) {
            try {
                JSONObject semesters = new JSONObject(map);
                Object mapped = semesters.opt(selection);
                if (mapped instanceof String && semesterId(clean((String) mapped))) return clean((String) mapped);
                // A selected label missing from a known map must never reuse an older explicit ID.
                if (!selection.isEmpty() || semesters.length() > 0) return "";
            } catch (org.json.JSONException invalidMap) {
                return "";
            }
        }
        // Legacy builds may store only an ID; no selected label can contradict it in this case.
        String fallback = clean(explicitId);
        return selection.isEmpty() && semesterId(fallback) ? fallback : "";
    }

    private static boolean semesterId(String value) {
        return value.matches("[A-Za-z]{2}[0-9][A-Za-z0-9_-]{0,76}");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
