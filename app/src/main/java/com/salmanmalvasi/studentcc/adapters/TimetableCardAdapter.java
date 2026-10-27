package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.google.android.material.color.MaterialColors;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.TimetableModel;

/**
 * RecyclerView adapter rendering timetable rows that mimic Google Calendar cards.
 */
public class TimetableCardAdapter extends RecyclerView.Adapter<TimetableCardAdapter.TimetableViewHolder> {

    private final List<TimetableModel> items = new ArrayList<>();

    public void setItems(@NonNull List<TimetableModel> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimetableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.timetable_item, parent, false);
        return new TimetableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimetableViewHolder holder, int position) {
        TimetableModel model = items.get(position);
        holder.bind(model);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder with cached references to timetable view components.
     */
    static class TimetableViewHolder extends RecyclerView.ViewHolder {
        private final TextView startTime;
        private final TextView endTime;
        private final TextView subjectName;
        private final TextView facultyName;
        private final TextView room;
        private final TextView classType;

        TimetableViewHolder(@NonNull View itemView) {
            super(itemView);
            startTime = itemView.findViewById(R.id.text_start_time);
            endTime = itemView.findViewById(R.id.text_end_time);
            subjectName = itemView.findViewById(R.id.text_subject_name);
            facultyName = itemView.findViewById(R.id.text_faculty);
            room = itemView.findViewById(R.id.text_room);
            classType = itemView.findViewById(R.id.text_class_type);
        }

        void bind(@NonNull TimetableModel model) {
            startTime.setText(model.getStartTime());
            endTime.setText(model.getEndTime());
            subjectName.setText(model.getSubjectName());

            if (model.getFacultyName() != null && !model.getFacultyName().isEmpty()) {
                facultyName.setVisibility(View.VISIBLE);
                facultyName.setText(model.getFacultyName());
            } else {
                facultyName.setVisibility(View.GONE);
            }

            if (model.getRoom() != null && !model.getRoom().isEmpty()) {
                room.setText(model.getRoom());
            } else {
                room.setText("Room TBD");
            }

            classType.setText(model.getClassType().name());

            // Example dynamic styling: change accent color based on class type
            int colorAttr;
            switch (model.getClassType()) {
                case LAB:
                    colorAttr = com.google.android.material.R.attr.colorTertiary;
                    break;
                case TUTORIAL:
                    colorAttr = com.google.android.material.R.attr.colorSecondary;
                    break;
                case THEORY:
                default:
                    colorAttr = com.google.android.material.R.attr.colorPrimary;
                    break;
            }
            int resolvedColor = MaterialColors.getColor(itemView, colorAttr);
            classType.setTextColor(resolvedColor);
        }
    }
}
