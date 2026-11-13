package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable per-account and per-origin consent plus a latest-value outbox. No login/session data is stored. */
final class StudentCCSharingStore {
    static final class Record {
        boolean present, enabled, removalPending;
        int consentVersion;
        Boolean pendingAcademicSummary, sentAcademicSummary;
        String owner = "", endpoint = "", subject;
        long revision;
        final Map<String, StudentCCContribution> pending = new LinkedHashMap<>();
        final Map<String, String> sent = new LinkedHashMap<>();
    }
    private final SharedPreferences preferences;
    StudentCCSharingStore(SharedPreferences preferences) { this.preferences = preferences; }

    synchronized Record read(String key) {
        Record record = new Record(); String value = preferences.getString(key, null); if (value == null) return record;
        record.present = true;
        try {
            JSONObject object = new JSONObject(value); record.enabled = object.optBoolean("enabled", false);
            record.removalPending = object.optBoolean("removalPending", false); record.revision = object.optLong("revision", 0);
            record.consentVersion = object.optInt("consentVersion", record.enabled ? 1 : 0);
            Object pendingAcademic = object.opt("pendingAcademicSummary"), sentAcademic = object.opt("sentAcademicSummary");
            record.pendingAcademicSummary = pendingAcademic instanceof Boolean ? (Boolean) pendingAcademic : null;
            record.sentAcademicSummary = sentAcademic instanceof Boolean ? (Boolean) sentAcademic : null;
            record.owner = object.optString("owner", ""); record.endpoint = object.optString("endpoint", "");
            record.subject = object.isNull("subject") ? null : object.optString("subject", null);
            JSONArray pending = object.optJSONArray("pending");
            if (pending != null) for (int i = 0; i < pending.length() && i < 128; i++) {
                StudentCCContribution contribution = StudentCCContribution.fromJson(pending.getJSONObject(i));
                record.pending.put(contribution.key(), contribution);
            }
            JSONObject sent = object.optJSONObject("sent");
            if (sent != null) { java.util.Iterator<String> keys = sent.keys(); while (keys.hasNext()) { String item = keys.next(); record.sent.put(item, sent.getString(item)); } }
        } catch (Exception error) { record.enabled = false; record.removalPending = true; record.pending.clear(); record.pendingAcademicSummary = null; }
        return record;
    }
    synchronized void enable(String key, String owner, String endpoint) {
        enable(key, owner, endpoint, 1);
    }
    synchronized void enable(String key, String owner, String endpoint, int consentVersion) {
        Record record = read(key); if (record.removalPending) return;
        record.owner = owner; record.endpoint = endpoint; record.enabled = true;
        // Private v2 acknowledgement cannot stand in for the newly approved public class tally.
        if (consentVersion >= 3 && record.consentVersion < 3) record.sentAcademicSummary = null;
        record.consentVersion = consentVersion; record.revision++; write(key, record);
    }
    synchronized void dismissSetup(String key, String owner, String endpoint) {
        Record record = read(key); if (record.enabled || record.removalPending) return;
        record.owner = owner; record.endpoint = endpoint; record.enabled = false;
        record.revision++; write(key, record);
    }
    synchronized void queueAcademicSummary(String key, Boolean latest) {
        Record record = read(key);
        if (!record.enabled || record.removalPending || record.consentVersion < 2 || latest == null) return;
        record.pendingAcademicSummary = latest.equals(record.sentAcademicSummary) ? null : latest;
        write(key, record);
    }
    synchronized void acknowledgeAcademicSummary(String key, boolean uploaded) {
        acknowledgeAcademicSummary(key, uploaded, false);
    }
    synchronized void acknowledgeAcademicSummary(String key, boolean uploaded, boolean shareClassTally) {
        Record record = read(key);
        if (!record.enabled || record.removalPending || record.consentVersion < 2) return;
        // An in-flight private request must not suppress the public request after a consent upgrade.
        if (shareClassTally != (record.consentVersion >= 3)) return;
        if (Boolean.valueOf(uploaded).equals(record.pendingAcademicSummary)) record.pendingAcademicSummary = null;
        record.sentAcademicSummary = uploaded;
        write(key, record);
    }
    /** Explicit refresh forgets server receipts only; consent, pending updates and identity survive. */
    synchronized void clearUploadAcknowledgements(String key, String owner, String endpoint) {
        Record record = read(key);
        if (!record.enabled || record.removalPending || !owner.equals(record.owner) || !endpoint.equals(record.endpoint)) return;
        record.sent.clear(); record.sentAcademicSummary = null;
        write(key, record);
    }
    synchronized void queue(String key, List<StudentCCContribution> latest) {
        queue(key, latest, null);
    }
    /** A verified snapshot replaces pending totals for that semester, including now-incomplete courses. */
    synchronized void queue(String key, List<StudentCCContribution> latest, String snapshotSemester) {
        Record record = read(key); if (!record.enabled || record.removalPending) return;
        if (snapshotSemester != null) {
            java.util.Iterator<StudentCCContribution> pending = record.pending.values().iterator();
            while (pending.hasNext()) {
                if (snapshotSemester.equals(pending.next().scope.semesterId)) pending.remove();
            }
        }
        for (StudentCCContribution contribution : latest) {
            if (!fingerprint(contribution).equals(record.sent.get(contribution.key()))) record.pending.put(contribution.key(), contribution);
            else record.pending.remove(contribution.key());
        }
        write(key, record);
    }
    synchronized void acknowledge(String key, StudentCCContribution contribution) {
        Record record = read(key); if (!record.enabled || record.removalPending) return;
        StudentCCContribution latest = record.pending.get(contribution.key());
        if (latest != null && fingerprint(latest).equals(fingerprint(contribution))) record.pending.remove(contribution.key());
        record.sent.put(contribution.key(), fingerprint(contribution)); write(key, record);
    }
    synchronized void rememberSubject(String key, String subject) {
        Record record = read(key); if (record.subject == null) { record.subject = subject; write(key, record); }
    }
    synchronized void withdraw(String key, String owner, String endpoint) {
        Record record = read(key); record.owner = owner; record.endpoint = endpoint;
        record.enabled = false; record.removalPending = true; record.revision++; record.pending.clear(); record.sent.clear();
        record.pendingAcademicSummary = null; record.sentAcademicSummary = null; write(key, record);
    }
    synchronized void confirmWithdrawal(String key, long revision) {
        Record record = read(key); if (record.enabled || record.revision != revision) return;
        record.removalPending = false; record.subject = null; record.pending.clear(); record.sent.clear();
        record.pendingAcademicSummary = null; record.sentAcademicSummary = null; write(key, record);
    }
    synchronized List<String> keysForOwner(String owner) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
            if (item.getValue() instanceof String && ((String) item.getValue()).trim().startsWith("{")
                    && owner.equals(read(item.getKey()).owner)) keys.add(item.getKey());
        }
        return keys;
    }
    static String fingerprint(StudentCCContribution contribution) { return contribution.courseCode + ":" + Double.toString(contribution.total); }
    private void write(String key, Record record) {
        try {
            JSONObject object = new JSONObject(); object.put("enabled", record.enabled); object.put("removalPending", record.removalPending);
            object.put("consentVersion", record.consentVersion);
            object.put("pendingAcademicSummary", record.pendingAcademicSummary == null ? JSONObject.NULL : record.pendingAcademicSummary);
            object.put("sentAcademicSummary", record.sentAcademicSummary == null ? JSONObject.NULL : record.sentAcademicSummary);
            object.put("owner", record.owner); object.put("endpoint", record.endpoint); object.put("subject", record.subject == null ? JSONObject.NULL : record.subject);
            object.put("revision", record.revision); JSONArray pending = new JSONArray();
            for (StudentCCContribution contribution : record.pending.values()) pending.put(contribution.toJson());
            object.put("pending", pending); JSONObject sent = new JSONObject();
            for (Map.Entry<String, String> item : record.sent.entrySet()) sent.put(item.getKey(), item.getValue()); object.put("sent", sent);
            if (!preferences.edit().putString(key, object.toString()).commit()) throw new IllegalStateException("Sharing settings could not be saved");
        } catch (org.json.JSONException error) { throw new IllegalStateException("Sharing settings could not be saved"); }
    }
}
