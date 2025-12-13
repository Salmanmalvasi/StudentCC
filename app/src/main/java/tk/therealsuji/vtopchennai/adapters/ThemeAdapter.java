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
    private String currentThemeName;

    public interface OnThemeClickListener {
        void onThemeClick(Theme theme);
    }

    public ThemeAdapter(List<Theme> themes, String currentThemeName, OnThemeClickListener listener) {
        this.themes = themes;
        this.currentThemeName = currentThemeName;
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
        holder.bind(theme, theme.getName().equals(currentThemeName));
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
        private View selectedIndicator;

        public ThemeViewHolder(@NonNull View itemView) {
            super(itemView);
            themeCard = itemView.findViewById(R.id.theme_card);
            primaryColorView = itemView.findViewById(R.id.primary_color_view);
            primaryContainerColorView = itemView.findViewById(R.id.primary_container_color_view);
            themeNameText = itemView.findViewById(R.id.theme_name_text);
            themeDescriptionText = itemView.findViewById(R.id.theme_description_text);
            selectedIndicator = itemView.findViewById(R.id.selected_indicator);

            themeCard.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onThemeClick(themes.get(position));
                }
            });
        }

        public void bind(Theme theme, boolean isSelected) {
            Context context = itemView.getContext();

            themeNameText.setText(theme.getName());
            themeDescriptionText.setText(theme.getDescription());

            // Set color previews
            primaryColorView.setBackgroundColor(context.getColor(theme.getPrimaryColorResId()));
            primaryContainerColorView.setBackgroundColor(context.getColor(theme.getPrimaryContainerColorResId()));

            // Show selected indicator and stroke
            if (isSelected) {
                selectedIndicator.setVisibility(View.VISIBLE);
                themeCard.setStrokeWidth(dpToPx(context, 3));
                themeCard.setStrokeColor(context.getColor(R.color.colorPrimary));
            } else {
                selectedIndicator.setVisibility(View.GONE);
                themeCard.setStrokeWidth(dpToPx(context, 1));
                themeCard.setStrokeColor(context.getColor(R.color.colorOutline));
            }
        }

        private int dpToPx(Context context, int dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density);
        }
    }
}
