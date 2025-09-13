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

public class HostelDataHelper {
    private static final String TAG = "HostelDataHelper";
    private static final String BASE_URL = "https://kanishka-developer.github.io/unmessify/json/en/";
    private static HostelDataHelper instance;
    private Context context;
    private Map<String, JSONObject> laundryData = new HashMap<>();
    private Map<String, JSONObject> messData = new HashMap<>();
    private ExecutorService executorService;
    
    private HostelDataHelper(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newFixedThreadPool(3);
        loadHostelData();
    }
    
    public static synchronized HostelDataHelper getInstance(Context context) {
        if (instance == null) {
            instance = new HostelDataHelper(context);
        }
        return instance;
    }
    
    private void loadHostelData() {
        // Load laundry data for all blocks asynchronously
        String[] laundryBlocks = {"A", "B", "CB", "CG", "D1", "D2", "E"};
        for (String block : laundryBlocks) {
            executorService.execute(() -> loadLaundryData(block));
        }
        
        // Load mess data for different meal types asynchronously
        String[] messTypes = {"M-N", "M-S", "M-V", "W-N", "W-S", "W-V"};
        for (String type : messTypes) {
            executorService.execute(() -> loadMessData(type));
        }
    }
    
    private void loadLaundryData(String block) {
        try {
            String fileName = "VITC-" + block + "-L.json";
            String json = fetchJSONFromURL(BASE_URL + fileName);
            if (json != null) {
                JSONObject data = new JSONObject(json);
                laundryData.put(block, data);
                Log.d(TAG, "Loaded laundry data for block: " + block);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading laundry data for block " + block, e);
        }
    }
    
    private void loadMessData(String type) {
        try {
            String fileName = "VITC-" + type + ".json";
            String json = fetchJSONFromURL(BASE_URL + fileName);
            if (json != null) {
                JSONObject data = new JSONObject(json);
                messData.put(type, data);
                Log.d(TAG, "Loaded mess data for type: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading mess data for type " + type, e);
        }
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
