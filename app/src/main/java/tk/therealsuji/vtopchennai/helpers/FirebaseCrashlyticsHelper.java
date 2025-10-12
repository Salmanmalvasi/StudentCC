package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Helper class for Firebase Crashlytics
 * Handles crash reporting and custom logging
 */
public class FirebaseCrashlyticsHelper {
    private static final String TAG = "CrashlyticsHelper";
    private static FirebaseCrashlytics crashlytics;

    /**
     * Initialize Firebase Crashlytics
     */
    public static void initialize(Context context) {
        crashlytics = FirebaseCrashlytics.getInstance();

        // Enable crashlytics collection
        crashlytics.setCrashlyticsCollectionEnabled(true);

        Log.d(TAG, "Firebase Crashlytics initialized");
    }

    /**
     * Log a custom message to Crashlytics
     */
    public static void log(String message) {
        if (crashlytics != null) {
            crashlytics.log(message);
            Log.d(TAG, "Logged to Crashlytics: " + message);
        }
    }

    /**
     * Log an exception to Crashlytics
     */
    public static void recordException(Throwable throwable) {
        if (crashlytics != null) {
            crashlytics.recordException(throwable);
            Log.d(TAG, "Recorded exception to Crashlytics: " + throwable.getMessage());
        }
    }

    /**
     * Set custom key-value pairs for crash context
     */
    public static void setCustomKey(String key, String value) {
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
            Log.d(TAG, "Set custom key: " + key + " = " + value);
        }
    }

    /**
     * Set custom key-value pairs for crash context (Boolean)
     */
    public static void setCustomKey(String key, boolean value) {
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
            Log.d(TAG, "Set custom key: " + key + " = " + value);
        }
    }

    /**
     * Set custom key-value pairs for crash context (Number)
     */
    public static void setCustomKey(String key, int value) {
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
            Log.d(TAG, "Set custom key: " + key + " = " + value);
        }
    }

    /**
     * Set user identifier for crash reports
     */
    public static void setUserId(String userId) {
        if (crashlytics != null) {
            crashlytics.setUserId(userId);
            Log.d(TAG, "Set user ID: " + userId);
        }
    }

    /**
     * Log app state information
     */
    public static void logAppState(String screen, String action) {
        log("App State - Screen: " + screen + ", Action: " + action);
        setCustomKey("last_screen", screen);
        setCustomKey("last_action", action);
    }

    /**
     * Log network request information
     */
    public static void logNetworkRequest(String url, int responseCode, String error) {
        log("Network Request - URL: " + url + ", Response: " + responseCode + ", Error: " + error);
        setCustomKey("last_request_url", url);
        setCustomKey("last_response_code", responseCode);
        if (error != null) {
            setCustomKey("last_network_error", error);
        }
    }

    /**
     * Log Firebase messaging events
     */
    public static void logFirebaseMessage(String messageId, String messageType, boolean success) {
        log("Firebase Message - ID: " + messageId + ", Type: " + messageType + ", Success: " + success);
        setCustomKey("last_message_id", messageId);
        setCustomKey("last_message_type", messageType);
        setCustomKey("last_message_success", success);
    }
}
