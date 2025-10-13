package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;

/**
 * Helper class for Firebase In-App Messaging functionality
 * Created by Salman Malvasi
 */
public class InAppMessagingHelper {
    private static final String TAG = "InAppMessagingHelper";
    private static InAppMessagingHelper instance;
    private FirebaseInAppMessaging inAppMessaging;
    private FirebaseAnalytics analytics;

    private InAppMessagingHelper() {
        // Private constructor for singleton
    }

    public static InAppMessagingHelper getInstance() {
        if (instance == null) {
            instance = new InAppMessagingHelper();
        }
        return instance;
    }

    /**
     * Initialize In-App Messaging
     */
    public void initialize(Context context) {
        try {
            inAppMessaging = FirebaseInAppMessaging.getInstance();
            analytics = FirebaseAnalytics.getInstance(context);
            
            // Enable data collection
            inAppMessaging.setAutomaticDataCollectionEnabled(true);
            
            Log.d(TAG, "In-App Messaging initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize In-App Messaging: " + e.getMessage());
        }
    }

    /**
     * Trigger an in-app message event
     */
    public void triggerEvent(String eventName) {
        if (inAppMessaging != null) {
            try {
                inAppMessaging.triggerEvent(eventName);
                Log.d(TAG, "Triggered event: " + eventName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to trigger event " + eventName + ": " + e.getMessage());
            }
        } else {
            Log.w(TAG, "In-App Messaging not initialized. Call initialize() first.");
        }
    }

    /**
     * Trigger an event and also log it to Analytics
     */
    public void triggerEventWithAnalytics(String eventName, Bundle parameters) {
        // Trigger in-app message
        triggerEvent(eventName);
        
        // Also log to Analytics
        if (analytics != null) {
            analytics.logEvent(eventName, parameters);
            Log.d(TAG, "Event logged to Analytics: " + eventName);
        }
    }

    /**
     * Test method to trigger multiple common events
     */
    public void testInAppMessaging() {
        Log.d(TAG, "Starting In-App Messaging test...");
        
        // Common events that usually trigger in-app messages
        triggerEvent("app_open");
        triggerEvent("welcome_message");
        triggerEvent("test_in_app_message");
        triggerEvent("user_engagement");
        triggerEvent("app_foreground");
        
        // Custom events with analytics
        Bundle bundle = new Bundle();
        bundle.putString("test_mode", "true");
        bundle.putLong("timestamp", System.currentTimeMillis());
        
        triggerEventWithAnalytics("student_app_open", bundle);
        triggerEventWithAnalytics("dashboard_view", bundle);
        
        Log.d(TAG, "In-App Messaging test completed");
    }

    /**
     * Enable or disable data collection
     */
    public void setDataCollectionEnabled(boolean enabled) {
        if (inAppMessaging != null) {
            inAppMessaging.setAutomaticDataCollectionEnabled(enabled);
            Log.d(TAG, "Data collection " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Check if In-App Messaging is properly initialized
     */
    public boolean isInitialized() {
        return inAppMessaging != null;
    }

    /**
     * Get device ID for testing purposes
     */
    public static String getDeviceId(Context context) {
        return android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
        );
    }

    /**
     * Log instructions for setting up test device in Firebase Console
     */
    public static void logTestDeviceInstructions(Context context) {
        String deviceId = getDeviceId(context);
        Log.d(TAG, "=== FIREBASE IN-APP MESSAGING TEST DEVICE SETUP ===");
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "");
        Log.d(TAG, "To test In-App Messaging:");
        Log.d(TAG, "1. Go to Firebase Console → In-App Messaging");
        Log.d(TAG, "2. Create a new campaign or edit existing one");
        Log.d(TAG, "3. In 'Targeting' section, click 'Test on device'");
        Log.d(TAG, "4. Add this device ID: " + deviceId);
        Log.d(TAG, "5. Set event trigger to 'app_open' or 'welcome_message'");
        Log.d(TAG, "6. Publish the campaign");
        Log.d(TAG, "7. Open your app and the message should appear");
        Log.d(TAG, "===============================================");
    }
}