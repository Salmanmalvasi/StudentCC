package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.CalendarEvent;

@Dao
public interface CalendarEventDao {
    
    @Insert
    void insertAll(List<CalendarEvent> events);

    @Query("DELETE FROM calendar_events")
    void clearAll();

    @Query("SELECT * FROM calendar_events ORDER BY date")
    Single<List<CalendarEvent>> getAll();

    @Query("SELECT * FROM calendar_events WHERE month = :month ORDER BY date")
    Single<List<CalendarEvent>> getByMonth(String month);

    @Query("SELECT DISTINCT month FROM calendar_events ORDER BY month")
    Single<List<String>> getDistinctMonths();
}
