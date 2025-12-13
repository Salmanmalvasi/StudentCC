package tk.therealsuji.vtopchennai.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.activities.MainActivity;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.models.Attendance;

public class AttendanceWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_attendance);

        // Open app on click
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent);

        // Fetch data
        AppDatabase db = AppDatabase.getInstance(context);
        db.attendanceDao().getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(attendanceList -> {
                    if (attendanceList != null && !attendanceList.isEmpty()) {
                        int totalAttended = 0;
                        int totalClasses = 0;

                        for (Attendance att : attendanceList) {
                            if (att.attended != null && att.total != null) {
                                totalAttended += att.attended;
                                totalClasses += att.total;
                            }
                        }

                        if (totalClasses > 0) {
                            float percentage = ((float) totalAttended / totalClasses) * 100;
                            views.setTextViewText(R.id.widget_percentage, String.format("%.1f%%", percentage));
                            views.setTextViewText(R.id.widget_status, "Overall Attendance");
                        } else {
                            views.setTextViewText(R.id.widget_percentage, "--%");
                            views.setTextViewText(R.id.widget_status, "No Data");
                        }
                    } else {
                        views.setTextViewText(R.id.widget_percentage, "--%");
                        views.setTextViewText(R.id.widget_status, "No Data");
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }, error -> {
                    views.setTextViewText(R.id.widget_percentage, "Err");
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });

        // Initial update while fetching
        views.setTextViewText(R.id.widget_percentage, "...");
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
