package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.AttendanceRecord;

@Dao
public interface AttendanceRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<AttendanceRecord> records);

    @Query("DELETE FROM attendance_records")
    Completable clearAll();

    @Query("DELETE FROM attendance_records WHERE course_id = :courseId")
    Completable clearByCourse(int courseId);

    @Query("SELECT * FROM attendance_records WHERE course_id = :courseId ORDER BY date DESC")
    Single<List<AttendanceRecord>> getForCourse(int courseId);

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    Single<List<AttendanceRecord>> getAll();

    @Query("SELECT COUNT(*) FROM attendance_records WHERE course_id = :courseId")
    Single<Integer> getCountForCourse(int courseId);
}
