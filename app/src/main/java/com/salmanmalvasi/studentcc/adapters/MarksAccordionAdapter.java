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

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.MarksCourseSummary;
import com.salmanmalvasi.studentcc.helpers.VitGradePassRules;
import com.salmanmalvasi.studentcc.helpers.VitGradePredictor;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.widgets.UniCCInsightsBinder;
import com.salmanmalvasi.studentcc.widgets.StudentCCStatisticsBinder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One scrollable list with stable, expandable course sections and lazy class insights. */
public class MarksAccordionAdapter extends RecyclerView.Adapter<MarksAccordionAdapter.CourseViewHolder> {
    public interface OnCourseExpandListener {
        void onCourseExpanded(Course.AllData course, List<Mark.AllData> displayedMarks);
    }

    private final List<CourseSection> sections = new ArrayList<>();
    private final List<Course.AllData> allCourses = new ArrayList<>();
    private final Map<Integer, List<Mark.AllData>> marksByCourse = new HashMap<>();
    private final Set<CourseViewHolder> holders = new HashSet<>();
    private final Map<String, CumulativeMark> cumulativeByCode = new HashMap<>();
    private OnCourseExpandListener expandListener;

    public MarksAccordionAdapter() {
        setHasStableIds(true);
    }

    public void setOnCourseExpandListener(OnCourseExpandListener listener) {
        expandListener = listener;
    }

