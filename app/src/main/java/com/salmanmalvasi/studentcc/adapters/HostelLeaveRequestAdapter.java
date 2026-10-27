package com.salmanmalvasi.studentcc.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.HostelLeaveRequest;

public class HostelLeaveRequestAdapter extends RecyclerView.Adapter<HostelLeaveRequestAdapter.LeaveRequestViewHolder> {
    private List<HostelLeaveRequest> leaveRequests;
    private SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public HostelLeaveRequestAdapter(List<HostelLeaveRequest> leaveRequests) {
        this.leaveRequests = leaveRequests;
    }

    @NonNull
    @Override
    public LeaveRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hostel_leave_request, parent, false);
        return new LeaveRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaveRequestViewHolder holder, int position) {
        HostelLeaveRequest request = leaveRequests.get(position);

        // Set leave type
        holder.leaveType.setText(request.getLeaveType());

        // Set visit place
        if (request.getVisitPlace() != null && !request.getVisitPlace().isEmpty()) {
            holder.visitPlace.setText("Visit: " + request.getVisitPlace());
            holder.visitPlace.setVisibility(View.VISIBLE);
        } else {
            holder.visitPlace.setVisibility(View.GONE);
        }

        // Set reason
        if (request.getReason() != null && !request.getReason().isEmpty()) {
            holder.reason.setText("Reason: " + request.getReason());
            holder.reason.setVisibility(View.VISIBLE);
        } else {
            holder.reason.setVisibility(View.GONE);
        }

        // Set dates
        String fromDate = formatDate(request.getFromDate());
        String toDate = formatDate(request.getToDate());
        holder.dates.setText("From: " + fromDate + "\nTo: " + toDate);

        // Set status with appropriate color
        holder.status.setText(request.getStatus());
        setStatusColor(holder.status, request.getStatus());

        // Set remarks if available
        if (request.getRemarks() != null && !request.getRemarks().isEmpty()) {
            holder.remarks.setText("Remarks: " + request.getRemarks());
            holder.remarks.setVisibility(View.VISIBLE);
        } else {
            holder.remarks.setVisibility(View.GONE);
        }

        // Set leave ID
        if (request.getLeaveId() != null && !request.getLeaveId().isEmpty()) {
            holder.leaveId.setText("ID: " + request.getLeaveId());
            holder.leaveId.setVisibility(View.VISIBLE);
        } else {
            holder.leaveId.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return leaveRequests.size();
    }

    private String formatDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return "N/A";
        }

        try {
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return dateString; // Return original if parsing fails
        }
    }

    private void setStatusColor(TextView statusView, String status) {
        int color;
        if (status == null) {
            color = Color.GRAY;
        } else {
            switch (status.toUpperCase()) {
                case "REQUEST APPROVED":
                    color = ContextCompat.getColor(statusView.getContext(), android.R.color.holo_green_dark);
                    break;
                case "LEAVE CLOSED":
                    color = ContextCompat.getColor(statusView.getContext(), android.R.color.holo_blue_dark);
                    break;
                case "REQUEST CANCELLED BEFORE APPROVAL":
                    color = ContextCompat.getColor(statusView.getContext(), android.R.color.holo_red_dark);
                    break;
                case "PENDING":
                    color = ContextCompat.getColor(statusView.getContext(), android.R.color.holo_orange_dark);
                    break;
                default:
                    color = Color.GRAY;
                    break;
            }
        }
        statusView.setTextColor(color);
    }

    public void updateLeaveRequests(List<HostelLeaveRequest> newRequests) {
        this.leaveRequests = newRequests;
        notifyDataSetChanged();
    }

    static class LeaveRequestViewHolder extends RecyclerView.ViewHolder {
        TextView leaveType, visitPlace, reason, dates, status, remarks, leaveId;

        public LeaveRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            leaveType = itemView.findViewById(R.id.tv_leave_type);
            visitPlace = itemView.findViewById(R.id.tv_visit_place);
            reason = itemView.findViewById(R.id.tv_reason);
            dates = itemView.findViewById(R.id.tv_dates);
            status = itemView.findViewById(R.id.tv_status);
            remarks = itemView.findViewById(R.id.tv_remarks);
            leaveId = itemView.findViewById(R.id.tv_leave_id);
        }
    }
}
