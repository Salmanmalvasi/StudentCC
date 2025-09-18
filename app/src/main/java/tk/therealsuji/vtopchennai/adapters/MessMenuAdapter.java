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

public class MessMenuAdapter extends RecyclerView.Adapter<MessMenuAdapter.ViewHolder> {
    private List<HostelDataHelper.MessMenu> menus;

    public MessMenuAdapter(List<HostelDataHelper.MessMenu> menus) {
        this.menus = menus;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mess_menu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (menus.isEmpty()) {
            holder.textViewDay.setText("No Menu Available");
            holder.textViewBreakfast.setText("Menu data is not available for today");
            holder.textViewLunch.setText("");
            holder.textViewSnacks.setText("");
            holder.textViewDinner.setText("");
        } else {
            HostelDataHelper.MessMenu menu = menus.get(position);
            holder.textViewDay.setText(menu.getDay());
            holder.textViewBreakfast.setText(menu.getBreakfast());
            holder.textViewLunch.setText(menu.getLunch());
            holder.textViewSnacks.setText(menu.getSnacks());
            holder.textViewDinner.setText(menu.getDinner());
        }
    }

    @Override
    public int getItemCount() {
        return Math.max(1, menus.size()); // Always show at least one item
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewDay, textViewBreakfast, textViewLunch, textViewSnacks, textViewDinner;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDay = itemView.findViewById(R.id.text_view_day);
            textViewBreakfast = itemView.findViewById(R.id.text_view_breakfast);
            textViewLunch = itemView.findViewById(R.id.text_view_lunch);
            textViewSnacks = itemView.findViewById(R.id.text_view_snacks);
            textViewDinner = itemView.findViewById(R.id.text_view_dinner);
        }
    }
}
