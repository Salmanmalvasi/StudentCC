package tk.therealsuji.vtopchennai.adapters;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.LeaveApplication;

/**
 * RecyclerView adapter for displaying leave history items.
 */
public class LeaveAdapter extends RecyclerView.Adapter<LeaveAdapter.LeaveViewHolder> {

    private final List<LeaveApplication> leaveList = new ArrayList<>();

    public void setItems(@NonNull List<LeaveApplication> items) {
        leaveList.clear();
        leaveList.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LeaveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leave_history, parent, false);
        return new LeaveViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaveViewHolder holder, int position) {
        LeaveApplication leave = leaveList.get(position);
        holder.bind(leave);
    }

    @Override
    public int getItemCount() {
        return leaveList.size();
    }

    /**
     * ViewHolder for leave history items
     */
    static class LeaveViewHolder extends RecyclerView.ViewHolder {
        private final View statusIndicator;
        private final TextView textLeaveId;
        private final TextView textLeaveType;
        private final TextView textDuration;
        private final TextView textReason;
        private final Chip chipStatus;

        LeaveViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            textLeaveId = itemView.findViewById(R.id.text_leave_id);
            textLeaveType = itemView.findViewById(R.id.text_leave_type);
            textDuration = itemView.findViewById(R.id.text_duration);
            textReason = itemView.findViewById(R.id.text_reason);
            chipStatus = itemView.findViewById(R.id.chip_status);
        }

        void bind(@NonNull LeaveApplication leave) {
            // Set leave ID
            textLeaveId.setText("#" + leave.getLeaveId());

            // Set leave type
            textLeaveType.setText(leave.getLeaveTypeDisplay());

            // Set duration
            String duration = leave.getFromDate() + " - " + leave.getToDate();
            textDuration.setText(duration);

            // Set reason
            String reason = leave.getReason();
            if (reason != null && !reason.isEmpty()) {
                textReason.setVisibility(View.VISIBLE);
                textReason.setText(reason);
            } else {
                textReason.setVisibility(View.GONE);
            }

            // Set status chip
            chipStatus.setText(leave.getStatusDisplay());
            int statusColor = getStatusColor(leave.getStatus());
            chipStatus.setChipBackgroundColorResource(statusColor);

            // Set status indicator color
            GradientDrawable indicator = (GradientDrawable) statusIndicator.getBackground();
            if (indicator != null) {
                indicator.setColor(ContextCompat.getColor(itemView.getContext(), statusColor));
            }
        }

        private int getStatusColor(LeaveApplication.Status status) {
            switch (status) {
                case REQUEST_APPROVED:
                    return R.color.green_500;
                case LEAVE_CLOSED:
                    return R.color.green_700;
                case REQUEST_PENDING:
                    return R.color.yellow_500;
                case REQUEST_CANCELLED:
                    return R.color.gray_400;
                case REJECTED:
                    return R.color.red_500;
                default:
                    return R.color.gray_500;
            }
        }
    }
}
