package com.salmanmalvasi.studentcc.models;

import com.google.firebase.Timestamp;

/**
 * Model for campus events submitted by clubs
 */
public class Event {
    public String id;
    public String clubId;
    public String clubName;
    public String title;
    public String description;
    public String posterUrl;
    public Timestamp startTime;
    public Timestamp endTime;
    public String venue;
    public String status; // "pending" | "approved" | "rejected"
    public String submittedBy; // Club lead UID
    public Timestamp submittedAt;
    public String approvedBy; // Admin UID
    public Timestamp approvedAt;
    public String rejectionReason;

    public Event() {
        // Required empty constructor for Firestore
    }

    public Event(String clubId, String clubName, String title, String description,
                 Timestamp startTime, Timestamp endTime, String venue, String submittedBy) {
        this.clubId = clubId;
        this.clubName = clubName;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        this.submittedBy = submittedBy;
        this.status = "pending";
        this.submittedAt = Timestamp.now();
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }

    public boolean isRejected() {
        return "rejected".equals(status);
    }

    /**
     * Get formatted date range for display
     */
    public String getFormattedDateRange() {
        if (startTime == null) return "";
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy");
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("h:mm a");
        
        String start = dateFormat.format(startTime.toDate()) + " at " + timeFormat.format(startTime.toDate());
        if (endTime != null) {
            return start + " - " + timeFormat.format(endTime.toDate());
        }
        return start;
    }
}
