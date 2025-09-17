package tk.therealsuji.vtopchennai.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.HostelDataHelper;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class LaundryNotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "laundry_notifications";

    @Override
    public void onReceive(Context context, Intent intent) {
        String studentType = SettingsRepository.getEncryptedSharedPreferences(context).getString("student_type", "");
        if (!"hosteller".equals(studentType)) return;

        String block = SettingsRepository.getEncryptedSharedPreferences(context).getString("hostel_block", "");
        String room = SettingsRepository.getEncryptedSharedPreferences(context).getString("room_number", "");

        HostelDataHelper helper = HostelDataHelper.getInstance(context);
        HostelDataHelper.LaundrySchedule today = helper.getTodaysLaundrySchedule(block, room);
        if (today == null) return;

        createChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_available)
                .setContentTitle("Laundry Day")
                .setContentText("Your laundry is scheduled today for room " + room)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(2011, builder.build());
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Laundry Notifications", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for laundry schedule days");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }
}


