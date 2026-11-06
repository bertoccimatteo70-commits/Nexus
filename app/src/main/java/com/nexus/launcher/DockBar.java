package com.nexus.launcher;

import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Customisable dock with up to five applications. Items come from the saved package list,
 * falling back to the first installed applications.
 */
public class DockBar extends LinearLayout {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public static final int MAX_ITEMS = 5;

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final List<AppInfo> dockApps = new ArrayList<>();
    private OnAppClickListener listener;

    public DockBar(MainActivity activity, LauncherSettings settings) {
        super(activity);
        this.activity = activity;
        this.settings = settings;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(Ui.dp(activity, 6), Ui.dp(activity, 8),
                Ui.dp(activity, 6), Ui.dp(activity, 8));
        applyTheme();
    }

    public void setOnAppClickListener(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppInfo> allApps) {
        dockApps.clear();
        if (allApps != null) {
            for (String pkg : settings.getDockPackages()) {
                if (dockApps.size() >= MAX_ITEMS) {
                    break;
                }
                AppInfo found = find(allApps, pkg);
                if (found != null && !dockApps.contains(found)) {
                    dockApps.add(found);
                }
            }
            if (dockApps.isEmpty()) {
                for (int i = 0; i < allApps.size() && dockApps.size() < MAX_ITEMS; i++) {
                    dockApps.add(allApps.get(i));
                }
            }
        }
        rebuild();
    }

    private AppInfo find(List<AppInfo> list, String packageName) {
        for (AppInfo app : list) {
            if (app.getPackageName().equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    public void applyTheme() {
        setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 26),
                Ui.dp(activity, 1), settings.rowBackground(activity)));
    }

    private void rebuild() {
        removeAllViews();
        if (dockApps.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        int iconSize = Ui.dp(activity, dockApps.size() > 4 ? 48 : 52);
        for (final AppInfo app : dockApps) {
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(Ui.dp(activity, 2), 0, Ui.dp(activity, 2), 0);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackground(Ui.rounded(0x00000000, Ui.dp(activity, 16)));

            ImageView icon = new ImageView(activity);
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setImageDrawable(app.getRoundedIcon(iconSize));
            item.addView(icon);

            TextView label = new TextView(activity);
            label.setText(app.getLabel());
            label.setTextSize(9f);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(settings.textColor(activity));
            label.setShadowLayer(3f, 0f, 1f, 0x66000000);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Ui.dp(activity, 3);
            item.addView(label, labelParams);

            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    if (listener != null) {
                        listener.onAppClick(app);
                    }
                }
            });

            addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }
}
