package tk.therealsuji.vtopchennai.services;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class ClassTimeMonitorService extends Service {
    private static final String TAG = "ClassTimeMonitor";
    private static final long CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1); // Check every minute
    
    private Handler handler;
    private Runnable timeChecker;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        timeChecker = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    checkAndUpdateClassCount();
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            startTimeMonitoring();
            Log.d(TAG, "Class time monitoring started");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimeMonitoring();
        Log.d(TAG, "Class time monitoring stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTimeMonitoring() {
        handler.post(timeChecker);
    }

    private void stopTimeMonitoring() {
        isRunning = false;
        handler.removeCallbacks(timeChecker);
    }

    private void checkAndUpdateClassCount() {
        try {
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            
            // Convert to 0-based index (Sunday = 0, Monday = 1, etc.)
            int dayIndex = dayOfWeek - 1;
            
            // Check if today is marked as holiday
            boolean isTodayHoliday = SettingsRepository.getSharedPreferences(this)
                    .getBoolean("holiday_" + dayIndex, false);
            
            if (isTodayHoliday) {
                Log.d(TAG, "Today is marked as holiday, skipping class count update");
                return;
            }
            
            // Get current class count
            int currentClassCount = SettingsRepository.getSharedPreferences(this)
                    .getInt("todaysClassesCount", 0);
            
            if (currentClassCount <= 0) {
                return; // No classes to update
            }
            
            // Check if any classes have ended based on typical VIT class timings
            // This is a simplified approach - you might want to make this more sophisticated
            boolean shouldDecreaseCount = checkIfClassEnded(currentHour, currentMinute);
            
            if (shouldDecreaseCount) {
                int newCount = Math.max(0, currentClassCount - 1);
                SettingsRepository.getSharedPreferences(this)
                        .edit()
                        .putInt("todaysClassesCount", newCount)
                        .apply();
                
                Log.d(TAG, "Class ended, updated count from " + currentClassCount + " to " + newCount);
                
                // Broadcast update to refresh UI
                Intent updateIntent = new Intent("CLASS_COUNT_UPDATED");
                updateIntent.putExtra("newCount", newCount);
                sendBroadcast(updateIntent);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking class times", e);
        }
    }
    
    private boolean checkIfClassEnded(int currentHour, int currentMinute) {
        // VIT typical class timings (you can adjust these)
        // Morning: 8:00-9:00, 9:00-10:00, 10:00-11:00, 11:00-12:00
        // Afternoon: 12:00-13:00, 14:00-15:00, 15:00-16:00, 16:00-17:00
        
        int currentTimeInMinutes = currentHour * 60 + currentMinute;
        
        // Check if we're at the end of a typical class slot
        int[] classEndTimes = {
            9 * 60,   // 9:00
            10 * 60,  // 10:00
            11 * 60,  // 11:00
            12 * 60,  // 12:00
            13 * 60,  // 13:00
            15 * 60,  // 15:00
            16 * 60,  // 16:00
            17 * 60   // 17:00
        };
        
        for (int endTime : classEndTimes) {
            // Check if we're within 2 minutes of a class end time
            if (Math.abs(currentTimeInMinutes - endTime) <= 2) {
                return true;
            }
        }
        
        return false;
    }
}
