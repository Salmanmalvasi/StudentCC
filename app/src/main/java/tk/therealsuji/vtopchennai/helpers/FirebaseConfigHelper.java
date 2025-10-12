package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * Helper class for Firebase Remote Config
 * Allows dynamic configuration without app updates
 */
public class FirebaseConfigHelper {
    private static final String TAG = "FirebaseConfigHelper";
    private static FirebaseRemoteConfig remoteConfig;
    private static final long CACHE_EXPIRATION = 3600; // 1 hour

    /**
     * Initialize Firebase Remote Config
     */
    public static void initialize(Context context) {
        remoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(CACHE_EXPIRATION)
                .build();

        remoteConfig.setConfigSettingsAsync(configSettings);

        // Set default values using a Map
        java.util.Map<String, Object> defaultValues = new java.util.HashMap<>();
        defaultValues.put("show_welcome_message", true);
        defaultValues.put("maintenance_mode", false);
        defaultValues.put("maintenance_message", "App is under maintenance. Please try again later.");
        defaultValues.put("feature_flag_attendance", true);
        defaultValues.put("feature_flag_marks", true);
        defaultValues.put("feature_flag_timetable", true);
        defaultValues.put("max_sync_retries", 3L);
        defaultValues.put("sync_interval_minutes", 30L);
        defaultValues.put("show_beta_features", false);
        defaultValues.put("in_app_message_enabled", true);

        remoteConfig.setDefaultsAsync(defaultValues);

        Log.d(TAG, "Firebase Remote Config initialized");
    }

    /**
     * Fetch remote config values
     */
    public static void fetchAndActivate() {
        if (remoteConfig == null) {
            Log.e(TAG, "Remote Config not initialized");
            return;
        }

        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(new OnCompleteListener<Boolean>() {
                    @Override
                    public void onComplete(Task<Boolean> task) {
                        if (task.isSuccessful()) {
                            boolean updated = task.getResult();
                            Log.d(TAG, "Remote config fetch " + (updated ? "successful with updates" : "successful with no updates"));
                        } else {
                            Log.e(TAG, "Remote config fetch failed", task.getException());
                        }
                    }
                });
    }

    /**
     * Get string value from remote config
     */
    public static String getString(String key) {
        if (remoteConfig == null) {
            Log.e(TAG, "Remote Config not initialized");
            return "";
        }
        return remoteConfig.getString(key);
    }

    /**
     * Get boolean value from remote config
     */
    public static boolean getBoolean(String key) {
        if (remoteConfig == null) {
            Log.e(TAG, "Remote Config not initialized");
            return false;
        }
        return remoteConfig.getBoolean(key);
    }

    /**
     * Get long value from remote config
     */
    public static long getLong(String key) {
        if (remoteConfig == null) {
            Log.e(TAG, "Remote Config not initialized");
            return 0;
        }
        return remoteConfig.getLong(key);
    }

    /**
     * Get double value from remote config
     */
    public static double getDouble(String key) {
        if (remoteConfig == null) {
            Log.e(TAG, "Remote Config not initialized");
            return 0.0;
        }
        return remoteConfig.getDouble(key);
    }

    // Common remote config keys for the app
    public static class Keys {
        public static final String SHOW_WELCOME_MESSAGE = "show_welcome_message";
        public static final String MAINTENANCE_MODE = "maintenance_mode";
        public static final String MAINTENANCE_MESSAGE = "maintenance_message";
        public static final String FEATURE_FLAG_ATTENDANCE = "feature_flag_attendance";
        public static final String FEATURE_FLAG_MARKS = "feature_flag_marks";
        public static final String FEATURE_FLAG_TIMETABLE = "feature_flag_timetable";
        public static final String MAX_SYNC_RETRIES = "max_sync_retries";
        public static final String SYNC_INTERVAL_MINUTES = "sync_interval_minutes";
        public static final String SHOW_BETA_FEATURES = "show_beta_features";
        public static final String IN_APP_MESSAGE_ENABLED = "in_app_message_enabled";
    }
}
