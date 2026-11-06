package com.nexus.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A single launchable application. The icon is loaded lazily so that startup stays fast
 * on low end devices.
 */
public class AppInfo {

    public static final Comparator<AppInfo> ALPHABETICAL = new Comparator<AppInfo>() {
        @Override
        public int compare(AppInfo a, AppInfo b) {
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        }
    };

    private final PackageManager packageManager;
    private final ResolveInfo resolveInfo;
    private final String packageName;
    private final String label;

    private Drawable icon;
    private Drawable roundedIcon;
    private int roundedIconSize;

    private AppInfo(PackageManager packageManager, ResolveInfo resolveInfo, String label) {
        this.packageManager = packageManager;
        this.resolveInfo = resolveInfo;
        this.packageName = resolveInfo.activityInfo.packageName;
        this.label = label;
    }

    @SuppressWarnings("deprecation")
    public static List<AppInfo> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0));
        } else {
            resolved = pm.queryIntentActivities(intent, 0);
        }

        List<AppInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(context.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            CharSequence loadedLabel = info.loadLabel(pm);
            String text = loadedLabel == null ? pkg : loadedLabel.toString();
            result.add(new AppInfo(pm, info, text));
        }
        Collections.sort(result, ALPHABETICAL);
        return result;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean matches(String query) {
        if (query == null) {
            return true;
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        if (label.toLowerCase().contains(lower)) {
            return true;
        }
        return packageName.toLowerCase().contains(lower);
    }

    public Drawable getIcon() {
        if (icon == null) {
            try {
                icon = resolveInfo.loadIcon(packageManager);
            } catch (Exception ignored) {
                icon = null;
            }
        }
        return icon;
    }

    public Drawable getRoundedIcon(int sizePx) {
        if (roundedIcon == null || roundedIconSize != sizePx) {
            roundedIcon = MainActivity.roundIcon(getIcon(), sizePx);
            roundedIconSize = sizePx;
        }
        return roundedIcon;
    }

    @Override
    public String toString() {
        return label + " (" + packageName + ")";
    }
}
