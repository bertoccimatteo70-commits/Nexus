package com.nexus.launcher;

import android.app.Activity;
import android.content.DialogInterface;
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
