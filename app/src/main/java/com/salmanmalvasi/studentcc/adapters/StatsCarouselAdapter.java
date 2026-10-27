package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.StatsItem;

public class StatsCarouselAdapter extends RecyclerView.Adapter<StatsCarouselAdapter.StatsViewHolder> {

    public interface OnStatClickListener {
        void onStatClick(int position, StatsItem item);
    }

    private final List<StatsItem> statsList = new ArrayList<>();
    private OnStatClickListener clickListener;

    public void setStats(List<StatsItem> stats) {
        statsList.clear();
        if (stats != null) {
            statsList.addAll(stats);
        }
        notifyDataSetChanged();
    }

    public void updateStat(int position, String value, String subtitle) {
        if (position < 0 || position >= statsList.size()) {
            return;
        }
        StatsItem current = statsList.get(position);
        statsList.set(position, new StatsItem(current.getIconResId(), value, subtitle != null ? subtitle : current.getLabel()));
        notifyItemChanged(position);
    }

    public void setOnStatClickListener(OnStatClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public StatsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_stats_carousel_item, parent, false);
        return new StatsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatsViewHolder holder, int position) {
        holder.bind(statsList.get(position), position);

        // Dynamic scale pop entrance
        holder.itemView.setAlpha(0.6f);
        holder.itemView.setScaleX(0.92f);
        holder.itemView.setScaleY(0.92f);
        holder.itemView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setStartDelay(position * 40L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
    }

    @Override
    public int getItemCount() {
        return statsList.size();
    }

    class StatsViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView value;
        TextView label;

        StatsViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.stats_icon);
            value = itemView.findViewById(R.id.stats_value);
            label = itemView.findViewById(R.id.stats_label);
        }

        void bind(StatsItem item, int position) {
            icon.setImageResource(item.getIconResId());
            value.setText(item.getValue());
            label.setText(item.getLabel());
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onStatClick(position, item);
                }
            });
        }
    }
}
