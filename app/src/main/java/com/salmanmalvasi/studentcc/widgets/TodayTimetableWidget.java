package com.salmanmalvasi.studentcc.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.activities.MainActivity;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.models.Timetable;

import java.util.Calendar;
import java.util.List;

/**
 * Single widget that always and only shows today's scheduled classes and venues.
 * Whichever day of the week it is, it automatically shows that day's timetable.
 */
public class TodayTimetableWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.salmanmalvasi.studentcc.widget.ACTION_REFRESH";

    private static final String[] DAY_NAMES = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) return;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        if (ACTION_REFRESH.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_classes_list);
                updateAppWidget(context, appWidgetManager, appWidgetId);
            } else {
                updateAllWidgets(context);
            }
            android.widget.Toast.makeText(context, "Timetable widget updated", android.widget.Toast.LENGTH_SHORT).show();
        } else if (Intent.ACTION_DATE_CHANGED.equals(action) ||
                   Intent.ACTION_TIME_CHANGED.equals(action) ||
                   Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            updateAllWidgets(context);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_timetable);

        // Always resolve to TODAY
        int calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int today = calDay - 1; // 0..6 (Sun..Sat)
        String dayName = DAY_NAMES[today];

        views.setTextViewText(R.id.widget_day_title, "Today • " + dayName);
        views.setTextViewText(R.id.widget_empty_title, "No Classes Today");
        views.setTextViewText(R.id.widget_empty_subtitle, "Enjoy your free time! ✨");

        // 1. General launch intent (Opens MainActivity -> Timetable / Home)
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.putExtra("target_tab", "home");
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingLaunch = PendingIntent.getActivity(context, appWidgetId * 10, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Make all non-list surfaces click to open the app!
        views.setOnClickPendingIntent(R.id.widget_root, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_header, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_title_container, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_day_title, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_class_count, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_empty_view, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_empty_title, pendingLaunch);
        views.setOnClickPendingIntent(R.id.widget_empty_subtitle, pendingLaunch);

        // 2. Settings button action (Opens MainActivity -> Settings & Profile)
        Intent settingsIntent = new Intent(context, MainActivity.class);
        settingsIntent.putExtra("target_tab", "profile");
        settingsIntent.putExtra("launchFragment", com.salmanmalvasi.studentcc.fragments.ProfileFragment.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingSettings = PendingIntent.getActivity(context, appWidgetId * 10 + 2, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_settings, pendingSettings);

        // 3. Refresh Action
        Intent refreshIntent = new Intent(context, TodayTimetableWidget.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPending);

        // 4. Bind RemoteViewsService for ListView
        Intent serviceIntent = new Intent(context, DayTimetableWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.putExtra("fixed_day", today);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_classes_list, serviceIntent);
        views.setEmptyView(R.id.widget_classes_list, R.id.widget_empty_view);

        // 5. Set pending intent template for list item clicks (MUTABLE on Android 12+ so fill-in intent works)
        int itemFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            itemFlags |= PendingIntent.FLAG_MUTABLE;
        }
        Intent itemClickIntent = new Intent(context, MainActivity.class);
        itemClickIntent.putExtra("target_tab", "home");
        itemClickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingItemLaunch = PendingIntent.getActivity(context, appWidgetId * 10 + 4, itemClickIntent, itemFlags);
        views.setPendingIntentTemplate(R.id.widget_classes_list, pendingItemLaunch);

        // Synchronously check class count so views has complete text before updating
        AppDatabase db = AppDatabase.getInstance(context);
        int queryDay = today;
        SharedPreferences sp = SettingsRepository.getSharedPreferences(context);
        if (today == 0 || today == 6) {
            int override = sp.getInt("working_override_" + today, -1);
            if (override >= 0 && override <= 6) {
                queryDay = override;
            }
        }
        boolean isHoliday = sp.getBoolean("holiday_" + today, false);

        if (isHoliday) {
            views.setTextViewText(R.id.widget_class_count, "Holiday (No Classes)");
        } else {
            try {
                List<Timetable.AllData> classes = db.timetableDao().get(queryDay).blockingGet();
                int count = (classes != null) ? classes.size() : 0;
                if (count == 0) {
                    views.setTextViewText(R.id.widget_class_count, "No classes scheduled");
                } else if (count == 1) {
                    views.setTextViewText(R.id.widget_class_count, "1 Class scheduled");
                } else {
                    views.setTextViewText(R.id.widget_class_count, count + " Classes scheduled");
                }
            } catch (Exception e) {
                views.setTextViewText(R.id.widget_class_count, "Today's Schedule");
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, TodayTimetableWidget.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids != null && ids.length > 0) {
            for (int id : ids) {
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_classes_list);
                updateAppWidget(context, mgr, id);
            }
        }
    }
}
