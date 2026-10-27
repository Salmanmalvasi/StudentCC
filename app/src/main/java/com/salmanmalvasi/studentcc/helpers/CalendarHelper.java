package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.models.CalendarEvent;

/**
 * Helper class to check if a date is a holiday or non-instructional day
 * Used by attendance calculator to handle holidays properly
 */
public class CalendarHelper {
    
    private static final String[] HOLIDAY_KEYWORDS = {
        "holiday", "pooja", "puja", "ayudha", "diwali", "pongal", "eid", "christmas", "good friday",
        "independence", "republic", "onam", "holi", "ramadan", "ganesh", "maha shivaratri", "vesak",
        "vacation", "term end", "no instructional", "noinstructional", "vinayakar chathurthi", 
        "gandhi jayanthi", "closed"
    };

    /**
     * Check if a specific date is a holiday or non-instructional day
     * @param context Android context
     * @param date Date to check (day of month)
     * @param month Month name (e.g., "JULY 2024")
     * @return Single that emits true if the date is a holiday/non-instructional day
     */
    public static Single<Boolean> isHoliday(Context context, int date, String month) {
        return AppDatabase.getInstance(context)
            .calendarEventDao()
            .getByMonth(month)
            .map(events -> {
                // Get day of week for this date
                Calendar cal = parseMonthAndDate(month, date);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                
                // Sunday is always a holiday
                if (dayOfWeek == Calendar.SUNDAY) {
                    return true;
                }
                
                // Saturday is holiday unless there's a "some day order" or working day indicator
                if (dayOfWeek == Calendar.SATURDAY) {
                    boolean hasWorkingDayOrder = false;
                    for (CalendarEvent event : events) {
                        if (event.date == date) {
                            String text = normalize(event.text);
                            String category = normalize(event.category);
                            // Check for "some day order" or similar working day indicators
                            if (text.contains("some day order") || 
                                text.contains("working day") ||
                                text.contains("compensatory") ||
                                category.contains("some day order") ||
                                category.contains("working day")) {
                                hasWorkingDayOrder = true;
                                break;
                            }
                        }
                    }
                    // Saturday is holiday unless explicitly marked as working day
                    return !hasWorkingDayOrder;
                }
                
                // Check events for this date
                for (CalendarEvent event : events) {
                    if (event.date == date) {
                        // Non-instructional days are holidays
                        if (isHolidayEvent(event)) {
                            return true;
                        }
                    }
                }
                
                return false;
            })
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Parse month name and date to Calendar object
     */
    private static Calendar parseMonthAndDate(String month, int date) {
        Calendar cal = Calendar.getInstance();
        try {
            // Parse month like "JULY 2024" or "JANUARY 2026"
            String[] parts = month.trim().split("\\s+");
            if (parts.length >= 2) {
                String monthName = parts[0].toUpperCase();
                int year = Integer.parseInt(parts[1]);
                
                String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                                  "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
                for (int i = 0; i < months.length; i++) {
                    if (months[i].equals(monthName)) {
                        cal.set(Calendar.YEAR, year);
                        cal.set(Calendar.MONTH, i);
                        cal.set(Calendar.DAY_OF_MONTH, date);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // If parsing fails, use current date as fallback
            cal = Calendar.getInstance();
        }
        return cal;
    }

    /**
     * Get all holiday dates for a specific month
     * @param context Android context
     * @param month Month name (e.g., "JULY 2024")
     * @return Single that emits list of holiday dates
     */
    public static Single<List<Integer>> getHolidayDates(Context context, String month) {
        return AppDatabase.getInstance(context)
            .calendarEventDao()
            .getByMonth(month)
            .map(events -> {
                List<Integer> holidayDates = new ArrayList<>();
                for (CalendarEvent event : events) {
                    if (isHolidayEvent(event)) {
                        holidayDates.add(event.date);
                    }
                }
                return holidayDates;
            })
            .subscribeOn(Schedulers.io());
    }

    /**
     * Get current month name in calendar format
     * @return Month name like "JANUARY 2026"
     */
    public static String getCurrentMonthName() {
        Calendar cal = Calendar.getInstance();
        String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                          "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
        return months[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR);
    }

    /**
     * Check if an event is a holiday or non-instructional day
     * Non-instructional days are always holidays
     */
    private static boolean isHolidayEvent(CalendarEvent event) {
        if (event == null) return false;
        
        String type = event.type != null ? event.type.toLowerCase() : "";
        String text = normalize(event.text);
        String category = normalize(event.category);
        
        // Non-instructional days are holidays
        if (type.contains("no instructional") || text.contains("no instructional") || category.contains("no instructional")) {
            return true;
        }
        
        // Check type
        if (type.contains("holiday")) return true;
        
        // Check category
        if (category.contains("holiday")) return true;
        
        // Check text for keywords
        for (String keyword : HOLIDAY_KEYWORDS) {
            if (text.contains(keyword) || category.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Normalize string for comparison
     */
    private static String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    /**
     * Check if today is a holiday
     * @param context Android context
     * @return Single that emits true if today is a holiday
     */
    public static Single<Boolean> isTodayHoliday(Context context) {
        Calendar today = Calendar.getInstance();
        int todayDate = today.get(Calendar.DAY_OF_MONTH);
        String currentMonth = getCurrentMonthName();
        return isHoliday(context, todayDate, currentMonth);
    }
}
