package com.salmanmalvasi.studentcc.widgets;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Keep metric values readable when a course card is narrow or text is enlarged. */
final class StatisticsMetricLayout {
    private StatisticsMetricLayout() { }

    static void adapt(View root, int metricsId) {
        float fontScale = root.getResources().getConfiguration().fontScale;
        int widthDp = root.getResources().getConfiguration().screenWidthDp;
        if (fontScale <= 1.3f && widthDp >= 360) return;
        LinearLayout metrics = root.findViewById(metricsId);
        metrics.setOrientation(LinearLayout.VERTICAL);
        int gap = Math.round(8 * root.getResources().getDisplayMetrics().density);
        for (int i = 0; i < metrics.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) metrics.getChildAt(i);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, i == 0 ? 0 : gap / 2, 0, 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            label.setMarginStart(gap);
            row.getChildAt(1).setLayoutParams(label);
        }
    }
}
