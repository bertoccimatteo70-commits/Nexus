package com.nexus.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Bottom sheet with the launcher settings.
 */
public final class SettingsPanel {

    private SettingsPanel() {
    }

    public static void show(final MainActivity activity, final LauncherSettings settings,
                            final List<AppInfo> apps, final Runnable onWallpaper,
                            final SettingsDialogs.OnSettingsChanged callback) {

        final Dialog dialog = new Dialog(activity, R.style.NexusSheet);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));
        root.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 10),
                Ui.dp(activity, 16), Ui.dp(activity, 20));

        View handle = new View(activity);
        handle.setBackground(Ui.rounded(settings.isDark(activity) ? 0x40FFFFFF : 0x26000000,
                Ui.dp(activity, 2)));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 36), Ui.dp(activity, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = Ui.dp(activity, 14);
        root.addView(handle, handleParams);

        TextView title = new TextView(activity);
        title.setText("Impostazioni");
        title.setTextSize(20f);
        title.setTextColor(settings.textColor(activity));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = Ui.dp(activity, 14);
        root.addView(title, titleParams);

        root.addView(row(activity, settings, "Tema", themeLabel(settings), new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                SettingsDialogs.showThemeDialog(activity, settings, callback);
            }
        }));

        root.addView(row(activity, settings, "Versione launcher", modeLabel(settings), new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                SettingsDialogs.showModeDialog(activity, settings, "Versione launcher",
                        "Nexus Lite riduce animazioni e carico grafico, Nexus usa tutti gli effetti.",
                        callback);
            }
        }));

        root.addView(row(activity, settings, "Dock", dockLabel(settings), new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                SettingsDialogs.showDockDialog(activity, settings, apps, callback);
            }
        }));

        root.addView(row(activity, settings, "Sfondo", wallpaperLabel(settings), new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                if (onWallpaper != null) {
                    onWallpaper.run();
                }
            }
        }));

        root.addView(row(activity, settings, "Launcher predefinito", "Apri impostazioni", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                activity.requestDefaultLauncher();
            }
        }));

        root.addView(row(activity, settings, "Informazioni", "Nexus", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                SettingsDialogs.showAboutDialog(activity, settings);
            }
        }));

        dialog.setContentView(root);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.5f;
            window.setAttributes(params);
        }
    }

    private static View row(final MainActivity activity, LauncherSettings settings,
                            String label, String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 15),
                Ui.dp(activity, 14), Ui.dp(activity, 15));
        row.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 16)));
        row.setClickable(true);
        row.setFocusable(true);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextSize(14f);
        labelView.setTextColor(settings.textColor(activity));
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(activity);
        valueView.setText(value);
        valueView.setTextSize(12f);
        valueView.setTextColor(settings.secondaryTextColor(activity));
        row.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        row.setLayoutParams(params);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });

        return row;
    }

    private static String themeLabel(LauncherSettings settings) {
        switch (settings.getThemeMode()) {
            case LIGHT:
                return "Chiaro";
            case DARK:
                return "Scuro";
            default:
                return "Sistema";
        }
    }

    private static String modeLabel(LauncherSettings settings) {
        return settings.isLite() ? "Nexus Lite" : "Nexus";
    }

    private static String dockLabel(LauncherSettings settings) {
        int count = settings.getDockPackages().size();
        return count == 0 ? "Predefinito" : count + " app";
    }

    private static String wallpaperLabel(LauncherSettings settings) {
        switch (settings.getBackgroundMode()) {
            case LauncherSettings.BG_IMAGE:
                return "Immagine";
            case LauncherSettings.BG_SYSTEM:
                return "Di sistema";
            default:
                return "Predefinito";
        }
    }
}
