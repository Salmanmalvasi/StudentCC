package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.interfaces.LaundryScheduleDao;
import tk.therealsuji.vtopchennai.interfaces.MessMenuDao;
import tk.therealsuji.vtopchennai.models.LaundrySchedule;
import tk.therealsuji.vtopchennai.models.MessMenu;

public class HostelDataHelper {
    private static final String TAG = "HostelDataHelper";
    private static final String BASE_URL = "https://kanishka-developer.github.io/unmessify/json/en/";
    private static HostelDataHelper instance;
    private Context context;
    private Map<String, JSONObject> laundryData = new HashMap<>();
    private Map<String, JSONObject> messData = new HashMap<>();
    private ExecutorService executorService;
    private AppDatabase appDatabase;
    private LaundryScheduleDao laundryScheduleDao;
    private MessMenuDao messMenuDao;

    private HostelDataHelper(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newFixedThreadPool(3);
        this.appDatabase = AppDatabase.getInstance(context);
        this.laundryScheduleDao = appDatabase.laundryScheduleDao();
        this.messMenuDao = appDatabase.messMenuDao();
        loadHostelDataOfflineFirst();
    }

    public static synchronized HostelDataHelper getInstance(Context context) {
        if (instance == null) {
            instance = new HostelDataHelper(context);
        }
        return instance;
    }

    private void loadHostelDataOfflineFirst() {
        // Load data from database first, then update from API in background
        loadDataFromDatabase();

        // Update data from API in background
        String[] laundryBlocks = {"A", "B", "CB", "CG", "D1", "D2", "E"};
        for (String block : laundryBlocks) {
            executorService.execute(() -> updateLaundryDataFromAPI(block));
        }

        String[] messTypes = {"M-N", "M-S", "M-V", "W-N", "W-S", "W-V"};
        for (String type : messTypes) {
            executorService.execute(() -> updateMessDataFromAPI(type));
        }
    }

    private void loadDataFromDatabase() {
        // Load laundry data from database to memory for immediate access
        String[] laundryBlocks = {"A", "B", "CB", "CG", "D1", "D2", "E"};
        for (String block : laundryBlocks) {
            laundryScheduleDao.getSchedulesByBlock(block)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(schedules -> {
                        // Convert database results back to JSONObject for compatibility
                        convertLaundrySchedulesToJSON(block, convertDbLaundryToHelper(schedules));
                    }, throwable -> {
                        Log.e(TAG, "Error loading laundry data from database for block " + block, throwable);
                    });
        }

        // Load mess data from database to memory for immediate access
        String[] messTypes = {"M-N", "M-S", "M-V", "W-N", "W-S", "W-V"};
        for (String type : messTypes) {
            messMenuDao.getMenusByType(type)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(menus -> {
                        // Convert database results back to JSONObject for compatibility
                        convertMessMenusToJSON(type, convertDbMessToHelper(menus));
                    }, throwable -> {
                        Log.e(TAG, "Error loading mess data from database for type " + type, throwable);
                    });
        }
    }

    private void convertLaundrySchedulesToJSON(String block, List<LaundrySchedule> schedules) {
        try {
            JSONObject data = new JSONObject();
            JSONArray list = new JSONArray();

            for (LaundrySchedule schedule : schedules) {
                JSONObject item = new JSONObject();
                item.put("Date", schedule.getDate());
                item.put("RoomNumber", schedule.getRoomNumber());
                list.put(item);
            }

            data.put("list", list);
            laundryData.put(block, data);
            Log.d(TAG, "Converted laundry data from database for block: " + block + " (" + schedules.size() + " items)");
        } catch (Exception e) {
            Log.e(TAG, "Error converting laundry data for block " + block, e);
        }
    }

