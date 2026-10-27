package com.salmanmalvasi.studentcc.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Entity(tableName = "hostel_leave_requests")
public class HostelLeaveRequest {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "leave_id")
    public String leaveId;

    @ColumnInfo(name = "visit_place")
    public String visitPlace;

    @ColumnInfo(name = "reason")
    public String reason;

    @ColumnInfo(name = "leave_type")
    public String leaveType;

    @ColumnInfo(name = "from_date")
    public String fromDate;

    @ColumnInfo(name = "to_date")
    public String toDate;

    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "remarks")
    public String remarks;

    // Additional fields expected by VTOPHostelHelper
    @ColumnInfo(name = "vtop_request_id")
    public String vtopRequestId;

    @ColumnInfo(name = "contact_number")
    public String contactNumber;

    @ColumnInfo(name = "is_emergency")
    public boolean isEmergency;

    @ColumnInfo(name = "request_date")
    public String requestDate;

    @ColumnInfo(name = "approval_comments")
    public String approvalComments;

    @ColumnInfo(name = "is_synced")
    public boolean isSynced;

    @ColumnInfo(name = "last_updated")
    public String lastUpdated;

    // Default constructor for Room
    public HostelLeaveRequest() {
        this.leaveId = "";
        this.isSynced = false;
        this.isEmergency = false;
    }

    // Constructor with parameters - ignored by Room
    @Ignore
    public HostelLeaveRequest(@NonNull String leaveId, String visitPlace, String reason,
                             String leaveType, String fromDate, String toDate,
                             String status, String remarks) {
        this.leaveId = leaveId;
        this.visitPlace = visitPlace;
        this.reason = reason;
        this.leaveType = leaveType;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.status = status;
        this.remarks = remarks;
        this.isSynced = false;
        this.isEmergency = false;
    }

    // Getters and setters for basic fields
    @NonNull
    public String getLeaveId() { return leaveId; }
    public void setLeaveId(@NonNull String leaveId) { this.leaveId = leaveId; }

    public String getVisitPlace() { return visitPlace; }
    public void setVisitPlace(String visitPlace) { this.visitPlace = visitPlace; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    // Getters and setters for additional fields (expected by VTOPHostelHelper)
    public String getVtopRequestId() { return vtopRequestId; }
    public void setVtopRequestId(String vtopRequestId) { this.vtopRequestId = vtopRequestId; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public boolean isEmergency() { return isEmergency; }
    public void setEmergency(boolean isEmergency) { this.isEmergency = isEmergency; }

    public String getRequestDate() { return requestDate; }
    public void setRequestDate(String requestDate) { this.requestDate = requestDate; }

    public String getApprovalComments() { return approvalComments; }
    public void setApprovalComments(String approvalComments) { this.approvalComments = approvalComments; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean isSynced) { this.isSynced = isSynced; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    // Alias methods expected by VTOPHostelHelper
    public String getLeaveFromDate() { return fromDate; }
    public void setLeaveFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getLeaveToDate() { return toDate; }
    public void setLeaveToDate(String toDate) { this.toDate = toDate; }

    // Validation method for date range
    public boolean isValidDateRange() {
        if (fromDate == null || toDate == null || fromDate.isEmpty() || toDate.isEmpty()) {
            return false;
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
            Date startDate = dateFormat.parse(fromDate);
            Date endDate = dateFormat.parse(toDate);

            if (startDate != null && endDate != null) {
                return !endDate.before(startDate);
            }
        } catch (ParseException e) {
            // If parsing fails, try without time
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
                Date startDate = simpleDateFormat.parse(fromDate);
                Date endDate = simpleDateFormat.parse(toDate);

                if (startDate != null && endDate != null) {
                    return !endDate.before(startDate);
                }
            } catch (ParseException ex) {
                return false;
            }
        }

        return false;
    }
}
