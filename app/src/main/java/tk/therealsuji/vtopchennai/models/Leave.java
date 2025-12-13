package tk.therealsuji.vtopchennai.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity for Leave/OD applications from VTOP.
 * Stores leave request history locally for offline access.
 */
@Entity(tableName = "leave")
public class Leave {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "leave_id")
    public String leaveId;

    @ColumnInfo(name = "from_date")
    public String fromDate;

    @ColumnInfo(name = "to_date")
    public String toDate;

    @ColumnInfo(name = "leave_type")
    public String leaveType;

    @ColumnInfo(name = "reason")
    public String reason;

    @ColumnInfo(name = "visit_place")
    public String visitPlace;

    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "remarks")
    public String remarks;

    @ColumnInfo(name = "applied_date")
    public String appliedDate;

    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    // Status constants
    public static final String STATUS_APPROVED = "REQUEST_APPROVED";
    public static final String STATUS_PENDING = "REQUEST_PENDING";
    public static final String STATUS_CLOSED = "LEAVE_CLOSED";
    public static final String STATUS_CANCELLED = "REQUEST_CANCELLED";
    public static final String STATUS_REJECTED = "REJECTED";

    // Leave type constants
    public static final String TYPE_HOME_LEAVE = "HOME_LEAVE";
    public static final String TYPE_ON_DUTY = "ON_DUTY";
    public static final String TYPE_MEDICAL = "MEDICAL";
    public static final String TYPE_EMERGENCY = "EMERGENCY";
    public static final String TYPE_OTHER = "OTHER";

    public Leave() {
        this.leaveId = "";
        this.lastUpdated = System.currentTimeMillis();
    }

    public Leave(@NonNull String leaveId, String fromDate, String toDate, 
                 String leaveType, String reason, String visitPlace, 
                 String status, String remarks) {
        this.leaveId = leaveId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.leaveType = leaveType;
        this.reason = reason;
        this.visitPlace = visitPlace;
        this.status = status;
        this.remarks = remarks;
        this.lastUpdated = System.currentTimeMillis();
    }

    @NonNull
    public String getLeaveId() {
        return leaveId;
    }

    public String getFromDate() {
        return fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public String getLeaveTypeDisplay() {
        if (leaveType == null) return "Leave";
        switch (leaveType) {
            case TYPE_HOME_LEAVE:
                return "Home Leave";
            case TYPE_ON_DUTY:
                return "On Duty (OD)";
            case TYPE_MEDICAL:
                return "Medical Leave";
            case TYPE_EMERGENCY:
                return "Emergency Leave";
            case TYPE_OTHER:
            default:
                return "Leave";
        }
    }

    public String getReason() {
        return reason;
    }

    public String getVisitPlace() {
        return visitPlace;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusDisplay() {
        if (status == null) return "Unknown";
        switch (status) {
            case STATUS_APPROVED:
                return "Approved";
            case STATUS_PENDING:
                return "Pending";
            case STATUS_CLOSED:
                return "Closed";
            case STATUS_CANCELLED:
                return "Cancelled";
            case STATUS_REJECTED:
                return "Rejected";
            default:
                return status;
        }
    }

    public String getRemarks() {
        return remarks;
    }

    public String getAppliedDate() {
        return appliedDate;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public boolean isActive() {
        return STATUS_APPROVED.equals(status) || STATUS_PENDING.equals(status);
    }

    public boolean isPast() {
        return STATUS_CLOSED.equals(status) || 
               STATUS_CANCELLED.equals(status) || 
               STATUS_REJECTED.equals(status);
    }
}
