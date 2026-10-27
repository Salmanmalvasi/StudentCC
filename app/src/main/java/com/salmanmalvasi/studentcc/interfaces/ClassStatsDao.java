package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.ClassStats;

@Dao
public interface ClassStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertClassStats(ClassStats classStats);

    @Update
    Completable updateClassStats(ClassStats classStats);

    @Delete
    Completable deleteClassStats(ClassStats classStats);

    @Query("SELECT * FROM class_stats WHERE course_code = :courseCode AND exam_title = :examTitle LIMIT 1")
    Single<ClassStats> getClassStats(String courseCode, String examTitle);

    @Query("SELECT * FROM class_stats WHERE course_code = :courseCode")
    Single<List<ClassStats>> getAllClassStatsForCourse(String courseCode);

    @Query("SELECT EXISTS(SELECT 1 FROM class_stats WHERE course_code = :courseCode AND exam_title = :examTitle)")
    Single<Boolean> hasClassStats(String courseCode, String examTitle);

    @Query("DELETE FROM class_stats WHERE course_code = :courseCode AND exam_title = :examTitle")
    Completable deleteClassStatsForExam(String courseCode, String examTitle);

    @Query("DELETE FROM class_stats")
    Completable deleteAllClassStats();
}
