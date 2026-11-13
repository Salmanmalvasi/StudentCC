package com.salmanmalvasi.studentcc.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;

@Dao
public interface MarksDao {
    default Completable insertMarks(Map<Integer, Mark> marks) {
        return this.getRawMarks()
                .concatMapCompletable(existing -> {
                    List<Mark> incoming = new ArrayList<>(marks.values());
                    com.salmanmalvasi.studentcc.helpers.MarksReadState.preserve(incoming, existing);
                    return this.deleteMarks().andThen(this.insertMarks(incoming));
                });
    }

    @Insert
    Completable insertMarks(List<Mark> marks);

    @Insert
    Completable insertCumulativeMarks(List<CumulativeMark> cumulativeMarks);

    /** The owning sync transaction persists assessment rows and their identity-scoped totals together. */
    default Completable replaceCumulativeMarks(List<CumulativeMark> incoming, List<Course> currentCourses,
                                                List<Course> previousCourses) {
        if (incoming.isEmpty()) return Completable.complete();
        return getAllCumulativeMarks().concatMapCompletable(cached -> {
            List<CumulativeMark> snapshot = com.salmanmalvasi.studentcc.helpers.CumulativeMarksCalculator
                    .mergeTotals(incoming, cached, currentCourses, previousCourses);
            return deleteCumulativeMarks().andThen(insertCumulativeMarks(snapshot));
        });
    }

    default Completable retainCumulativeMarksForCourses(List<Course> currentCourses, List<Course> previousCourses) {
        return getAllCumulativeMarks().concatMapCompletable(cached -> {
            List<CumulativeMark> retained = com.salmanmalvasi.studentcc.helpers.CumulativeMarksCalculator
                    .retainTotalsForCourses(cached, currentCourses, previousCourses);
            return deleteCumulativeMarks().andThen(insertCumulativeMarks(retained));
        });
    }

    @Query("UPDATE cumulative_marks SET grade = :grade WHERE course_code = :courseCode")
    Completable updateReleasedGrade(String courseCode, String grade);

    @Query("UPDATE marks SET is_read = 1 WHERE course_id = :courseId AND is_read IS 0")
    Completable setMarksRead(int courseId);

    /** Match exactly the versions displayed when the course opened, including null unreleased scores. */
    default Completable setMarksReadSnapshot(int courseId, List<Mark.AllData> displayedMarks) {
        List<Completable> updates = new ArrayList<>();
        for (Mark.AllData mark : displayedMarks) {
            if (mark.courseId != null && mark.courseId == courseId && !Boolean.TRUE.equals(mark.isRead)) {
                updates.add(setAssessmentRead(courseId, mark.title, mark.score, mark.maxScore,
                        mark.weightage, mark.maxWeightage, mark.status));
            }
        }
        return Completable.concat(updates);
    }

    @Query("UPDATE marks SET is_read = 1 WHERE course_id = :courseId AND title IS :title " +
            "AND score IS :score AND max_score IS :maxScore AND weightage IS :weightage " +
            "AND max_weightage IS :maxWeightage AND status IS :status")
    Completable setAssessmentRead(int courseId, String title, Double score, Double maxScore,
                                  Double weightage, Double maxWeightage, String status);

    @Query("DELETE FROM marks")
    Completable deleteMarks();

    @Query("DELETE FROM cumulative_marks")
    Completable deleteCumulativeMarks();

    @Query("SELECT courses.id AS courseId, courses.code AS courseCode, courses.title AS courseTitle, " +
        "courses.type AS courseType, courses.credits AS credits, courses.vtop_class_id AS vtopClassId, " +
        "courses.semester_id AS semesterId, courses.course_system AS courseSystem, " +
        "COUNT(CASE WHEN is_read IS 0 THEN 1 END) AS unreadMarkCount " +
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
