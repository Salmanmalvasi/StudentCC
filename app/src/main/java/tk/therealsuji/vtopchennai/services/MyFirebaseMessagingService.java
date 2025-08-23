package tk.therealsuji.vtopchennai.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.MainActivity;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "update_notifications";
    private static final String CHANNEL_NAME = "App Updates";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage);
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            handleNotificationMessage(remoteMessage);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        
        // Save token to preferences
        SettingsRepository.getSharedPreferences(this)
                .edit()
                .putString("fcm_token", token)
                .apply();
        
        // Send token to your server if needed
        sendTokenToServer(token);
    }

    private void handleDataMessage(RemoteMessage remoteMessage) {
        String title = remoteMessage.getData().get("title");
        String body = remoteMessage.getData().get("body");
        String url = remoteMessage.getData().get("url");
        String type = remoteMessage.getData().get("type");

        if ("update_available".equals(type)) {
            showUpdateNotification(title, body, url);
        }
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification().getTitle();
        String body = remoteMessage.getNotification().getBody();
        
        // Default to app launch
        showUpdateNotification(title, body, SettingsRepository.APP_BASE_URL);
    }

    private void showUpdateNotification(String title, String body, String url) {
        Intent intent;
        
        if (url != null && !url.isEmpty()) {
            // Open website
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        } else {
            // Open app
            intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_available)
                .setContentTitle(title != null ? title : "StudentCC Update Available")
                .setContentText(body != null ? body : "A new version is available. Visit our website to download!")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(body != null ? body : "A new version of StudentCC is available. Visit our website to download the latest version with new features and bug fixes!"));

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for app updates");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendTokenToServer(String token) {
        // TODO: Implement sending token to your server/Firebase Firestore
        // For now, we'll just log it
        Log.d(TAG, "Token to send to server: " + token);
        
        // You could store this in Firestore for sending targeted notifications
        // FirebaseFirestore.getInstance()
        //     .collection("fcm_tokens")
        //     .document("user_tokens")
        //     .update("tokens", FieldValue.arrayUnion(token));
    }
}
