package com.nexus.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persisted launcher preferences. Everything the redesigned settings UI changes ends up here,
 * so the state survives launcher restarts and can be exported for a local backup.
 */
public class LauncherSettings {

    public static final int COLOR_ACCENT = 0xFF7C4DFF;

    /** Accent palette shown in Personalizzazione. */
    public static final int[] ACCENTS = {
            0xFF7C4DFF, 0xFF3D8BFF, 0xFF00B8A9, 0xFF43C463,
            0xFFFFB300, 0xFFFF6B4A, 0xFFF2508E, 0xFF7A7A85
    };

    /** Soft tints used behind the category icons on the settings dashboard. */
    public static final int[] CATEGORY_TINTS = {
            0xFFE9DEFB, 0xFFDCE7FF, 0xFFFFE1EA, 0xFFD8F3E6,
            0xFFFFEBD2, 0xFFE3E1FF, 0xFFFFF4C9, 0xFFD9F1F7,
            0xFFE8E8EF, 0xFFF0E6FB, 0xFFE4F0EA
    };

    public static final int BG_DEFAULT = 0;
    public static final int BG_IMAGE = 1;
    public static final int BG_SYSTEM = 2;

    public static final int SHAPE_ROUNDED = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_SQUARE = 2;

    public static final int PAGE_SLIDE = 0;
    public static final int PAGE_FADE = 1;
    public static final int PAGE_NONE = 2;

    public static final int FOLDER_GLASS = 0;
    public static final int FOLDER_SOLID = 1;
    public static final int FOLDER_ACCENT = 2;

    public static final int CONTRAST_NORMAL = 0;
    public static final int CONTRAST_HIGH = 1;

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
    private static final String KEY_ACCENT = "accent_color";
    private static final String KEY_ICON_SHAPE = "icon_shape";
    private static final String KEY_LABEL_SCALE = "label_scale";
    private static final String KEY_ANIMATIONS = "animations_enabled";
    private static final String KEY_ANIM_SPEED = "animation_speed";
    private static final String KEY_PAGE_EFFECT = "page_effect";
    private static final String KEY_HIDDEN = "hidden_packages";
    private static final String KEY_FOLDER_STYLE = "folder_style";
    private static final String KEY_CONTRAST = "contrast_mode";
    private static final String KEY_SEARCH_OPENS_DRAWER = "search_opens_drawer";
    private static final String KEY_DRAWER_ALPHABETICAL = "drawer_alphabetical";

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

    public int getContrastMode() {
        return prefs.getInt(KEY_CONTRAST, CONTRAST_NORMAL);
    }

    public void setContrastMode(int mode) {
        prefs.edit().putInt(KEY_CONTRAST, mode).apply();
    }

    private boolean highContrast() {
        return getContrastMode() == CONTRAST_HIGH;
    }

    public int accentColor(Context context) {
        return prefs.getInt(KEY_ACCENT, COLOR_ACCENT);
    }

    public void setAccentColor(int color) {
        prefs.edit().putInt(KEY_ACCENT, color).apply();
    }

