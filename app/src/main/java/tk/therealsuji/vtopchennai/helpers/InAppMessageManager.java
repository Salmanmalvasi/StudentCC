package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tk.therealsuji.vtopchennai.models.InAppMessage;

/**
 * Manager for in-app messages from Firebase
 * Handles storage, display logic, and message lifecycle
 */
public class InAppMessageManager {
    private static final String TAG = "InAppMessageManager";
    private static final String PREF_KEY = "in_app_messages";
    private static final String PREF_DISMISSED_KEY = "dismissed_message_ids";
    
    private static InAppMessageManager instance;
    private final Gson gson;
    
    private InAppMessageManager() {
        gson = new Gson();
    }
    
    public static InAppMessageManager getInstance() {
        if (instance == null) {
            instance = new InAppMessageManager();
        }
        return instance;
    }
    
    /**
     * Add a new in-app message
     */
    public void addMessage(String messageId, String title, String message, 
                          String imageUrl, String actionUrl, String priority) {
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        
        InAppMessage inAppMessage = new InAppMessage();
        inAppMessage.id = messageId;
        inAppMessage.title = title;
        inAppMessage.message = message;
        inAppMessage.imageUrl = imageUrl;
        inAppMessage.actionUrl = actionUrl;
        inAppMessage.priority = priority != null ? priority : "normal";
        inAppMessage.timestamp = System.currentTimeMillis();
        inAppMessage.isRead = false;
        
        // Don't add if message was already dismissed
        if (isMessageDismissed(messageId)) {
            Log.d(TAG, "Message already dismissed: " + messageId);
            return;
        }
        
        List<InAppMessage> messages = getAllMessages();
        
        // Remove existing message with same ID
        final String finalMessageId = messageId;
        messages.removeIf(msg -> finalMessageId.equals(msg.id));
        
        // Add new message
        messages.add(inAppMessage);
        
        // Sort by priority and timestamp
        messages.sort((a, b) -> {
            // High priority first
            if ("high".equals(a.priority) && !"high".equals(b.priority)) return -1;
            if (!"high".equals(a.priority) && "high".equals(b.priority)) return 1;
            
            // Then by timestamp (newest first)
            return Long.compare(b.timestamp, a.timestamp);
        });
        
        // Keep only last 50 messages
        if (messages.size() > 50) {
            messages = messages.subList(0, 50);
        }
        
        saveMessages(messages);
        Log.d(TAG, "Added in-app message: " + title);
    }
    
    /**
     * Get all messages
     */
    public List<InAppMessage> getAllMessages() {
        Context context = AppContextProvider.getContext();
        if (context == null) return new ArrayList<>();
        
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        String json = prefs.getString(PREF_KEY, "[]");
        
        Type type = new TypeToken<List<InAppMessage>>(){}.getType();
        List<InAppMessage> messages = gson.fromJson(json, type);
        
        return messages != null ? messages : new ArrayList<>();
    }
    
    /**
     * Get unread messages
     */
    public List<InAppMessage> getUnreadMessages() {
        List<InAppMessage> allMessages = getAllMessages();
        List<InAppMessage> unreadMessages = new ArrayList<>();
        
        for (InAppMessage message : allMessages) {
            if (!message.isRead && !isMessageDismissed(message.id)) {
                unreadMessages.add(message);
            }
        }
        
        return unreadMessages;
    }
    
    /**
     * Get messages with high priority
     */
    public List<InAppMessage> getHighPriorityMessages() {
        List<InAppMessage> allMessages = getAllMessages();
        List<InAppMessage> highPriorityMessages = new ArrayList<>();
        
        for (InAppMessage message : allMessages) {
            if ("high".equals(message.priority) && !message.isRead && !isMessageDismissed(message.id)) {
                highPriorityMessages.add(message);
            }
        }
        
        return highPriorityMessages;
    }
    
    /**
     * Mark message as read
     */
    public void markAsRead(String messageId) {
        List<InAppMessage> messages = getAllMessages();
        
        for (InAppMessage message : messages) {
            if (messageId.equals(message.id)) {
                message.isRead = true;
                break;
            }
        }
        
        saveMessages(messages);
    }
    
    /**
     * Dismiss message permanently
     */
    public void dismissMessage(String messageId) {
        Context context = AppContextProvider.getContext();
        if (context == null) return;
        
        // Add to dismissed messages
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        String dismissedJson = prefs.getString(PREF_DISMISSED_KEY, "[]");
        
        Type type = new TypeToken<List<String>>(){}.getType();
        List<String> dismissedIds = gson.fromJson(dismissedJson, type);
        
        if (dismissedIds == null) dismissedIds = new ArrayList<>();
        
        if (!dismissedIds.contains(messageId)) {
            dismissedIds.add(messageId);
            
            // Keep only last 100 dismissed IDs
            if (dismissedIds.size() > 100) {
                dismissedIds = dismissedIds.subList(dismissedIds.size() - 100, dismissedIds.size());
            }
            
            prefs.edit().putString(PREF_DISMISSED_KEY, gson.toJson(dismissedIds)).apply();
        }
        
        // Remove from active messages
        List<InAppMessage> messages = getAllMessages();
        final String finalMessageId = messageId;
        messages.removeIf(msg -> finalMessageId.equals(msg.id));
        saveMessages(messages);
    }
    
    /**
     * Check if message was dismissed
     */
    private boolean isMessageDismissed(String messageId) {
        Context context = AppContextProvider.getContext();
        if (context == null) return false;
        
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        String dismissedJson = prefs.getString(PREF_DISMISSED_KEY, "[]");
        
        Type type = new TypeToken<List<String>>(){}.getType();
        List<String> dismissedIds = gson.fromJson(dismissedJson, type);
        
        return dismissedIds != null && dismissedIds.contains(messageId);
    }
    
    /**
     * Save messages to preferences
     */
    private void saveMessages(List<InAppMessage> messages) {
        Context context = AppContextProvider.getContext();
        if (context == null) return;
        
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        String json = gson.toJson(messages);
        prefs.edit().putString(PREF_KEY, json).apply();
    }
    
    /**
     * Clear all messages
     */
    public void clearAllMessages() {
        saveMessages(new ArrayList<>());
    }
    
    /**
     * Get unread message count
     */
    public int getUnreadCount() {
        return getUnreadMessages().size();
    }
    
    /**
     * Clear old messages (older than 30 days)
     */
    public void clearOldMessages() {
        List<InAppMessage> messages = getAllMessages();
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        
        messages.removeIf(message -> message.timestamp < thirtyDaysAgo);
        saveMessages(messages);
    }
}
