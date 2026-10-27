package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.EventHubEvent;

public class FeaturedEventAdapter extends RecyclerView.Adapter<FeaturedEventAdapter.ViewHolder> {

    private List<EventHubEvent> events = new ArrayList<>();
    private Context context;

    public void setEvents(List<EventHubEvent> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_featured_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventHubEvent event = events.get(position);

        holder.titleText.setText(event.title);
        holder.dateText.setText(event.getFormattedDate());
        // holder.venueText.setText(event.venue != null ? event.venue : "Venue TBA");
        
        if (event.clubName != null && !event.clubName.isEmpty()) {
            holder.clubNameText.setText(event.clubName);
        } else {
            holder.clubNameText.setText("VIT Event");
        }

        // Open event URL on card click
        holder.itemView.setOnClickListener(v -> {
            if (event.eventUrl != null && !event.eventUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(event.eventUrl));
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView dateText;
        // TextView venueText; // Removed
        TextView clubNameText;
        // TextView actionButton; // Removed

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.tv_event_title);
            dateText = itemView.findViewById(R.id.tv_date);
            // venueText = itemView.findViewById(R.id.tv_venue); // Removed
            clubNameText = itemView.findViewById(R.id.tv_club_name);
            // actionButton = itemView.findViewById(R.id.tv_register_btn); // Removed
        }
    }
}