package tk.therealsuji.vtopchennai.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import tk.therealsuji.vtopchennai.R;

public class CircularProgressDrawable extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private RectF rectF;
    private int progress = 0;
    private int max = 100;
    private float strokeWidth = 12f;
    private int backgroundColor;
    private int progressColor;

    public CircularProgressDrawable(Context context) {
        super(context);
        init();
    }

    public CircularProgressDrawable(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(3f); // Thicker border for better visibility
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorPrimary)); // Use theme primary color

        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(3f); // Thicker border for better visibility
        progressPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorPrimary)); // Use theme primary color
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        rectF = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(centerX, centerY) - strokeWidth / 2f;

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        // Draw background circle
        canvas.drawArc(rectF, 0, 360, false, backgroundPaint);

        // Draw progress arc - ensure we don't draw if progress is 0
        if (progress > 0) {
            float sweepAngle = (float) progress / max * 360f;
            android.util.Log.d("CircularProgress", "Drawing progress: " + progress + ", Max: " + max + ", SweepAngle: " + sweepAngle);
            canvas.drawArc(rectF, -90, sweepAngle, false, progressPaint);
        }
    }

    public void setProgress(int progress) {
        // Ensure progress is within valid bounds
        this.progress = Math.max(0, Math.min(progress, max));
        android.util.Log.d("CircularProgress", "setProgress called with: " + progress + ", bounded to: " + this.progress);
        invalidate();
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }
}
