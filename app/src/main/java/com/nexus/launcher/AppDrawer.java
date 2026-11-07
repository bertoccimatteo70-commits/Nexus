package com.nexus.launcher;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Application drawer: alphabetical list of every launchable app with real time search and a
 * long press action to place an app on the home screen.
 */
public class AppDrawer extends FrameLayout {

    public interface Callback {
        void onRequestClose();

        void onAppLongPress(AppInfo app);
    }

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final Callback callback;

    private final LinearLayout panel;
    private final SearchBar search;
    private final HomeGrid grid;
    private final TextView countView;

    private final List<AppInfo> sorted = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastQuery = null;
    private String pendingQuery = "";
    private final Runnable pendingFilter = new Runnable() {
        @Override
        public void run() {
            applyFilter(pendingQuery);
        }
    };

    public AppDrawer(MainActivity activity, LauncherSettings settings, List<AppInfo> apps,
                     Callback callback) {
        super(activity);
        this.activity = activity;
        this.settings = settings;
        this.callback = callback;

        if (apps != null) {
            sorted.addAll(apps);
        }
        if (settings.isLite() || settings.drawerAlphabetical()) {
            Collections.sort(sorted, AppInfo.ALPHABETICAL);
        }

        View backdrop = new View(activity);
        backdrop.setBackgroundColor(settings.scrimColor(activity));
        backdrop.setOnClickListener(v -> {
            if (this.callback != null) {
                this.callback.onRequestClose();
            }
        });
        addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 14),
                Ui.dp(activity, 14), Ui.dp(activity, 10));
        panel.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        panelParams.topMargin = Ui.statusBarHeight(activity) + Ui.dp(activity, 12);
        panelParams.bottomMargin = Ui.navBarHeight(activity) + Ui.dp(activity, 10);
        addView(panel, panelParams);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("App");
        title.setTextSize(24f);
        title.setTextColor(settings.textColor(activity));
        titles.addView(title);

        countView = new TextView(activity);
        countView.setTextSize(11f);
        countView.setTextColor(settings.secondaryTextColor(activity));
        titles.addView(countView);

        if (settings.isLite()) {
            TextView hint = new TextView(activity);
            hint.setText("Tocca un'app per aprirla · tieni premuto per aggiungerla alla home");
            hint.setTextSize(12f);
            hint.setTextColor(settings.secondaryTextColor(activity));
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = Ui.dp(activity, 2);
            titles.addView(hint, hintParams);
        }

        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(settings.secondaryTextColor(activity));
        close.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 10),
                Ui.dp(activity, 10), Ui.dp(activity, 10));
        close.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 20)));
        close.setOnClickListener(v -> {
            if (this.callback != null) {
                this.callback.onRequestClose();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(activity, 38), Ui.dp(activity, 38)));

        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (settings.isLite()) {
            TextView searchLabel = new TextView(activity);
            searchLabel.setText("Cerca app");
            searchLabel.setTextSize(13f);
            searchLabel.setTextColor(settings.textColor(activity));
            searchLabel.setTypeface(searchLabel.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Ui.dp(activity, 14);
            labelParams.leftMargin = Ui.dp(activity, 4);
            panel.addView(searchLabel, labelParams);
        }

        search = new SearchBar(activity, settings);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, settings.isLite()
                ? Ui.dp(activity, 52) : Ui.dp(activity, 46));
        searchParams.topMargin = settings.isLite() ? Ui.dp(activity, 6) : Ui.dp(activity, 12);
        panel.addView(search, searchParams);

        grid = new HomeGrid(activity, settings);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gridParams.topMargin = Ui.dp(activity, 8);
        panel.addView(grid, gridParams);

        grid.setOnAppClickListener(app -> {
            activity.launchApp(app);
            if (this.callback != null) {
                this.callback.onRequestClose();
            }
        });
        grid.setOnAppLongClickListener(app -> {
            if (this.callback != null) {
                this.callback.onAppLongPress(app);
            }
        });

        search.setOnQueryChangeListener(query -> scheduleFilter(query));
        applyFilter("");

        setAlpha(settings.isLite() ? 1f : 0f);
    }

    public void setQuery(String query) {
        search.setQuery(query);
        applyFilter(query == null ? "" : query);
    }

    /**
     * Typing is debounced so a large app list is filtered once per short pause instead of on
     * every keystroke, which keeps the drawer responsive while the keyboard is open.
     */
    private void scheduleFilter(String query) {
        pendingQuery = query == null ? "" : query;
        handler.removeCallbacks(pendingFilter);
        handler.postDelayed(pendingFilter, 45L);
    }

    private void applyFilter(String query) {
        if (query != null && query.equals(lastQuery)) {
            return;
        }
        lastQuery = query;
        handler.removeCallbacks(pendingFilter);
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo app : sorted) {
            if (app.matches(query)) {
                filtered.add(app);
            }
        }
        grid.setApps(filtered);
        String text;
        if (query == null || query.trim().isEmpty()) {
            text = filtered.size() + " app installate · "
                    + (settings.drawerAlphabetical() ? "ordine alfabetico" : "ordine di installazione");
        } else {
            text = filtered.size() + " risultati";
        }
        countView.setText(text);
    }

    public void animateIn() {
        int duration = settings.decorAnimationsEnabled() ? settings.animDuration(240) : 0;
        if (duration == 0) {
            setAlpha(1f);
            panel.setTranslationY(0f);
            return;
        }
        float start = getResources().getDisplayMetrics().heightPixels * 0.4f;
        panel.setTranslationY(start);
        setAlpha(0f);
        animate().alpha(1f).setDuration(duration).start();
        panel.animate().translationY(0f).setDuration(duration).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(pendingFilter);
        super.onDetachedFromWindow();
    }

    public void animateOut(final Runnable done) {
        int duration = settings.decorAnimationsEnabled() ? settings.animDuration(180) : 0;
        if (duration == 0) {
            if (done != null) {
                done.run();
            }
            return;
        }
        float end = getResources().getDisplayMetrics().heightPixels * 0.4f;
        animate().alpha(0f).setDuration(duration).start();
        panel.animate().translationY(end).setDuration(duration).withEndAction(done).start();
    }
}
