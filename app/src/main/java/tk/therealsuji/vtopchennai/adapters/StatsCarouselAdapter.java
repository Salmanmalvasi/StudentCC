package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.StatsItem;

public class StatsCarouselAdapter extends RecyclerView.Adapter<StatsCarouselAdapter.StatsViewHolder> {

    private List<StatsItem> statsList;

    public StatsCarouselAdapter(List<StatsItem> statsList) {
        this.statsList = statsList;
    }

    @NonNull
    @Override
    public StatsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_stats_carousel_item, parent,
                false);
        return new StatsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatsViewHolder holder, int position) {
        StatsItem item = statsList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return statsList.size();
    }

    static class StatsViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView value;
        TextView label;

        public StatsViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.stats_icon);
            value = itemView.findViewById(R.id.stats_value);
            label = itemView.findViewById(R.id.stats_label);
        }

        public void bind(StatsItem item) {
            icon.setImageResource(item.getIconResId());
            value.setText(item.getValue());
            label.setText(item.getLabel());
        }
    }
}
