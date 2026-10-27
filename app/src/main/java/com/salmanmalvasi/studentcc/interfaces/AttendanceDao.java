package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.Attendance;

@Dao
public interface AttendanceDao {
    @Insert
    Completable insert(List<Attendance> attendance);

    @Query("DELETE FROM attendance")
    Completable delete();

    @Query("UPDATE attendance SET total = :newTotal, percentage = :newPercentage WHERE course_id = :courseId")
    Completable updateTotals(int courseId, int newTotal, int newPercentage);

    @Query("SELECT * FROM attendance WHERE course_id = :courseId LIMIT 1")
    Single<com.salmanmalvasi.studentcc.models.Attendance> getAttendanceByCourseId(int courseId);
    
    @Query("SELECT * FROM attendance")
    Single<List<Attendance>> getAll();
}
