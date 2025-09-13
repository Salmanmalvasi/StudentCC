package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.HostelDataHelper;

public class LaundryScheduleAdapter extends RecyclerView.Adapter<LaundryScheduleAdapter.ViewHolder> {
    private List<HostelDataHelper.LaundrySchedule> schedules;
    
    public LaundryScheduleAdapter(List<HostelDataHelper.LaundrySchedule> schedules) {
        this.schedules = schedules;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_laundry_schedule, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (schedules.isEmpty()) {
            holder.textViewDate.setText("No Laundry Data");
            holder.textViewRoomNumber.setText("Unable to load laundry schedule");
        } else {
            HostelDataHelper.LaundrySchedule schedule = schedules.get(position);
            if ("Countdown".equals(schedule.getRoomNumber())) {
                holder.textViewDate.setText(schedule.getDate());
                holder.textViewRoomNumber.setText("Your next laundry schedule");
            } else {
                holder.textViewDate.setText("Today's Laundry Schedule");
                holder.textViewRoomNumber.setText("Room Numbers: " + schedule.getRoomNumber());
            }
        }
    }
    
    @Override
    public int getItemCount() {
        return Math.max(1, schedules.size()); // Always show at least one item
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewDate, textViewRoomNumber;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDate = itemView.findViewById(R.id.text_view_date);
            textViewRoomNumber = itemView.findViewById(R.id.text_view_room_number);
        }
    }
}
