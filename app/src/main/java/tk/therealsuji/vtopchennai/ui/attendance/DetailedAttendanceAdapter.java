package tk.therealsuji.vtopchennai.ui.attendance;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.AttendanceRecord;

public class DetailedAttendanceAdapter extends RecyclerView.Adapter<DetailedAttendanceAdapter.ViewHolder> {
    private final List<AttendanceRecord> items = new ArrayList<>();

    public void submitList(List<AttendanceRecord> records) {
        items.clear();
        if (records != null) items.addAll(records);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detailed_attendance, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
    TextView date, day, status, session, remarks;
    View statusBg;
    CardView cardView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.text_date);
            day = itemView.findViewById(R.id.text_day);
            status = itemView.findViewById(R.id.text_status);
            session = itemView.findViewById(R.id.text_session);
            remarks = itemView.findViewById(R.id.text_remarks);
        }

        void bind(AttendanceRecord record) {
            Context context = itemView.getContext();
            // Set date
            date.setText(record.dateString != null ? record.dateString : "");

            // Set day of week (if possible)
            String dayStr = "";
            if (record.date != null) {
                try {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(record.date);
                    String[] days = context.getResources().getStringArray(context.getResources().getIdentifier("days", "array", context.getPackageName()));
                    int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
                    // Calendar.SUNDAY=1, so shift to 0-based index
                    dayStr = days != null && days.length >= 7 ? days[dayOfWeek - 1] : "";
                } catch (Exception e) {
                    dayStr = "";
                }
            }
            day.setText(dayStr);

            // Set status text and background
            String statusStr = record.status != null ? record.status : "";
            status.setText(statusStr);
            if (statusStr.equalsIgnoreCase("Present")) {
                status.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_attendance_status_present));
            } else if (statusStr.equalsIgnoreCase("OD")) {
                status.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_attendance_status_od));
            } else if (statusStr.equalsIgnoreCase("Absent")) {
                status.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_attendance_status_absent));
            } else {
                status.setBackground(null);
            }

            // Show session as Lab/Class; remarks holds slotName if available
            String label = record.session;
            session.setText(label != null ? label : "");

            // Show remarks if available
            if (record.remarks != null && !record.remarks.isEmpty()) {
                remarks.setText(record.remarks);
                remarks.setVisibility(View.VISIBLE);
            } else {
                remarks.setVisibility(View.GONE);
            }
        }
    }
}
