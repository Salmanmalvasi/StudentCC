package com.salmanmalvasi.studentcc.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.salmanmalvasi.studentcc.models.AttendanceRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GitHub contribution style attendance heatmap view.
 * Displays classes attended per day in a 7-row calendar grid.
 * Counts Present and On Duty as attended, using dark green for fewer classes and light green for more classes.
 */
public class AttendanceHeatmapView extends View {

    public interface OnDayClickListener {
        void onDayClicked(Calendar date, int attendedCount, int presentCount, int odCount, int totalConducted);
    }

    public static class DayData {
        public Calendar date;
        public int attendedCount = 0; // Present + On Duty
        public int presentCount = 0;
        public int odCount = 0;
        public int absentCount = 0;
        public int totalConducted = 0;

        public DayData(Calendar date) {
            this.date = (Calendar) date.clone();
        }
    }

    // Grid dimension configs in DP
    private static final float CELL_SIZE_DP = 13f;
    private static final float CELL_GAP_DP = 3.5f;
    private static final float CELL_RADIUS_DP = 3f;
    private static final float LEFT_MARGIN_DP = 22f;
    private static final float TOP_MARGIN_DP = 18f;

    // Green color shades (dark green for less classes, lighter vibrant green for more classes)
    private static final int COLOR_EMPTY = 0xFF1E293B; // Dark slate background
    private static final int COLOR_EMPTY_STROKE = 0xFF334155;
    private static final int COLOR_LEVEL_1 = 0xFF14532D; // 1 class: darkest forest green
    private static final int COLOR_LEVEL_2 = 0xFF15803D; // 2 classes: dark emerald green
    private static final int COLOR_LEVEL_3 = 0xFF22C55E; // 3 classes: medium vibrant green
    private static final int COLOR_LEVEL_4 = 0xFF86EFAC; // 4+ classes: brightest light mint green
    private static final int COLOR_FUTURE = 0xFF0F172A; // Muted future days
    private static final int COLOR_LABEL = 0xFF94A3B8; // Slate label text
    private static final int COLOR_SELECTION = 0xFF38BDF8; // Sky blue selection border

    private float cellSize;
    private float cellGap;
    private float cellRadius;
    private float leftMargin;
    private float topMargin;

    private Paint cellPaint;
    private Paint strokePaint;
    private Paint textPaint;
    private Paint selectionPaint;
    private RectF tempRect;

    private Calendar startDate;
    private Calendar endDate;
    private int numWeeks = 16;
    private final Map<String, DayData> dayDataMap = new HashMap<>();
    private final List<AttendanceRecord> records = new ArrayList<>();

    private int selectedCol = -1;
    private int selectedRow = -1;
    private OnDayClickListener onDayClickListener;

    public AttendanceHeatmapView(Context context) {
        super(context);
        init();
    }

    public AttendanceHeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AttendanceHeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        cellSize = CELL_SIZE_DP * dm.density;
        cellGap = CELL_GAP_DP * dm.density;
        cellRadius = CELL_RADIUS_DP * dm.density;
        leftMargin = LEFT_MARGIN_DP * dm.density;
        topMargin = TOP_MARGIN_DP * dm.density;

        cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1f * dm.density);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_LABEL);
        textPaint.setTextSize(9f * dm.scaledDensity);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setColor(COLOR_SELECTION);
        selectionPaint.setStrokeWidth(2f * dm.density);

        tempRect = new RectF();

        // Default: 16 weeks ending today
        endDate = Calendar.getInstance();
        normalizeCalendar(endDate);

        startDate = (Calendar) endDate.clone();
        startDate.add(Calendar.WEEK_OF_YEAR, -15);
        alignToMonday(startDate);
        numWeeks = 16;
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.onDayClickListener = listener;
    }

    public void setAttendanceData(List<AttendanceRecord> attendanceRecords) {
        records.clear();
        dayDataMap.clear();

        if (attendanceRecords != null) {
            records.addAll(attendanceRecords);
        }

        Calendar minCal = null;
        Calendar maxCal = Calendar.getInstance();
        normalizeCalendar(maxCal);

        for (AttendanceRecord record : records) {
            Calendar cal = parseRecordCalendar(record);
            if (cal == null)
                continue;

            if (minCal == null || cal.before(minCal)) {
                minCal = (Calendar) cal.clone();
            }
            if (cal.after(maxCal)) {
                maxCal = (Calendar) cal.clone();
            }

            String key = getDayKey(cal);
            DayData data = dayDataMap.get(key);
            if (data == null) {
                data = new DayData(cal);
                dayDataMap.put(key, data);
            }

            data.totalConducted++;
            String status = record.status != null ? record.status.trim().toLowerCase() : "";
            boolean isPresent = status.contains("present") || status.equals("p");
            boolean isOD = status.contains("on duty") || status.contains("duty") || status.equals("od");

            if (isPresent) {
                data.presentCount++;
                data.attendedCount++; // Count Present and On Duty as same
            } else if (isOD) {
                data.odCount++;
                data.attendedCount++; // Count Present and On Duty as same
            } else if (status.contains("absent") || status.equals("a")) {
                data.absentCount++;
            }
        }

        if (minCal == null) {
            minCal = Calendar.getInstance();
            minCal.add(Calendar.WEEK_OF_YEAR, -15);
        }

        startDate = (Calendar) minCal.clone();
        alignToMonday(startDate);

        endDate = (Calendar) maxCal.clone();
        alignToSunday(endDate);

        long diffDays = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (24L * 3600L * 1000L) + 1;
        numWeeks = Math.max(12, (int) Math.ceil((double) diffDays / 7.0));

        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (leftMargin + numWeeks * (cellSize + cellGap) + cellGap * 2);
        int height = (int) (topMargin + 7 * (cellSize + cellGap) + cellGap * 2);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (startDate == null)
            return;

        Calendar today = Calendar.getInstance();
        normalizeCalendar(today);

        // 1. Draw Month Labels on Top
        Calendar iterMonth = (Calendar) startDate.clone();
        int lastMonth = -1;
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);

        for (int w = 0; w < numWeeks; w++) {
            float x = leftMargin + w * (cellSize + cellGap);
            int m = iterMonth.get(Calendar.MONTH);
            if (m != lastMonth) {
                canvas.drawText(monthFormat.format(iterMonth.getTime()), x, topMargin - 4f, textPaint);
                lastMonth = m;
            }
            iterMonth.add(Calendar.WEEK_OF_YEAR, 1);
        }

        // 2. Draw Day of Week Labels (Mon, Wed, Fri)
        float textYOffset = cellSize * 0.75f;
        canvas.drawText("M", 4f, topMargin + 0 * (cellSize + cellGap) + textYOffset, textPaint);
        canvas.drawText("W", 4f, topMargin + 2 * (cellSize + cellGap) + textYOffset, textPaint);
        canvas.drawText("F", 4f, topMargin + 4 * (cellSize + cellGap) + textYOffset, textPaint);

        // 3. Draw Grid Cells
        Calendar cellDate = (Calendar) startDate.clone();

        for (int w = 0; w < numWeeks; w++) {
            for (int d = 0; d < 7; d++) {
                float left = leftMargin + w * (cellSize + cellGap);
                float top = topMargin + d * (cellSize + cellGap);
                tempRect.set(left, top, left + cellSize, top + cellSize);

                boolean isFuture = cellDate.after(today);
                String key = getDayKey(cellDate);
                DayData data = dayDataMap.get(key);
                int attended = data != null ? data.attendedCount : 0;

                int fillColor;
                if (isFuture) {
                    fillColor = COLOR_FUTURE;
                } else if (attended == 0) {
                    fillColor = COLOR_EMPTY;
                } else if (attended == 1) {
                    fillColor = COLOR_LEVEL_1;
                } else if (attended == 2) {
                    fillColor = COLOR_LEVEL_2;
                } else if (attended == 3) {
                    fillColor = COLOR_LEVEL_3;
                } else {
                    fillColor = COLOR_LEVEL_4; // 4+ classes: brightest light mint green
                }

                cellPaint.setColor(fillColor);
                canvas.drawRoundRect(tempRect, cellRadius, cellRadius, cellPaint);

                // Subtle cell stroke
                if (!isFuture && attended == 0) {
                    strokePaint.setColor(COLOR_EMPTY_STROKE);
                    canvas.drawRoundRect(tempRect, cellRadius, cellRadius, strokePaint);
                }

                // Highlight selected cell
                if (w == selectedCol && d == selectedRow) {
                    canvas.drawRoundRect(tempRect, cellRadius, cellRadius, selectionPaint);
                }

                cellDate.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();

            if (x >= leftMargin && y >= topMargin) {
                int col = (int) ((x - leftMargin) / (cellSize + cellGap));
                int row = (int) ((y - topMargin) / (cellSize + cellGap));

                if (col >= 0 && col < numWeeks && row >= 0 && row < 7) {
                    selectedCol = col;
                    selectedRow = row;
                    invalidate();

                    Calendar clickedDate = (Calendar) startDate.clone();
                    clickedDate.add(Calendar.DAY_OF_YEAR, col * 7 + row);

                    String key = getDayKey(clickedDate);
                    DayData data = dayDataMap.get(key);

                    if (onDayClickListener != null) {
                        int attended = data != null ? data.attendedCount : 0;
                        int present = data != null ? data.presentCount : 0;
                        int od = data != null ? data.odCount : 0;
                        int total = data != null ? data.totalConducted : 0;
                        onDayClickListener.onDayClicked(clickedDate, attended, present, od, total);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private Calendar parseRecordCalendar(AttendanceRecord record) {
        if (record == null)
            return null;

        if (record.date != null && record.date > 0) {
            long millis = record.date > 10000000000L ? record.date : record.date * 1000L;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(millis);
            normalizeCalendar(cal);
            return cal;
        }

        if (record.dateString != null && !record.dateString.trim().isEmpty()) {
            String[] formats = { "yyyy-MM-dd", "dd-MMM-yyyy", "dd-MM-yyyy", "dd/MM/yyyy" };
            for (String fmt : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.ENGLISH);
                    Date parsed = sdf.parse(record.dateString.trim());
                    if (parsed != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(parsed);
                        normalizeCalendar(cal);
                        return cal;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private void normalizeCalendar(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void alignToMonday(Calendar cal) {
        normalizeCalendar(cal);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        // Calendar.MONDAY is 2, SUNDAY is 1
        int daysBack = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_YEAR, -daysBack);
    }

    private void alignToSunday(Calendar cal) {
        normalizeCalendar(cal);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysForward = (dayOfWeek == Calendar.SUNDAY) ? 0 : (Calendar.SATURDAY - dayOfWeek + 1);
        cal.add(Calendar.DAY_OF_YEAR, daysForward);
    }

    private String getDayKey(Calendar cal) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }
}
