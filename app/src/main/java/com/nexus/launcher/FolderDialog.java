package com.nexus.launcher;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Opens a folder: shows its applications and lets the user rename it, add apps and remove them.
 */
public final class FolderDialog {

    private FolderDialog() {
    }

    public static void show(final MainActivity activity, final LauncherSettings settings,
                            final HomeLayout layout, final HomeItem folder,
                            final Map<String, AppInfo> appIndex, final Runnable onChanged) {
        if (folder == null || !folder.isFolder()) {
            return;
        }

        final Dialog dialog = new Dialog(activity, R.style.NexusSheet);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12),
                Ui.dp(activity, 16), Ui.dp(activity, 18));
        root.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        final TextView title = new TextView(activity);
        title.setText(folder.getLabel());
        title.setTextSize(18f);
        title.setTextColor(settings.textColor(activity));
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(settings.secondaryTextColor(activity));
        close.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 10),
                Ui.dp(activity, 10), Ui.dp(activity, 10));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(activity, 40), Ui.dp(activity, 40)));

        root.addView(header);

        final HomeGrid grid = new HomeGrid(activity, settings);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 300));
        gridParams.topMargin = Ui.dp(activity, 10);
        root.addView(grid, gridParams);

        final TextView empty = new TextView(activity);
        empty.setText("Cartella vuota");
        empty.setTextColor(settings.secondaryTextColor(activity));
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        root.addView(empty);

        final TextView addRow = new TextView(activity);
        addRow.setText("Aggiungi app");
        addRow.setTextColor(LauncherSettings.COLOR_ACCENT);
        addRow.setTextSize(14f);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14),
                Ui.dp(activity, 16), Ui.dp(activity, 14));
        addRow.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 16)));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addParams.topMargin = Ui.dp(activity, 10);
        root.addView(addRow, addParams);

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                title.setText(folder.getLabel());
                List<AppInfo> children = new ArrayList<>();
                for (String pkg : folder.getChildren()) {
                    AppInfo app = appIndex.get(pkg);
                    if (app != null) {
                        children.add(app);
                    }
                }
                grid.setApps(children);
                boolean visible = !children.isEmpty();
                grid.setVisibility(visible ? View.VISIBLE : View.GONE);
                empty.setVisibility(visible ? View.GONE : View.VISIBLE);
            }
        };

        title.setOnClickListener(v -> rename(activity, settings, layout, folder, refresh));
        addRow.setOnClickListener(v -> addApps(activity, settings, layout, folder, appIndex, refresh));

        grid.setOnAppClickListener(app -> {
            activity.launchApp(app);
            dialog.dismiss();
        });
        grid.setOnAppLongClickListener(app -> {
            new AlertDialog.Builder(activity)
                    .setTitle(app.getLabel())
                    .setMessage("Rimuovere l'app dalla cartella?")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Rimuovi", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            layout.removeFromFolder(folder, app.getPackageName());
                            refresh.run();
                            if (!folder.isFolder() || folder.getChildren().isEmpty()) {
                                dialog.dismiss();
                            }
                        }
                    })
                    .show();
        });

        refresh.run();

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (onChanged != null) {
                onChanged.run();
            }
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private static void rename(final MainActivity activity, final LauncherSettings settings,
                               final HomeLayout layout, final HomeItem folder,
                               final Runnable refresh) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(folder.getLabel());
        input.setSelection(input.getText().length());
        input.setTextColor(settings.textColor(activity));
        input.setHintTextColor(settings.secondaryTextColor(activity));
        input.setHint("Nome cartella");

        int padding = Ui.dp(activity, 16);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        new AlertDialog.Builder(activity)
                .setTitle("Rinomina cartella")
                .setView(input)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(activity, "Il nome non può essere vuoto",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        folder.setLabel(name);
                        layout.save();
                        refresh.run();
                    }
                })
                .show();
    }

    private static void addApps(final MainActivity activity, final LauncherSettings settings,
                                final HomeLayout layout, final HomeItem folder,
                                final Map<String, AppInfo> appIndex, final Runnable refresh) {
        List<AppInfo> candidates = new ArrayList<>();
        for (AppInfo app : appIndex.values()) {
            if (!folder.getChildren().contains(app.getPackageName())) {
                candidates.add(app);
            }
        }
        Collections.sort(candidates, AppInfo.ALPHABETICAL);
        if (candidates.isEmpty()) {
            Toast.makeText(activity, "Tutte le app sono già nella cartella", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] labels = new CharSequence[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = candidates.get(i).getLabel();
        }

        new AlertDialog.Builder(activity)
                .setTitle("Aggiungi app")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Aggiungi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean added = false;
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) {
                                layout.addToFolder(folder, candidates.get(i).getPackageName());
                                added = true;
                            }
                        }
                        if (added) {
                            refresh.run();
                        }
                    }
                })
                .show();
    }
}
