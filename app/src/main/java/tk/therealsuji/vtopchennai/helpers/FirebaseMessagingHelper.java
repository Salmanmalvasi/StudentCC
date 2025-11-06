package tk.therealsuji.vtopchennai.helpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.MainActivity;
import tk.therealsuji.vtopchennai.models.InAppMessage;

/**
 * Helper class for Firebase Cloud Messaging functionality
 * Handles FCM token management, message processing, and notifications
 */
public class FirebaseMessagingHelper {
    private static final String TAG = "FirebaseMessaging";
    private static final String PREF_NAME = "firebase_messaging_prefs";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_MESSAGES = "stored_messages";
    private static final String KEY_LAST_TOKEN_REFRESH = "last_token_refresh";
    private static final String CHANNEL_ID = "vtop_notifications";
    private static final String CHANNEL_NAME = "VTOP Notifications";

    private static FirebaseMessagingHelper instance;
    private Context context;
    private SharedPreferences prefs;
    private FirebaseAnalytics analytics;

    private FirebaseMessagingHelper(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.analytics = FirebaseAnalytics.getInstance(context);
        createNotificationChannel();
    }

    public static synchronized FirebaseMessagingHelper getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseMessagingHelper(context);
        }
        return instance;
    }

    /**
     * Initialize Firebase messaging and get FCM token
     */
    public void initializeMessaging() {
        Log.d(TAG, "🚀 Initializing Firebase Messaging...");

        long lastRefresh = prefs.getLong(KEY_LAST_TOKEN_REFRESH, 0L);
        long refreshIntervalMs = TimeUnit.HOURS.toMillis(6);
        long timeSinceRefresh = System.currentTimeMillis() - lastRefresh;

        if (timeSinceRefresh < refreshIntervalMs && !TextUtils.isEmpty(getFCMToken())) {
            Log.d(TAG, "⏭️ Skipping FCM token fetch; last refresh " + (timeSinceRefresh / 1000) + " seconds ago");
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "❌ Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        Log.d(TAG, "✅ FCM Registration Token: " + token);

                        // Store token for server communication if needed
                        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        prefs.edit()
                            .putString(KEY_FCM_TOKEN, token)
                            .putLong(KEY_LAST_TOKEN_REFRESH, System.currentTimeMillis())
                            .apply();

                        Log.d(TAG, "✅ Firebase Messaging initialization completed!");
                    }
                });
    }

    /**
     * Subscribe to a topic for receiving targeted messages
     * @param topic Topic name (e.g., "announcements", "updates")
     */
    public void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener(task -> {
                String msg = "Subscribed to " + topic;
                if (!task.isSuccessful()) {
                    msg = "Subscribe to " + topic + " failed";
                    Log.w(TAG, msg, task.getException());
                } else {
                    Log.d(TAG, msg);
                }
            });
    }

    /**
     * Unsubscribe from a topic
     * @param topic Topic name to unsubscribe from
     */
    public void unsubscribeFromTopic(String topic) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener(task -> {
                String msg = "Unsubscribed from " + topic;
                if (!task.isSuccessful()) {
                    msg = "Unsubscribe from " + topic + " failed";
                    Log.w(TAG, msg, task.getException());
                } else {
                    Log.d(TAG, msg);
                }
            });
    }

    /**
     * Handle incoming FCM message
     * @param remoteMessage The received message
     */
    public void handleIncomingMessage(RemoteMessage remoteMessage) {
        Log.d(TAG, "🔄 Processing incoming message...");
        Map<String, String> data = remoteMessage.getData();

        String messageType = data.get("type");
        String priority = data.get("priority");
        String title = data.get("title");
        String body = data.get("body");
        String actionUrl = extractFirstValidUrl(data);

        Log.d(TAG, "📋 Message details - Type: " + messageType + ", Priority: " + priority + ", Title: " + title);

        if (messageType != null && title != null && body != null) {
            // Create in-app message
            String messageId = "msg_" + System.currentTimeMillis();
            Log.d(TAG, "💾 Creating in-app message with ID: " + messageId);

            InAppMessageManager.getInstance().addMessage(
                messageId,
                title,
                body,
                null, // imageUrl
                actionUrl,
                priority != null ? priority : "medium"
            );

            // Track analytics
            Bundle bundle = new Bundle();
            bundle.putString("message_type", messageType);
            bundle.putString("message_priority", priority);
            FirebaseAnalytics.getInstance(context).logEvent("message_received", bundle);

            Log.d(TAG, "✅ In-app message created and analytics logged");
        } else {
            Log.w(TAG, "⚠️ Message missing required fields - skipping in-app message creation");
        }

        // Always surface a notification for data messages that include text
        if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(body)) {
            showNotification(
                !TextUtils.isEmpty(title) ? title : context.getString(R.string.app_name),
                !TextUtils.isEmpty(body) ? body : context.getString(R.string.app_name),
                data
            );
        }
    }

    /**
     * Handle data messages (background processing)
     * @param data Message data
     */
    private void handleDataMessage(Map<String, String> data) {
        try {
            String messageType = data.get("type");
            String title = data.get("title");
            String body = data.get("body");
            String action = data.get("action");

            // Store message for in-app display
            storeInAppMessage(title, body, messageType, action);

            // Process different message types
            switch (messageType != null ? messageType : "default") {
                case "announcement":
                    handleAnnouncementMessage(data);
                    break;
                case "update_required":
                    handleUpdateRequiredMessage(data);
                    break;
                case "maintenance":
                    handleMaintenanceMessage(data);
                    break;
                default:
                    // Show notification for general messages
                    showNotification(title, body);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling data message", e);
        }
    }

    /**
     * Handle notification messages (foreground display)
     * @param remoteMessage The message containing notification
     */
    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            showNotification(notification.getTitle(), notification.getBody());
        }
    }

    /**
     * Handle announcement messages
     * @param data Message data
     */
    private void handleAnnouncementMessage(Map<String, String> data) {
        String title = data.get("title");
        String body = data.get("body");
        String priority = data.get("priority");

        // Show high priority announcements as notifications
        if ("high".equals(priority)) {
            showNotification("📢 " + title, body);
        }

        Log.d(TAG, "Announcement received: " + title);
    }

    /**
     * Handle update required messages
     * @param data Message data
     */
    private void handleUpdateRequiredMessage(Map<String, String> data) {
        String version = data.get("required_version");
        String message = data.get("body");

        // Store update requirement
        prefs.edit()
            .putBoolean("update_required", true)
            .putString("required_version", version)
            .putString("update_message", message)
            .apply();

        // Show immediate notification
        showNotification("🔄 Update Required", message);

        Log.d(TAG, "Update required for version: " + version);
    }

    /**
     * Handle maintenance messages
     * @param data Message data
     */
    private void handleMaintenanceMessage(Map<String, String> data) {
        String startTime = data.get("start_time");
        String endTime = data.get("end_time");
        String message = data.get("body");

        // Store maintenance info
        prefs.edit()
            .putBoolean("maintenance_scheduled", true)
            .putString("maintenance_start", startTime)
            .putString("maintenance_end", endTime)
            .putString("maintenance_message", message)
            .apply();

        showNotification("🔧 Maintenance Scheduled", message);

        Log.d(TAG, "Maintenance scheduled: " + startTime + " - " + endTime);
    }

    /**
     * Store in-app message for later display
     * @param title Message title
     * @param body Message body
     * @param type Message type
     * @param action Action to perform
     */
    private void storeInAppMessage(String title, String body, String type, String action) {
        try {
            JSONObject message = new JSONObject();
            message.put("id", System.currentTimeMillis());
            message.put("title", title);
            message.put("body", body);
            message.put("type", type);
            message.put("action", action);
            message.put("timestamp", System.currentTimeMillis());
            message.put("read", false);

            // Get existing messages
            String existingMessages = prefs.getString(KEY_MESSAGES, "[]");
            org.json.JSONArray messages = new org.json.JSONArray(existingMessages);

            // Add new message
            messages.put(message);

            // Keep only last 50 messages
            if (messages.length() > 50) {
                org.json.JSONArray trimmed = new org.json.JSONArray();
                for (int i = messages.length() - 50; i < messages.length(); i++) {
                    trimmed.put(messages.get(i));
                }
                messages = trimmed;
            }

            // Save back
            prefs.edit().putString(KEY_MESSAGES, messages.toString()).apply();

        } catch (JSONException e) {
            Log.e(TAG, "Error storing in-app message", e);
        }
    }

    /**
     * Get all stored in-app messages
     * @return List of messages
     */
    public List<JSONObject> getInAppMessages() {
        List<JSONObject> messageList = new ArrayList<>();
        try {
            String messagesJson = prefs.getString(KEY_MESSAGES, "[]");
            org.json.JSONArray messages = new org.json.JSONArray(messagesJson);

            for (int i = 0; i < messages.length(); i++) {
                messageList.add(messages.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error getting in-app messages", e);
        }
        return messageList;
    }

    /**
     * Get unread message count
     * @return Number of unread messages
     */
    public int getUnreadMessageCount() {
        int count = 0;
        List<JSONObject> messages = getInAppMessages();
        for (JSONObject message : messages) {
            try {
                if (!message.getBoolean("read")) {
                    count++;
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error checking message read status", e);
            }
        }
        return count;
    }

    /**
     * Mark message as read
     * @param messageId Message ID to mark as read
     */
    public void markMessageAsRead(long messageId) {
        try {
            String messagesJson = prefs.getString(KEY_MESSAGES, "[]");
            org.json.JSONArray messages = new org.json.JSONArray(messagesJson);

            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if (message.getLong("id") == messageId) {
                    message.put("read", true);
                    break;
                }
            }

            prefs.edit().putString(KEY_MESSAGES, messages.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error marking message as read", e);
        }
    }

    /**
     * Show system notification
     * @param title Notification title
     * @param body Notification body
     */
    private void showNotification(String title, String body) {
        showNotification(title, body, null);
    }

    /**
     * Show system notification with data
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data from FCM message
     */
    public void showNotification(String title, String body, Map<String, String> data) {
        Log.d(TAG, "🔔 Creating notification - Title: " + title + ", Body: " + body);

        Intent intent = buildNotificationIntent(data);

        int requestCode = (int) (System.currentTimeMillis() & 0xFFFFFF);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create notification channel first
        createNotificationChannel();

        NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_available) // Use existing icon
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            int notificationId = (int) System.currentTimeMillis(); // Unique ID
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.d(TAG, "✅ Notification displayed with ID: " + notificationId);
        } else {
            Log.e(TAG, "❌ NotificationManager is null - cannot show notification");
        }
    }

    /**
     * Create notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "📺 Creating notification channel: " + CHANNEL_ID);
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for VTOP app updates and announcements");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created successfully");
            } else {
                Log.e(TAG, "❌ NotificationManager is null - cannot create channel");
            }
        }
    }

    /**
     * Save FCM token locally
     * @param token FCM registration token
     */
    private void saveFCMToken(String token) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();
    }

    /**
     * Get saved FCM token
     * @return FCM token or null if not available
     */
    public String getFCMToken() {
        return prefs.getString(KEY_FCM_TOKEN, null);
    }

    /**
     * Send token to your server (implement based on your backend)
     * @param token FCM token to send
     */
    private void sendTokenToServer(String token) {
        // Implement this method to send token to your backend server
        // Example: Make HTTP request to your server with the token
        Log.d(TAG, "TODO: Send token to server: " + token);
    }

    /**
     * Track message received event in Firebase Analytics
     * @param remoteMessage The received message
     */
    private void trackMessageReceived(RemoteMessage remoteMessage) {
        if (analytics != null) {
            // Track basic message received event
            analytics.logEvent("fcm_message_received", null);

            // Track specific message types if available
            Map<String, String> data = remoteMessage.getData();
            if (data.containsKey("type")) {
                String messageType = data.get("type");
                analytics.logEvent("fcm_message_" + messageType, null);
            }
        }
    }

    /**
     * Check if update is required based on received messages
     * @return true if update is required
     */
    public boolean isUpdateRequired() {
        return prefs.getBoolean("update_required", false);
    }

    /**
     * Get required version from messages
     * @return required version string
     */
    public String getRequiredVersion() {
        return prefs.getString("required_version", null);
    }

    /**
     * Check if maintenance is scheduled
     * @return true if maintenance is scheduled
     */
    public boolean isMaintenanceScheduled() {
        return prefs.getBoolean("maintenance_scheduled", false);
    }

    /**
     * Get maintenance message
     * @return maintenance message
     */
    public String getMaintenanceMessage() {
        return prefs.getString("maintenance_message", null);
    }

    /**
     * Clear all stored messages
     */
    public void clearAllMessages() {
        prefs.edit()
            .putString(KEY_MESSAGES, "[]")
            .putBoolean("update_required", false)
            .putBoolean("maintenance_scheduled", false)
            .apply();
    }

    /**
     * Update FCM token when it's refreshed
     * @param token New FCM token
     */
    public void updateFCMToken(String token) {
        Log.d(TAG, "🔄 Updating FCM token: " + token);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();

        // Here you would typically send the token to your server
        // For now, just log it
        Log.d(TAG, "✅ FCM token updated and stored locally");
    }

    private Intent buildNotificationIntent(Map<String, String> data) {
        String targetUrl = extractFirstValidUrl(data);

        if (!TextUtils.isEmpty(targetUrl)) {
            Uri uri = Uri.parse(targetUrl);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return browserIntent;
        }

        Intent defaultIntent = new Intent(context, MainActivity.class);
        defaultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                defaultIntent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        return defaultIntent;
    }

    private String extractFirstValidUrl(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        String[] candidateKeys = { "action_url", "url", "link", "target_url", "deep_link" };

        for (String key : candidateKeys) {
            String candidate = data.get(key);
            if (isValidHttpUrl(candidate)) {
                return candidate.trim();
            }
        }

        return null;
    }

    private boolean isValidHttpUrl(String candidate) {
        if (TextUtils.isEmpty(candidate)) {
            return false;
        }

        try {
            Uri uri = Uri.parse(candidate.trim());
            String scheme = uri.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            Log.w(TAG, "Invalid URL supplied for notification: " + candidate, e);
            return false;
        }
    }

    /**
     * Test method to create a sample notification and in-app message
     * Call this for testing purposes
     */
    public void sendTestMessage() {
        Log.d(TAG, "🧪 Sending test message...");

        // Create test notification
        showNotification("🧪 Test Notification", "This is a test notification from the app!");

        // Create test in-app message
        String testMessageId = "test_" + System.currentTimeMillis();
        InAppMessageManager.getInstance().addMessage(
            testMessageId,
            "Test In-App Message",
            "This is a test in-app message to verify the messaging system is working correctly.",
            null, // imageUrl
            null, // actionUrl
            "high"
        );

        Log.d(TAG, "✅ Test message sent successfully!");
    }
}
