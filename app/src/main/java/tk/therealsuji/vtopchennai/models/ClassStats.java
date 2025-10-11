package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity to store class statistics (average and standard deviation) for exams
 */
@Entity(tableName = "class_stats")
public class ClassStats {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "course_code")
    public String courseCode;

    @ColumnInfo(name = "exam_title")
    public String examTitle;

    @ColumnInfo(name = "class_average")
    public Double classAverage;

    @ColumnInfo(name = "max_score")
    public Double maxScore;

    @ColumnInfo(name = "standard_deviation")
    public Double standardDeviation;

    @ColumnInfo(name = "created_at")
    public Long createdAt;

    @ColumnInfo(name = "updated_at")
    public Long updatedAt;

    public ClassStats() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    @Ignore
    public ClassStats(String courseCode, String examTitle, Double classAverage, Double maxScore, Double standardDeviation) {
        this();
        this.courseCode = courseCode;
        this.examTitle = examTitle;
        this.classAverage = classAverage;
        this.maxScore = maxScore;
        this.standardDeviation = standardDeviation;
    }

    /**
     * Calculate how many marks ahead/behind the class average
     * @param myScore My score in the exam
     * @return Positive if ahead, negative if behind
     */
    public Double calculateMarksAheadBehind(Double myScore) {
        if (myScore == null || classAverage == null) {
            return null;
        }
        return myScore - classAverage;
    }

    /**
     * Calculate normalized score relative to maximum possible ahead score
     * If maxScore is 50 and classAverage is 30, max possible ahead is 20
     * If I scored 40, I'm 10 ahead out of max possible 20, so 50% ahead
     * @param myScore My score in the exam
     * @return Value between -100 and 100 representing percentage of max possible advantage
     */
    public Double calculateNormalizedScore(Double myScore) {
        if (myScore == null || classAverage == null || maxScore == null) {
            return null;
        }

        double marksAhead = calculateMarksAheadBehind(myScore);
        double maxPossibleAhead = maxScore - classAverage;
        double maxPossibleBehind = classAverage; // Can't go below 0

        if (marksAhead >= 0) {
            // Ahead of average
            if (maxPossibleAhead <= 0) return 100.0; // Already at or above max
            return (marksAhead / maxPossibleAhead) * 100.0;
        } else {
            // Behind average
            if (maxPossibleBehind <= 0) return -100.0; // Average is 0
            return (marksAhead / maxPossibleBehind) * 100.0;
        }
    }
}
