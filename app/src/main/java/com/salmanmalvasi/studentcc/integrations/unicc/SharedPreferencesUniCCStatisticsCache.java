package com.salmanmalvasi.studentcc.integrations.unicc;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

final class SharedPreferencesUniCCStatisticsCache implements UniCCStatisticsCache {
    private static final int MAX_ENTRIES = 250;
    private final SharedPreferences preferences;

    SharedPreferencesUniCCStatisticsCache(SharedPreferences preferences) { this.preferences = preferences; }

    @Override public synchronized Entry read(String key) {
        String json = preferences.getString(key, null);
        if (json == null) return null;
        try {
            JSONObject object = new JSONObject(json);
            long at = object.getLong("fetchedAtMillis");
            if (at <= 0 || object.getInt("cacheVersion") != 1
                    || object.getInt("marksBasis") != UniCCClassStatistics.CURRENT_MARKS_BASIS) {
                preferences.edit().remove(key).apply();
                return null;
            }
            return new Entry(UniCCStatisticsClient.parseStatistics(json), at);
        } catch (Exception ignored) {
            preferences.edit().remove(key).apply();
            return null;
        }
    }

    @Override public synchronized void write(String key, Entry entry) {
        try {
            JSONObject json = new JSONObject();
            json.put("cacheVersion", 1);
            json.put("marksBasis", entry.statistics.marksBasis);
            json.put("mean", entry.statistics.mean);
            json.put("variance", entry.statistics.variance);
            json.put("sd", entry.statistics.sd);
            json.put("count", entry.statistics.count);
            json.put("fetchedAtMillis", entry.fetchedAtMillis);
            SharedPreferences.Editor editor = preferences.edit();
            Map<String, ?> all = preferences.getAll();
            if (!all.containsKey(key) && all.size() >= MAX_ENTRIES) {
                String oldestKey = null;
                long oldestAt = Long.MAX_VALUE;
                for (Map.Entry<String, ?> cached : all.entrySet()) {
                    long at = 0;
                    if (cached.getValue() instanceof String) {
                        try {
                            at = new JSONObject((String) cached.getValue()).optLong("fetchedAtMillis", 0);
                        } catch (JSONException ignored) { /* Invalid entries are evicted first. */ }
                    }
                    if (oldestKey == null || at < oldestAt) {
                        oldestAt = at;
                        oldestKey = cached.getKey();
                    }
                }
                if (oldestKey != null) editor.remove(oldestKey);
            }
            editor.putString(key, json.toString()).apply();
        } catch (JSONException ignored) { /* Statistics remain usable if cache encoding fails. */ }
    }

    @Override public synchronized void remove(String key) { preferences.edit().remove(key).apply(); }
}
