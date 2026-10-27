package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Slot;

@Dao
public interface CoursesDao {
    @Insert
    Completable insertCourses(List<Course> courses);

    @Insert
    Completable insertSlots(List<Slot> slots);

    @Query("DELETE FROM courses")
    Completable deleteAll();

    @Query("SELECT DISTINCT code FROM courses, slots WHERE slots.course_id = courses.id ORDER BY slot")
    Single<List<String>> getCourseCodes();

    @Query("SELECT courses.id AS courseId, title AS courseTitle, code AS courseCode, type AS courseType, faculty, slot, venue, attendance.attended AS attendanceAttended, attendance.total AS attendanceTotal, attendance.percentage AS attendancePercentage " +
            "FROM slots JOIN courses ON courses.id = slots.course_id " +
            "LEFT JOIN attendance ON attendance.course_id = courses.id " +
            "WHERE slots.id = :slotId")
    Single<Course.AllData> getCourse(int slotId);

    @Query("SELECT courses.id AS courseId, title AS courseTitle, type AS courseType, faculty, slot, venue, attended AS attendanceAttended, total AS attendanceTotal, percentage AS attendancePercentage " +
            "FROM courses LEFT JOIN slots ON slots.course_id = courses.id LEFT JOIN attendance ON attendance.course_id = courses.id WHERE code = :courseCode")
    Single<List<Course.AllData>> getCourse(String courseCode);

    @Query("SELECT id FROM courses WHERE code = :courseCode LIMIT 1")
    Single<Integer> getCourseIdByCode(String courseCode);

    @Query("SELECT * FROM courses")
    Single<List<Course>> getAllCourses();

    @Query("SELECT * FROM slots")
    Single<List<Slot>> getAllSlots();
}
