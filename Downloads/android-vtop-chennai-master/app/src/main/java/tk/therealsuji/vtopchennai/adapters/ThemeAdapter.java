package tk.therealsuji.vtopchennai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.Theme;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder> {

    private List<Theme> themes;
    private OnThemeClickListener listener;

    public interface OnThemeClickListener {
        void onThemeClick(Theme theme);
    }

    public ThemeAdapter(List<Theme> themes, OnThemeClickListener listener) {
        this.themes = themes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ThemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_item_theme, parent, false);
        return new ThemeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeViewHolder holder, int position) {
        Theme theme = themes.get(position);
        holder.bind(theme);
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    class ThemeViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView themeCard;
        private View primaryColorView;
        private View primaryContainerColorView;
        private TextView themeNameText;
        private TextView themeDescriptionText;

        public ThemeViewHolder(@NonNull View itemView) {
            super(itemView);
            themeCard = itemView.findViewById(R.id.theme_card);
            primaryColorView = itemView.findViewById(R.id.primary_color_view);
            primaryContainerColorView = itemView.findViewById(R.id.primary_container_color_view);
            themeNameText = itemView.findViewById(R.id.theme_name_text);
            themeDescriptionText = itemView.findViewById(R.id.theme_description_text);

            themeCard.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onThemeClick(themes.get(position));
                }
            });
        }

        public void bind(Theme theme) {
            Context context = itemView.getContext();

            themeNameText.setText(theme.getName());
            themeDescriptionText.setText(theme.getDescription());

            // Set color previews
            primaryColorView.setBackgroundColor(context.getColor(theme.getPrimaryColorResId()));
            primaryContainerColorView.setBackgroundColor(context.getColor(theme.getPrimaryContainerColorResId()));
        }
    }
}
