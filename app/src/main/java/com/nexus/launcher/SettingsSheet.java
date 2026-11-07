package com.nexus.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Nexus style bottom sheet. Replaces every AlertDialog used by the settings so the interface
 * keeps one visual language: rounded card, soft divider rows and accent selection.
 */
public final class SettingsSheet {

    public interface OnSelect {
        void onSelect(int index);
    }

    public interface Builder {
        void build(LinearLayout content, Dialog dialog);
    }

    private SettingsSheet() {
    }

    /** Container returned by {@link #create}. */
    public static final class Sheet {
        public final Activity activity;
        public final LauncherSettings settings;
        public final Dialog dialog;
        public final LinearLayout content;
        private final LinearLayout column;

        private Sheet(Activity activity, LauncherSettings settings, Dialog dialog,
                      LinearLayout content, LinearLayout column) {
            this.activity = activity;
            this.settings = settings;
            this.dialog = dialog;
            this.content = content;
            this.column = column;
        }

        public void dismiss() {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
                // already dismissed
            }
        }
    }

    public static Sheet create(Activity activity, LauncherSettings settings,
                               String title, String subtitle) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 10),
                Ui.dp(activity, 18), Ui.dp(activity, 18));
        column.setBackground(Ui.rounded(settings.pageBackgroundColor(activity),
                Ui.dp(activity, 28), 0, 0));

        View handle = new View(activity);
        handle.setBackground(Ui.rounded(0x33808088, Ui.dp(activity, 3)));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 36), Ui.dp(activity, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = Ui.dp(activity, 2);
        handleParams.bottomMargin = Ui.dp(activity, 14);
        column.addView(handle, handleParams);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(19f);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(settings.textColor(activity));
        column.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = new TextView(activity);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(13f);
            subtitleView.setTextColor(settings.secondaryTextColor(activity));
            subtitleView.setLineSpacing(Ui.dp(activity, 2), 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Ui.dp(activity, 4);
            column.addView(subtitleView, params);
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = Ui.dp(activity, 12);
        column.addView(content, contentParams);

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(false);
        scroller.addView(column);
        scroller.setVerticalScrollBarEnabled(false);

        FrameLayout root = new FrameLayout(activity);
        int side = Ui.dp(activity, 10);
        root.setPadding(side, side, side, side);
        root.addView(scroller, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.42f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return new Sheet(activity, settings, dialog, content, column);
    }

    // ------------------------------------------------------------------ rows

    public static void addChoiceRow(LinearLayout parent, Activity activity, LauncherSettings settings,
                                    String label, String description, boolean selected,
                                    boolean squareIndicator, View.OnClickListener listener) {
        FrameLayout indicator = new FrameLayout(activity);
        indicator.setBackground(Ui.rounded(selected ? settings.accentColor(activity) : 0x00000000,
                Ui.dp(activity, squareIndicator ? 7 : 11),
                Ui.dp(activity, 2),
                selected ? settings.accentColor(activity) : 0x44808088));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 22), Ui.dp(activity, 22));
        indicatorParams.rightMargin = Ui.dp(activity, 14);
        indicatorParams.gravity = Gravity.CENTER_VERTICAL;

        if (selected) {
            View dot = new View(activity);
            dot.setBackground(Ui.rounded(0xFFFFFFFF, Ui.dp(activity, squareIndicator ? 3 : 6)));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    Ui.dp(activity, 9), Ui.dp(activity, 9));
            dotParams.gravity = Gravity.CENTER;
            indicator.addView(dot, dotParams);
        }

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 13),
                Ui.dp(activity, 14), Ui.dp(activity, 13));
        row.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 16)));
        row.setClickable(true);
        row.addView(indicator, indicatorParams);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(15f);
        title.setTextColor(settings.textColor(activity));
        texts.addView(title);
        if (!TextUtils.isEmpty(description)) {
            TextView sub = new TextView(activity);
            sub.setText(description);
            sub.setTextSize(12f);
            sub.setTextColor(settings.secondaryTextColor(activity));
            sub.setLineSpacing(Ui.dp(activity, 1), 1f);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subParams.topMargin = Ui.dp(activity, 2);
            texts.addView(sub, subParams);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (listener != null) {
            row.setOnClickListener(listener);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        parent.addView(row, params);
    }

    public static void addPrimaryButton(LinearLayout parent, Activity activity,
                                        LauncherSettings settings, String label, int color,
                                        View.OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(15f);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        boolean filled = color != 0;
        button.setTextColor(filled ? 0xFFFFFFFF : settings.textColor(activity));
        button.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14),
                Ui.dp(activity, 16), Ui.dp(activity, 14));
        button.setBackground(Ui.rounded(filled ? color : settings.rowBackground(activity),
                Ui.dp(activity, 18)));
        button.setClickable(true);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, 14);
        parent.addView(button, params);
    }

    public static void addBodyText(LinearLayout parent, Activity activity, LauncherSettings settings,
                                   CharSequence text) {
        TextView body = new TextView(activity);
        body.setText(text);
        body.setTextSize(13.5f);
        body.setTextColor(settings.secondaryTextColor(activity));
        body.setLineSpacing(Ui.dp(activity, 4), 1f);
        body.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 4),
                Ui.dp(activity, 2), Ui.dp(activity, 6));
        parent.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // -------------------------------------------------------------- presets

    public static void choice(Activity activity, LauncherSettings settings, String title,
                              String subtitle, final String[] labels, int selected,
                              final OnSelect onSelect) {
        final Sheet sheet = create(activity, settings, title, subtitle);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            addChoiceRow(sheet.content, activity, settings, labels[i], null, i == selected, false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sheet.dismiss();
                            if (onSelect != null) {
                                onSelect.onSelect(index);
                            }
                        }
                    });
        }
        addPrimaryButton(sheet.content, activity, settings, "Annulla",
                0x00000000, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                    }
                });
        sheet.dialog.show();
    }

    public static void multiChoice(Activity activity, LauncherSettings settings, String title,
                                   String subtitle, final String[] labels, boolean[] checked,
                                   final String applyLabel, final OnMultiSelect onApply) {
        final Sheet sheet = create(activity, settings, title, subtitle);
        final boolean[] state = checked != null ? checked.clone() : new boolean[labels.length];
        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        sheet.content.addView(list);

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                list.removeAllViews();
                for (int i = 0; i < labels.length; i++) {
                    final int index = i;
                    addChoiceRow(list, activity, settings, labels[i], null, state[i], true,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    state[index] = !state[index];
                                    rebuild[0].run();
                                }
                            });
                }
            }
        };
        rebuild[0].run();

        addPrimaryButton(sheet.content, activity, settings,
                TextUtils.isEmpty(applyLabel) ? "Applica" : applyLabel,
                settings.accentColor(activity), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                        if (onApply != null) {
                            onApply.onApply(state);
                        }
                    }
                });
        addPrimaryButton(sheet.content, activity, settings, "Annulla", 0x00000000,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                    }
                });
        sheet.dialog.show();
    }

    public interface OnMultiSelect {
        void onApply(boolean[] checked);
    }

    public static void actions(Activity activity, LauncherSettings settings, String title,
                               String subtitle, final String[] labels, final OnSelect onSelect) {
        choice(activity, settings, title, subtitle, labels, -1, onSelect);
    }

    public static void confirm(Activity activity, LauncherSettings settings, String title,
                               String message, String confirmLabel, final Runnable onConfirm) {
        final Sheet sheet = create(activity, settings, title, message);
        addPrimaryButton(sheet.content, activity, settings,
                TextUtils.isEmpty(confirmLabel) ? "Conferma" : confirmLabel,
                0xFFE5484D, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                    }
                });
        addPrimaryButton(sheet.content, activity, settings, "Annulla", 0x00000000,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                    }
                });
        sheet.dialog.show();
    }

    public static void message(Activity activity, LauncherSettings settings, String title,
                               CharSequence body) {
        final Sheet sheet = create(activity, settings, title, null);
        addBodyText(sheet.content, activity, settings, body);
        addPrimaryButton(sheet.content, activity, settings, "Chiudi", 0x00000000,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                    }
                });
        sheet.dialog.show();
    }

    public static void custom(Activity activity, LauncherSettings settings, String title,
                              String subtitle, Builder builder) {
        Sheet sheet = create(activity, settings, title, subtitle);
        builder.build(sheet.content, sheet.dialog);
        sheet.dialog.show();
    }
}
