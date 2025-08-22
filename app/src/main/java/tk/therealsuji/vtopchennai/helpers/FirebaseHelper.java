package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;

/**
 * Firebase Helper class to manage Firebase services
 * Created by Salman Malvasi
 */
public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";
    
    private static FirebaseHelper instance;
    private FirebaseAnalytics firebaseAnalytics;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private FirebaseMessaging messaging;
    
    private FirebaseHelper() {
        // Private constructor for singleton
    }
    
    public static FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }
    
    /**
     * Initialize Firebase services
     */
    public void initializeFirebase(Context context) {
        try {
            // Initialize Firebase Analytics
            firebaseAnalytics = FirebaseAnalytics.getInstance(context);
            
            // Initialize Firebase Auth
            firebaseAuth = FirebaseAuth.getInstance();
            
            // Initialize Firestore
            firestore = FirebaseFirestore.getInstance();
            
            // Initialize Firebase Storage
            storage = FirebaseStorage.getInstance();
            
            // Initialize Firebase Messaging
            messaging = FirebaseMessaging.getInstance();
            
            Log.d(TAG, "Firebase services initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
        }
    }
    
    /**
     * Get Firebase Analytics instance
     */
    public FirebaseAnalytics getAnalytics() {
        return firebaseAnalytics;
    }
    
    /**
     * Get Firebase Auth instance
     */
    public FirebaseAuth getAuth() {
        return firebaseAuth;
    }
    
    /**
     * Get Firestore instance
     */
    public FirebaseFirestore getFirestore() {
        return firestore;
    }
    
    /**
     * Get Firebase Storage instance
     */
    public FirebaseStorage getStorage() {
        return storage;
    }
    
    /**
     * Get Firebase Messaging instance
     */
    public FirebaseMessaging getMessaging() {
        return messaging;
    }
    
    /**
     * Log custom event to Firebase Analytics
     */
    public void logEvent(String eventName, String parameterName, String parameterValue) {
        if (firebaseAnalytics != null) {
            Bundle bundle = new Bundle();
            bundle.putString(parameterName, parameterValue);
            firebaseAnalytics.logEvent(eventName, bundle);
        }
    }
    
    /**
     * Log user login event
     */
    public void logUserLogin(String userId) {
        logEvent("user_login", "user_id", userId);
    }
    
    /**
     * Log GPA calculation event
     */
    public void logGPACalculation(String gpaValue) {
        logEvent("gpa_calculation", "gpa_value", gpaValue);
    }
    
    /**
     * Log attendance view event
     */
    public void logAttendanceView(String attendancePercentage) {
        logEvent("attendance_view", "attendance_percentage", attendancePercentage);
    }
} 