package com.salmanmalvasi.studentcc.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.salmanmalvasi.studentcc.models.CalendarEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Robust helper to sync or export Academic Calendar events to Google Calendar.
 * Supports direct background sync to Google Calendar on device via CalendarContract,
 * as well as standard RFC 5545 .ics calendar export.
 */
public class GoogleCalendarHelper {

    private static final String TAG = "GoogleCalendarHelper";
    public static final int REQUEST_CALENDAR_PERMISSIONS = 1002;

    public static class SyncResult {
        public boolean success;
        public int insertedCount;
        public int alreadyExistsCount;
        public int failedCount;
        public int totalConsidered;
        public String calendarAccount = "Google Calendar";
        public String errorMessage;
        public String lastError;
    }

    /**
     * Parses month string in any format (e.g., "JULY 2024", "OCTOBER 2026", "2026-10", "OCT-2026")
     * and day of month into a Calendar instance.
     */
    public static Calendar parseEventDate(CalendarEvent event) {
        if (event == null || event.month == null || event.date <= 0) {
            return null;
        }

        String clean = event.month.trim();

        // 1. Try standard date format patterns
        String[] patterns = {
                "MMMM yyyy",
                "MMM yyyy",
                "MMM-yyyy",
                "MMMM-yyyy",
                "yyyy-MM",
                "MM-yyyy",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
                sdf.setLenient(true);
                Date parsed = sdf.parse(clean);
                if (parsed != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(parsed);
                    cal.set(Calendar.DAY_OF_MONTH, event.date);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal;
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Fallback: split by space, dash, or underscore
        try {
            String[] parts = clean.split("[\\s\\-_]+");
            if (parts.length >= 2) {
                int year = -1;
                int monthIdx = -1;
                String[] months = {
                        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
                };
                for (String part : parts) {
                    if (part.matches("\\d{4}")) {
                        year = Integer.parseInt(part);
                    } else if (part.matches("\\d{1,2}")) {
                        int num = Integer.parseInt(part);
                        if (num >= 1 && num <= 12) {
                            monthIdx = num - 1;
                        }
                    } else {
                        String upper = part.toUpperCase(Locale.ENGLISH);
                        for (int i = 0; i < months.length; i++) {
                            if (months[i].startsWith(upper) || (upper.length() >= 3 && months[i].contains(upper))) {
                                monthIdx = i;
                                break;
                            }
                        }
                    }
                }
                if (year > 2000 && monthIdx >= 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, monthIdx);
                    cal.set(Calendar.DAY_OF_MONTH, event.date);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Checks if the app has WRITE_CALENDAR and READ_CALENDAR permissions.
     */
    public static boolean hasCalendarPermissions(Context context) {
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests calendar permissions on an Activity.
     */
    public static void requestCalendarPermissions(Activity activity) {
        if (activity == null) return;
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, REQUEST_CALENDAR_PERMISSIONS);
    }

    /**
     * Directly inserts academic calendar events into the device's Google Calendar.
     * Selects a WRITABLE calendar (access level >= 500) prioritizing the personal Google account.
     * Prevents duplicates by checking existing events with the same title & date range.
     */
    public static SyncResult syncEventsToDeviceCalendar(Context context, List<CalendarEvent> events, boolean onlyKeyEvents) {
        SyncResult result = new SyncResult();
        if (events == null || events.isEmpty()) {
            result.errorMessage = "No academic events found to sync.";
            return result;
        }

        if (!hasCalendarPermissions(context)) {
            result.errorMessage = "Calendar permission not granted.";
            return result;
        }

        ContentResolver cr = context.getContentResolver();
        long calId = -1;
        String calAccount = "Google Calendar";
        int bestScore = -1;

        // 1. Find the best WRITABLE Google Calendar account or primary calendar on the device
        try (Cursor cur = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.IS_PRIMARY,
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.VISIBLE
                },
                null,
                null,
                null
        )) {
            if (cur != null) {
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    String accName = cur.getString(1);
                    String accType = cur.getString(2);
                    String displayName = cur.getString(3);
                    int isPrimary = 0;
                    try { isPrimary = cur.getInt(4); } catch (Exception ignored) {}
                    int accessLevel = 0;
                    try { accessLevel = cur.getInt(5); } catch (Exception ignored) {}
                    int visible = 1;
                    try { visible = cur.getInt(6); } catch (Exception ignored) {}

                    Log.d(TAG, "Candidate Calendar: id=" + id +
                            ", accName=" + accName +
                            ", accType=" + accType +
                            ", displayName=" + displayName +
                            ", accessLevel=" + accessLevel +
                            ", isPrimary=" + isPrimary);

                    // CRITICAL: Skip read-only calendars (e.g. Holidays in India = 200, Birthdays = 200).
                    // Must have at least CAL_ACCESS_CONTRIBUTOR (500) to allow inserting events.
                    if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        continue;
                    }

                    int score = accessLevel;

                    // Strongly prefer Google accounts
                    if ("com.google".equalsIgnoreCase(accType)) {
                        score += 1000;
                        // Prioritize personal calendar where displayName matches email
                        if (accName != null && displayName != null &&
                                (accName.equalsIgnoreCase(displayName) ||
                                        displayName.toLowerCase().contains("events") ||
                                        displayName.toLowerCase().contains("personal"))) {
                            score += 500;
                        }
                    }

                    if (isPrimary == 1) {
                        score += 300;
                    }

                    if (visible == 1) {
                        score += 100;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        calId = id;
                        calAccount = (displayName != null && !displayName.isEmpty()) ? displayName : (accName != null ? accName : "Google Calendar");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not access device calendars", e);
            result.errorMessage = "Could not access device calendars: " + e.getMessage();
            return result;
        }

        if (calId == -1) {
            Log.w(TAG, "No writable calendar account found on device.");
            result.errorMessage = "No writable calendar account found on device.";
            return result;
        }

        Log.i(TAG, "Selected writable calendar: id=" + calId + " (" + calAccount + ")");
        result.calendarAccount = calAccount;
        int inserted = 0;
        int alreadyExistsCount = 0;
        int failedCount = 0;
        int considered = 0;
        String lastError = null;

        for (CalendarEvent event : events) {
            Calendar eventCal = parseEventDate(event);
            if (eventCal == null) continue;

            considered++;

            // For ALL_DAY events in Android CalendarProvider, DTSTART and DTEND must be in UTC midnight
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utcCal.set(eventCal.get(Calendar.YEAR), eventCal.get(Calendar.MONTH), eventCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            utcCal.set(Calendar.MILLISECOND, 0);
            long startUtcMillis = utcCal.getTimeInMillis();

            Calendar endUtcCal = (Calendar) utcCal.clone();
            endUtcCal.add(Calendar.DAY_OF_MONTH, 1);
            long endUtcMillis = endUtcCal.getTimeInMillis();

            String title = (event.text != null && !event.text.trim().isEmpty()) ? event.text.trim() : "VIT Academic Event";
            String desc = "StudentCC VIT Academic Calendar\nType: " + (event.type != null ? event.type : "Event") +
                    (event.category != null ? " (" + event.category + ")" : "");

            // Duplicate prevention check: check if an event with same title exists within the day margin
            long startMargin = startUtcMillis - 43200000L; // -12h
            long endMargin = startUtcMillis + 129600000L;  // +36h
            boolean exists = false;

            try (Cursor dupCheck = cr.query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID},
                    CalendarContract.Events.CALENDAR_ID + " = ? AND " +
                            CalendarContract.Events.TITLE + " = ? AND " +
                            CalendarContract.Events.DTSTART + " >= ? AND " +
                            CalendarContract.Events.DTSTART + " <= ?",
                    new String[]{String.valueOf(calId), title, String.valueOf(startMargin), String.valueOf(endMargin)},
                    null
            )) {
                if (dupCheck != null && dupCheck.moveToFirst()) {
                    exists = true;
                }
            } catch (Exception ignored) {
            }

            if (exists) {
                alreadyExistsCount++;
                continue;
            }

            // Insert into Google Calendar
            try {
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.CALENDAR_ID, calId);
                values.put(CalendarContract.Events.TITLE, title);
                values.put(CalendarContract.Events.DESCRIPTION, desc);
                values.put(CalendarContract.Events.DTSTART, startUtcMillis);
                values.put(CalendarContract.Events.DTEND, endUtcMillis);
                values.put(CalendarContract.Events.ALL_DAY, 1);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);
                values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

                Uri insertedUri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
                if (insertedUri != null) {
                    inserted++;
                } else {
                    failedCount++;
                    lastError = "CalendarProvider returned null URI";
                }
            } catch (Exception e) {
                failedCount++;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "Failed to insert event: " + title, e);
            }
        }

        result.totalConsidered = considered;
        result.insertedCount = inserted;
        result.alreadyExistsCount = alreadyExistsCount;
        result.failedCount = failedCount;
        result.lastError = lastError;

        if (inserted > 0 || (alreadyExistsCount > 0 && alreadyExistsCount == considered)) {
            result.success = true;
        } else if (failedCount > 0 && inserted == 0 && alreadyExistsCount == 0) {
            result.success = false;
            result.errorMessage = lastError != null ? lastError : "Failed to insert events into calendar.";
        } else {
            result.success = true;
        }

        Log.i(TAG, "Sync complete. inserted=" + inserted + ", alreadyExists=" + alreadyExistsCount + ", failed=" + failedCount);
        return result;
    }

