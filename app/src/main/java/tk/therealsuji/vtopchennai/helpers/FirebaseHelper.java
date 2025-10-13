package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;

/**
 * Firebase Helper class to manage Firebase services
 * Created by Salman Malvasi
 */
public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";

    private static FirebaseHelper instance;
    private FirebaseAnalytics firebaseAnalytics;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private FirebaseMessaging messaging;
    private FirebaseInAppMessaging inAppMessaging;

    private FirebaseHelper() {
        // Private constructor for singleton
    }

    public static FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    /**
     * Initialize Firebase services
     */
    public void initializeFirebase(Context context) {
        try {
            // Initialize Firebase Analytics
            firebaseAnalytics = FirebaseAnalytics.getInstance(context);

            // Initialize Firebase Auth
            firebaseAuth = FirebaseAuth.getInstance();

            // Initialize Firestore
            firestore = FirebaseFirestore.getInstance();

            // Initialize Firebase Storage
            storage = FirebaseStorage.getInstance();

            // Initialize Firebase Messaging
            messaging = FirebaseMessaging.getInstance();

            // Initialize Firebase In-App Messaging
            inAppMessaging = FirebaseInAppMessaging.getInstance();
            
            // Enable data collection for In-App Messaging
            inAppMessaging.setAutomaticDataCollectionEnabled(true);

            // Get FCM token for this device
            initializeFCMToken(context);

            Log.d(TAG, "Firebase services initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
        }
    }

    /**
     * Initialize FCM token and save it
     */
    private void initializeFCMToken(Context context) {
        messaging.getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();
                Log.d(TAG, "FCM Registration Token: " + token);

                // Save token to preferences
                SettingsRepository.getSharedPreferences(context)
                        .edit()
                        .putString("fcm_token", token)
                        .apply();

                // Subscribe to topic for update notifications
                subscribeToUpdateTopic();
            });
    }

    /**
     * Subscribe to update notifications topic
     */
    public void subscribeToUpdateTopic() {
        if (messaging != null) {
            messaging.subscribeToTopic("update_notifications")
                .addOnCompleteListener(task -> {
                    String msg = "Subscribed to update notifications";
                    if (!task.isSuccessful()) {
                        msg = "Failed to subscribe to update notifications";
                    }
                    Log.d(TAG, msg);
                });
        }
    }

    /**
     * Unsubscribe from update notifications topic
     */
    public void unsubscribeFromUpdateTopic() {
        if (messaging != null) {
            messaging.unsubscribeFromTopic("update_notifications")
                .addOnCompleteListener(task -> {
                    String msg = "Unsubscribed from update notifications";
                    if (!task.isSuccessful()) {
                        msg = "Failed to unsubscribe from update notifications";
                    }
                    Log.d(TAG, msg);
                });
        }
    }

    /**
     * Get Firebase Analytics instance
     */
    public FirebaseAnalytics getAnalytics() {
        return firebaseAnalytics;
    }

    /**
     * Get Firebase Auth instance
     */
    public FirebaseAuth getAuth() {
        return firebaseAuth;
    }

    /**
     * Get Firestore instance
     */
    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    /**
     * Get Firebase Storage instance
     */
    public FirebaseStorage getStorage() {
        return storage;
    }

    /**
     * Get Firebase Messaging instance
     */
    public FirebaseMessaging getMessaging() {
        return messaging;
    }

    /**
     * Get Firebase In-App Messaging instance
     */
    public FirebaseInAppMessaging getInAppMessaging() {
        return inAppMessaging;
    }

    /**
     * Log custom event to Firebase Analytics
     */
    public void logEvent(String eventName, String parameterName, String parameterValue) {
        if (firebaseAnalytics != null) {
            Bundle bundle = new Bundle();
            bundle.putString(parameterName, parameterValue);
            firebaseAnalytics.logEvent(eventName, bundle);
        }
    }

    /**
     * Log user login event
     */
    public void logUserLogin(String userId) {
        logEvent("user_login", "user_id", userId);
    }

    /**
     * Log GPA calculation event
     */
    public void logGPACalculation(String gpaValue) {
        logEvent("gpa_calculation", "gpa_value", gpaValue);
    }

    /**
     * Log attendance view event
     */
    public void logAttendanceView(String attendancePercentage) {
        logEvent("attendance_view", "attendance_percentage", attendancePercentage);
    }

    /**
     * Trigger Firebase In-App Messaging event
     */
    public void triggerInAppMessage(String eventName) {
        if (inAppMessaging != null) {
            try {
                inAppMessaging.triggerEvent(eventName);
                Log.d(TAG, "Triggered in-app message event: " + eventName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to trigger in-app message: " + e.getMessage());
            }
        }
    }

    /**
     * Enable/disable Firebase In-App Messaging data collection
     */
    public void setInAppMessagingEnabled(boolean enabled) {
        if (inAppMessaging != null) {
            inAppMessaging.setAutomaticDataCollectionEnabled(enabled);
            Log.d(TAG, "In-App Messaging data collection " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Test In-App Messaging by triggering a custom event
     */
    public void testInAppMessaging() {
        triggerInAppMessage("test_in_app_message");
        triggerInAppMessage("app_open");
        triggerInAppMessage("welcome_message");
    }
}
