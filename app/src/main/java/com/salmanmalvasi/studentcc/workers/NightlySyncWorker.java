package com.salmanmalvasi.studentcc.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.activities.MainActivity;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.FastScraper;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.models.SemesterSummary;

/**
 * Background worker that executes nightly between 12:00 AM and 1:00 AM
 * to keep academic data (attendance and marks) silently synchronized.
 */
public class NightlySyncWorker extends Worker {
    private static final String TAG = "NightlySyncWorker";
    private static final String CHANNEL_ID = "nightly_sync_channel";
    private static final int NOTIFICATION_ID = 2002;

    public NightlySyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Nightly background sync started in 12:00 AM - 1:00 AM window.");
        Context context = getApplicationContext();

        try {
            if (!SettingsRepository.isSignedIn(context)) {
                Log.i(TAG, "User not signed in. Skipping nightly sync.");
                return Result.success();
            }

            SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context);
            String cookies = FastScraper.getVtopCookies();
            String csrf = sharedPreferences.getString("last_csrf", "");
            String authorizedId = SettingsRepository.getAuthorizedUsername(context);
            String userAgent = sharedPreferences.getString("userAgent", FastScraper.DEFAULT_USER_AGENT);
            String currentSemName = sharedPreferences.getString("semester", "");
            String currentSemId = sharedPreferences.getString("semesterSubId", "");

            if (currentSemId.isEmpty()) {
                currentSemId = "CURRENT_SEM";
            }

            boolean syncSucceeded = false;

            // Attempt fast direct scraper if cookies and csrf are valid
            if (!cookies.isEmpty() && !csrf.isEmpty() && !authorizedId.isEmpty()) {
                try {
                    SemesterSummary summary = FastScraper.scrapeSemesterData(
                            csrf, authorizedId, currentSemId, currentSemName, cookies, userAgent
                    );
                    if (summary != null && summary.totalClasses > 0) {
                        sharedPreferences.edit()
                                .putFloat("overallAttendance", summary.attendancePercentage)
                                .putInt("attendedClasses", summary.attendedClasses)
                                .putInt("totalClasses", summary.totalClasses)
                                .putLong("lastRefreshed", System.currentTimeMillis())
                                .putLong("lastAutoSyncTime", System.currentTimeMillis())
                                .apply();
                        syncSucceeded = true;
                        Log.i(TAG, "Nightly sync updated attendance via direct session scraper: " +
                                summary.attendancePercentage + "%");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Direct session scraper in nightly worker encountered: " + e.getMessage());
                }
            }

            // Record sync timestamp
            sharedPreferences.edit()
                    .putLong("lastNightlySyncTime", System.currentTimeMillis())
                    .apply();

            if (syncSucceeded) {
                showCompletionNotification("Attendance & marks synced overnight.");
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Nightly sync error", e);
            return Result.success(); // Do not retry repeatedly at night
        }
    }

    private void showCompletionNotification(String message) {
        Context context = getApplicationContext();
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nightly Data Sync",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Silent background updates between 12 AM and 1 AM");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle("Data Synced")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
