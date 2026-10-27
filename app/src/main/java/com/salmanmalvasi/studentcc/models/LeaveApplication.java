package com.salmanmalvasi.studentcc.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model class representing a hostel leave application.
 * Maps to VTOP leave/OD history data.
 */
public class LeaveApplication {

    public enum Status {
        REQUEST_APPROVED,
        REQUEST_PENDING,
        LEAVE_CLOSED,
        REQUEST_CANCELLED,
        REJECTED,
        UNKNOWN
    }

    public enum LeaveType {
        HOME_LEAVE,
        ON_DUTY,
        MEDICAL,
        EMERGENCY,
        OTHER
    }

    private final String leaveId;
    private final String fromDate;
    private final String toDate;
    private final LeaveType leaveType;
    private final String leaveTypeRaw; // Original VTOP leave type string
    private final String reason;
    private final String visitPlace;
    private final Status status;
    private final String remarks;

    public LeaveApplication(@NonNull String leaveId,
                            @NonNull String fromDate,
                            @NonNull String toDate,
                            @NonNull LeaveType leaveType,
                            @Nullable String leaveTypeRaw,
                            @Nullable String reason,
                            @Nullable String visitPlace,
                            @NonNull Status status,
                            @Nullable String remarks) {
        this.leaveId = leaveId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.leaveType = leaveType;
        this.leaveTypeRaw = leaveTypeRaw;
        this.reason = reason;
        this.visitPlace = visitPlace;
        this.status = status;
        this.remarks = remarks;
    }

    @NonNull
    public String getLeaveId() {
        return leaveId;
    }

    @NonNull
    public String getFromDate() {
        return fromDate;
    }

    @NonNull
    public String getToDate() {
        return toDate;
    }

    @NonNull
    public LeaveType getLeaveType() {
        return leaveType;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public String getVisitPlace() {
        return visitPlace;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getRemarks() {
        return remarks;
    }

    /**
     * Parse status string from VTOP to enum
     */
    public static Status parseStatus(String statusStr) {
        if (statusStr == null) return Status.UNKNOWN;
        String normalized = statusStr.toUpperCase().trim();
        
        if (normalized.contains("REQUEST APPROVED")) return Status.REQUEST_APPROVED;
        if (normalized.contains("LEAVE CLOSED")) return Status.LEAVE_CLOSED;
        if (normalized.contains("REQUEST PENDING")) return Status.REQUEST_PENDING;
        if (normalized.contains("CANCELLED")) return Status.REQUEST_CANCELLED;
        if (normalized.contains("REJECTED")) return Status.REJECTED;
        
        return Status.UNKNOWN;
    }

    /**
     * Parse leave type string from VTOP to enum
     */
    public static LeaveType parseLeaveType(String typeStr) {
        if (typeStr == null) return LeaveType.OTHER;
        String normalized = typeStr.toUpperCase().trim();
        
        if (normalized.contains("HOME")) return LeaveType.HOME_LEAVE;
        if (normalized.contains("OD") || normalized.contains("ON DUTY")) return LeaveType.ON_DUTY;
        if (normalized.contains("MEDICAL")) return LeaveType.MEDICAL;
        if (normalized.contains("EMERGENCY")) return LeaveType.EMERGENCY;
        
        return LeaveType.OTHER;
    }

    /**
     * Get display name for leave type - uses original VTOP type if available
     */
    public String getLeaveTypeDisplay() {
        // If we have the original VTOP type, format it nicely
        if (leaveTypeRaw != null && !leaveTypeRaw.isEmpty()) {
            // Convert "HOME TOWN" -> "Home Town", "LOCAL GUARDIAN" -> "Local Guardian"
            String[] words = leaveTypeRaw.toLowerCase().split(" ");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (word.length() > 0) {
                    result.append(Character.toUpperCase(word.charAt(0)))
                          .append(word.substring(1))
                          .append(" ");
                }
            }
            return result.toString().trim();
        }
        
        // Fallback to enum display
        switch (leaveType) {
            case HOME_LEAVE: return "Home Leave";
            case ON_DUTY: return "On Duty (OD)";
            case MEDICAL: return "Medical";
            case EMERGENCY: return "Emergency";
            default: return "Other";
        }
    }

    /**
     * Get display name for status
     */
    public String getStatusDisplay() {
        switch (status) {
            case REQUEST_APPROVED: return "Approved";
            case LEAVE_CLOSED: return "Closed";
            case REQUEST_PENDING: return "Pending";
            case REQUEST_CANCELLED: return "Cancelled";
            case REJECTED: return "Rejected";
            default: return "Unknown";
        }
    }
}
