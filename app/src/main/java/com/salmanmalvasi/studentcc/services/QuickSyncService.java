package com.salmanmalvasi.studentcc.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.activities.MainActivity;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.sync.NativeSyncBridge;

/**
 * QuickSyncService — a lightweight, fast foreground service that syncs ONLY:
 *   • Attendance summary
 *   • Detailed attendance calendar
 *   • Marks
 *
 * It is completely separate from the legacy WebView-based VTOPService pipeline.
 * It uses the NativeSyncBridge in quick-sync mode, reusing session cookies already
 * established by a prior VTOPService (or WebView) login.
 */
public class QuickSyncService extends Service {

    private static final String TAG = "QuickSyncService";
    private static final String CHANNEL_ID = "quick_sync_channel";
    private static final int NOTIFICATION_ID = 2001;

    public interface QuickSyncCallback {
        void onLoading(boolean isLoading);
        void onComplete();
        void onError(String message);
    }

    public class QuickSyncBinder extends Binder {
        public QuickSyncService getService() {
            return QuickSyncService.this;
        }

        public void setCallback(QuickSyncCallback cb) {
            callback = cb;
        }
    }

    private final QuickSyncBinder binder = new QuickSyncBinder();
    private QuickSyncCallback callback;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        notificationBuilder = buildNotification("Starting quick sync…");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            Log.d(TAG, "Quick sync already running — ignoring duplicate start");
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
        running = true;

        new Thread(this::performQuickSync).start();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void performQuickSync() {
        try {
            // Retrieve stored CSRF / authorizedID from SharedPreferences
            SharedPreferences prefs = SettingsRepository.getSharedPreferences(getApplicationContext());
            SharedPreferences encPrefs = SettingsRepository.getEncryptedSharedPreferences(getApplicationContext());

            if (encPrefs == null) {
                finishWithError("Not signed in");
                return;
            }

            String csrf = prefs.getString("last_csrf", "");
            String authId = prefs.getString("username", "");
            String semesterId = prefs.getString("semesterID", "");

            // Also try the semester map to pick the first (current) semester if semesterID is blank
            if (semesterId == null || semesterId.isEmpty()) {
                String semMapJson = prefs.getString("semestersMapJson", "");
                if (semMapJson != null && !semMapJson.isEmpty()) {
                    try {
                        org.json.JSONObject map = new org.json.JSONObject(semMapJson);
                        java.util.Iterator<String> keys = map.keys();
                        if (keys.hasNext()) {
                            semesterId = map.getString(keys.next());
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Pull session cookies from CookieManager (set by a prior VTOPService login)
            String cookieHeader = android.webkit.CookieManager.getInstance().getCookie(SettingsRepository.VTOP_API_BASE_URL);
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                cookieHeader = android.webkit.CookieManager.getInstance().getCookie(SettingsRepository.VTOP_BASE_URL);
            }
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                cookieHeader = android.webkit.CookieManager.getInstance().getCookie("https://vtopcc.vit.ac.in");
            }

            if (csrf.isEmpty() || authId.isEmpty() || cookieHeader == null || cookieHeader.isEmpty() || semesterId == null || semesterId.isEmpty()) {
                Log.w(TAG, "Quick sync: missing session tokens (csrf=" + csrf.isEmpty() + ", authId=" + authId.isEmpty() + ", cookie=" + (cookieHeader == null) + ", sem=" + (semesterId == null || semesterId.isEmpty()) + ")");
                finishWithError("No active session. Please run a full sync first.");
                return;
            }

            updateNotification("Syncing attendance & marks…");

            String userAgent = prefs.getString("userAgent", null);
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            NativeSyncBridge bridge = new NativeSyncBridge(db, SettingsRepository.VTOP_API_BASE_URL, userAgent, this);
            bridge.setQuickSyncMode(true);   // Only: attendance summary + detailed attendance + marks

            NativeSyncBridge.BridgeResult result = bridge.executeSync(csrf, authId, cookieHeader, semesterId,
                    (completed, total, message) -> {
                        int pct = total > 0 ? (completed * 100 / total) : 0;
                        updateNotification("Quick sync: " + pct + "% — " + (message != null ? message : ""));
                    });

            if (result.success) {
                // Persist attendance aggregate to SharedPreferences for HomeFragment
                if (result.stagedData != null && result.stagedData.profileAttributes.containsKey("overallAttendance")) {
                    try {
                        float overall = Float.parseFloat(result.stagedData.profileAttributes.get("overallAttendance"));
                        int attended = Integer.parseInt(result.stagedData.profileAttributes.get("attendedClasses"));
                        int total = Integer.parseInt(result.stagedData.profileAttributes.get("totalClasses"));
                        prefs.edit()
                                .putFloat("overallAttendance", overall)
                                .putInt("attendedClasses", attended)
                                .putInt("totalClasses", total)
                                .putLong("lastRefreshed", System.currentTimeMillis())
                                .apply();
                    } catch (Exception ignored) {}
                }

                // Update freshness timestamps
                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance()
                        .recordSuccessfulRefresh(getApplicationContext(),
                                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_ATTENDANCE_SUMMARY);
                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance()
                        .recordSuccessfulRefresh(getApplicationContext(),
                                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_MARKS);
                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.getInstance()
                        .recordSuccessfulRefresh(getApplicationContext(),
                                com.salmanmalvasi.studentcc.sync.DataFreshnessManager.DATASET_DETAILED_ATTENDANCE);

                Log.i(TAG, "Quick sync completed in " + result.durationMs + " ms");
                finishSuccess();
            } else if (result.sessionExpired) {
                Log.w(TAG, "Quick sync failed — session expired. Please run a full sync.");
                finishWithError("Session expired. Run a full sync to refresh.");
            } else {
                Log.w(TAG, "Quick sync failed: " + result.errorMessage);
                finishWithError("Quick sync failed: " + result.errorMessage);
            }

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during quick sync", e);
            finishWithError("Error: " + e.getMessage());
        }
    }

    private void finishSuccess() {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            if (callback != null) callback.onComplete();
        });
        stopForeground(true);
        stopSelf();
        running = false;
    }

    private void finishWithError(String msg) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            if (callback != null) {
                callback.onError(msg);
                callback.onLoading(false);
            }
        });
        stopForeground(true);
        stopSelf();
        running = false;
    }

    private void updateNotification(String text) {
        notificationBuilder.setContentText(text);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Quick Sync",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Fast attendance & marks sync");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flash)
                .setContentTitle("Quick Sync")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi);
    }
}
