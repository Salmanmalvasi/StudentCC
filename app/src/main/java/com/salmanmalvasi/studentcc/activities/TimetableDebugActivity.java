package com.salmanmalvasi.studentcc.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.TimetableDebugHelper;

import java.io.File;

/**
 * Debug Activity to view timetable debug logs
 * Helps diagnose why some students can't see their timetable
 */
public class TimetableDebugActivity extends AppCompatActivity {
    private TextView debugLogTextView;
    private TextView instructionsTextView;
    private Button copyButton;
    private Button saveButton;
    private Button refreshButton;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout programmatically
        scrollView = new ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText("🐛 Timetable Debug Log");
        titleView.setTextSize(24);
        titleView.setPadding(0, 0, 0, 16);
        layout.addView(titleView);

        // Instructions
        instructionsTextView = new TextView(this);
        instructionsTextView.setText("📋 Instructions:\n" +
            "1. Sync your data first (pull to refresh on home screen)\n" +
            "2. Click 'Refresh Log' to see the latest debug info\n" +
            "3. Click 'Save to Downloads' to save as a file\n" +
            "4. Share the file 'StudentCC_Timetable_Debug.txt' from Downloads folder\n\n" +
            "📁 File will be saved to: Downloads/StudentCC_Timetable_Debug.txt");
        instructionsTextView.setTextSize(12);
        instructionsTextView.setPadding(0, 0, 0, 24);
        instructionsTextView.setBackgroundColor(0xFFE3F2FD); // Light blue background
        instructionsTextView.setPadding(16, 16, 16, 16);
        layout.addView(instructionsTextView);

        // Debug log text
        debugLogTextView = new TextView(this);
        debugLogTextView.setTextSize(11);
        debugLogTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        debugLogTextView.setBackgroundColor(0xFFF5F5F5); // Light gray background
        debugLogTextView.setPadding(16, 16, 16, 16);
        layout.addView(debugLogTextView);

        // Button container
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
        buttonLayout.setOrientation(android.widget.LinearLayout.VERTICAL);

        // Save to Downloads button
        saveButton = new Button(this);
        saveButton.setText("💾 Save to Downloads");
        saveButton.setOnClickListener(v -> saveLogToDownloads());
        buttonLayout.addView(saveButton);

        // Copy button
        copyButton = new Button(this);
        copyButton.setText("📋 Copy Log to Clipboard");
        copyButton.setOnClickListener(v -> copyLogToClipboard());
        buttonLayout.addView(copyButton);

        // Refresh button
        refreshButton = new Button(this);
        refreshButton.setText("🔄 Refresh Log");
        refreshButton.setOnClickListener(v -> loadDebugLog());
        buttonLayout.addView(refreshButton);

        layout.addView(buttonLayout);
        scrollView.addView(layout);
        setContentView(scrollView);

        loadDebugLog();
    }

    private void loadDebugLog() {
        TimetableDebugHelper debugHelper = TimetableDebugHelper.getInstance(this);
        String log = debugHelper.getDebugLog();
        
        if (log == null || log.isEmpty()) {
            debugLogTextView.setText("❌ No debug log available yet.\n\n" +
                "📝 Steps to generate debug log:\n" +
                "1. Go back to home screen\n" +
                "2. Pull down to refresh and sync your data\n" +
                "3. Wait for sync to complete\n" +
                "4. Come back here and tap 'Refresh Log'\n\n" +
                "If you still don't see logs, please contact the developer.");
        } else {
            debugLogTextView.setText(log);
            // Scroll to bottom to see latest logs
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private void saveLogToDownloads() {
        TimetableDebugHelper debugHelper = TimetableDebugHelper.getInstance(this);
        debugHelper.saveDebugLogToFile();
        // Toast is shown by the helper method
    }

    private void copyLogToClipboard() {
        String log = debugLogTextView.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Timetable Debug Log", log);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "✅ Log copied to clipboard!\nNow paste and share it with the developer.", Toast.LENGTH_LONG).show();
    }
}
