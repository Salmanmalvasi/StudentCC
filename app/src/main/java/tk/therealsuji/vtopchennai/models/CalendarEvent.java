package tk.therealsuji.vtopchennai.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "calendar_events")
public class CalendarEvent {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public int id;

    public String month;        // e.g., "JULY 2024"
    public int date;            // Day of month (1-31)
    public String text;         // Event description
    public String type;         // "Instructional Day", "Holiday", "Other"
    public String color;        // Color code from VTOP
    public String category;     // Event category

    public CalendarEvent() {}

    public CalendarEvent(String month, int date, String text, String type, String color, String category) {
        this.month = month;
        this.date = date;
        this.text = text;
        this.type = type;
        this.color = color;
        this.category = category;
    }
}
