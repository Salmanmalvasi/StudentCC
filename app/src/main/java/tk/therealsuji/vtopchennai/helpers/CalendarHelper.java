package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

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
                for (CalendarEvent event : events) {
                    if (event.date == date) {
                        // Check if it's a holiday or non-instructional day
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
     */
    private static boolean isHolidayEvent(CalendarEvent event) {
        if (event == null) return false;
        
        String type = event.type != null ? event.type.toLowerCase() : "";
        String text = normalize(event.text);
        String category = normalize(event.category);
        
        // Check type
        if (type.contains("holiday")) return true;
        if (type.contains("no instructional")) return true;
        
        // Check category
        if (category.contains("no instructional")) return true;
        
        // Check text for keywords
        for (String keyword : HOLIDAY_KEYWORDS) {
            if (text.contains(keyword) || category.contains(keyword)) {
                return true;
            }
        }
        
        // NOT an instructional day is also a holiday
        if (type.equals("other") && !type.contains("instructional")) {
            // If it's not explicitly marked as instructional, and has certain keywords
            if (text.contains("no ") || text.contains("closed") || category.contains("no ")) {
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
