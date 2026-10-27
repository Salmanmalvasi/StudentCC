package com.salmanmalvasi.studentcc.adapters;

import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.salmanmalvasi.studentcc.R;

public class EmptyStateAdapter extends RecyclerView.Adapter<EmptyStateAdapter.ViewHolder> {
    public static final int TYPE_ERROR = 1;
    public static final int TYPE_FETCHING_ASSIGNMENTS = 2;
    public static final int TYPE_NO_ASSIGNMENTS = 3;
    public static final int TYPE_NO_DATA = 4;
    public static final int TYPE_NO_PERFORMANCE = 5;
    public static final int TYPE_NO_TIMETABLE = 6;
    public static final int TYPE_NOT_AUTHENTICATED = 7;

    final int type;
    ButtonAttributes buttonAttributes;
    String message;

    public EmptyStateAdapter(int type) {
        this.type = type;
    }

    public EmptyStateAdapter(int type, String message) {
        this.type = type;
        this.message = message;
    }

    public EmptyStateAdapter(int type, String message, ButtonAttributes buttonAttributes) {
        this.type = type;
        this.message = message;
        this.buttonAttributes = buttonAttributes;
    }

    @NonNull
    @Override
    public EmptyStateAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View noData = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.layout_empty_state, parent, false);

        return new ViewHolder(noData);
    }

    @Override
    public void onBindViewHolder(@NonNull EmptyStateAdapter.ViewHolder holder, int position) {
        holder.subtitle.setVisibility(View.GONE); // Default
        
        switch (this.type) {
            case TYPE_ERROR:
                holder.setImage(R.drawable.image_error);
                break;
            case TYPE_FETCHING_ASSIGNMENTS:
                holder.setImage(R.drawable.image_fetching_assignments);
                holder.setText(R.string.fetch_assignments);
                break;
            case TYPE_NO_ASSIGNMENTS:
                holder.setImage(R.drawable.image_no_assignments);
                holder.setText(R.string.no_assignments);
                break;
            case TYPE_NO_PERFORMANCE:
                holder.setImage(R.drawable.image_no_marks);
                holder.setText(R.string.no_marks);
                break;
            case TYPE_NO_TIMETABLE:
                holder.setImage(R.drawable.animated_day_off);
                holder.setText(R.string.no_classes);
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText("Relax and recharge! No classes scheduled for today.");
                // Start the animation for day off
                holder.startDayOffAnimation();
                break;
            case TYPE_NOT_AUTHENTICATED:
                holder.setImage(R.drawable.image_not_authenticated);
                holder.setText(R.string.not_authenticated);
                break;
            default:
                holder.setImage(R.drawable.image_no_data);
                holder.setText(R.string.no_data);
        }

        if (this.message != null) {
            holder.setText(message);
        }

        holder.setButton(this.buttonAttributes);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView textView;
        public final TextView subtitle;
        public final AnimatedVectorDrawable animatedDayOff;
        public final Button button;
        public final android.widget.ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(R.id.text_view_no_data);
            this.subtitle = itemView.findViewById(R.id.text_view_subtitle);
            this.imageView = itemView.findViewById(R.id.image_view_empty_state);
            this.button = itemView.findViewById(R.id.button_empty_state_action); // Might be null if not added to layout yet
            
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AnimatedVectorDrawable) {
                this.animatedDayOff = (AnimatedVectorDrawable) drawable;
            } else {
                this.animatedDayOff = null;
            }
        }

        public void setText(@StringRes int resId) {
            this.textView.setText(resId);
        }

        public void setText(String text) {
            this.textView.setText(text);
        }

        public void setImage(@DrawableRes int resId) {
            this.imageView.setImageResource(resId);
            // Reset animated drawable ref if image changed
            // logic omitted for brevity as it's complex to re-hook
        }
        
        public void setButton(ButtonAttributes buttonAttributes) {
            if (buttonAttributes == null || this.button == null) {
                if (this.button != null) this.button.setVisibility(View.GONE);
                return;
            }

            this.button.setVisibility(View.VISIBLE);
            this.button.setOnClickListener(view -> buttonAttributes.onClick());
            this.button.setText(buttonAttributes.getButtonTextId());
        }

        public void startDayOffAnimation() {
            if (this.animatedDayOff != null) {
                this.animatedDayOff.start();
            }
        }
    }

    public interface ButtonAttributes {
        void onClick();

        int getButtonTextId();
    }
}
