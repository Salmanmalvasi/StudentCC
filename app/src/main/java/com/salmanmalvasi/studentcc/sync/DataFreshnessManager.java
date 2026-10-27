package com.salmanmalvasi.studentcc.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.salmanmalvasi.studentcc.helpers.SettingsRepository;

import java.util.Locale;

/**
 * Manages dataset-specific cache freshness policies and tracks last successful refresh times.
 * Ensures local-first UI rendering without blocking on network requests when data is fresh,
 * while isolating cached data by user account and academic semester.
 */
public final class DataFreshnessManager {

    private static final String PREF_NAME = "dataset_freshness_prefs";

    // Dataset identifiers
    public static final String DATASET_ATTENDANCE_SUMMARY = "attendance_summary";
    public static final String DATASET_DETAILED_ATTENDANCE = "detailed_attendance";
    public static final String DATASET_MARKS = "marks";
    public static final String DATASET_TIMETABLE = "timetable";
    public static final String DATASET_CALENDAR = "calendar";
    public static final String DATASET_PROFILE_COURSES = "profile_courses";

    // Freshness windows (in milliseconds)
    public static final long FRESHNESS_ATTENDANCE_SUMMARY_MS = 3 * 60 * 1000L;       // 3 minutes (2-5m range)
    public static final long FRESHNESS_DETAILED_ATTENDANCE_MS = 7 * 60 * 1000L;      // 7 minutes (5-10m range)
    public static final long FRESHNESS_MARKS_MS = 10 * 60 * 1000L;                   // 10 minutes (5-15m range)
    public static final long FRESHNESS_TIMETABLE_MS = 8 * 60 * 60 * 1000L;           // 8 hours (6-12h range)
    public static final long FRESHNESS_CALENDAR_MS = 8 * 60 * 60 * 1000L;            // 8 hours (6-12h range)
    public static final long FRESHNESS_PROFILE_COURSES_MS = 16 * 60 * 60 * 1000L;    // 16 hours (12-24h range)

    private static DataFreshnessManager instance;

    public static synchronized DataFreshnessManager getInstance() {
        if (instance == null) {
            instance = new DataFreshnessManager();
        }
        return instance;
    }

    private SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private String buildScopedKey(Context context, String dataset) {
        String username = "anon";
        SharedPreferences enc = SettingsRepository.getEncryptedSharedPreferences(context.getApplicationContext());
        if (enc != null) {
            username = enc.getString("username", "anon");
        }
        String semester = SettingsRepository.getSharedPreferences(context.getApplicationContext()).getString("semester", "default");
        return "freshness_" + dataset + "_" + username + "_" + semester;
    }

    public long getFreshnessWindowMs(String dataset) {
        if (dataset == null) return FRESHNESS_ATTENDANCE_SUMMARY_MS;
        switch (dataset) {
            case DATASET_ATTENDANCE_SUMMARY:
                return FRESHNESS_ATTENDANCE_SUMMARY_MS;
            case DATASET_DETAILED_ATTENDANCE:
                return FRESHNESS_DETAILED_ATTENDANCE_MS;
            case DATASET_MARKS:
                return FRESHNESS_MARKS_MS;
            case DATASET_TIMETABLE:
                return FRESHNESS_TIMETABLE_MS;
            case DATASET_CALENDAR:
                return FRESHNESS_CALENDAR_MS;
            case DATASET_PROFILE_COURSES:
                return FRESHNESS_PROFILE_COURSES_MS;
            default:
                return FRESHNESS_ATTENDANCE_SUMMARY_MS;
        }
    }

    /**
     * Checks whether the specified dataset is currently fresh for the active account and semester.
     */
    public boolean isFresh(Context context, String dataset) {
        if (context == null || dataset == null) return false;
        long lastRefresh = getLastRefreshTime(context, dataset);
        if (lastRefresh <= 0) return false;

        long window = getFreshnessWindowMs(dataset);
        long age = System.currentTimeMillis() - lastRefresh;
        return age >= 0 && age < window;
    }

    public boolean isStale(Context context, String dataset) {
        return !isFresh(context, dataset);
    }

    /**
     * Checks whether any core academic dataset (attendance or marks) is stale.
     */
    public boolean isAnyCoreStale(Context context) {
        return isStale(context, DATASET_ATTENDANCE_SUMMARY)
                || isStale(context, DATASET_MARKS);
    }

    /**
     * Retrieves the epoch timestamp of the last successful refresh for the dataset.
     */
    public long getLastRefreshTime(Context context, String dataset) {
        if (context == null || dataset == null) return 0L;
        return getPrefs(context).getLong(buildScopedKey(context, dataset), 0L);
    }

    /**
     * Records a verified successful refresh for a specific dataset.
     */
    public void recordSuccessfulRefresh(Context context, String dataset) {
        if (context == null || dataset == null) return;
        long now = System.currentTimeMillis();
        getPrefs(context).edit().putLong(buildScopedKey(context, dataset), now).apply();
        LocalCacheMetrics.getInstance().recordCacheRefreshSuccess(dataset);
    }

    /**
     * Records a failed refresh attempt. Note: does NOT advance the successful refresh timestamp.
     */
    public void recordRefreshFailure(Context context, String dataset, String reason) {
        if (context == null || dataset == null) return;
        LocalCacheMetrics.getInstance().recordCacheRefreshFailure(dataset, reason);
    }

    /**
     * Formats the last refreshed timestamp into a concise, user-friendly relative string.
     */
    public String getFormattedLastUpdated(Context context, String dataset) {
        long lastRefresh = getLastRefreshTime(context, dataset);
        if (lastRefresh <= 0) return "Not updated yet";

        long diff = System.currentTimeMillis() - lastRefresh;
        if (diff < 0) return "Just now";

        long seconds = diff / 1000L;
        if (seconds < 60) return "Just now";

        long minutes = seconds / 60L;
        if (minutes < 60) {
            return String.format(Locale.getDefault(), "Updated %dm ago", minutes);
        }

        long hours = minutes / 60L;
        if (hours < 24) {
            return String.format(Locale.getDefault(), "Updated %dh ago", hours);
        }

        long days = hours / 24L;
        return String.format(Locale.getDefault(), "Updated %dd ago", days);
    }

    /**
     * Invalidates cache freshness for a dataset, e.g. on manual force sync.
     */
    public void invalidateDataset(Context context, String dataset) {
        if (context == null || dataset == null) return;
        getPrefs(context).edit().remove(buildScopedKey(context, dataset)).apply();
    }

    /**
     * Invalidates all freshness records (e.g. on sign out or account switch).
     */
    public void invalidateAll(Context context) {
        if (context == null) return;
        getPrefs(context).edit().clear().apply();
    }
}
