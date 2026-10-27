package com.salmanmalvasi.studentcc.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.animations.AlphaAnimation;
import com.salmanmalvasi.studentcc.animations.ResizeAnimation;

public class PerformanceCard extends LinearLayout {
    AppCompatTextView title, scoreText, subtitleText;
    Context context;
    ProgressBar scoreProgress;

    JSONObject defaultValues;

    public PerformanceCard(Context context) {
        super(context);

        this.context = context;
        this.initialize();
    }

    public PerformanceCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        this.title = new AppCompatTextView(context);
        this.scoreText = new AppCompatTextView(context);
        this.subtitleText = new AppCompatTextView(context);
        this.scoreProgress = new ProgressBar(context, null, 0, R.style.Widget_VTOP_CircularProgressBar);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PerformanceCard, 0, 0);

        try {
            this.setTitle(a.getString(R.styleable.PerformanceCard_title));
            this.setScore(
                    (double) a.getFloat(R.styleable.PerformanceCard_score, 0),
                    (double) a.getFloat(R.styleable.PerformanceCard_total, 100)
            );
        } finally {
            a.recycle();
        }

        this.context = context;
        this.initialize();
    }

    private void initialize() {
        float pixelDensity = this.context.getResources().getDisplayMetrics().density;
        this.defaultValues = new JSONObject();

        TypedValue colorPrimary = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorPrimary, colorPrimary, true);

        LayoutParams titleParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(
                0,
                0,
                0,
                (int) (10 * pixelDensity)
        );
        this.title.setLayoutParams(titleParams);
        this.title.setTextColor(colorPrimary.data);
        this.title.setTextSize(15);

        this.addView(this.title);

        /*
            This empty view is used to fill blank space
         */
        View emptyView = new RelativeLayout(this.context);
        LayoutParams outerContainerParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                0,
                1
        );
        emptyView.setLayoutParams(outerContainerParams);

        this.addView(emptyView);

        RelativeLayout container = new RelativeLayout(this.context);
        LayoutParams containerParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        container.setLayoutParams(containerParams);

        RelativeLayout.LayoutParams scoreProgressParams = new RelativeLayout.LayoutParams(
                (int) (100 * pixelDensity),
                (int) (100 * pixelDensity)
        );
        this.scoreProgress.setIndeterminateDrawable(ContextCompat.getDrawable(this.getContext(), R.drawable.background_circular_progress_indeterminate));
        this.scoreProgress.setLayoutParams(scoreProgressParams);

        RelativeLayout.LayoutParams scoreTextParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        scoreTextParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        this.scoreText.setLayoutParams(scoreTextParams);
        this.scoreText.setTextColor(colorPrimary.data);
        this.scoreText.setTextSize(17);
        this.scoreText.setTypeface(this.scoreText.getTypeface(), Typeface.BOLD);

        container.addView(this.scoreProgress);
        container.addView(this.scoreText);
        this.addView(container);

        // Add subtitle text below the circular progress
        LayoutParams subtitleParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, (int) (10 * pixelDensity), 0, 0);
        this.subtitleText.setLayoutParams(subtitleParams);
        this.subtitleText.setTextColor(colorPrimary.data);
        this.subtitleText.setTextSize(12);
        this.subtitleText.setVisibility(GONE); // Initially hidden
        this.addView(this.subtitleText);

        this.setBackground(ContextCompat.getDrawable(this.context, R.drawable.background_card));
        this.setOrientation(VERTICAL);
        this.setPadding(
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity)
        );
    }

    public void setTitle(String title) {
        this.title.setText(title);
    }

    public void setSubtitle(String subtitle) {
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            this.subtitleText.setText(subtitle);
            this.subtitleText.setVisibility(VISIBLE);
        } else {
            this.subtitleText.setVisibility(GONE);
        }
    }

    public void hideScoreDisplay() {
        // Hide the circular progress and score text, but keep subtitle visible
        this.scoreProgress.setVisibility(GONE);
        this.scoreText.setVisibility(GONE);
    }

    public void showScoreDisplay() {
        // Show the circular progress and score text
        this.scoreProgress.setVisibility(VISIBLE);
        this.scoreText.setVisibility(VISIBLE);
    }

    /**
     * Display relative performance in a two-line format:
     * Line 1: Large number (e.g., "-1.2")
     * Line 2: "weightage from average"
     */
    public void setRelativePerformance(String relativeValue) {
        if (relativeValue != null && !relativeValue.trim().isEmpty()) {
            // Hide the circular progress and percentage
            this.hideScoreDisplay();

            // Use the scoreText area to display the large number
            this.scoreText.setText(relativeValue);
            this.scoreText.setVisibility(VISIBLE);

            // Use subtitle for the description
            this.subtitleText.setText("weightage from average");
            this.subtitleText.setVisibility(VISIBLE);
        } else {
            this.hide();
        }
    }

    public void setScore(Double score, Double max) {
        score = Math.ceil(score);
        max = Math.ceil(max);

        this.scoreProgress.setProgress(score.intValue(), true);
        this.scoreProgress.setSecondaryProgress(max.intValue());

        String scoreText = (max >= 100) ? score.intValue() + "%" : score.intValue() + "/" + max.intValue();
        this.scoreText.setText(scoreText);
    }

    public void setIndeterminate(boolean indeterminate) {
        this.scoreProgress.setIndeterminate(indeterminate);

        if (indeterminate) {
            this.scoreText.setVisibility(INVISIBLE);
            this.subtitleText.setVisibility(GONE);
        } else {
            this.scoreText.setVisibility(VISIBLE);
            // Don't automatically show subtitle, let setSubtitle control it
        }
    }

    public void show() {
        this.clearAnimation();
        this.post(() -> {
            try {
                LinearLayout card = this;
                int cardWidth = this.defaultValues.getInt("cardWidth");

                ResizeAnimation expandAnimation = new ResizeAnimation(card, ResizeAnimation.DIRECTION_X, cardWidth, this.defaultValues);
                AlphaAnimation alphaAnimation = new AlphaAnimation(card, 1);

                expandAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        card.startAnimation(alphaAnimation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });

                card.setVisibility(VISIBLE);
                card.startAnimation(expandAnimation);
            } catch (Exception ignored) {
            }
        });
    }

    public void hide() {
        this.clearAnimation();
        this.post(() -> {
            try {
                LinearLayout card = this;

                if (!this.defaultValues.has("cardWidth")) {
                    LayoutParams layoutParams = (LayoutParams) card.getLayoutParams();

                    this.defaultValues.put("cardWidth", card.getMeasuredWidth());
                    this.defaultValues.put("marginStart", layoutParams.getMarginStart());
                    this.defaultValues.put("marginEnd", layoutParams.getMarginEnd());
                }

                ResizeAnimation compressAnimation = new ResizeAnimation(card, ResizeAnimation.DIRECTION_X, 0, this.defaultValues);
                AlphaAnimation alphaAnimation = new AlphaAnimation(card, 0);

                alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        card.setVisibility(INVISIBLE);
                        card.startAnimation(compressAnimation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });

                card.startAnimation(alphaAnimation);
            } catch (Exception ignored) {
            }

        });
    }
}
