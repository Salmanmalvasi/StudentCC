package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.Exam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Shows every scheduled sitting, combining duplicate rows introduced by course slots. */
public class ExamsItemAdapter extends RecyclerView.Adapter<ExamsItemAdapter.ViewHolder> {
    private static final TimeZone CAMPUS_TIME_ZONE = TimeZone.getTimeZone("Asia/Kolkata");
    private final List<Exam.AllData> exams = new ArrayList<>();

    public ExamsItemAdapter(List<Exam.AllData> rows) {
        Map<String, Exam.AllData> grouped = new LinkedHashMap<>();
        for (Exam.AllData row : rows) {
            String key = row.courseCode + "|" + row.courseTitle + "|" + row.startTime + "|" + row.endTime;
            Exam.AllData exam = grouped.get(key);
            if (exam == null) {
                exam = row;
                exam.slots = new ArrayList<>();
                grouped.put(key, exam);
                exams.add(exam);
            }
            if (row.slot != null && !row.slot.trim().isEmpty() && !exam.slots.contains(row.slot)) exam.slots.add(row.slot);
        }
        long now = System.currentTimeMillis();
        exams.sort((left, right) -> {
            int completed = Boolean.compare(isCompleted(left, now), isCompleted(right, now));
            if (completed != 0) return completed;
            return Long.compare(left.startTime == null ? Long.MAX_VALUE : left.startTime,
                    right.startTime == null ? Long.MAX_VALUE : right.startTime);
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_item_exams, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(exams.get(position)); }

    @Override
    public int getItemCount() { return exams.size(); }

    private static boolean isCompleted(Exam.AllData exam, long now) {
        long end = exam.endTime != null ? exam.endTime : exam.startTime != null ? exam.startTime + 86400000L : Long.MAX_VALUE;
        return end < now;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView courseTitle, courseCode, date, timings, venue, status;
        private final ChipGroup slots;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            courseTitle = itemView.findViewById(R.id.text_view_course_title);
            courseCode = itemView.findViewById(R.id.text_view_course_code);
            date = itemView.findViewById(R.id.text_view_date);
            timings = itemView.findViewById(R.id.text_view_timings);
            venue = itemView.findViewById(R.id.text_view_venue);
            status = itemView.findViewById(R.id.text_view_exam_status);
            slots = itemView.findViewById(R.id.chip_group_slots);
        }

        void bind(Exam.AllData exam) {
            courseTitle.setText(exam.courseTitle == null || exam.courseTitle.trim().isEmpty()
                    ? itemView.getContext().getString(R.string.exam_schedule_course_fallback) : exam.courseTitle);
            courseCode.setText(exam.courseCode);
            courseCode.setVisibility(exam.courseCode == null || exam.courseCode.trim().isEmpty() ? View.GONE : View.VISIBLE);

            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
            dateFormat.setTimeZone(CAMPUS_TIME_ZONE);
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            timeFormat.setTimeZone(CAMPUS_TIME_ZONE);
            date.setText(exam.startTime == null ? itemView.getContext().getString(R.string.exam_schedule_date_pending)
                    : dateFormat.format(exam.startTime));
            Calendar examDay = Calendar.getInstance(CAMPUS_TIME_ZONE);
            if (exam.startTime != null) examDay.setTimeInMillis(exam.startTime);
            boolean dateOnly = exam.startTime == null || (exam.endTime == null
                    && examDay.get(Calendar.HOUR_OF_DAY) == 0 && examDay.get(Calendar.MINUTE) == 0);
            if (dateOnly) {
                timings.setText(R.string.exam_schedule_time_pending);
            } else if (exam.endTime != null) {
                timings.setText(timeFormat.format(exam.startTime) + " – " + timeFormat.format(exam.endTime));
            } else {
                timings.setText(itemView.getContext().getString(R.string.exam_schedule_from_time, timeFormat.format(exam.startTime)));
            }

            String seat = exam.seatLocation == null ? "" : exam.seatLocation;
            if (exam.seatNumber != null) seat += (seat.isEmpty() ? "" : " · ") + exam.seatNumber;
            String room = exam.venue == null || exam.venue.trim().isEmpty()
                    ? itemView.getContext().getString(R.string.exam_schedule_venue_pending) : exam.venue;
            if (!seat.isEmpty()) room += "\n" + itemView.getContext().getString(R.string.exam_schedule_seat, seat);
            venue.setText(room);

            Calendar today = Calendar.getInstance(CAMPUS_TIME_ZONE);
            boolean completed = isCompleted(exam, today.getTimeInMillis());
            boolean sameDay = exam.startTime != null && examDay.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && examDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            status.setText(exam.startTime == null ? R.string.exam_schedule_pending : completed
                    ? R.string.exam_schedule_completed : sameDay ? R.string.exam_schedule_today : R.string.exam_schedule_upcoming);
            itemView.setAlpha(completed ? 0.65f : 1f);

            slots.removeAllViews();
            for (String slotText : exam.slots) {
                Chip chip = new Chip(itemView.getContext());
                chip.setText(slotText);
                chip.setCheckable(false);
                chip.setClickable(false);
                slots.addView(chip);
            }
            slots.setVisibility(exam.slots.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}
