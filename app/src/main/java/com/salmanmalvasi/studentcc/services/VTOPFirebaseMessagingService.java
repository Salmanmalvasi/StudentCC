package com.salmanmalvasi.studentcc.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import com.salmanmalvasi.studentcc.helpers.FirebaseMessagingHelper;

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
        Log.d(TAG, "📱 Message ID: " + remoteMessage.getMessageId());

        FirebaseMessagingHelper helper = FirebaseMessagingHelper.getInstance(this);

        // If message has data payload, handle it through helper (which processes types, stores in-app messages, and shows notification)
        if (remoteMessage.getData() != null && !remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "📊 Message data payload: " + remoteMessage.getData());
            helper.handleIncomingMessage(remoteMessage);
        } else if (remoteMessage.getNotification() != null) {
            // Notification-only message
            Log.d(TAG, "🔔 Message Notification Title: " + remoteMessage.getNotification().getTitle());
            helper.showNotification(
                remoteMessage.getNotification().getTitle(),
                remoteMessage.getNotification().getBody()
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