    public int surfaceColor(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFF14141A : 0xF21A1A22;
        }
        return highContrast() ? 0xFFFFFFFF : 0xF2FFFFFF;
    }

    public int cardColor(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFF17171D : 0xFF1C1C24;
        }
        return highContrast() ? 0xFFFFFFFF : 0xFFFDFCFF;
    }

    public int pageBackgroundColor(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFF000000 : 0xFF0B0B10;
        }
        return highContrast() ? 0xFFFFFFFF : 0xFFF4F2F9;
    }

    public int textColor(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFFFFFFFF : 0xFFF2F2F7;
        }
        return highContrast() ? 0xFF000000 : 0xFF14141A;
    }

    public int secondaryTextColor(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFFD5D5DE : 0xFF9C9CAB;
        }
        return highContrast() ? 0xFF3A3A44 : 0xFF63636F;
    }

    public int searchBackground(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0xFF202028 : 0xFF23232E;
        }
        return highContrast() ? 0xFFECECF2 : 0xFFEDEDF3;
    }

    public int rowBackground(Context context) {
        if (isDark(context)) {
            return highContrast() ? 0x1FFFFFFF : 0x14FFFFFF;
        }
        return highContrast() ? 0x14000000 : 0x0D000000;
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

    public String launcherModeName() {
        return isLite() ? "Nexus Lite" : "Nexus";
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

    public boolean hasCustomColumns() {
        int saved = prefs.getInt(KEY_COLUMNS, 0);
        return saved >= 3 && saved <= 7;
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

    public int getIconShape() {
        int value = prefs.getInt(KEY_ICON_SHAPE, SHAPE_ROUNDED);
        return value < 0 || value > SHAPE_SQUARE ? SHAPE_ROUNDED : value;
    }

    public void setIconShape(int shape) {
        prefs.edit().putInt(KEY_ICON_SHAPE, shape).apply();
    }

    public boolean showLabels() {
        return prefs.getBoolean(KEY_LABELS, true);
    }

    public void setShowLabels(boolean show) {
        prefs.edit().putBoolean(KEY_LABELS, show).apply();
    }

    public int getLabelScalePercent() {
        int value = prefs.getInt(KEY_LABEL_SCALE, 100);
        if (value < 80 || value > 140) {
            return 100;
        }
        return value;
    }

    public void setLabelScalePercent(int percent) {
        prefs.edit().putInt(KEY_LABEL_SCALE, percent).apply();
    }

    public float labelScale() {
        return getLabelScalePercent() / 100f;
    }

    public int getFolderStyle() {
        int value = prefs.getInt(KEY_FOLDER_STYLE, FOLDER_GLASS);
        return value < 0 || value > FOLDER_ACCENT ? FOLDER_GLASS : value;
    }

    public void setFolderStyle(int style) {
        prefs.edit().putInt(KEY_FOLDER_STYLE, style).apply();
    }

    public int getWidgetHostId() {
        return prefs.getInt(KEY_WIDGET_HOST, 0);
    }

    public void setWidgetHostId(int hostId) {
        prefs.edit().putInt(KEY_WIDGET_HOST, hostId).apply();
    }

    // ------------------------------------------------------------ animations

    public boolean animationsEnabled() {
        return prefs.getBoolean(KEY_ANIMATIONS, true);
    }

    public void setAnimationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply();
    }

    public int getAnimationSpeedPercent() {
        int value = prefs.getInt(KEY_ANIM_SPEED, 100);
        if (value < 50 || value > 150) {
            return 100;
        }
        return value;
    }

    public void setAnimationSpeedPercent(int percent) {
        prefs.edit().putInt(KEY_ANIM_SPEED, percent).apply();
    }

    public int getPageEffect() {
        int value = prefs.getInt(KEY_PAGE_EFFECT, PAGE_SLIDE);
        return value < 0 || value > PAGE_NONE ? PAGE_SLIDE : value;
    }

    public void setPageEffect(int effect) {
        prefs.edit().putInt(KEY_PAGE_EFFECT, effect).apply();
    }

    /**
     * Animation duration in milliseconds. Zero means "apply instantly": the user disabled
     * animations or Nexus Lite is active.
     */
    public int animDuration(int base) {
        if (!animationsEnabled() || isLite()) {
            return 0;
        }
        return Math.max(1, Math.round(base * (getAnimationSpeedPercent() / 100f)));
    }

    // ---------------------------------------------------------- applications

    public List<String> getHiddenPackages() {
        List<String> result = new ArrayList<>();
        String raw = prefs.getString(KEY_HIDDEN, "");
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

    public void setHiddenPackages(List<String> packages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(packages.get(i));
        }
        prefs.edit().putString(KEY_HIDDEN, builder.toString()).apply();
    }

    public boolean isHidden(String packageName) {
        return packageName != null && getHiddenPackages().contains(packageName);
    }

    public boolean searchOpensDrawer() {
        return prefs.getBoolean(KEY_SEARCH_OPENS_DRAWER, true);
    }

    public void setSearchOpensDrawer(boolean enabled) {
        prefs.edit().putBoolean(KEY_SEARCH_OPENS_DRAWER, enabled).apply();
    }

    public boolean drawerAlphabetical() {
        return prefs.getBoolean(KEY_DRAWER_ALPHABETICAL, true);
    }

    public void setDrawerAlphabetical(boolean alphabetical) {
        prefs.edit().putBoolean(KEY_DRAWER_ALPHABETICAL, alphabetical).apply();
    }

    // ------------------------------------------------------------- resetting

    /** Resets theme, accent, icon shape, icon size and label scale. */
    public void resetPersonalization() {
        prefs.edit()
                .remove(KEY_THEME_MODE)
                .remove(KEY_ACCENT)
                .remove(KEY_ICON_SHAPE)
                .remove(KEY_ICON_SIZE)
                .remove(KEY_LABEL_SCALE)
                .remove(KEY_CONTRAST)
                .apply();
    }

    /** Resets columns, labels, folder appearance and dock. */
    public void resetHomeAppearance() {
        prefs.edit()
                .remove(KEY_COLUMNS)
                .remove(KEY_LABELS)
                .remove(KEY_FOLDER_STYLE)
                .remove(KEY_DOCK)
                .apply();
    }

    public void resetAnimations() {
        prefs.edit()
                .remove(KEY_ANIMATIONS)
                .remove(KEY_ANIM_SPEED)
                .remove(KEY_PAGE_EFFECT)
                .apply();
    }

    public void resetApplicationPrefs() {
        prefs.edit()
                .remove(KEY_HIDDEN)
                .remove(KEY_SEARCH_OPENS_DRAWER)
                .remove(KEY_DRAWER_ALPHABETICAL)
                .apply();
    }

    /** Clears every preference, including the persisted home layout. */
    public void clearEverything() {
        int widgetHostId = getWidgetHostId();
        prefs.edit().clear().apply();
        if (widgetHostId > 0) {
            setWidgetHostId(widgetHostId);
        }
    }

    // --------------------------------------------------------------- backup

    /** Serialises every preference (layout included) as a JSON object. */
    public String exportJson() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    root.put(entry.getKey(), value);
                } else if (value instanceof Integer) {
                    root.put(entry.getKey(), value);
                } else if (value instanceof Long) {
                    root.put(entry.getKey(), value);
                } else if (value instanceof Boolean) {
                    root.put(entry.getKey(), value);
                } else if (value instanceof Float) {
                    root.put(entry.getKey(), value);
                }
            }
            root.put("_backup_created_at", System.currentTimeMillis());
            root.put("_backup_app", "com.nexus.launcher");
        } catch (Exception e) {
            return null;
        }
        return root.toString();
    }

    /** Restores a backup produced by {@link #exportJson()}. */
    public boolean importJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(json);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("_backup_")) {
                    continue;
                }
                Object value = root.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Double) {
                    editor.putFloat(key, ((Double) value).floatValue());
                } else {
                    editor.putString(key, String.valueOf(value));
                }
            }
            editor.commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Package list helpers used by the visibility sheet. */
    public List<String> hiddenOrEmpty() {
        return new ArrayList<>(Arrays.asList(getHiddenPackages().toArray(new String[0])));
    }
}
