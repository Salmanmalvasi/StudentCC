package tk.therealsuji.vtopchennai.models;

import com.google.firebase.Timestamp;

/**
 * Model for campus clubs
 */
public class Club {
    public String id;
    public String name;
    public String description;
    public String logoUrl;
    public String leadEmail;
    public String leadName;
    public String status; // "pending" | "approved" | "rejected"
    public Timestamp createdAt;
    public String approvedBy; // Admin UID
    public Timestamp approvedAt;

    public Club() {
        // Required empty constructor for Firestore
    }

    public Club(String name, String description, String leadEmail, String leadName) {
        this.name = name;
        this.description = description;
        this.leadEmail = leadEmail;
        this.leadName = leadName;
        this.status = "pending";
        this.createdAt = Timestamp.now();
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
}
