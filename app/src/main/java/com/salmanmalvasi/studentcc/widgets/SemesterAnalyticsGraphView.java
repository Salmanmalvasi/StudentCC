package com.salmanmalvasi.studentcc.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.salmanmalvasi.studentcc.models.SemesterSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * State-of-the-Art Academic Analytics Graph View.
 * Displays high-contrast modern rounded telemetry bars for Attendance,
 * a glowing cubic-spline curve with gradient area fill for Marks,
 * 75% minimum criteria guidance, and interactive touch floating tooltip.
 */
public class SemesterAnalyticsGraphView extends View {
    public static final int MODE_COMBINED = 0;
    public static final int MODE_ATTENDANCE = 1;
    public static final int MODE_MARKS = 2;

    private int currentMode = MODE_COMBINED;
    private final List<SemesterSummary> semesterList = new ArrayList<>();

    // Paints
    private Paint bgPaint;
    private Paint borderPaint;
    private Paint gridPaint;
    private Paint targetLinePaint;
    private Paint targetTextPaint;
    private Paint barTrackPaint;
    private Paint barPaint;
    private Paint linePaint;
    private Paint lineAreaPaint;
    private Paint nodeHaloPaint;
    private Paint nodePaint;
    private Paint nodeCenterPaint;
    private Paint labelPaint;
    private Paint valueTextPaint;
    private Paint cursorLinePaint;
    private Paint tooltipBgPaint;
    private Paint tooltipBorderPaint;
    private Paint tooltipTextPaint;

    // Drawing paths & geometries
    private final Path trendPath = new Path();
    private final Path areaPath = new Path();
    private final RectF bgRect = new RectF();
    private final RectF barTrackRect = new RectF();
    private final RectF barRect = new RectF();
    private final RectF tooltipRect = new RectF();

    private float animProgress = 0f;
    private int selectedIndex = -1;
    private boolean isDarkMode = true;

    public SemesterAnalyticsGraphView(Context context) {
        super(context);
        init();
    }

