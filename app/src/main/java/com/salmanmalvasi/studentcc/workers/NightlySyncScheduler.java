package com.salmanmalvasi.studentcc.workers;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a daily background sync job to fire randomly between 12:00 AM and 1:00 AM.
 */
public final class NightlySyncScheduler {
    private static final String TAG = "NightlySyncScheduler";
    public static final String WORK_NAME = "nightly_vtop_sync";

    private NightlySyncScheduler() {
    }

    /**
     * Enqueues or updates the 24-hour periodic work request targeting 12:00 AM - 1:00 AM window.
     */
    public static void schedule(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        try {
            Calendar now = Calendar.getInstance();

            // Target 12:00 AM (00:00) with a randomized minute (0-59) and second (0-59)
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, 0);

            Random random = new Random();
            int randomMinute = random.nextInt(60);
            int randomSecond = random.nextInt(60);

            target.set(Calendar.MINUTE, randomMinute);
            target.set(Calendar.SECOND, randomSecond);
            target.set(Calendar.MILLISECOND, 0);

            // If the target time for today has already passed, schedule for the next day's 12 AM
            if (now.after(target)) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }

            long initialDelayMs = target.getTimeInMillis() - now.getTimeInMillis();
            long delayMinutes = TimeUnit.MILLISECONDS.toMinutes(initialDelayMs);

            Log.i(TAG, "Scheduling nightly sync in " + delayMinutes + " minutes (around " +
                    String.format(java.util.Locale.US, "12:%02d AM", randomMinute) + ")");

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    NightlySyncWorker.class,
                    24, TimeUnit.HOURS,
                    15, TimeUnit.MINUTES // 15-minute flex interval
            )
                    .setConstraints(constraints)
                    .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                    .addTag(WORK_NAME)
                    .build();

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
            );

            Log.i(TAG, "Nightly sync periodic work request enqueued successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule nightly sync", e);
        }
    }
}
