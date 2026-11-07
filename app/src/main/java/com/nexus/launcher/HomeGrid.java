package com.nexus.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable grid of installed applications with rounded icons. Optionally shows a trailing
 * tile that opens the full app drawer.
 */
public class HomeGrid extends GridView {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public interface OnAppLongClickListener {
        void onAppLongClick(AppInfo app);
    }

    private final LauncherSettings settings;
    private final AppGridAdapter adapter = new AppGridAdapter();
    private final List<AppInfo> apps = new ArrayList<>();
    private final int iconSizePx;
    private final int labelTextSizeDp;

    private boolean showAllAppsTile = false;
    private OnAppClickListener appClickListener;
    private OnAppLongClickListener appLongClickListener;
    private Runnable allAppsClickListener;

    public HomeGrid(Context context, LauncherSettings settings) {
        super(context);
        this.settings = settings;

        int screenWidthDp = context.getResources().getConfiguration().screenWidthDp;
        if (screenWidthDp <= 0) {
            screenWidthDp = 360;
        }
        int columns = Math.max(3, settings.getColumns(context));
        this.iconSizePx = settings.getIconSizePx(context);
        this.labelTextSizeDp = screenWidthDp >= 400 ? 11 : 10;
        if (settings.isLite()) {
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        setNumColumns(columns);
        setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        setHorizontalSpacing(Ui.dp(context, 2));
        setVerticalSpacing(Ui.dp(context, 6));
        setClipToPadding(false);
        setSelector(new ColorDrawable(Color.TRANSPARENT));
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setAdapter(adapter);

        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (showAllAppsTile && position == apps.size()) {
                    if (allAppsClickListener != null) {
                        allAppsClickListener.run();
                    }
                    return;
                }
                if (position >= 0 && position < apps.size() && appClickListener != null) {
                    appClickListener.onAppClick(apps.get(position));
                }
            }
        });
    }

    public void setOnAppClickListener(OnAppClickListener listener) {
        this.appClickListener = listener;
    }

    public void setOnAllAppsClickListener(Runnable listener) {
        this.allAppsClickListener = listener;
    }

    public void setOnAppLongClickListener(OnAppLongClickListener listener) {
        this.appLongClickListener = listener;
        setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (showAllAppsTile && position == apps.size()) {
                    return false;
                }
                if (position >= 0 && position < apps.size() && appLongClickListener != null) {
                    appLongClickListener.onAppLongClick(apps.get(position));
                    return true;
                }
                return false;
            }
        });
    }

    public void setShowAllAppsTile(boolean show) {
        if (this.showAllAppsTile != show) {
            this.showAllAppsTile = show;
            adapter.notifyDataSetChanged();
        }
    }

    public void setApps(List<AppInfo> newApps) {
        apps.clear();
        if (newApps != null) {
            apps.addAll(newApps);
        }
        adapter.notifyDataSetChanged();
        if (!apps.isEmpty()) {
            setSelection(0);
        }
    }

    public void applyTheme() {
        adapter.notifyDataSetChanged();
    }

    private View createItem() {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(Ui.dp(context, 2), Ui.dp(context, 6), Ui.dp(context, 2), Ui.dp(context, 6));

        ImageView icon = new ImageView(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);

        TextView label = new TextView(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(context, 4);
        label.setLayoutParams(labelParams);
        label.setTextSize(labelTextSizeDp * settings.labelScale());
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setShadowLayer(3f, 0f, 1f, 0x59000000);

        item.addView(icon);
        item.addView(label);

        ItemHolder holder = new ItemHolder();
        holder.icon = icon;
        holder.label = label;
        item.setTag(holder);
        return item;
    }

    private View createAllAppsTile() {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(Ui.dp(context, 2), Ui.dp(context, 6), Ui.dp(context, 2), Ui.dp(context, 6));

        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
        icon.setLayoutParams(iconParams);
        icon.setImageResource(R.drawable.ic_apps);
        icon.setColorFilter(settings.textColor(context));
        int inset = Math.round(iconSizePx * 0.2f);
        icon.setPadding(inset, inset, inset, inset);
        icon.setAlpha(0.85f);

        TextView label = new TextView(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(context, 4);
        label.setLayoutParams(labelParams);
        label.setText("Tutte le app");
        label.setTextSize(labelTextSizeDp * settings.labelScale());
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(settings.secondaryTextColor(context));
        label.setShadowLayer(3f, 0f, 1f, 0x59000000);

        item.addView(icon);
        item.addView(label);
        return item;
    }

    private static class ItemHolder {
        ImageView icon;
        TextView label;
    }

    private class AppGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.size() + (showAllAppsTile ? 1 : 0);
        }

        @Override
        public Object getItem(int position) {
            return position < apps.size() ? apps.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (showAllAppsTile && position == apps.size()) {
                return createAllAppsTile();
            }

            View view = convertView;
            ItemHolder holder;
            if (view == null || !(view.getTag() instanceof ItemHolder)) {
                view = createItem();
                holder = (ItemHolder) view.getTag();
            } else {
                holder = (ItemHolder) view.getTag();
            }

            AppInfo app = apps.get(position);
            holder.label.setText(app.getLabel());
            holder.label.setTextColor(settings.textColor(getContext()));
            holder.icon.setImageDrawable(app.getRoundedIcon(iconSizePx, settings.getIconShape()));
            return view;
        }
    }
}
