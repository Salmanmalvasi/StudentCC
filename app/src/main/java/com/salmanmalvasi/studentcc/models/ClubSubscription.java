package com.salmanmalvasi.studentcc.models;

import com.google.firebase.Timestamp;

/**
 * Model for user subscriptions to specific clubs
 */
public class ClubSubscription {
    public String clubId;
    public String clubName;
    public boolean subscribed;
    public Timestamp subscribedAt;

    public ClubSubscription() {
        // Required empty constructor for Firestore
    }

    public ClubSubscription(String clubId, String clubName, boolean subscribed) {
        this.clubId = clubId;
        this.clubName = clubName;
        this.subscribed = subscribed;
        if (subscribed) {
            this.subscribedAt = Timestamp.now();
        }
    }
}
