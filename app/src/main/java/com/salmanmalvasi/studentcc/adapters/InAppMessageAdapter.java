package com.salmanmalvasi.studentcc.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.InAppMessage;

/**
 * Adapter for displaying in-app messages
 */
public class InAppMessageAdapter extends RecyclerView.Adapter<InAppMessageAdapter.ViewHolder> {
    private List<InAppMessage> messages = new ArrayList<>();
    private final OnMessageClickListener listener;

    public interface OnMessageClickListener {
        void onMessageClick(InAppMessage message);
    }

    public InAppMessageAdapter(OnMessageClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<InAppMessage> newMessages) {
        this.messages = newMessages != null ? newMessages : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_in_app_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InAppMessage message = messages.get(position);

        // Set title
        holder.titleText.setText(message.title);

        // Set message content
        holder.messageText.setText(message.message);

        // Set timestamp
        holder.timeText.setText(message.getFormattedTime());

        // Set priority indicator
        if (message.isHighPriority()) {
            holder.priorityIndicator.setVisibility(View.VISIBLE);
            holder.priorityIndicator.setColorFilter(
                ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_dark)
            );
        } else {
            holder.priorityIndicator.setVisibility(View.GONE);
        }

        // Set action indicator
        if (message.hasAction()) {
            holder.actionIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.actionIndicator.setVisibility(View.GONE);
        }

        // Style based on read status
        if (message.isRead) {
            holder.titleText.setTypeface(null, Typeface.NORMAL);
            holder.messageText.setAlpha(0.7f);
            holder.cardView.setStrokeWidth(0);
        } else {
            holder.titleText.setTypeface(null, Typeface.BOLD);
            holder.messageText.setAlpha(1.0f);
            holder.cardView.setStrokeWidth(2);
            holder.cardView.setStrokeColor(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.colorPrimary)
            );
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMessageClick(message);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView titleText;
        TextView messageText;
        TextView timeText;
        ImageView priorityIndicator;
        ImageView actionIndicator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            titleText = itemView.findViewById(R.id.text_message_title);
            messageText = itemView.findViewById(R.id.text_message_content);
            timeText = itemView.findViewById(R.id.text_message_time);
            priorityIndicator = itemView.findViewById(R.id.icon_priority);
            actionIndicator = itemView.findViewById(R.id.icon_action);
        }
    }
}
