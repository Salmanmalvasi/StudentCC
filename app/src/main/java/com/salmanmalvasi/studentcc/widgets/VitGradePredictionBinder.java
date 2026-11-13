package com.salmanmalvasi.studentcc.widgets;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.VitGradePredictor;

import java.text.DecimalFormat;
import java.util.Map;

/** Shows a conditional VIT estimate using only the currently displayed statistics pool. */
public final class VitGradePredictionBinder {
    private final View root;
    private final TextView grade;
    private final TextView assumptions;
    private final MaterialButton ranges;
    private boolean absolute;
    private boolean embedded;
    private boolean projectedPassFailed;
    private Double personalTotal;
    private int marksBasis;
    private double mean;
    private double sd;
    private int count;
    private String source;
    private VitGradePredictor.PassStatus passStatus = VitGradePredictor.PassStatus.UNKNOWN;
    private VitGradePredictor.Prediction prediction;
    private AlertDialog rangesDialog;

    public VitGradePredictionBinder(View root, int gradeId, int assumptionsId, int rangesId) {
        this.root = root;
        grade = root.findViewById(gradeId);
        assumptions = root.findViewById(assumptionsId);
        ranges = root.findViewById(rangesId);
        ranges.setOnClickListener(v -> showRanges());
    }

    public void bind(Double total, int basis, double mean, double sd, int count, String source,
                     VitGradePredictor.PassStatus passStatus, boolean embedded) {
        personalTotal = total;
        marksBasis = basis;
        this.mean = mean;
        this.sd = sd;
        this.count = count;
        this.source = source;
        this.passStatus = passStatus;
        this.embedded = embedded;
        render();
    }

    private void render() {
        projectedPassFailed = embedded && marksBasis == 60 && personalTotal != null
                && Math.ceil(personalTotal * 100.0 / marksBasis) < 50;
        VitGradePredictor.PassStatus calculationPass = projectedPassFailed ? VitGradePredictor.PassStatus.FAILED : passStatus;
        prediction = absolute ? VitGradePredictor.absolute(personalTotal, marksBasis, calculationPass)
                : VitGradePredictor.relative(personalTotal, marksBasis, mean, sd, count, calculationPass);
        projectedPassFailed |= prediction.projectedFatBelowMinimum;
        root.setVisibility(View.VISIBLE);
        if (!prediction.isAvailable()) {
            grade.setText(personalTotal == null ? R.string.vit_grade_missing_total : R.string.vit_grade_insufficient_sample);
            grade.setTextSize(13);
            assumptions.setVisibility(View.GONE);
            // Absolute grading needs no sample spread, so allow choosing it for a known small class.
            ranges.setVisibility(personalTotal == null ? View.GONE : View.VISIBLE);
            return;
        }
        grade.setTextSize(16);
        String scheme = root.getContext().getString(absolute ? R.string.vit_grade_absolute : R.string.vit_grade_relative);
        grade.setText(root.getContext().getString(prediction.projected ? R.string.vit_grade_projected
                : R.string.vit_grade_predicted, prediction.grade, scheme));
        String note;
        if (passStatus == VitGradePredictor.PassStatus.FAILED && !prediction.projected) {
            note = root.getContext().getString(R.string.vit_grade_pass_failed);
        } else if (projectedPassFailed || passStatus == VitGradePredictor.PassStatus.FAILED) {
            note = root.getContext().getString(R.string.vit_grade_projected_pass_failed);
        } else {
            note = root.getContext().getString(prediction.projected ? R.string.vit_grade_projected_note
                    : R.string.vit_grade_final_note);
            note += "\n" + root.getContext().getString(absolute ? R.string.vit_grade_absolute_assumption
                    : R.string.vit_grade_relative_assumption);
            if (!prediction.projected && prediction.assumesPassRequirements) {
                note += "\n" + root.getContext().getString(R.string.vit_grade_pass_unknown);
            }
        }
        assumptions.setText(note);
        assumptions.setVisibility(View.VISIBLE);
        ranges.setVisibility(View.VISIBLE);
    }

    private void showRanges() {
        if (prediction == null) return;
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, Integer> boundary : prediction.lowerBoundaries.entrySet()) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(root.getContext().getString(R.string.vit_grade_range_line, boundary.getKey(), boundary.getValue()));
        }
        String content;
        if (!prediction.isAvailable()) {
            content = root.getContext().getString(R.string.vit_grade_insufficient_sample);
        } else if (passStatus == VitGradePredictor.PassStatus.FAILED && !prediction.projected) {
            content = root.getContext().getString(R.string.vit_grade_pass_failed);
        } else if (projectedPassFailed || passStatus == VitGradePredictor.PassStatus.FAILED) {
            content = root.getContext().getString(R.string.vit_grade_projected_pass_failed);
        } else {
            lines.append('\n').append(root.getContext().getString(R.string.vit_grade_range_failure));
            String total = root.getContext().getString(prediction.projected ? R.string.vit_grade_projection_basis
                    : R.string.vit_grade_final_basis, new DecimalFormat("0.##").format(prediction.totalOutOf100));
            content = root.getContext().getString(R.string.vit_grade_ranges_intro, source, count, total, lines);
        }
        content += "\n\n" + root.getContext().getString(R.string.vit_grade_relative_rules);
        rangesDialog = new MaterialAlertDialogBuilder(root.getContext())
                .setTitle(root.getContext().getString(R.string.vit_grade_ranges_title, source))
                .setMessage(content)
                .setPositiveButton(R.string.got_it, null)
                .setNegativeButton(absolute ? R.string.vit_grade_use_relative : R.string.vit_grade_use_absolute,
                        (dialog, which) -> { absolute = !absolute; render(); })
                .setNeutralButton(R.string.vit_grade_rules_source, (dialog, which) -> root.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse("https://chennai.vit.ac.in/files/Academic-Regulations-5.0.pdf"))))
                .show();
    }

    public void clear() {
        if (rangesDialog != null) rangesDialog.dismiss();
        rangesDialog = null;
        root.setVisibility(View.GONE);
        prediction = null;
    }

    public void reset() {
        clear();
        absolute = false;
    }
}
