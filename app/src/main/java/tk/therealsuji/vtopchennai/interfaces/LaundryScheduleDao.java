package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.LaundrySchedule;

@Dao
public interface LaundryScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<LaundrySchedule> schedules);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(LaundrySchedule schedule);

    @Update
    Completable update(LaundrySchedule schedule);

    @Delete
    Completable delete(LaundrySchedule schedule);

    @Query("SELECT * FROM laundry_schedules WHERE block = :block ORDER BY CAST(date as INTEGER)")
    Single<List<LaundrySchedule>> getSchedulesByBlock(String block);

    @Query("SELECT * FROM laundry_schedules WHERE block = :block AND date = :date")
    Single<List<LaundrySchedule>> getSchedulesByBlockAndDate(String block, String date);

    @Query("SELECT * FROM laundry_schedules WHERE block = :block AND date = :date AND (room_number LIKE '%' || :roomNumber || '%' OR :roomNumber LIKE '%' || room_number || '%')")
    Single<LaundrySchedule> getTodaysSchedule(String block, String date, String roomNumber);

    @Query("SELECT * FROM laundry_schedules WHERE block = :block AND CAST(date as INTEGER) > :currentDate AND (room_number LIKE '%' || :roomNumber || '%' OR :roomNumber LIKE '%' || room_number || '%') ORDER BY CAST(date as INTEGER) LIMIT 1")
    Single<LaundrySchedule> getNextSchedule(String block, int currentDate, String roomNumber);

    @Query("DELETE FROM laundry_schedules WHERE block = :block")
    Completable deleteByBlock(String block);

    @Query("DELETE FROM laundry_schedules")
    Completable deleteAll();

    @Query("SELECT COUNT(*) FROM laundry_schedules WHERE block = :block")
    Single<Integer> getCountByBlock(String block);
}