    public SemesterAnalyticsGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SemesterAnalyticsGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);

        isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface tfBold = Typeface.create("sans-serif", Typeface.BOLD);

        // Chart Card Background & Border
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(isDarkMode ? Color.parseColor("#090E17") : Color.parseColor("#F8FAFC"));

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(isDarkMode ? Color.parseColor("#1E293B") : Color.parseColor("#E2E8F0"));
        borderPaint.setStrokeWidth(dpToPx(1f));

        // Horizontal Gridlines
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(isDarkMode ? Color.parseColor("#152238") : Color.parseColor("#E2E8F0"));
        gridPaint.setStrokeWidth(dpToPx(1f));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(3), dpToPx(3)}, 0));

        // 75% Safe Threshold Line
        targetLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetLinePaint.setColor(Color.parseColor("#F43F5E"));
        targetLinePaint.setStrokeWidth(dpToPx(1.5f));
        targetLinePaint.setStyle(Paint.Style.STROKE);
        targetLinePaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(4), dpToPx(3)}, 0));

        targetTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetTextPaint.setColor(Color.parseColor("#F43F5E"));
        targetTextPaint.setTextSize(spToPx(10f));
        targetTextPaint.setTypeface(tfBold);

        // Bar Track (Background Cylinder)
        barTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barTrackPaint.setColor(isDarkMode ? Color.parseColor("#131D2D") : Color.parseColor("#EDF2F7"));
        barTrackPaint.setStyle(Paint.Style.FILL);

        // Attendance Bar Paint
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);

        // Marks Spline Line
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(dpToPx(3.2f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // Spline Area Glow
        lineAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineAreaPaint.setStyle(Paint.Style.FILL);

        // Nodes
        nodeHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodeHaloPaint.setColor(Color.parseColor("#33A855F7"));
        nodeHaloPaint.setStyle(Paint.Style.FILL);

        nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodePaint.setColor(Color.parseColor("#A855F7"));
        nodePaint.setStyle(Paint.Style.FILL);

        nodeCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodeCenterPaint.setColor(Color.WHITE);
        nodeCenterPaint.setStyle(Paint.Style.FILL);

        // Axis Labels
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B"));
        labelPaint.setTextSize(spToPx(10.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(tfMedium);

        // Text values above bars
        valueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valueTextPaint.setColor(isDarkMode ? Color.parseColor("#F8FAFC") : Color.parseColor("#0F172A"));
        valueTextPaint.setTextSize(spToPx(10.5f));
        valueTextPaint.setTextAlign(Paint.Align.CENTER);
        valueTextPaint.setTypeface(tfBold);

        // Interactive Cursor Line
        cursorLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorLinePaint.setColor(Color.parseColor("#38BDF8"));
        cursorLinePaint.setStrokeWidth(dpToPx(1.2f));
        cursorLinePaint.setStyle(Paint.Style.STROKE);
        cursorLinePaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(2), dpToPx(2)}, 0));

        // Tooltip
        tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipBgPaint.setColor(isDarkMode ? Color.parseColor("#F20F172A") : Color.parseColor("#F2FFFFFF"));
        tooltipBgPaint.setStyle(Paint.Style.FILL);

        tooltipBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipBorderPaint.setColor(Color.parseColor("#38BDF8"));
        tooltipBorderPaint.setStrokeWidth(dpToPx(1f));
        tooltipBorderPaint.setStyle(Paint.Style.STROKE);

        tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipTextPaint.setColor(isDarkMode ? Color.WHITE : Color.parseColor("#0F172A"));
        tooltipTextPaint.setTextSize(spToPx(11f));
        tooltipTextPaint.setTypeface(tfBold);
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<SemesterSummary> data) {
        semesterList.clear();
        if (data != null) {
            semesterList.addAll(data);
        }
        selectedIndex = -1;
        startAnimation();
    }

    public void setMode(int mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            startAnimation();
        }
    }

    private void startAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        // 1. Draw Card Surface & Rounded Outline
        float radius = dpToPx(18f);
        bgRect.set(0, 0, width, height);
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
        canvas.drawRoundRect(bgRect, radius, radius, borderPaint);

        if (semesterList.isEmpty()) {
            Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            emptyPaint.setColor(isDarkMode ? Color.parseColor("#64748B") : Color.parseColor("#94A3B8"));
            emptyPaint.setTextSize(spToPx(13f));
            emptyPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No semester data loaded yet", width / 2f, height / 2f, emptyPaint);
            return;
        }

        float paddingLeft = dpToPx(20f);
        float paddingRight = dpToPx(20f);
        float paddingTop = dpToPx(38f);
        float paddingBottom = dpToPx(36f);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        // 2. Draw Horizontal Gridlines (0%, 25%, 50%, 75%, 100%)
        for (int p = 0; p <= 100; p += 25) {
            float y = paddingTop + chartHeight * (1f - (p / 100f));
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);
        }

        // 3. Draw 75% Safe Threshold Line
        if (currentMode != MODE_MARKS) {
            float y75 = paddingTop + chartHeight * (1f - 0.75f);
            canvas.drawLine(paddingLeft, y75, width - paddingRight, y75, targetLinePaint);
            canvas.drawText("75% Safe", width - paddingRight - dpToPx(2f), y75 - dpToPx(4f), targetTextPaint);
        }

        int count = semesterList.size();
        float stepX = chartWidth / count;
        float barWidth = Math.min(stepX * 0.44f, dpToPx(30f));
        float barCornerRadius = barWidth / 2f; // Fully rounded modern pill

        trendPath.reset();
        areaPath.reset();
        boolean hasPathStarted = false;
        float[] nodePointsX = new float[count];
        float[] nodePointsY = new float[count];

        // 4. Draw Attendance Bars & Tracks
        for (int i = 0; i < count; i++) {
            SemesterSummary sem = semesterList.get(i);
            float centerX = paddingLeft + (i * stepX) + (stepX / 2f);

            float att = Math.max(0f, Math.min(100f, sem.attendancePercentage));
            float animatedAtt = (att * animProgress);
            float barHeight = chartHeight * (animatedAtt / 100f);
            float topY = paddingTop + chartHeight - barHeight;
            float bottomY = paddingTop + chartHeight;

            nodePointsX[i] = centerX;

            if (currentMode != MODE_MARKS) {
                // Background Track cylinder (shows 100% capacity)
                barTrackRect.set(centerX - barWidth / 2f, paddingTop, centerX + barWidth / 2f, bottomY);
                canvas.drawRoundRect(barTrackRect, barCornerRadius, barCornerRadius, barTrackPaint);

                // Active Bar
                if (barHeight > 0) {
                    barRect.set(centerX - barWidth / 2f, topY, centerX + barWidth / 2f, bottomY);

                    // Dynamic Gradient Palette
                    int startColor;
                    int endColor;
                    if (att >= 85f) {
                        startColor = Color.parseColor("#10B981"); // Glowing Emerald
                        endColor = Color.parseColor("#047857");
                    } else if (att >= 75f) {
                        startColor = Color.parseColor("#38BDF8"); // Electric Sky/Cyan
                        endColor = Color.parseColor("#1D4ED8");
                    } else {
                        startColor = Color.parseColor("#F43F5E"); // Alert Rose/Coral
                        endColor = Color.parseColor("#9F1239");
                    }

                    if (selectedIndex == i) {
                        startColor = Color.parseColor("#F59E0B"); // Glowing Amber if selected
                        endColor = Color.parseColor("#D97706");
                    }

                    barPaint.setShader(new LinearGradient(
                            barRect.left, barRect.top,
                            barRect.left, barRect.bottom,
                            startColor, endColor, Shader.TileMode.CLAMP
                    ));
                    canvas.drawRoundRect(barRect, barCornerRadius, barCornerRadius, barPaint);

                    // Percentage Text above bar
                    if (animProgress > 0.5f && barHeight > dpToPx(14f)) {
                        String pctText = Math.round(att) + "%";
                        canvas.drawText(pctText, centerX, topY - dpToPx(5f), valueTextPaint);
                    }
                }
            }

            // Calculate Marks Coordinates
            float marks = Math.max(0f, Math.min(100f, sem.averageMarksPercentage));
            float animatedMarks = (marks * animProgress);
            float marksY = paddingTop + chartHeight * (1f - (animatedMarks / 100f));
            nodePointsY[i] = marksY;

            if (!hasPathStarted) {
                trendPath.moveTo(centerX, marksY);
                areaPath.moveTo(centerX, bottomY);
                areaPath.lineTo(centerX, marksY);
                hasPathStarted = true;
            } else {
                float prevX = nodePointsX[i - 1];
                float prevY = nodePointsY[i - 1];
                float cX = (prevX + centerX) / 2f;
                trendPath.cubicTo(cX, prevY, cX, marksY, centerX, marksY);
                areaPath.cubicTo(cX, prevY, cX, marksY, centerX, marksY);
            }

            if (i == count - 1) {
                areaPath.lineTo(centerX, bottomY);
                areaPath.close();
            }

            // X-Axis Semester label
            String semShort = getAbbreviatedSemesterName(sem.semesterName, i);
            if (selectedIndex == i) {
                labelPaint.setColor(Color.parseColor("#38BDF8"));
                labelPaint.setFakeBoldText(true);
            } else {
                labelPaint.setColor(isDarkMode ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B"));
                labelPaint.setFakeBoldText(false);
            }
            canvas.drawText(semShort, centerX, height - dpToPx(12f), labelPaint);
        }

        boolean hasAnyMarks = false;
        for (SemesterSummary s : semesterList) {
            if (s.averageMarksPercentage > 0) {
                hasAnyMarks = true;
                break;
            }
        }

        // 5. Draw Marks Spline Line & Glowing Area Fill
        if (currentMode != MODE_ATTENDANCE && hasPathStarted && hasAnyMarks) {
            // Area Fill with soft vertical gradient
            lineAreaPaint.setShader(new LinearGradient(
                    0, paddingTop, 0, paddingTop + chartHeight,
                    isDarkMode ? Color.parseColor("#388B5CF6") : Color.parseColor("#208B5CF6"),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(areaPath, lineAreaPaint);

            // Curve Line
            linePaint.setShader(new LinearGradient(
                    paddingLeft, 0, width - paddingRight, 0,
                    Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899"),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(trendPath, linePaint);

            // Nodes
            for (int i = 0; i < count; i++) {
                SemesterSummary sem = semesterList.get(i);
                if (sem.averageMarksPercentage <= 0) continue;

                float nx = nodePointsX[i];
                float ny = nodePointsY[i];

                canvas.drawCircle(nx, ny, dpToPx(7f), nodeHaloPaint);
                canvas.drawCircle(nx, ny, dpToPx(4.5f), nodePaint);
                canvas.drawCircle(nx, ny, dpToPx(2.2f), nodeCenterPaint);

                if (currentMode == MODE_MARKS && animProgress > 0.5f) {
                    String mText = Math.round(sem.averageMarksPercentage) + "%";
                    canvas.drawText(mText, nx, ny - dpToPx(8f), valueTextPaint);
                }
            }
        }

        // 6. Draw Selected Column Cursor & Tooltip
        if (selectedIndex >= 0 && selectedIndex < count) {
            SemesterSummary selectedSem = semesterList.get(selectedIndex);
            float cx = nodePointsX[selectedIndex];

            // Vertical Guide line
            canvas.drawLine(cx, paddingTop, cx, paddingTop + chartHeight, cursorLinePaint);

            // Tooltip Content
            String marksStr = selectedSem.averageMarksPercentage > 0 ?
                    Math.round(selectedSem.averageMarksPercentage) + "%" : "In Progress";
            String infoText;
            if (selectedSem.totalClasses == 0) {
                String gpaStr = (selectedSem.gpa != null && !selectedSem.gpa.isEmpty()) ?
                        "GPA: " + selectedSem.gpa : "Completed";
                infoText = selectedSem.semesterName + "  •  " + gpaStr +
                        (selectedSem.averageMarksPercentage > 0 ? "  •  Score: " + marksStr : "");
            } else {
                infoText = selectedSem.semesterName + "  •  Att: " + Math.round(selectedSem.attendancePercentage) +
                        "%  •  Marks: " + marksStr;
            }

            float textWidth = tooltipTextPaint.measureText(infoText);
            float tooltipW = textWidth + dpToPx(24f);
            float tooltipH = dpToPx(30f);
            float tooltipX = Math.max(dpToPx(10f), Math.min(width - dpToPx(10f) - tooltipW, cx - (tooltipW / 2f)));
            float tooltipY = dpToPx(7f);

            tooltipRect.set(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH);
            canvas.drawRoundRect(tooltipRect, dpToPx(15f), dpToPx(15f), tooltipBgPaint);
            canvas.drawRoundRect(tooltipRect, dpToPx(15f), dpToPx(15f), tooltipBorderPaint);

            Paint.FontMetrics fm = tooltipTextPaint.getFontMetrics();
            float textBaseline = tooltipY + (tooltipH / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText(infoText, tooltipX + (tooltipW / 2f), textBaseline, tooltipTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int count = semesterList.size();
            if (count == 0) return false;

            float paddingLeft = dpToPx(20f);
            float paddingRight = dpToPx(20f);
            float chartWidth = getWidth() - paddingLeft - paddingRight;
            float stepX = chartWidth / count;

            float touchX = event.getX() - paddingLeft;
            int newIndex = (int) (touchX / stepX);
            if (newIndex >= 0 && newIndex < count) {
                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    invalidate();
                }
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Keep selection visible for user inspection
            return true;
        }
        return super.onTouchEvent(event);
    }

    private String getAbbreviatedSemesterName(String name, int index) {
        if (name == null || name.trim().isEmpty()) return "";
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase(Locale.ENGLISH);
        String season = "";
        if (lower.contains("fall")) season = "Fall";
        else if (lower.contains("winter")) season = "Win";
        else if (lower.contains("summer")) season = "Sum";
        else if (lower.contains("spring")) season = "Spr";

        String year = "";
        for (int i = 0; i < trimmed.length() - 1; i++) {
            if (Character.isDigit(trimmed.charAt(i)) && Character.isDigit(trimmed.charAt(i + 1))) {
                if (i + 3 < trimmed.length() && Character.isDigit(trimmed.charAt(i + 2)) && Character.isDigit(trimmed.charAt(i + 3))) {
                    year = "'" + trimmed.substring(i + 2, i + 4);
                    break;
                }
            }
        }

        if (!season.isEmpty()) {
            return season + (!year.isEmpty() ? " " + year : "");
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length > 0) {
            String p0 = parts[0];
            if (p0.length() > 6) p0 = p0.substring(0, 6);
            return p0 + (!year.isEmpty() ? " " + year : "");
        }
        return trimmed;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
