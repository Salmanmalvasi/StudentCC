package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Centralized Firebase Analytics helper for tracking user interactions
 */
public class FirebaseAnalyticsHelper {
    private static final String TAG = "FirebaseHelper";
    private static FirebaseAnalytics mFirebaseAnalytics;

    /**
     * Initialize Firebase Analytics
     */
    public static void initialize(Context context) {
        if (mFirebaseAnalytics == null) {
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
            Log.d(TAG, "Firebase Analytics initialized");
        }
    }

    /**
     * Get Firebase Analytics instance
     */
    public static FirebaseAnalytics getInstance(Context context) {
        if (mFirebaseAnalytics == null) {
            initialize(context);
        }
        return mFirebaseAnalytics;
    }

    // ========== BUTTON TRACKING ==========

    /**
     * Track button presses with button name and location
     */
    public static void trackButtonPress(Context context, String buttonName, String screenName) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("button_name", buttonName);
        bundle.putString("screen_name", screenName);
        bundle.putString(FirebaseAnalytics.Param.SOURCE, screenName);

        mFirebaseAnalytics.logEvent("button_press", bundle);
        Log.d(TAG, "Tracked button press: " + buttonName + " on " + screenName);
    }

    /**
     * Track menu item selections
     */
    public static void trackMenuItemSelected(Context context, String itemName, String menuType) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("menu_item", itemName);
        bundle.putString("menu_type", menuType);

        mFirebaseAnalytics.logEvent("menu_item_selected", bundle);
        Log.d(TAG, "Tracked menu selection: " + itemName + " (" + menuType + ")");
    }

    /**
     * Track tab switches in bottom navigation
     */
    public static void trackTabSwitch(Context context, String tabName) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("tab_name", tabName);
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, tabName);

        mFirebaseAnalytics.logEvent("tab_switch", bundle);
        Log.d(TAG, "Tracked tab switch: " + tabName);
    }

    // ========== SCREEN TRACKING ==========

    /**
     * Track screen views
     */
    public static void trackScreenView(Context context, String screenName, String screenClass) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass);

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
        Log.d(TAG, "Tracked screen view: " + screenName);
    }

    // ========== USER ACTIONS ==========

    /**
     * Track login events
     */
    public static void trackLogin(Context context, String studentType, boolean isSuccessful) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("student_type", studentType);
        bundle.putBoolean("login_successful", isSuccessful);
        bundle.putString(FirebaseAnalytics.Param.METHOD, "vtop");

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
        Log.d(TAG, "Tracked login: " + studentType + " (success: " + isSuccessful + ")");
    }

    /**
     * Track logout events
     */
    public static void trackLogout(Context context) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.METHOD, "manual");

        mFirebaseAnalytics.logEvent("logout", bundle);
        Log.d(TAG, "Tracked logout");
    }

    /**
     * Track data sync events
     */
    public static void trackDataSync(Context context, String syncType, boolean isSuccessful) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("sync_type", syncType);
        bundle.putBoolean("sync_successful", isSuccessful);

        mFirebaseAnalytics.logEvent("sync_data", bundle);
        Log.d(TAG, "Tracked data sync: " + syncType + " (success: " + isSuccessful + ")");
    }

    // ========== FEATURE USAGE ==========

    /**
     * Track hostel feature usage
     */
    public static void trackHostelFeature(Context context, String featureName, String action) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("feature_name", featureName);
        bundle.putString("action", action);
        bundle.putString("category", "hostel");

        mFirebaseAnalytics.logEvent("feature_usage", bundle);
        Log.d(TAG, "Tracked hostel feature: " + featureName + " - " + action);
    }

    /**
     * Track attendance view toggles
     */
    public static void trackAttendanceToggle(Context context, String displayType) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("display_type", displayType);
        bundle.putString("feature", "attendance_display");

        mFirebaseAnalytics.logEvent("toggle_attendance_display", bundle);
        Log.d(TAG, "Tracked attendance toggle: " + displayType);
    }

    /**
     * Track timetable interactions
     */
    public static void trackTimetableAction(Context context, String action, String dayOfWeek) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("day_of_week", dayOfWeek);
        bundle.putString("feature", "timetable");

        mFirebaseAnalytics.logEvent("timetable_interaction", bundle);
        Log.d(TAG, "Tracked timetable action: " + action + " for " + dayOfWeek);
    }

    /**
     * Track exam view events
     */
    public static void trackExamView(Context context, String courseCode, String examType) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, courseCode);
        bundle.putString("exam_type", examType);
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "exam");

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
        Log.d(TAG, "Tracked exam view: " + courseCode + " (" + examType + ")");
    }

    // ========== SETTINGS & PREFERENCES ==========

    /**
     * Track theme changes
     */
    public static void trackThemeChange(Context context, String themeType) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("theme_type", themeType);
        bundle.putString("setting", "theme");

        mFirebaseAnalytics.logEvent("setting_change", bundle);
        Log.d(TAG, "Tracked theme change: " + themeType);
    }

    /**
     * Track notification permission changes
     */
    public static void trackNotificationPermission(Context context, boolean granted) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putBoolean("permission_granted", granted);
        bundle.putString("permission_type", "notifications");

        mFirebaseAnalytics.logEvent("permission_change", bundle);
        Log.d(TAG, "Tracked notification permission: " + granted);
    }

    // ========== LAUNDRY SYSTEM ==========

    /**
     * Track laundry notification interactions
     */
    public static void trackLaundryNotification(Context context, String action) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("feature", "laundry_notifications");

        mFirebaseAnalytics.logEvent("laundry_interaction", bundle);
        Log.d(TAG, "Tracked laundry interaction: " + action);
    }

    /**
     * Track laundry schedule views
     */
    public static void trackLaundryScheduleView(Context context, String dayOfWeek) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("day_of_week", dayOfWeek);
        bundle.putString("feature", "laundry_schedule");

        mFirebaseAnalytics.logEvent("laundry_schedule_view", bundle);
        Log.d(TAG, "Tracked laundry schedule view: " + dayOfWeek);
    }

    // ========== ERROR TRACKING ==========

    /**
     * Track errors and exceptions
     */
    public static void trackError(Context context, String errorType, String errorMessage, String location) {
        if (mFirebaseAnalytics == null) initialize(context);

        Bundle bundle = new Bundle();
        bundle.putString("error_type", errorType);
        bundle.putString("error_message", errorMessage);
        bundle.putString("location", location);

        mFirebaseAnalytics.logEvent("app_error", bundle);
        Log.d(TAG, "Tracked error: " + errorType + " in " + location);
    }

    // ========== CUSTOM EVENTS ==========

    /**
     * Track custom events with flexible parameters
     */
    public static void trackCustomEvent(Context context, String eventName, Bundle parameters) {
        if (mFirebaseAnalytics == null) initialize(context);

        mFirebaseAnalytics.logEvent(eventName, parameters);
        Log.d(TAG, "Tracked custom event: " + eventName);
    }

    /**
     * Set user properties
     */
    public static void setUserProperty(Context context, String name, String value) {
        if (mFirebaseAnalytics == null) initialize(context);

        mFirebaseAnalytics.setUserProperty(name, value);
        Log.d(TAG, "Set user property: " + name + " = " + value);
    }
}
