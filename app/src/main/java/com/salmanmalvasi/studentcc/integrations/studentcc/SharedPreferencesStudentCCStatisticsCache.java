package com.salmanmalvasi.studentcc.integrations.studentcc;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

final class SharedPreferencesStudentCCStatisticsCache implements StudentCCStatisticsCache {
    private final SharedPreferences preferences;
    SharedPreferencesStudentCCStatisticsCache(SharedPreferences preferences) { this.preferences = preferences; }
    @Override public synchronized Entry read(String key, StudentCCClassScope scope) {
        String value = preferences.getString(key, null);
        if (value == null) return null;
        try {
            JSONObject object = new JSONObject(value);
            if (object.getInt("cacheVersion") != 1 || object.getLong("fetchedAtMillis") <= 0) throw new IllegalArgumentException();
            return new Entry(StudentCCStatisticsClient.parseStatistics(value, scope), object.getLong("fetchedAtMillis"));
        } catch (Exception error) { preferences.edit().remove(key).apply(); return null; }
    }
    @Override public synchronized void write(String key, Entry entry) {
        try {
            StudentCCClassStatistics stats = entry.statistics;
            JSONObject object = stats.scope.toJson(); object.put("source", "studentcc");
            object.put("status", stats.hasValues() ? "available" : "insufficient_data");
            object.put("count", stats.count); object.put("minContributors", stats.minContributors);
            if (stats.cgpaAtLeastNineCount != null) object.put("cgpaAtLeastNineCount", stats.cgpaAtLeastNineCount);
            if (stats.hasValues()) { object.put("mean", stats.mean); object.put("variance", stats.variance); object.put("sd", stats.sd); }
            SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT); date.setTimeZone(TimeZone.getTimeZone("UTC"));
            object.put("updatedAt", stats.updatedAtMillis == 0 ? JSONObject.NULL : date.format(new Date(stats.updatedAtMillis)));
            object.put("cacheVersion", 1); object.put("fetchedAtMillis", entry.fetchedAtMillis);
            SharedPreferences.Editor editor = preferences.edit(); Map<String, ?> all = preferences.getAll();
            if (!all.containsKey(key) && all.size() >= 250) {
                String oldest = null; long at = Long.MAX_VALUE;
                for (Map.Entry<String, ?> item : all.entrySet()) {
                    long time = 0; try { time = new JSONObject(String.valueOf(item.getValue())).optLong("fetchedAtMillis", 0); } catch (Exception ignored) {}
                    if (oldest == null || time < at) { oldest = item.getKey(); at = time; }
                }
                if (oldest != null) editor.remove(oldest);
            }
            editor.putString(key, object.toString()).apply();
        } catch (Exception ignored) { /* Aggregate cache is optional. */ }
    }
    @Override public synchronized void remove(String key) { preferences.edit().remove(key).apply(); }
    @Override public synchronized void clear() { preferences.edit().clear().apply(); }
}
