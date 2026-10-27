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

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.EventHubEvent;

/**
 * Adapter for displaying EventHub events in a RecyclerView.
 * Binds to the views defined in item_eventhub_event.xml:
 * event_category (TextView), event_fee (TextView inside event_fee_card),
 * event_title, event_date, event_venue, event_team_size
 */
public class EventHubAdapter extends RecyclerView.Adapter<EventHubAdapter.EventViewHolder> {

    private List<EventHubEvent> events = new ArrayList<>();
    private Context context;

    public void setEvents(List<EventHubEvent> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_eventhub_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventHubEvent event = events.get(position);

        // Set event title
        if (holder.titleText != null) {
            holder.titleText.setText(event.title != null ? event.title : "");
        }

        // Set date
        if (holder.dateText != null) {
            holder.dateText.setText(event.getFormattedDate());
        }

        // Set venue
        if (holder.venueText != null) {
            holder.venueText.setText(event.venue != null ? event.venue : "Venue TBA");
        }

        // Set category (this is a TextView in the layout, not a Chip)
        if (holder.categoryText != null) {
            if (event.category != null && !event.category.isEmpty()) {
                holder.categoryText.setText(event.category);
                holder.categoryText.setVisibility(View.VISIBLE);
            } else if (event.clubName != null && !event.clubName.isEmpty()) {
                holder.categoryText.setText(event.clubName);
                holder.categoryText.setVisibility(View.VISIBLE);
            } else {
                holder.categoryText.setVisibility(View.GONE);
            }
        }

        // Set fee
        if (holder.feeText != null) {
            String feeDisplay = event.getFeeDisplay();
            holder.feeText.setText(feeDisplay);
        }

        // Set team size
        if (holder.teamSizeText != null) {
            if (event.teamSize != null && !event.teamSize.isEmpty() && !event.teamSize.equals("1")) {
                holder.teamSizeText.setText("Team: " + event.teamSize);
                holder.teamSizeText.setVisibility(View.VISIBLE);
            } else {
                holder.teamSizeText.setVisibility(View.GONE);
            }
        }

        // Open event URL on card click
        if (holder.cardView != null) {
            holder.cardView.setOnClickListener(v -> {
                if (event.eventUrl != null && !event.eventUrl.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(event.eventUrl));
                    context.startActivity(intent);
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://eventhubcc.vit.ac.in/EventHub/"));
                    context.startActivity(intent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView titleText;
        final TextView dateText;
        final TextView venueText;
        final TextView feeText;
        final TextView teamSizeText;
        final TextView categoryText;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView instanceof MaterialCardView ? (MaterialCardView) itemView : null;
            titleText = itemView.findViewById(R.id.event_title);
            dateText = itemView.findViewById(R.id.event_date);
            venueText = itemView.findViewById(R.id.event_venue);
            feeText = itemView.findViewById(R.id.event_fee);
            teamSizeText = itemView.findViewById(R.id.event_team_size);
            categoryText = itemView.findViewById(R.id.event_category);
        }
    }
}
