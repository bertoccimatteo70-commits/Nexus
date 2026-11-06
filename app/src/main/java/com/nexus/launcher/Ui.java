package com.nexus.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable rounded(int color, float radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusPx);
        if (strokeWidthPx > 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable verticalGradient(int[] colors) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        drawable.setShape(GradientDrawable.RECTANGLE);
        return drawable;
    }
}
