package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;

@Dao
public interface MarksDao {
    default Completable insertMarks(Map<Integer, Mark> marks) {

        return this.getMarksRead()
                .concatMapCompletable(readMarks -> {
                    for (Mark markItem : readMarks) {
                        if (marks.containsKey(markItem.signature)) {
                            Objects.requireNonNull(marks.get(markItem.signature)).isRead = true;
                        }
                    }

                    return this.deleteMarks().andThen(this.insertMarks(new ArrayList<>(marks.values())));
                });
    }

    @Insert
    Completable insertMarks(List<Mark> marks);

    @Insert
    Completable insertCumulativeMarks(List<CumulativeMark> cumulativeMarks);

    @Query("UPDATE marks SET is_read = 1 WHERE course_id = :courseId AND is_read IS 0")
    Completable setMarksRead(int courseId);

    @Query("DELETE FROM marks")
    Completable deleteMarks();

    @Query("DELETE FROM cumulative_marks")
    Completable deleteCumulativeMarks();

    @Query("SELECT courses.id AS courseId, courses.code AS courseCode, courses.title AS courseTitle, COUNT(CASE WHEN is_read IS 0 THEN 1 END) AS unreadMarkCount " +
        "FROM marks INNER JOIN courses ON course_id = courses.id " +
        "GROUP BY courses.id")
    Single<List<Course.AllData>> getCourses();

    @Query("SELECT id, is_read, signature FROM marks WHERE is_read IS 1")
    Single<List<Mark>> getMarksRead();

    @Query("SELECT * FROM marks")
    Single<List<Mark>> getRawMarks();

    @Query("SELECT COUNT(id) FROM marks WHERE is_read IS 0")
    Single<Integer> getMarksUnreadCount();

    @Query("SELECT courses.id AS courseId, courses.code AS courseCode, courses.title AS courseTitle, type AS courseType, marks.title AS title, score, max_score AS maxScore, weightage, max_weightage AS maxWeightage, average, status, is_read AS isRead " +
        "FROM marks INNER JOIN courses ON course_id = courses.id " +
        "WHERE course_id = :courseId " +
        "ORDER BY type DESC, title")
    Single<List<Mark.AllData>> getMarks(int courseId);

    @Query("SELECT courses.id AS courseId, courses.code AS courseCode, courses.title AS courseTitle, type AS courseType, marks.title AS title, score, max_score AS maxScore, weightage, max_weightage AS maxWeightage, average, status, is_read AS isRead " +
        "FROM marks INNER JOIN courses ON course_id = courses.id " +
        "ORDER BY courses.title, type DESC, marks.title")
    Single<List<Mark.AllData>> getAllMarks();

    @Query("SELECT * FROM cumulative_marks WHERE course_code = :courseCode")
    Single<CumulativeMark> getCumulativeMark(String courseCode);

    @Query("SELECT * FROM cumulative_marks")
    Single<List<CumulativeMark>> getAllCumulativeMarks();
}
