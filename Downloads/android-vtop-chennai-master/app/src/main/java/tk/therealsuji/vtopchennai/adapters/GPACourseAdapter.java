package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.GPACourse;

public class GPACourseAdapter extends RecyclerView.Adapter<GPACourseAdapter.CourseViewHolder> {
    private List<GPACourse> courses;
    private OnCourseDeleteListener deleteListener;

    public interface OnCourseDeleteListener {
        void onCourseDelete(int position);
    }

    public GPACourseAdapter() {
        this.courses = new ArrayList<>();
    }

    public void setOnCourseDeleteListener(OnCourseDeleteListener listener) {
        this.deleteListener = listener;
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
        private TextView creditHoursText;
        private TextView gradeText;
        private ImageButton deleteButton;

        public CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            creditHoursText = itemView.findViewById(R.id.credit_hours_text);
            gradeText = itemView.findViewById(R.id.grade_text);
            deleteButton = itemView.findViewById(R.id.delete_course_button);
        }

        public void bind(GPACourse course, int position) {
            creditHoursText.setText(String.format("%.1f credits", course.getCreditHours()));
            gradeText.setText(GPACourse.getGradeDisplayText(course.getGrade()));

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onCourseDelete(position);
                }
            });
        }
    }
}