    /**
     * Generates a standard RFC 5545 .ics file for Google Calendar or third-party calendar apps.
     */
    public static File createIcsFile(Context context, List<CalendarEvent> events, boolean onlyKeyEvents) {
        try {
            File calendarDir = new File(context.getCacheDir(), "calendar");
            if (!calendarDir.exists()) calendarDir.mkdirs();

            File icsFile = new File(calendarDir, "vit_academic_calendar.ics");
            SimpleDateFormat dateStampFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
            dateStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

            String nowUtc = dateStampFormat.format(Calendar.getInstance().getTime());

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(icsFile), StandardCharsets.UTF_8))) {
                writer.println("BEGIN:VCALENDAR");
                writer.println("VERSION:2.0");
                writer.println("PRODID:-//StudentCC//VIT Academic Calendar//EN");
                writer.println("CALSCALE:GREGORIAN");
                writer.println("METHOD:PUBLISH");
                writer.println("X-WR-CALNAME:VIT Academic Calendar");
                writer.println("X-WR-TIMEZONE:Asia/Kolkata");

                int index = 0;
                for (CalendarEvent event : events) {
                    Calendar eventCal = parseEventDate(event);
                    if (eventCal == null) continue;

                    Calendar endCal = (Calendar) eventCal.clone();
                    endCal.add(Calendar.DAY_OF_YEAR, 1);

                    String startDateStr = dayFormat.format(eventCal.getTime());
                    String endDateStr = dayFormat.format(endCal.getTime());
                    String summary = escapeIcs(event.text != null && !event.text.trim().isEmpty() ? event.text.trim() : "VIT Academic Event");
                    String desc = escapeIcs("VIT Academic Calendar • " +
                            (event.type != null ? event.type : "Event") +
                            (event.category != null ? " (" + event.category + ")" : ""));

                    writer.println("BEGIN:VEVENT");
                    writer.println("UID:vit_event_" + (event.id > 0 ? event.id : index++) + "@studentcc");
                    writer.println("DTSTAMP:" + nowUtc);
                    writer.println("DTSTART;VALUE=DATE:" + startDateStr);
                    writer.println("DTEND;VALUE=DATE:" + endDateStr);
                    writer.println("SUMMARY:" + summary);
                    writer.println("DESCRIPTION:" + desc);
                    writer.println("STATUS:CONFIRMED");
                    writer.println("TRANSP:TRANSPARENT");
                    writer.println("END:VEVENT");
                }

                writer.println("END:VCALENDAR");
            }

            return icsFile;
        } catch (Exception e) {
            Log.e(TAG, "Error generating ICS file", e);
            return null;
        }
    }

    /**
     * Opens the .ics file directly in the Google Calendar app (or shows chooser).
     */
    public static void openIcsInGoogleCalendar(Context context, File icsFile) {
        if (icsFile == null || !icsFile.exists()) {
            Toast.makeText(context, "Could not create calendar file", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    icsFile
            );

            // Grant read permission to Google Calendar package
            try {
                context.grantUriPermission("com.google.android.calendar", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }

            PackageManager pm = context.getPackageManager();
            Intent googleIntent = new Intent(Intent.ACTION_VIEW);
            googleIntent.setDataAndType(contentUri, "text/calendar");
            googleIntent.setPackage("com.google.android.calendar");
            googleIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            if (googleIntent.resolveActivity(pm) != null) {
                context.startActivity(googleIntent);
                return;
            }

            Intent genericIntent = new Intent(Intent.ACTION_VIEW);
            genericIntent.setDataAndType(contentUri, "text/calendar");
            genericIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(genericIntent, "Open with Google Calendar");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Error opening ICS", e);
            Toast.makeText(context, "Error opening calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Opens Google Calendar app directly so user can inspect synced dates.
     */
    public static void openGoogleCalendarApp(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("com.google.android.calendar");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }

            // Open calendar time view in default calendar app
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(String.valueOf(System.currentTimeMillis()));
            Intent calIntent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            calIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(calIntent);
        } catch (Exception ignored) {
            Toast.makeText(context, "Please open Google Calendar to view synced events", Toast.LENGTH_SHORT).show();
        }
    }

    private static String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
