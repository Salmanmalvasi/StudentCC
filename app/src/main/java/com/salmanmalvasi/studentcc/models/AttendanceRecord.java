package com.salmanmalvasi.studentcc.models;

import static androidx.room.ForeignKey.CASCADE;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "attendance_records",
        foreignKeys = @ForeignKey(
                entity = Course.class,
                parentColumns = "id",
                childColumns = "course_id",
                onDelete = CASCADE
        ),
        indices = {@Index("course_id")}
)
public class AttendanceRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "course_id")
    public Integer courseId;

    // Date of the class (epoch timestamp)
    @ColumnInfo(name = "date")
    public Long date;

    // Session info (e.g., "Morning", "Afternoon", slot info)
    @ColumnInfo(name = "session")
    public String session;

    // Status: "Present", "Absent", "OD" (On Duty)
    @ColumnInfo(name = "status")
    public String status;

    // Optional remarks from the system
    @ColumnInfo(name = "remarks")
    public String remarks;

    // Store the original date string for display purposes
    @ColumnInfo(name = "date_string")
    public String dateString;
}
