package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mess_menus")
public class MessMenu {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "mess_type")
    public String messType; // M-N, M-S, M-V, W-N, W-S, W-V

    @ColumnInfo(name = "day")
    public String day;

    @ColumnInfo(name = "breakfast")
    public String breakfast;

    @ColumnInfo(name = "lunch")
    public String lunch;

    @ColumnInfo(name = "snacks")
    public String snacks;

    @ColumnInfo(name = "dinner")
    public String dinner;

    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    // Constructors
    public MessMenu() {}

    @androidx.room.Ignore
    public MessMenu(String messType, String day, String breakfast, String lunch, String snacks, String dinner) {
        this.messType = messType;
        this.day = day;
        this.breakfast = breakfast;
        this.lunch = lunch;
        this.snacks = snacks;
        this.dinner = dinner;
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMessType() { return messType; }
    public void setMessType(String messType) { this.messType = messType; }

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

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}
