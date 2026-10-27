package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.EventClub;

public class EventCategoryAdapter extends RecyclerView.Adapter<EventCategoryAdapter.ViewHolder> {

    private List<EventClub> categories = new ArrayList<>();
    private String selectedCategoryId = "0"; // Default to "All"
    private OnCategoryClickListener listener;
    private Context context;

    public interface OnCategoryClickListener {
        void onCategoryClick(EventClub category);
    }

    public void setCategories(List<EventClub> categories, OnCategoryClickListener listener) {
        this.categories = categories != null ? categories : new ArrayList<>();
        // Ensure "All" is always first
        boolean hasAll = false;
        for (EventClub c : this.categories) {
            if (c.id.equals("0")) {
                hasAll = true; 
                break; 
            }
        }
        
        if (!hasAll) {
            this.categories.add(0, new EventClub("0", "All"));
        }
        this.listener = listener;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_event_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventClub category = categories.get(position);
        
        holder.chip.setText(category.name);
        holder.chip.setChecked(category.id.equals(selectedCategoryId));
        
        holder.chip.setOnClickListener(v -> {
            selectedCategoryId = category.id;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        Chip chip;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            chip = (Chip) itemView;
        }
    }
}