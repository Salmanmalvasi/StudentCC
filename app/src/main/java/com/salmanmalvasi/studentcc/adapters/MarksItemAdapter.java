package com.salmanmalvasi.studentcc.adapters;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.Mark;

import java.text.DecimalFormat;
import java.util.List;

/** Read-only assessment scores and VTOP averages, with explicit weighted values. */
public class MarksItemAdapter extends RecyclerView.Adapter<MarksItemAdapter.ViewHolder> {
    private final List<Mark.AllData> marks;

    public MarksItemAdapter(List<Mark.AllData> marks, Course.AllData course) {
        this.marks = marks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_item_marks, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(marks.get(position));
    }

    @Override
    public int getItemCount() {
        return marks.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final TextView title;
        private final TextView average;
        private final TextView status;
        private final TextView score;
        private final TextView scoreLabel;
        private final TextView weighted;
        private final ProgressBar progress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            title = itemView.findViewById(R.id.text_view_title);
            average = itemView.findViewById(R.id.text_view_average);
            status = itemView.findViewById(R.id.text_view_status);
            score = itemView.findViewById(R.id.text_view_score);
            scoreLabel = itemView.findViewById(R.id.text_view_mark_type);
            weighted = itemView.findViewById(R.id.text_view_weighted_detail);
            progress = itemView.findViewById(R.id.progress_bar_score);
            itemView.findViewById(R.id.chip_course_type).setVisibility(View.GONE);
            card.setOnLongClickListener(null);
            card.setLongClickable(false);
        }

        void bind(Mark.AllData mark) {
            title.setText(mark.title);
            title.getOverlay().clear();
            score.setOnClickListener(null);
            scoreLabel.setText(R.string.score);
            progress.setMax(1000);
            progress.setProgress(0);
            if (finite(mark.score) && finite(mark.maxScore) && mark.maxScore > 0) {
                score.setText(number(mark.score) + "/" + number(mark.maxScore));
                progress.setProgress((int) Math.max(0, Math.min(1000, mark.score / mark.maxScore * 1000)));
            } else {
                score.setText("—");
                scoreLabel.setText(R.string.marks_score_unavailable);
            }
            if (finite(mark.weightage) && finite(mark.maxWeightage) && mark.maxWeightage > 0) {
                weighted.setText(card.getContext().getString(R.string.marks_weighted_detail,
                        number(mark.weightage), number(mark.maxWeightage)));
                weighted.setVisibility(View.VISIBLE);
            } else weighted.setVisibility(View.GONE);
            if (finite(mark.average)) {
                average.setText(Html.fromHtml(card.getContext().getString(R.string.average, mark.average),
                        Html.FROM_HTML_MODE_LEGACY));
                average.setVisibility(View.VISIBLE);
            } else average.setVisibility(View.GONE);
            if (mark.status != null && !mark.status.trim().isEmpty()) {
                status.setText(Html.fromHtml(card.getContext().getString(R.string.status, mark.status),
                        Html.FROM_HTML_MODE_LEGACY));
                status.setVisibility(View.VISIBLE);
            } else status.setVisibility(View.GONE);
        }

        private static boolean finite(Double value) {
            return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
        }

        private static String number(double value) {
            return new DecimalFormat("0.##").format(value);
        }
    }
}
