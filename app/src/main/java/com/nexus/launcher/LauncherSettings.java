package com.nexus.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted launcher preferences: Nexus / Nexus Lite mode, theme, wallpaper and dock contents.
 */
public class LauncherSettings {

    public static final int COLOR_ACCENT = 0xFF7C4DFF;

    public static final int BG_DEFAULT = 0;
    public static final int BG_IMAGE = 1;
    public static final int BG_SYSTEM = 2;

    private static final String PREFS_NAME = "nexus_launcher_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_LAUNCHER_MODE = "launcher_mode";
    private static final String KEY_BG_MODE = "bg_mode";
    private static final String KEY_BG_PATH = "bg_path";
    private static final String KEY_DOCK = "dock_packages";
    private static final String KEY_FIRST_LAUNCH = "first_launch_done";
    private static final String KEY_COLUMNS = "home_columns";
    private static final String KEY_ICON_SIZE = "icon_size_dp";
    private static final String KEY_LABELS = "show_labels";
    private static final String KEY_WIDGET_HOST = "widget_host_id";

    public enum ThemeMode {
        SYSTEM,
        LIGHT,
        DARK
    }

    public enum LauncherMode {
        LITE,
        FULL
    }

    private final SharedPreferences prefs;

    public LauncherSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- theme

    public ThemeMode getThemeMode() {
        String value = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name());
        try {
            return ThemeMode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ThemeMode.SYSTEM;
        }
    }

    public void setThemeMode(ThemeMode mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name()).apply();
    }

    public boolean isDark(Context context) {
        ThemeMode mode = getThemeMode();
        if (mode == ThemeMode.DARK) {
            return true;
        }
        if (mode == ThemeMode.LIGHT) {
            return false;
        }
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public int surfaceColor(Context context) {
        return isDark(context) ? 0xF21A1A22 : 0xF2FFFFFF;
    }

    public int textColor(Context context) {
        return isDark(context) ? 0xFFF2F2F7 : 0xFF14141A;
    }

    public int secondaryTextColor(Context context) {
        return isDark(context) ? 0xFF9C9CAB : 0xFF63636F;
    }

    public int searchBackground(Context context) {
        return isDark(context) ? 0xFF23232E : 0xFFEDEDF3;
    }

    public int rowBackground(Context context) {
        return isDark(context) ? 0x14FFFFFF : 0x0D000000;
    }

    public int scrimColor(Context context) {
        return isDark(context) ? 0x99000000 : 0x66000000;
    }

    // -------------------------------------------------------------- launcher

    public LauncherMode getLauncherMode() {
        String value = prefs.getString(KEY_LAUNCHER_MODE, LauncherMode.FULL.name());
        try {
            return LauncherMode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return LauncherMode.FULL;
        }
    }

    public void setLauncherMode(LauncherMode mode) {
        prefs.edit().putString(KEY_LAUNCHER_MODE, mode.name()).apply();
    }

    public boolean isLite() {
        return getLauncherMode() == LauncherMode.LITE;
    }

    // ------------------------------------------------------------- wallpaper

    public int getBackgroundMode() {
        return prefs.getInt(KEY_BG_MODE, BG_DEFAULT);
    }

    public void setBackgroundMode(int mode) {
        prefs.edit().putInt(KEY_BG_MODE, mode).apply();
    }

    public String getBackgroundPath() {
        return prefs.getString(KEY_BG_PATH, null);
    }

    public void setBackgroundPath(String path) {
        if (path == null) {
            prefs.edit().remove(KEY_BG_PATH).apply();
        } else {
            prefs.edit().putString(KEY_BG_PATH, path).apply();
        }
    }

    // ------------------------------------------------------------------ dock

    public List<String> getDockPackages() {
        List<String> result = new ArrayList<>();
        String raw = prefs.getString(KEY_DOCK, "");
        if (raw != null && !raw.isEmpty()) {
            for (String piece : raw.split(",")) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public void setDockPackages(List<String> packages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(packages.get(i));
        }
        prefs.edit().putString(KEY_DOCK, builder.toString()).apply();
    }

    public void resetDock() {
        prefs.edit().remove(KEY_DOCK).apply();
    }

    // ----------------------------------------------------------- first start

    public boolean isFirstLaunchDone() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, false);
    }

    public void setFirstLaunchDone(boolean done) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, done).apply();
    }

    // ------------------------------------------------------- home appearance

    /** Columns of the home grid; 0 means automatic based on the screen width. */
    public int getColumns(Context context) {
        int saved = prefs.getInt(KEY_COLUMNS, 0);
        if (saved >= 3 && saved <= 7) {
            return saved;
        }
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) {
            widthDp = 360;
        }
        if (widthDp >= 600) {
            return 6;
        }
        if (widthDp >= 400) {
            return 5;
        }
        return 4;
    }

    public void setColumns(int columns) {
        if (columns <= 0) {
            prefs.edit().remove(KEY_COLUMNS).apply();
        } else {
            prefs.edit().putInt(KEY_COLUMNS, columns).apply();
        }
    }

    public int getIconSizeDp() {
        int value = prefs.getInt(KEY_ICON_SIZE, 52);
        return Math.max(38, Math.min(72, value));
    }

    public void setIconSizeDp(int sizeDp) {
        prefs.edit().putInt(KEY_ICON_SIZE, sizeDp).apply();
    }

    public int getIconSizePx(Context context) {
        return Ui.dp(context, getIconSizeDp());
    }

    public boolean showLabels() {
        return prefs.getBoolean(KEY_LABELS, true);
    }

    public void setShowLabels(boolean show) {
        prefs.edit().putBoolean(KEY_LABELS, show).apply();
    }

    public int getWidgetHostId() {
        return prefs.getInt(KEY_WIDGET_HOST, 0);
    }

    public void setWidgetHostId(int hostId) {
        prefs.edit().putInt(KEY_WIDGET_HOST, hostId).apply();
    }

    /** Animation duration: Nexus Lite disables transitions and extra rendering work. */
    public int animDuration(int base) {
        return isLite() ? 0 : base;
    }
}
