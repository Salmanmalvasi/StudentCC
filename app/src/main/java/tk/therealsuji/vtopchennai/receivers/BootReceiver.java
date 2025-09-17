package tk.therealsuji.vtopchennai.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.AlarmManager;
import android.app.PendingIntent;
import java.util.Calendar;

import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Timetable;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            return;
        }

        if (!SettingsRepository.isSignedIn(context)) {
            return;
        }

        SettingsRepository.clearNotificationPendingIntents(context);

        AppDatabase appDatabase = AppDatabase.getInstance(context);
        TimetableDao timetableDao = appDatabase.timetableDao();
        ExamsDao examsDao = appDatabase.examsDao();

        timetableDao
                .getTimetable()
                .subscribeOn(Schedulers.single())
                .subscribe(new SingleObserver<List<Timetable>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Timetable> timetable) {
                        for (int i = 0; i < timetable.size(); ++i) {
                            try {
                                SettingsRepository.setTimetableNotifications(context, timetable.get(i));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });

        examsDao
                .getExams()
                .subscribeOn(Schedulers.single())
                .subscribe(new SingleObserver<List<Exam>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Exam> exams) {
                        SettingsRepository.setExamNotifications(context, exams);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });

        // Schedule daily laundry check at 8 AM
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent laundryIntent = new Intent(context, LaundryNotificationReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, 2011, laundryIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            long trigger = cal.getTimeInMillis();
            if (trigger < System.currentTimeMillis()) trigger += AlarmManager.INTERVAL_DAY;
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
        }
    }
}