    private void convertMessMenusToJSON(String type, List<MessMenu> menus) {
        try {
            JSONObject data = new JSONObject();
            JSONArray list = new JSONArray();

            for (MessMenu menu : menus) {
                JSONObject item = new JSONObject();
                item.put("Day", menu.getDay());
                item.put("Breakfast", menu.getBreakfast());
                item.put("Lunch", menu.getLunch());
                item.put("Snacks", menu.getSnacks());
                item.put("Dinner", menu.getDinner());
                list.put(item);
            }

            data.put("list", list);
            messData.put(type, data);
            Log.d(TAG, "Converted mess data from database for type: " + type + " (" + menus.size() + " items)");
        } catch (Exception e) {
            Log.e(TAG, "Error converting mess data for type " + type, e);
        }
    }

    public void syncHostelData() {
        // Public method to manually sync hostel data (called during app sync)
        String[] laundryBlocks = {"A", "B", "CB", "CG", "D1", "D2", "E"};
        for (String block : laundryBlocks) {
            executorService.execute(() -> updateLaundryDataFromAPI(block));
        }

        String[] messTypes = {"M-N", "M-S", "M-V", "W-N", "W-S", "W-V"};
        for (String type : messTypes) {
            executorService.execute(() -> updateMessDataFromAPI(type));
        }
    }

    private void updateLaundryDataFromAPI(String block) {
        try {
            String fileName = "VITC-" + block + "-L.json";
            String json = fetchJSONFromURL(BASE_URL + fileName);
            if (json != null) {
                JSONObject data = new JSONObject(json);
                laundryData.put(block, data);

                // Save to database
                saveLaundryDataToDatabase(block, data);
                Log.d(TAG, "Updated laundry data for block: " + block);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating laundry data for block " + block, e);
        }
    }

    private void updateMessDataFromAPI(String type) {
        try {
            String fileName = "VITC-" + type + ".json";
            String json = fetchJSONFromURL(BASE_URL + fileName);
            if (json != null) {
                JSONObject data = new JSONObject(json);
                messData.put(type, data);

                // Save to database
                saveMessDataToDatabase(type, data);
                Log.d(TAG, "Updated mess data for type: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating mess data for type " + type, e);
        }
    }

    private void saveLaundryDataToDatabase(String block, JSONObject data) {
        try {
            if (data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                List<tk.therealsuji.vtopchennai.models.LaundrySchedule> schedules = new ArrayList<>();

                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    tk.therealsuji.vtopchennai.models.LaundrySchedule schedule = new tk.therealsuji.vtopchennai.models.LaundrySchedule();
                    schedule.setBlock(block);
                    schedule.setDate(item.optString("Date", ""));
                    schedule.setRoomNumber(item.optString("RoomNumber", ""));
                    schedule.setLastUpdated(System.currentTimeMillis());
                    schedules.add(schedule);
                }

                // Clear old data and insert new data
                laundryScheduleDao.deleteByBlock(block)
                        .andThen(laundryScheduleDao.insertAll(schedules))
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Log.d(TAG, "Saved laundry data to database for block: " + block + " (" + schedules.size() + " items)"),
                                throwable -> Log.e(TAG, "Error saving laundry data to database for block " + block, throwable)
                        );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing laundry data for database save for block " + block, e);
        }
    }

    private void saveMessDataToDatabase(String type, JSONObject data) {
        try {
            if (data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                List<tk.therealsuji.vtopchennai.models.MessMenu> menus = new ArrayList<>();

                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    tk.therealsuji.vtopchennai.models.MessMenu menu = new tk.therealsuji.vtopchennai.models.MessMenu();
                    menu.setMessType(type);
                    menu.setDay(item.optString("Day", ""));
                    menu.setBreakfast(item.optString("Breakfast", ""));
                    menu.setLunch(item.optString("Lunch", ""));
                    menu.setSnacks(item.optString("Snacks", ""));
                    menu.setDinner(item.optString("Dinner", ""));
                    menu.setLastUpdated(System.currentTimeMillis());
                    menus.add(menu);
                }

                // Clear old data and insert new data
                messMenuDao.deleteByType(type)
                        .andThen(messMenuDao.insertAll(menus))
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Log.d(TAG, "Saved mess data to database for type: " + type + " (" + menus.size() + " items)"),
                                throwable -> Log.e(TAG, "Error saving mess data to database for type " + type, throwable)
                        );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing mess data for database save for type " + type, e);
        }
    }

