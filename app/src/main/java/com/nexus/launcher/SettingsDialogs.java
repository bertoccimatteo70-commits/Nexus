package com.nexus.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog helpers used by the settings panel.
 */
public final class SettingsDialogs {

    public interface OnSettingsChanged {
        void onSettingsChanged();
    }

    private SettingsDialogs() {
    }

    public static void showThemeDialog(final Activity activity, final LauncherSettings settings,
                                      final OnSettingsChanged callback) {
        final LauncherSettings.ThemeMode[] modes = LauncherSettings.ThemeMode.values();
        String[] labels = {"Segui il sistema", "Chiaro", "Scuro"};
        int checked = settings.getThemeMode().ordinal();

        new AlertDialog.Builder(activity)
                .setTitle("Tema")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setThemeMode(modes[which]);
                        dialog.dismiss();
                        if (callback != null) {
                            callback.onSettingsChanged();
                        }
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    public static void showModeDialog(final Activity activity, final LauncherSettings settings,
                                      String title, String message,
                                      final OnSettingsChanged callback) {
        String[] labels = {"Nexus Lite - leggero", "Nexus - completo"};
        int checked = settings.isLite() ? 0 : 1;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setLauncherMode(which == 0
                                ? LauncherSettings.LauncherMode.LITE
                                : LauncherSettings.LauncherMode.FULL);
                        dialog.dismiss();
                        if (callback != null) {
                            callback.onSettingsChanged();
                        }
                    }
                })
                .setNegativeButton("Annulla", null);

        if (message != null) {
            builder.setMessage(message);
        }
        builder.show();
    }

    public static void showDockDialog(final Activity activity, final LauncherSettings settings,
                                      final List<AppInfo> apps, final OnSettingsChanged callback) {
        if (apps == null || apps.isEmpty()) {
            Toast.makeText(activity, "Nessuna app installata da aggiungere al dock",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] labels = new CharSequence[apps.size()];
        final boolean[] selected = new boolean[apps.size()];
        List<String> current = settings.getDockPackages();
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).getLabel();
            selected[i] = current.contains(apps.get(i).getPackageName());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Dock personalizzato");
        builder.setMultiChoiceItems(labels, selected, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                selected[which] = isChecked;
                int count = 0;
                for (boolean value : selected) {
                    if (value) {
                        count++;
                    }
                }
                if (count > DockBar.MAX_ITEMS) {
                    selected[which] = false;
                    if (dialog instanceof AlertDialog) {
                        ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                    }
                    Toast.makeText(activity, "Massimo " + DockBar.MAX_ITEMS + " app nel dock",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setPositiveButton("Salva", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                List<String> packages = new ArrayList<>();
                for (int i = 0; i < selected.length; i++) {
                    if (selected[i]) {
                        packages.add(apps.get(i).getPackageName());
                    }
                }
                settings.setDockPackages(packages);
                if (callback != null) {
                    callback.onSettingsChanged();
                }
            }
        });
        builder.setNeutralButton("Ripristina", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                settings.resetDock();
                if (callback != null) {
                    callback.onSettingsChanged();
                }
            }
        });
        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    /** Grid columns, icon size and labels, applied immediately. */
    public static void showAppearanceDialog(final Activity activity, final LauncherSettings settings,
                                            final OnSettingsChanged callback) {
        final Dialog dialog = new Dialog(activity, R.style.NexusSheet);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));
        root.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 16),
                Ui.dp(activity, 18), Ui.dp(activity, 18));

        TextView title = new TextView(activity);
        title.setText("Griglia e icone");
        title.setTextSize(18f);
        title.setTextColor(settings.textColor(activity));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = Ui.dp(activity, 12);
        root.addView(title, titleParams);

        final TextView columnsValue = new TextView(activity);
        columnsValue.setTextSize(12f);
        columnsValue.setTextColor(settings.secondaryTextColor(activity));
        columnsValue.setText(settings.getColumns(activity) + " colonne");

        final TextView iconsValue = new TextView(activity);
        iconsValue.setTextSize(12f);
        iconsValue.setTextColor(settings.secondaryTextColor(activity));
        iconsValue.setText(settings.getIconSizeDp() + " dp");

        root.addView(valueRow(activity, settings, "Colonne home", columnsValue, new Runnable() {
            @Override
            public void run() {
                final String[] labels = {"Automatico", "4 colonne", "5 colonne", "6 colonne"};
                final int[] values = {0, 4, 5, 6};
                new AlertDialog.Builder(activity)
                        .setTitle("Colonne home")
                        .setSingleChoiceItems(labels, -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                settings.setColumns(values[which]);
                                columnsValue.setText(settings.getColumns(activity) + " colonne");
                                d.dismiss();
                                if (callback != null) {
                                    callback.onSettingsChanged();
                                }
                            }
                        })
                        .setNegativeButton("Annulla", null)
                        .show();
            }
        }));

        root.addView(valueRow(activity, settings, "Dimensione icone", iconsValue, new Runnable() {
            @Override
            public void run() {
                final String[] labels = {"Piccole (44 dp)", "Medie (52 dp)",
                        "Grandi (60 dp)", "Molto grandi (68 dp)"};
                final int[] values = {44, 52, 60, 68};
                new AlertDialog.Builder(activity)
                        .setTitle("Dimensione icone")
                        .setSingleChoiceItems(labels, -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                settings.setIconSizeDp(values[which]);
                                iconsValue.setText(settings.getIconSizeDp() + " dp");
                                d.dismiss();
                                if (callback != null) {
                                    callback.onSettingsChanged();
                                }
                            }
                        })
                        .setNegativeButton("Annulla", null)
                        .show();
            }
        }));

        CheckBox labels = new CheckBox(activity);
        labels.setText("Mostra le etichette delle app");
        labels.setTextColor(settings.textColor(activity));
        labels.setChecked(settings.showLabels());
        labels.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 10),
                Ui.dp(activity, 6), Ui.dp(activity, 10));
        labels.setOnCheckedChangeListener((button, checked) -> {
            settings.setShowLabels(checked);
            if (callback != null) {
                callback.onSettingsChanged();
            }
        });
        root.addView(labels);

        TextView close = new TextView(activity);
        close.setText("Chiudi");
        close.setTextSize(14f);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(LauncherSettings.COLOR_ACCENT);
        close.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 14),
                Ui.dp(activity, 12), Ui.dp(activity, 14));
        close.setClickable(true);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = Ui.dp(activity, 10);
        root.addView(close, closeParams);

        dialog.setContentView(root);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private static View valueRow(final Activity activity, final LauncherSettings settings,
                                 String label, TextView value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 15),
                Ui.dp(activity, 14), Ui.dp(activity, 15));
        row.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 16)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        row.setLayoutParams(params);
        row.setClickable(true);
        row.setOnClickListener(v -> onClick.run());

        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(settings.textColor(activity));
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value);
        return row;
    }

    public static void showAboutDialog(Activity activity, LauncherSettings settings) {
        String version = "1.0";
        try {
            version = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // keep the default value
        }
        String mode = settings.isLite() ? "Nexus Lite" : "Nexus";
        String message = "Versione " + version + "\n"
                + "Configurazione: " + mode + "\n\n"
                + "Launcher Android in Java con estetica Pixel e dettagli iOS.\n"
                + "Griglia app, icone arrotondate, dock personalizzabile, ricerca, "
                + "sfondo personalizzato e temi chiaro/scuro.";
        new AlertDialog.Builder(activity)
                .setTitle("Nexus")
                .setMessage(message)
                .setPositiveButton("Chiudi", null)
                .show();
    }

    public static void showDefaultLauncherDialog(Activity activity, final Runnable onConfirm) {
        new AlertDialog.Builder(activity)
                .setTitle("Launcher predefinito")
                .setMessage("Imposta Nexus come launcher predefinito per usarlo al posto della home di sistema.")
                .setPositiveButton("Imposta", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                    }
                })
                .setNegativeButton("Non ora", null)
                .show();
    }
}
