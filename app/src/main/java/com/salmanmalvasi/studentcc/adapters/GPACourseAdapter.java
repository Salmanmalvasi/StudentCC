package com.salmanmalvasi.studentcc.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.GPACourse;

public class GPACourseAdapter extends RecyclerView.Adapter<GPACourseAdapter.CourseViewHolder> {
    private List<GPACourse> courses;
    private OnCourseDeleteListener deleteListener;
    private OnCourseEditListener editListener;

    public interface OnCourseDeleteListener {
        void onCourseDelete(int position);
    }

    public interface OnCourseEditListener {
        void onCourseEdit(int position);
    }

    public GPACourseAdapter() {
        this.courses = new ArrayList<>();
    }

    public void setOnCourseDeleteListener(OnCourseDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setOnCourseEditListener(OnCourseEditListener listener) {
        this.editListener = listener;
    }

    public void setCourses(List<GPACourse> courses) {
        this.courses = courses;
        notifyDataSetChanged();
    }

    public void addCourse(GPACourse course) {
        courses.add(course);
        notifyItemInserted(courses.size() - 1);
    }

    public void removeCourse(int position) {
        if (position >= 0 && position < courses.size()) {
            courses.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, courses.size() - position);
        }
    }

    public void clearCourses() {
        courses.clear();
        notifyDataSetChanged();
    }

    public List<GPACourse> getCourses() {
        return courses;
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_item_course, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        GPACourse course = courses.get(position);
        holder.bind(course, position);
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    class CourseViewHolder extends RecyclerView.ViewHolder {
        private TextView courseIconText;
        private TextView courseTitleText;
        private TextView creditHoursText;
        private TextView gradeText;
        private ImageButton deleteButton;

        public CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            courseIconText = itemView.findViewById(R.id.course_icon_text);
            courseTitleText = itemView.findViewById(R.id.course_title_text);
            creditHoursText = itemView.findViewById(R.id.credit_hours_text);
            gradeText = itemView.findViewById(R.id.grade_text);
            deleteButton = itemView.findViewById(R.id.delete_course_button);
        }

        public void bind(GPACourse course, int position) {
            // Set course icon (you can customize this based on course type)
            courseIconText.setText("📚");

            // Set course title
            String title = course.getCourseTitle();
            String code = course.getCourseCode();
            if (title != null && !title.trim().isEmpty() && code != null && !code.trim().isEmpty()) {
                courseTitleText.setText(code.trim() + " • " + title.trim());
            } else if (title != null && !title.trim().isEmpty()) {
                courseTitleText.setText(title.trim());
            } else if (code != null && !code.trim().isEmpty()) {
                courseTitleText.setText(code.trim());
            } else {
                courseTitleText.setText("Course " + (position + 1));
            }

            // Set credits badge
            creditHoursText.setText(String.format("%.1f credits", course.getCreditHours()));

            // Set grade badge
        String grade = course.getGrade();
        gradeText.setText(grade != null && !grade.trim().isEmpty()
            ? GPACourse.getGradeDisplayText(grade)
            : "--");

            deleteButton.setOnClickListener(v -> {
                int adapterPosition = getAdapterPosition();
                if (deleteListener != null && adapterPosition != RecyclerView.NO_POSITION) {
                    deleteListener.onCourseDelete(adapterPosition);
                }
            });

            // Tap to edit course grade/credits
            itemView.setOnClickListener(v -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    showEditDialog(itemView.getContext(), adapterPosition);
                }
            });
        }

        private void showEditDialog(Context context, int position) {
            if (position < 0 || position >= courses.size()) return;
            GPACourse course = courses.get(position);

            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_course, null);
            TextInputEditText editCredits = dialogView.findViewById(R.id.edit_credits_input);
            TextInputLayout gradeLayout = dialogView.findViewById(R.id.edit_grade_layout);
            AutoCompleteTextView editGrade = dialogView.findViewById(R.id.edit_grade_dropdown);

            // Pre-fill current values
            editCredits.setText(String.format("%.1f", course.getCreditHours()));
            editGrade.setText(course.getGrade(), false);

            // Setup grade dropdown
            String[] grades = GPACourse.getAvailableGrades();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, grades);
            editGrade.setAdapter(adapter);

            new AlertDialog.Builder(context)
                    .setTitle("Edit Course")
                    .setView(dialogView)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String creditsStr = editCredits.getText() != null ? editCredits.getText().toString().trim() : "";
                        String gradeStr = editGrade.getText() != null ? editGrade.getText().toString().trim() : "";

                        if (!creditsStr.isEmpty()) {
                            try {
                                double credits = Double.parseDouble(creditsStr);
                                if (credits > 0) {
                                    course.setCreditHours(credits);
                                }
                            } catch (NumberFormatException ignored) {}
                        }

                        if (!gradeStr.isEmpty()) {
                            course.setGrade(gradeStr);
                        }

                        notifyItemChanged(position);
                        if (editListener != null) {
                            editListener.onCourseEdit(position);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
}