    // New database-first methods for offline access
    public Single<LaundrySchedule> getTodaysLaundryScheduleFromDB(String block, String roomNumber) {
        Calendar calendar = Calendar.getInstance();
        String currentDate = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));

        return laundryScheduleDao.getTodaysSchedule(block, currentDate, roomNumber)
                .subscribeOn(Schedulers.io())
                .map(this::convertDbLaundryToHelper)
                .doOnSuccess(schedule -> Log.d(TAG, "Found today's laundry schedule from database"))
                .doOnError(throwable -> Log.e(TAG, "Error getting today's laundry schedule from database", throwable));
    }

    public Single<LaundrySchedule> getNextLaundryScheduleFromDB(String block, String roomNumber) {
        Calendar calendar = Calendar.getInstance();
        int currentDate = calendar.get(Calendar.DAY_OF_MONTH);

        return laundryScheduleDao.getNextSchedule(block, currentDate, roomNumber)
                .subscribeOn(Schedulers.io())
                .map(this::convertDbLaundryToHelper)
                .doOnSuccess(schedule -> Log.d(TAG, "Found next laundry schedule from database"))
                .doOnError(throwable -> Log.e(TAG, "Error getting next laundry schedule from database", throwable));
    }

    public Single<MessMenu> getTodaysMessMenuFromDB(String block, String gender, String mealType) {
        // Determine mess type based on gender and meal type
        final String messType = (gender.equals("male") ? "M-" : "W-") + mealType; // N, S, or V

        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String today = days[dayOfWeek - 1];

        return messMenuDao.getMenuByTypeAndDay(messType, today)
                .subscribeOn(Schedulers.io())
                .map(this::convertDbMessToHelper)
                .doOnSuccess(menu -> Log.d(TAG, "Found today's mess menu from database for " + messType))
                .doOnError(throwable -> Log.e(TAG, "Error getting today's mess menu from database for " + messType, throwable));
    }

    private String fetchJSONFromURL(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                Log.e(TAG, "HTTP error: " + responseCode + " for URL: " + urlString);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching JSON from URL: " + urlString, e);
        }
        return null;
    }

    public List<LaundrySchedule> getLaundrySchedule(String block) {
        List<LaundrySchedule> schedules = new ArrayList<>();
        try {
            JSONObject data = laundryData.get(block);
            if (data != null && data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    LaundrySchedule schedule = new LaundrySchedule();
                    schedule.setDate(item.optString("Date", ""));
                    schedule.setRoomNumber(item.optString("RoomNumber", ""));
                    schedules.add(schedule);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing laundry schedule for block " + block, e);
        }
        return schedules;
    }

    public List<MessMenu> getMessMenu(String block, String gender, String mealType) {
        List<MessMenu> menus = new ArrayList<>();
        try {
            // Determine mess type based on gender and meal type
            String messType = gender.equals("male") ? "M-" : "W-";
            messType += mealType; // N, S, or V

            JSONObject data = messData.get(messType);
            if (data != null && data.has("list")) {
                JSONArray list = data.getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    MessMenu menu = new MessMenu();
                    menu.setDay(item.optString("Day", ""));
                    menu.setBreakfast(item.optString("Breakfast", ""));
                    menu.setLunch(item.optString("Lunch", ""));
                    menu.setSnacks(item.optString("Snacks", ""));
                    menu.setDinner(item.optString("Dinner", ""));
                    menus.add(menu);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing mess menu for block " + block, e);
        }
        return menus;
    }

    public MessMenu getTodaysMessMenu(String block, String gender, String mealType) {
        try {
            // Determine mess type based on gender and meal type
            String messType = gender.equals("male") ? "M-" : "W-";
            messType += mealType; // N, S, or V
            Log.d(TAG, "Looking for mess type: " + messType + " for block: " + block);
            Log.d(TAG, "Available mess data keys: " + messData.keySet());

            JSONObject data = messData.get(messType);
            Log.d(TAG, "Data for " + messType + ": " + (data != null ? "found" : "not found"));
            if (data != null && data.has("list")) {
                JSONArray list = data.getJSONArray("list");

                // Get current day of week
                Calendar calendar = Calendar.getInstance();
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
                String today = days[dayOfWeek - 1];

                // Find today's menu
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String day = item.optString("Day", "");
                    if (today.equalsIgnoreCase(day)) {
                        MessMenu menu = new MessMenu();
                        menu.setDay(day);
                        menu.setBreakfast(item.optString("Breakfast", ""));
                        menu.setLunch(item.optString("Lunch", ""));
                        menu.setSnacks(item.optString("Snacks", ""));
                        menu.setDinner(item.optString("Dinner", ""));
                        return menu;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing today's mess menu for block " + block, e);
        }
        return null;
    }

    public LaundrySchedule getTodaysLaundrySchedule(String block, String roomNumber) {
        try {
            JSONObject data = laundryData.get(block);
            if (data != null && data.has("list")) {
                JSONArray list = data.getJSONArray("list");

                // Get current day of month
                Calendar calendar = Calendar.getInstance();
                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

                // Find today's laundry schedule
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String dateStr = item.optString("Date", "");
                    String roomRange = item.optString("RoomNumber", "");

                    if (String.valueOf(dayOfMonth).equals(dateStr) && isRoomInRange(roomNumber, roomRange)) {
                        LaundrySchedule schedule = new LaundrySchedule();
                        schedule.setDate(dateStr);
                        schedule.setRoomNumber(roomRange);
                        return schedule;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing today's laundry schedule for block " + block, e);
        }
        return null;
    }

    public LaundrySchedule getNextLaundrySchedule(String block, String roomNumber) {
        try {
            JSONObject data = laundryData.get(block);
            if (data != null && data.has("list")) {
                JSONArray list = data.getJSONArray("list");

                // Get current day of month
                Calendar calendar = Calendar.getInstance();
                int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

                // Find next laundry schedule for this room
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String dateStr = item.optString("Date", "");
                    String roomRange = item.optString("RoomNumber", "");

                    if (!dateStr.equals("null") && !dateStr.isEmpty()) {
                        try {
                            int scheduleDay = Integer.parseInt(dateStr);
                            if (scheduleDay > currentDay && isRoomInRange(roomNumber, roomRange)) {
                                LaundrySchedule schedule = new LaundrySchedule();
                                schedule.setDate(dateStr);
                                schedule.setRoomNumber(roomRange);
                                return schedule;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid date strings
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing next laundry schedule for block " + block, e);
        }
        return null;
    }

    public int getDaysUntilNextLaundry(String block, String roomNumber) {
        LaundrySchedule nextSchedule = getNextLaundrySchedule(block, roomNumber);
        if (nextSchedule != null) {
            try {
                Calendar calendar = Calendar.getInstance();
                int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
                int nextDay = Integer.parseInt(nextSchedule.getDate());
                return nextDay - currentDay;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error calculating days until next laundry", e);
            }
        }
        return -1; // No laundry found
    }

    private boolean isRoomInRange(String roomNumber, String roomRange) {
        if (roomRange == null || roomRange.equals("null") || roomRange.isEmpty()) {
            return false;
        }

        try {
            int roomNum = Integer.parseInt(roomNumber);

            // Handle different range formats
            if (roomRange.contains(" - ")) {
                String[] parts = roomRange.split(" - ");
                if (parts.length == 2) {
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    return roomNum >= start && roomNum <= end;
                }
            } else if (roomRange.contains(" & others")) {
                // Handle cases like "1136 - 1339 & others"
                String rangePart = roomRange.split(" & others")[0];
                if (rangePart.contains(" - ")) {
                    String[] parts = rangePart.split(" - ");
                    if (parts.length == 2) {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        return roomNum >= start && roomNum <= end;
                    }
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing room number or range: " + roomNumber + " / " + roomRange, e);
        }

        return false;
    }

    public String getMessCaterer(String block, String gender, String mealType) {
        // This would be determined based on the block and meal type
        // For now, return a placeholder
        return "Mess Caterer for " + block + " " + gender + " " + mealType;
    }

    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // Conversion methods between database models and helper classes
    private List<LaundrySchedule> convertDbLaundryToHelper(List<tk.therealsuji.vtopchennai.models.LaundrySchedule> dbSchedules) {
        List<LaundrySchedule> helperSchedules = new ArrayList<>();
        for (tk.therealsuji.vtopchennai.models.LaundrySchedule dbSchedule : dbSchedules) {
            LaundrySchedule helperSchedule = new LaundrySchedule();
            helperSchedule.setDate(dbSchedule.getDate());
            helperSchedule.setRoomNumber(dbSchedule.getRoomNumber());
            helperSchedules.add(helperSchedule);
        }
        return helperSchedules;
    }

    private LaundrySchedule convertDbLaundryToHelper(tk.therealsuji.vtopchennai.models.LaundrySchedule dbSchedule) {
        LaundrySchedule helperSchedule = new LaundrySchedule();
        helperSchedule.setDate(dbSchedule.getDate());
        helperSchedule.setRoomNumber(dbSchedule.getRoomNumber());
        return helperSchedule;
    }

    private List<MessMenu> convertDbMessToHelper(List<tk.therealsuji.vtopchennai.models.MessMenu> dbMenus) {
        List<MessMenu> helperMenus = new ArrayList<>();
        for (tk.therealsuji.vtopchennai.models.MessMenu dbMenu : dbMenus) {
            MessMenu helperMenu = new MessMenu();
            helperMenu.setDay(dbMenu.getDay());
            helperMenu.setBreakfast(dbMenu.getBreakfast());
            helperMenu.setLunch(dbMenu.getLunch());
            helperMenu.setSnacks(dbMenu.getSnacks());
            helperMenu.setDinner(dbMenu.getDinner());
            helperMenus.add(helperMenu);
        }
        return helperMenus;
    }

    private MessMenu convertDbMessToHelper(tk.therealsuji.vtopchennai.models.MessMenu dbMenu) {
        MessMenu helperMenu = new MessMenu();
        helperMenu.setDay(dbMenu.getDay());
        helperMenu.setBreakfast(dbMenu.getBreakfast());
        helperMenu.setLunch(dbMenu.getLunch());
        helperMenu.setSnacks(dbMenu.getSnacks());
        helperMenu.setDinner(dbMenu.getDinner());
        return helperMenu;
    }

    // Data model classes
    public static class LaundrySchedule {
        private String date;
        private String roomNumber;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getRoomNumber() { return roomNumber; }
        public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    }

    public static class MessMenu {
        private String day;
        private String breakfast;
        private String lunch;
        private String snacks;
        private String dinner;

        public String getDay() { return day; }
        public void setDay(String day) { this.day = day; }
        public String getBreakfast() { return breakfast; }
        public void setBreakfast(String breakfast) { this.breakfast = breakfast; }
        public String getLunch() { return lunch; }
        public void setLunch(String lunch) { this.lunch = lunch; }
        public String getSnacks() { return snacks; }
        public void setSnacks(String snacks) { this.snacks = snacks; }
        public String getDinner() { return dinner; }
        public void setDinner(String dinner) { this.dinner = dinner; }
    }
}