    public void setMarks(List<Mark.AllData> marks, List<Course.AllData> courses,
                         List<CumulativeMark> cumulativeMarks) {
        Set<Integer> expandedIds = new HashSet<>();
        for (CourseSection section : sections) {
            if (section.expanded) expandedIds.add(section.course.courseId);
        }
        sections.clear();
        allCourses.clear();
        allCourses.addAll(courses);
        marksByCourse.clear();
        cumulativeByCode.clear();
        for (CumulativeMark total : cumulativeMarks) {
            if (total.courseCode != null) cumulativeByCode.put(total.courseCode, total);
        }
        Map<Integer, CourseSection> byId = new LinkedHashMap<>();
        for (Course.AllData course : courses) {
            if (course.courseId == null) continue;
            CourseSection section = new CourseSection();
            section.course = course;
            section.expanded = expandedIds.contains(course.courseId);
            byId.put(course.courseId, section);
            marksByCourse.put(course.courseId, section.marks);
        }
        for (Mark.AllData mark : marks) {
            CourseSection section = byId.get(mark.courseId);
            if (section != null) section.marks.add(mark);
        }
        for (CourseSection section : byId.values()) {
            if (!section.marks.isEmpty()) sections.add(section);
        }
        sections.sort((first, second) -> {
            String firstTitle = first.course.courseTitle == null ? "" : first.course.courseTitle;
            String secondTitle = second.course.courseTitle == null ? "" : second.course.courseTitle;
            int titleOrder = firstTitle.compareToIgnoreCase(secondTitle);
            return titleOrder != 0 ? titleOrder : Integer.compare(componentOrder(first.course), componentOrder(second.course));
        });
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return sections.get(position).course.courseId;
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CourseViewHolder holder = new CourseViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_marks_course_accordion, parent, false));
        holders.add(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        holder.bind(sections.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull CourseViewHolder holder) {
        holder.release();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    public void release() {
        for (CourseViewHolder holder : holders) holder.release();
        holders.clear();
        expandListener = null;
    }

    private static int componentOrder(Course.AllData course) {
        return MarksCourseSummary.isLab(course) ? 1 : MarksCourseSummary.isProject(course) ? 2 : 0;
    }

    class CourseViewHolder extends RecyclerView.ViewHolder {
        private final TextView courseTitle;
        private final TextView courseCode;
        private final TextView weighted;
        private final TextView assessments;
        private final TextView unread;
        private final View unreadDot;
        private final TextView combined;
        private final ImageView expandIcon;
        private final LinearLayout marksContainer;
        private final LinearProgressIndicator progress;
        private final View header;
        private final RecyclerView marksRecycler;
        private final UniCCInsightsBinder insights;
        private final StudentCCStatisticsBinder studentCCStatistics;

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            courseTitle = itemView.findViewById(R.id.text_course_title);
            courseCode = itemView.findViewById(R.id.text_course_code);
            weighted = itemView.findViewById(R.id.text_course_weighted);
            assessments = itemView.findViewById(R.id.text_course_assessments);
            unread = itemView.findViewById(R.id.text_course_unread);
            unreadDot = itemView.findViewById(R.id.dot_course_updated);
            combined = itemView.findViewById(R.id.text_course_combined);
            expandIcon = itemView.findViewById(R.id.icon_expand);
            marksContainer = itemView.findViewById(R.id.marks_container);
            progress = itemView.findViewById(R.id.progress_course_weighted);
            header = itemView.findViewById(R.id.course_header);
            marksRecycler = itemView.findViewById(R.id.recycler_course_marks);
            marksRecycler.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            marksRecycler.setNestedScrollingEnabled(false);
            marksRecycler.setItemAnimator(null);
            insights = new UniCCInsightsBinder(itemView.findViewById(R.id.unicc_insights_panel));
            studentCCStatistics = new StudentCCStatisticsBinder(itemView.findViewById(R.id.studentcc_statistics_panel));
        }

        void bind(CourseSection section) {
            release();
            Course.AllData course = section.course;
            String title = course.courseTitle == null ? course.courseCode : course.courseTitle;
            courseTitle.setText(title);
            int component = MarksCourseSummary.isLab(course) ? R.string.marks_component_lab
                    : MarksCourseSummary.isProject(course) ? R.string.marks_component_project : R.string.marks_component_theory;
            courseCode.setText((course.courseCode == null ? "" : course.courseCode) + " · " + itemView.getContext().getString(component));
            MarksCourseSummary summary = MarksCourseSummary.from(section.marks);
            weighted.setText(summary.maximum > 0 && summary.recordedAssessments > 0
                    ? number(summary.score) + " / " + number(summary.maximum) : "—");
            progress.setVisibility(summary.maximum > 0 && summary.recordedAssessments > 0 ? View.VISIBLE : View.GONE);
            progress.setMax(1000);
            progress.setProgress(summary.maximum > 0 ? (int) Math.max(0, Math.min(1000, summary.score / summary.maximum * 1000)) : 0);
            assessments.setText(itemView.getContext().getString(R.string.marks_assessment_count, summary.recordedAssessments, summary.assessments));
            unread.setText(itemView.getContext().getString(R.string.marks_new_assessments, summary.unreadAssessments));
            unread.setVisibility(summary.unreadAssessments > 0 ? View.VISIBLE : View.GONE);
            unreadDot.setVisibility(summary.unreadAssessments > 0 ? View.VISIBLE : View.GONE);
            CumulativeMark total = cumulativeByCode.get(course.courseCode);
            combined.setVisibility(View.GONE);
            if (total != null) {
                String detail = "";
                if (total.grandTotal != null && total.grandMax != null) {
                    detail = itemView.getContext().getString(R.string.marks_course_combined, number(total.grandTotal), number(total.grandMax));
                }
                if (total.grade != null && !total.grade.trim().isEmpty()) {
                    detail += (detail.isEmpty() ? "" : "\n") + itemView.getContext().getString(R.string.marks_course_grade, total.grade);
                }
                if (!detail.isEmpty()) {
                    combined.setText(detail);
                    combined.setVisibility(View.VISIBLE);
                }
            }
            marksRecycler.setAdapter(null);
            updateExpansion(section);
            header.setOnClickListener(v -> {
                section.expanded = !section.expanded;
                if (section.expanded && expandListener != null) {
                    expandListener.onCourseExpanded(course, new ArrayList<>(section.marks));
                    for (Mark.AllData mark : section.marks) mark.isRead = true;
                    unread.setVisibility(View.GONE);
                    unreadDot.setVisibility(View.GONE);
                }
                updateExpansion(section);
            });
        }

        private void updateExpansion(CourseSection section) {
            marksContainer.setVisibility(section.expanded ? View.VISIBLE : View.GONE);
            expandIcon.setRotation(section.expanded ? 90 : 0);
            String action = itemView.getContext().getString(section.expanded
                    ? R.string.marks_collapse_course : R.string.marks_expand_course, courseTitle.getText());
            header.setContentDescription(action + ". " + courseCode.getText() + ". "
                    + itemView.getContext().getString(R.string.marks_weighted) + ": " + weighted.getText()
                    + ". " + assessments.getText()
                    + (unread.getVisibility() == View.VISIBLE ? ". " + unread.getText() : ""));
            if (section.expanded) {
                if (marksRecycler.getAdapter() == null) {
                    marksRecycler.setAdapter(new MarksItemAdapter(section.marks, section.course));
                }
                boolean embedded = MarksCourseSummary.isEmbedded(section.course, allCourses);
                VitGradePredictor.PassStatus projectedPassStatus =
                        VitGradePassRules.evaluateInternalProjection(section.course, allCourses, marksByCourse);
                insights.bind(section.course, MarksCourseSummary.comparableInternalScore(section.course, allCourses, marksByCourse),
                        embedded, projectedPassStatus);
                Double finalScore = MarksCourseSummary.completeFinalScore(section.course, allCourses, marksByCourse);
                studentCCStatistics.bind(section.course, finalScore == null
                        ? MarksCourseSummary.completeInternalScore(section.course, allCourses, marksByCourse) : finalScore,
                        finalScore == null ? 60 : 100, finalScore == null ? projectedPassStatus
                                : VitGradePassRules.evaluate(section.course, allCourses, marksByCourse), embedded);
            } else {
                insights.dispose();
                studentCCStatistics.dispose();
            }
        }

        private void release() {
            insights.dispose();
            studentCCStatistics.dispose();
        }
    }

    private static String number(double value) {
        return new DecimalFormat("0.##").format(value);
    }

    static class CourseSection {
        Course.AllData course;
        final List<Mark.AllData> marks = new ArrayList<>();
        boolean expanded;
    }
}
