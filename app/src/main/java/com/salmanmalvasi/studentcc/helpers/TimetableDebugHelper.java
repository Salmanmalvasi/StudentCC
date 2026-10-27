package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug helper for troubleshooting timetable issues
 * Logs detailed information to help diagnose why some students can't see their timetable
 */
public class TimetableDebugHelper {
    private static final String TAG = "TimetableDebug";
    private static TimetableDebugHelper instance;
    private Context context;
    private StringBuilder debugLog;
    
    private TimetableDebugHelper() {
        debugLog = new StringBuilder();
        logDebug("===== TIMETABLE DEBUG SESSION STARTED =====");
        logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-DD HH:mm:ss", Locale.getDefault()).format(new Date()));
    }
    
    public static TimetableDebugHelper getInstance(Context context) {
        if (instance == null) {
            instance = new TimetableDebugHelper();
        }
        instance.context = context;
        return instance;
    }
    
    public void logDebug(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logMessage = "[" + timestamp + "] " + message;
        Log.d(TAG, logMessage);
        debugLog.append(logMessage).append("\n");
    }
    
    public void logError(String message, Exception e) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logMessage = "[" + timestamp + "] ERROR: " + message;
        if (e != null) {
            logMessage += " - " + e.getMessage();
        }
        Log.e(TAG, logMessage, e);
        debugLog.append(logMessage).append("\n");
        if (e != null) {
            debugLog.append("Stack trace: ").append(Log.getStackTraceString(e)).append("\n");
        }
    }
    
    public void logSemesterInfo(String semesterName, String semesterId) {
        logDebug("=== SEMESTER INFO ===");
        logDebug("Selected Semester: " + semesterName);
        logDebug("Semester ID: " + semesterId);
    }
    
    public void logCoursesParsing(String responseString) {
        logDebug("=== COURSES PARSING ===");
        try {
            JSONObject response = new JSONObject(responseString);
            JSONArray courses = response.getJSONArray("courses");
            logDebug("Total courses found: " + courses.length());
            
            for (int i = 0; i < courses.length(); i++) {
                JSONObject course = courses.getJSONObject(i);
                logDebug("Course " + (i + 1) + ": " + course.optString("code") + " - " + course.optString("title"));
                logDebug("  Type: " + course.optString("type"));
                logDebug("  Slots: " + course.optJSONArray("slots"));
            }
        } catch (Exception e) {
            logError("Failed to parse courses response", e);
        }
    }
    
    public void logTimetableParsing(String responseString) {
        logDebug("=== TIMETABLE PARSING ===");
        try {
            JSONObject response = new JSONObject(responseString);
            JSONArray labArray = response.getJSONArray("lab");
            JSONArray theoryArray = response.getJSONArray("theory");
            
            logDebug("Lab slots found: " + labArray.length());
            logDebug("Theory slots found: " + theoryArray.length());
            
            if (labArray.length() == 0 && theoryArray.length() == 0) {
                logDebug("❌ WARNING: NO TIMETABLE DATA FOUND!");
                logDebug("This is the root cause - timetable arrays are empty");
            }
            
            // Log first few lab slots
            for (int i = 0; i < Math.min(3, labArray.length()); i++) {
                JSONObject lab = labArray.getJSONObject(i);
                logDebug("Lab slot " + (i + 1) + ": " + lab.toString());
            }
            
            // Log first few theory slots
            for (int i = 0; i < Math.min(3, theoryArray.length()); i++) {
                JSONObject theory = theoryArray.getJSONObject(i);
                logDebug("Theory slot " + (i + 1) + ": " + theory.toString());
            }
            
        } catch (Exception e) {
            logError("Failed to parse timetable response", e);
        }
    }
    
    public void logHTMLResponse(String html) {
        logDebug("=== HTML RESPONSE ANALYSIS ===");
        if (html == null || html.isEmpty()) {
            logDebug("❌ HTML is null or empty!");
            return;
        }
        
        logDebug("HTML length: " + html.length() + " characters");
        
        // Check for common error messages
        String htmlLower = html.toLowerCase();
        if (htmlLower.contains("no record(s) found")) {
            logDebug("❌ HTML contains 'no record(s) found' - VTOP has no timetable data");
            logDebug(">>> THIS IS THE PROBLEM: VTOP hasn't published the timetable yet");
        }
        if (htmlLower.contains("not authorized")) {
            logDebug("❌ HTML contains 'not authorized' - authentication issue");
        }
        if (htmlLower.contains("timetablestyle")) {
            logDebug("✓ HTML contains 'timeTableStyle' - timetable table found");
        } else {
            logDebug("❌ HTML does NOT contain 'timeTableStyle' - timetable table missing!");
        }
        if (htmlLower.contains("studentdetails")) {
            logDebug("✓ HTML contains 'studentDetails' - student info found");
        } else {
            logDebug("❌ HTML does NOT contain 'studentDetails' - student info missing!");
        }
        
        // Log first 1000 characters for inspection
        String preview = html.substring(0, Math.min(1000, html.length()));
        logDebug("HTML Preview (first 1000 chars): " + preview + "...");
    }
    
    public void logSlotMapping(String slotName, Integer slotId, String courseType) {
        logDebug("Mapping slot: " + slotName + " -> ID: " + slotId + " (Type: " + courseType + ")");
    }
    
    public void logDatabaseInsert(int timetableCount) {
        logDebug("=== DATABASE INSERT ===");
        logDebug("Inserting " + timetableCount + " timetable entries");
    }
    
    public void logTimetableComplete(boolean success) {
        if (success) {
            logDebug("✅ Timetable download COMPLETED successfully");
        } else {
            logDebug("❌ Timetable download FAILED");
        }
    }
    
    public String getDebugLog() {
        return debugLog.toString();
    }
    
    public void saveDebugLogToFile() {
        try {
            // Save to multiple locations to ensure accessibility
            
            // Location 1: App's external files directory
            File logFile1 = new File(context.getExternalFilesDir(null), "timetable_debug.log");
            FileWriter writer1 = new FileWriter(logFile1);
            writer1.write(debugLog.toString());
            writer1.close();
            logDebug("Debug log saved to: " + logFile1.getAbsolutePath());
            
            // Location 2: Downloads folder (more accessible)
            File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            File logFile2 = new File(downloadsDir, "StudentCC_Timetable_Debug.txt");
            FileWriter writer2 = new FileWriter(logFile2);
            writer2.write("=== STUDENTCC TIMETABLE DEBUG LOG ===\n");
            writer2.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
            writer2.write("Please share this file with the developer\n\n");
            writer2.write(debugLog.toString());
            writer2.close();
            logDebug("Debug log ALSO saved to Downloads: " + logFile2.getAbsolutePath());
            
            // Show toast to user with clear instructions
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                android.widget.Toast.makeText(context, 
                    "✅ Debug log saved to Downloads folder!\nFile: StudentCC_Timetable_Debug.txt", 
                    android.widget.Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            logError("Failed to save debug log to file", e);
            // Show error toast
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                android.widget.Toast.makeText(context, 
                    "❌ Failed to save log. Use the 'Copy Log' button instead.", 
                    android.widget.Toast.LENGTH_LONG).show();
            });
        }
    }
    
    public void reset() {
        debugLog = new StringBuilder();
        logDebug("===== DEBUG SESSION RESET =====");
    }
}
