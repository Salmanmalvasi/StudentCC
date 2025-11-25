package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents skip feasibility for a full day
 * Simple model: Can you skip THIS ENTIRE DAY and stay above 75%/85%?
 */
@Entity(tableName = "skip_recommendations")
public class SkipRecommendation {
    
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    // Date information
    @ColumnInfo(name = "date")
    public long date; // Date timestamp (beginning of day)
    
    @ColumnInfo(name = "day_of_week")
    public String dayOfWeek; // Monday, Tuesday, etc.
    
    // Skip decision
    @ColumnInfo(name = "can_skip")
    public boolean canSkip; // True = safe to skip entire day
    
    @ColumnInfo(name = "is_risky")
    public boolean isRisky; // True = borderline (one subject 73-75%)
    
    @ColumnInfo(name = "has_classes")
    public boolean hasClasses; // False = no classes scheduled
    
    @ColumnInfo(name = "is_long_weekend")
    public boolean isLongWeekend; // Friday before holiday
    
    // Attendance projections
    @ColumnInfo(name = "current_overall")
    public float currentOverall;
    
    @ColumnInfo(name = "overall_after_skip")
    public float overallAfterSkip;
    
    @ColumnInfo(name = "num_classes")
    public int numClasses; // Number of classes this day
    
    @ColumnInfo(name = "has_labs")
    public boolean hasLabs; // True if any lab on this day
    
    // Risk reasons (why can't skip)
    @ColumnInfo(name = "risk_reason")
    public String riskReason; // "PHY101 will drop to 73%"
    
    @ColumnInfo(name = "calculated_at")
    public long calculatedAt;
    
    // Non-database fields for display
    @Ignore
    public List<ClassInfo> classes = new ArrayList<>();
    
    @Ignore
    public List<SubjectImpact> subjectImpacts = new ArrayList<>();
    
    // Helper classes for display data
    public static class ClassInfo {
        public String courseCode;
        public String courseTitle;
        public String startTime;
        public String endTime;
        public String courseType; // Lab, Theory
    }
    
    public static class SubjectImpact {
        public String courseCode;
        public String courseTitle;
        public float currentAttendance;
        public float afterSkipAttendance;
        public boolean isSafe; // >= 75%
    }
    
    // Helper methods
    public boolean isSafeToSkip() {
        return canSkip && !isRisky && overallAfterSkip >= 85.0f;
    }
    
    public String getStatusLabel() {
        if (!hasClasses) return "No Classes";
        if (canSkip && !isRisky) return "Safe to Skip";
        if (isRisky) return "Risky - One Subject Close";
        return "Don't Skip";
    }
    
    public String getDisplayDate() {
        // Format: "Monday, Nov 15"
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, MMM dd", java.util.Locale.ENGLISH);
        return sdf.format(new java.util.Date(date));
    }
}
