package com.nexus.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Bottom sheet with the launcher settings.
 */
public final class SettingsPanel {

    /** Callbacks for the actions that live on the home screen. */
    public interface Actions {
        void onWallpaper();

        void onAddWidget();

        void onResetHome();

        void onPages();
    }

    private SettingsPanel() {
    }

    public static void show(final MainActivity activity, final LauncherSettings settings,
                            final List<AppInfo> apps, final Actions actions,
                            final SettingsDialogs.OnSettingsChanged callback) {

        final Dialog dialog = new Dialog(activity, R.style.NexusSheet);

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
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

        root.addView(row(activity, settings, "Griglia e icone",
                settings.getColumns(activity) + " colonne - " + settings.getIconSizeDp() + " dp",
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        SettingsDialogs.showAppearanceDialog(activity, settings, callback);
                    }
                }));

        root.addView(row(activity, settings, "Pagine home", "Gestisci", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                if (actions != null) {
                    actions.onPages();
                }
            }
        }));

        root.addView(row(activity, settings, "Widget", "Aggiungi", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                if (actions != null) {
                    actions.onAddWidget();
                }
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
                if (actions != null) {
                    actions.onWallpaper();
                }
            }
        }));

        root.addView(row(activity, settings, "Launcher predefinito", "Apri impostazioni",
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        activity.requestDefaultLauncher();
                    }
                }));

        root.addView(row(activity, settings, "Ripristina home", "Ripristina", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                if (actions != null) {
                    actions.onResetHome();
                }
            }
        }));

        root.addView(row(activity, settings, "Informazioni", "Nexus", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                SettingsDialogs.showAboutDialog(activity, settings);
            }
        }));

        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(scroll);
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

    private static View row(final MainActivity activity, final LauncherSettings settings,
                            String label, String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 15),
                Ui.dp(activity, 14), Ui.dp(activity, 15));
        row.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 16)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = Ui.dp(activity, 8);
        row.setLayoutParams(rowParams);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });

        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(settings.textColor(activity));
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView current = new TextView(activity);
        current.setText(value);
        current.setTextSize(12f);
        current.setTextColor(settings.secondaryTextColor(activity));
        row.addView(current);

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
                return "Sistema";
            default:
                return "Nexus";
        }
    }
}
