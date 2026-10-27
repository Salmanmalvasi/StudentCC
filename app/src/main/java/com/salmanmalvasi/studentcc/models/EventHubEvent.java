package com.salmanmalvasi.studentcc.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

/**
 * Model class representing an event from VIT EventHub
 * https://eventhubcc.vit.ac.in/EventHub/
 */
@Entity(tableName = "eventhub_events")
public class EventHubEvent {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public int id;

    public String title;           // Event name (e.g., "CODE CLASH")
    public String category;        // VITian/Non-VITian, General, etc.
    public String date;            // Event date (e.g., "2026-02-17")
    public String venue;           // Event venue
    public String fee;             // Event fee (e.g., "Free", "₹500")
    public String teamSize;        // Team size (e.g., "1-3")
    public String eventUrl;        // Link to event details
    public String clubName;        // Organizing club name
    
    @ColumnInfo(defaultValue = "0")
    public boolean isFeatured;     // Whether the event is featured
    
    public long lastUpdated;       // Timestamp when event was fetched
    public boolean isFree;         // Quick filter for free events

    public EventHubEvent() {}

    @Ignore
    public EventHubEvent(String title, String category, String date, String venue, 
                         String fee, String teamSize, String eventUrl, String clubName) {
        this.title = title;
        this.category = category;
        this.date = date;
        this.venue = venue;
        this.fee = fee;
        this.teamSize = teamSize;
        this.eventUrl = eventUrl;
        this.clubName = clubName;
        this.lastUpdated = System.currentTimeMillis();
        this.isFree = fee != null && fee.toLowerCase().contains("free");
        this.isFeatured = false;
    }

    /**
     * Get formatted display date
     */
    public String getFormattedDate() {
        if (date == null || date.isEmpty()) return "";
        try {
            // Convert YYYY-MM-DD to more readable format
            String[] parts = date.split("-");
            if (parts.length == 3) {
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                int monthIndex = Integer.parseInt(parts[1]) - 1;
                if (monthIndex >= 0 && monthIndex < 12) {
                    return parts[2] + " " + months[monthIndex] + " " + parts[0];
                }
            }
        } catch (Exception e) {
            // Fallback to original format
        }
        return date;
    }

    /**
     * Get fee display text
     */
    public String getFeeDisplay() {
        if (isFree) {
            return "FREE";
        }
        if (fee == null || fee.isEmpty()) return "";
        // Add rupee symbol if it's a number
        try {
            int amount = Integer.parseInt(fee.replaceAll("[^0-9]", ""));
            if (amount > 0) {
                return "₹" + amount;
            }
        } catch (Exception e) {
            // Fallback to original fee string
        }
        return fee;
    }

    /**
     * Check if event is upcoming (date >= today)
     */
    public boolean isUpcoming() {
        if (date == null || date.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH);
            java.util.Date eventDate = sdf.parse(date);
            java.util.Date today = new java.util.Date();
            return eventDate != null && !eventDate.before(today);
        } catch (Exception e) {
            return true; // Assume upcoming if parsing fails
        }
    }
}
