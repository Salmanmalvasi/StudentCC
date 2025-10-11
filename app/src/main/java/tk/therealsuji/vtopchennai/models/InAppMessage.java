package tk.therealsuji.vtopchennai.models;

/**
 * Model for in-app messages from Firebase
 */
public class InAppMessage {
    public String id;
    public String title;
    public String message;
    public String imageUrl;
    public String actionUrl;
    public String priority; // "high", "normal", "low"
    public long timestamp;
    public boolean isRead;
    public String messageType; // "announcement", "update", "alert", "promotion"

    public InAppMessage() {
        // Default constructor
    }

    public InAppMessage(String id, String title, String message) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.priority = "normal";
    }

    /**
     * Check if message is high priority
     */
    public boolean isHighPriority() {
        return "high".equals(priority);
    }

    /**
     * Check if message has an action URL
     */
    public boolean hasAction() {
        return actionUrl != null && !actionUrl.trim().isEmpty();
    }

    /**
     * Check if message has an image
     */
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }

    /**
     * Get formatted timestamp
     */
    public String getFormattedTime() {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60 * 1000) {
            return "Just now";
        } else if (diff < 60 * 60 * 1000) {
            return (diff / (60 * 1000)) + "m ago";
        } else if (diff < 24 * 60 * 60 * 1000) {
            return (diff / (60 * 60 * 1000)) + "h ago";
        } else {
            return (diff / (24 * 60 * 60 * 1000)) + "d ago";
        }
    }
}
