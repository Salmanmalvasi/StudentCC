package com.salmanmalvasi.studentcc.helpers;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.widgets.AttendanceWidget;
import com.salmanmalvasi.studentcc.widgets.TodayTimetableWidget;

public class WidgetHelper {

    public static boolean isWidgetPinningSupported(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = context.getSystemService(AppWidgetManager.class);
            return appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported();
        }
        return false;
    }

    public static boolean hasAnyWidgetInstalled(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager == null) return false;
            int[] timetableIds = manager.getAppWidgetIds(new ComponentName(context, TodayTimetableWidget.class));
            int[] attendanceIds = manager.getAppWidgetIds(new ComponentName(context, AttendanceWidget.class));
            return (timetableIds != null && timetableIds.length > 0) || (attendanceIds != null && attendanceIds.length > 0);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void showWidgetPickerDialog(Context context) {
        String[] options = new String[] {
                "📅 Today's Timetable Widget\n(Current class, venue & time on home screen)",
                "📊 Attendance Tracker Widget\n(Live overall percentage & class counts)"
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle("Add StudentCC Widget")
                .setIcon(R.drawable.ic_widgets)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pinWidget(context, TodayTimetableWidget.class);
                    } else {
                        pinWidget(context, AttendanceWidget.class);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void pinWidget(Context context, Class<?> widgetClass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = context.getSystemService(AppWidgetManager.class);
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported()) {
                ComponentName myProvider = new ComponentName(context, widgetClass);
                appWidgetManager.requestPinAppWidget(myProvider, null, null);
                Toast.makeText(context, "Follow the prompt to add widget to home screen", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context,
                        "Your launcher does not support direct widget pinning. Please touch & hold your home screen and choose StudentCC.",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(context,
                    "Touch and hold an empty space on your home screen, tap Widgets, and add StudentCC.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
