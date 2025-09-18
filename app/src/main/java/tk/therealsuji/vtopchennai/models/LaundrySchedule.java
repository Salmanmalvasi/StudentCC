package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "laundry_schedules")
public class LaundrySchedule {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "block")
    public String block;

    @ColumnInfo(name = "date")
    public String date;

    @ColumnInfo(name = "room_number")
    public String roomNumber;

    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    // Constructors
    public LaundrySchedule() {
        // Empty constructor for Room
    }

    @Ignore
    public LaundrySchedule(String block, String date, String roomNumber, long lastUpdated) {
        this.block = block;
        this.date = date;
        this.roomNumber = roomNumber;
        this.lastUpdated = lastUpdated;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}
