package tk.therealsuji.vtopchennai.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import tk.therealsuji.vtopchennai.helpers.FirebaseMessagingHelper;

/**
 * Firebase Cloud Messaging Service
 * Handles incoming FCM messages
 */
public class VTOPFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "VTOPFCMService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "📱 MESSAGE RECEIVED! From: " + remoteMessage.getFrom());

        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "📊 Message data payload: " + remoteMessage.getData());

            FirebaseMessagingHelper helper = FirebaseMessagingHelper.getInstance(this);
            helper.handleIncomingMessage(remoteMessage);
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "🔔 Message Notification Title: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "🔔 Message Notification Body: " + remoteMessage.getNotification().getBody());

            FirebaseMessagingHelper helper = FirebaseMessagingHelper.getInstance(this);
            helper.showNotification(
                remoteMessage.getNotification().getTitle(),
                remoteMessage.getNotification().getBody(),
                remoteMessage.getData()
            );
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "🔄 Refreshed token: " + token);

        // Send token to your app server if needed
        FirebaseMessagingHelper helper = FirebaseMessagingHelper.getInstance(this);
        helper.updateFCMToken(token);
    }

    /**
     * Called when a message is sent to Firebase and it needs to be deleted
     * @param msgId The message ID
     */
    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Messages deleted on server");
    }

    /**
     * Called when an upstream message has been successfully sent to the FCM connection server
     * @param msgId The message ID
     */
    @Override
    public void onMessageSent(@NonNull String msgId) {
        super.onMessageSent(msgId);
        Log.d(TAG, "Message sent: " + msgId);
    }

    /**
     * Called when there was an error sending an upstream message
     * @param msgId The message ID
     * @param exception Exception details
     */
    @Override
    public void onSendError(@NonNull String msgId, @NonNull Exception exception) {
        super.onSendError(msgId, exception);
        Log.e(TAG, "Message send error: " + msgId, exception);
    }
}
