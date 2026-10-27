package com.salmanmalvasi.studentcc.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.salmanmalvasi.studentcc.R;

public class ThemeManager {
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";

    public static final String THEME_DEFAULT = "Default";
    public static final String THEME_OCEAN = "Ocean";
    public static final String THEME_SUNSET = "Sunset";
    public static final String THEME_MIDNIGHT = "Midnight";
    public static final String THEME_FOREST = "Forest";
    public static final String THEME_PURPLE = "Purple";
    public static final String THEME_MATERIAL = "Material";

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_MIDNIGHT);

        if (THEME_DEFAULT.equals(theme)) {
            theme = THEME_MIDNIGHT;
        }

        switch (theme) {
            case THEME_OCEAN:
                activity.setTheme(R.style.Theme_VTOP_Blue);
                break;
            case THEME_SUNSET:
                activity.setTheme(R.style.Theme_VTOP_Red);
                break;
            case THEME_MIDNIGHT:
                activity.setTheme(R.style.Theme_VTOP_Black);
                break;
            case THEME_FOREST:
                activity.setTheme(R.style.Theme_VTOP_Green);
                break;
            case THEME_PURPLE:
                activity.setTheme(R.style.Theme_VTOP_Purple);
                break;
            case THEME_MATERIAL:
                // Use Material You theme with dynamic colors
                activity.setTheme(R.style.Theme_VTOP);
                // Enable dynamic colors for Material theme
                try {
                    com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(activity);
                } catch (Exception e) {
                    // Fallback if dynamic colors not available
                    activity.setTheme(R.style.Theme_VTOP);
                }
                break;
            default:
                activity.setTheme(R.style.Theme_VTOP); // Default
                break;
        }
    }

    public static void saveTheme(Context context, String themeName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, themeName).apply();
    }

    public static String getCurrentTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    String theme = prefs.getString(KEY_THEME, THEME_MIDNIGHT);
    return THEME_DEFAULT.equals(theme) ? THEME_MIDNIGHT : theme;
    }
}
