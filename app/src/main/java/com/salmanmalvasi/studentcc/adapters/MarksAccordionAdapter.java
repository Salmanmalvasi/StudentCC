package com.salmanmalvasi.studentcc.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

/**
 * Single scrollable marks list with expandable course sections (dropdown style).
 */
public class MarksAccordionAdapter extends RecyclerView.Adapter<MarksAccordionAdapter.CourseViewHolder> {

    public interface OnCourseExpandListener {
        void onCourseExpanded(Course.AllData course, int position, View performanceCardsRoot);
    }

    private final List<CourseSection> sections = new ArrayList<>();
    private OnCourseExpandListener expandListener;

    public void setOnCourseExpandListener(OnCourseExpandListener listener) {
        this.expandListener = listener;
    }

    public void setMarks(List<Mark.AllData> marks, List<Course.AllData> courses) {
        sections.clear();
        Map<Integer, CourseSection> byCourseId = new LinkedHashMap<>();

        for (Course.AllData course : courses) {
            if (course.courseId != null) {
                CourseSection section = new CourseSection();
                section.course = course;
                byCourseId.put(course.courseId, section);
            }
        }

        for (Mark.AllData mark : marks) {
            CourseSection section = byCourseId.get(mark.courseId);
            if (section != null) {
                section.marks.add(mark);
            }
        }

        for (Course.AllData course : courses) {
            if (course.courseId != null && byCourseId.containsKey(course.courseId)) {
                CourseSection section = byCourseId.get(course.courseId);
                if (!section.marks.isEmpty()) {
                    sections.add(section);
                }
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_marks_course_accordion, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        holder.bind(sections.get(position), position);
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    class CourseViewHolder extends RecyclerView.ViewHolder {
        private final TextView courseTitle;
        private final TextView courseCode;
        private final ImageView expandIcon;
        private final LinearLayout marksContainer;
        private final View header;

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            courseTitle = itemView.findViewById(R.id.text_course_title);
            courseCode = itemView.findViewById(R.id.text_course_code);
            expandIcon = itemView.findViewById(R.id.icon_expand);
            marksContainer = itemView.findViewById(R.id.marks_container);
            header = itemView.findViewById(R.id.course_header);
        }

        void bind(CourseSection section, int position) {
            Course.AllData course = section.course;
            courseTitle.setText(course.courseTitle);
            courseCode.setText(course.courseCode);

            marksContainer.removeAllViews();
            marksContainer.setVisibility(section.expanded ? View.VISIBLE : View.GONE);
            expandIcon.setRotation(section.expanded ? 90f : 0f);

            if (section.expanded) {
                View performanceCards = LayoutInflater.from(itemView.getContext())
                        .inflate(R.layout.layout_course_performance_cards, marksContainer, false);
                marksContainer.addView(performanceCards);

                RecyclerView recyclerView = new RecyclerView(itemView.getContext());
                recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                recyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                recyclerView.setNestedScrollingEnabled(false);
                recyclerView.setAdapter(new MarksItemAdapter(section.marks, course));
                marksContainer.addView(recyclerView);

                if (expandListener != null) {
                    expandListener.onCourseExpanded(course, position, performanceCards);
                }
            }

            header.setOnClickListener(v -> {
                section.expanded = !section.expanded;
                notifyItemChanged(position);
            });
        }
    }

    static class CourseSection {
        Course.AllData course;
        final List<Mark.AllData> marks = new ArrayList<>();
        boolean expanded = false;
    }
}
