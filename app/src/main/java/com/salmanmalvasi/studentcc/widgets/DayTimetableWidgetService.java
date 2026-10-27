package com.salmanmalvasi.studentcc.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.models.Timetable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DayTimetableWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new DayTimetableRemoteViewsFactory(getApplicationContext(), intent);
    }

    private static class DayTimetableRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final int appWidgetId;
        private int fixedDay = -1; // -1 means dynamic "Today"
        private int currentResolvedDay = 1;
        private final List<Timetable.AllData> classes = new ArrayList<>();

        public DayTimetableRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            this.fixedDay = intent.getIntExtra("fixed_day", -1);
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            classes.clear();

            SharedPreferences sp = SettingsRepository.getSharedPreferences(context);

            // Always dynamically resolve to today's day
            int calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            int targetDay = calDay - 1; // 0..6 (Sun..Sat)
            this.currentResolvedDay = targetDay;

            // Check weekend working override
            int queryDay = targetDay;
            if (targetDay == 0 || targetDay == 6) {
                int override = sp.getInt("working_override_" + targetDay, -1);
                if (override >= 0 && override <= 6) {
                    queryDay = override;
                }
            }

            // Check holiday
            boolean isHoliday = sp.getBoolean("holiday_" + targetDay, false);
            if (isHoliday) {
                return;
            }

            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Timetable.AllData> result = db.timetableDao().get(queryDay).blockingGet();
                if (result != null) {
                    classes.addAll(result);
                }
            } catch (Exception e) {
                android.util.Log.w("DayTimetableWidget", "Failed to query timetable for day " + queryDay, e);
            }
        }

        @Override
        public void onDestroy() {
            classes.clear();
        }

        @Override
        public int getCount() {
            return classes.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= classes.size()) {
                return null;
            }

            Timetable.AllData item = classes.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_timetable_class);

            // 1. Time
            String start = (item.startTime != null && !item.startTime.isEmpty()) ? item.startTime : "--:--";
            String end = (item.endTime != null && !item.endTime.isEmpty()) ? item.endTime : "--:--";
            views.setTextViewText(R.id.item_class_start_time, start);
            views.setTextViewText(R.id.item_class_end_time, end);

            // 2. Course Name & Code
            String title = (item.courseTitle != null && !item.courseTitle.isEmpty())
                    ? item.courseTitle
                    : ((item.courseCode != null) ? item.courseCode : "Class");
            views.setTextViewText(R.id.item_class_course_title, title);

            String code = (item.courseCode != null && !item.courseCode.isEmpty()) ? item.courseCode : "";
            views.setTextViewText(R.id.item_class_course_code, code);

            // 3. Venue (The specific venue prominently highlighted)
            String venue = (item.venue != null && !item.venue.trim().isEmpty())
                    ? "📍 " + item.venue.trim()
                    : "📍 Venue TBA";
            views.setTextViewText(R.id.item_class_venue, venue);

            // Fill-in click intent to open HomeFragment on this day
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra("selected_day", currentResolvedDay);
            views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
