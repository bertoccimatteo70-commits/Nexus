package com.nexus.launcher;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home screen: clock, search, application grid, dock and the app drawer.
 */
public class HomeScreen extends FrameLayout {

    private static final int HOME_APP_LIMIT = 15;
    private static final long CLOCK_INTERVAL_MS = 20000L;

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final WallpaperPickerDialog wallpaperPicker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN);
    private final List<AppInfo> allApps = new ArrayList<>();

    private ImageView wallpaperView;
    private View scrimView;
    private LinearLayout column;
    private TextView clockView;
    private TextView dateView;
    private ImageView appsButton;
    private ImageView settingsButton;
    private SearchBar searchBar;
    private HomeGrid grid;
    private DockBar dock;

    private FrameLayout drawer;
    private LinearLayout drawerPanel;
    private SearchBar drawerSearch;
    private HomeGrid drawerGrid;
    private boolean drawerOpen;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, CLOCK_INTERVAL_MS);
        }
    };

    public HomeScreen(MainActivity activity, LauncherSettings settings) {
        super(activity);
        this.activity = activity;
        this.settings = settings;
        this.wallpaperPicker = new WallpaperPickerDialog(activity, settings, new Runnable() {
            @Override
            public void run() {
                onSettingsChanged();
            }
        });

        build();
        reloadApps();
        applyTheme();
        applyBackground();
        updateClock();
    }

    // ------------------------------------------------------------------ build

    private void build() {
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wallpaperView = new ImageView(activity);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wallpaperView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scrimView = new View(activity);
        addView(scrimView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(activity, 16), statusBarHeight() + Ui.dp(activity, 10),
                Ui.dp(activity, 16), navBarHeight() + Ui.dp(activity, 8));
        addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        column.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        searchBar = new SearchBar(activity, settings);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48));
        searchParams.topMargin = Ui.dp(activity, 16);
        column.addView(searchBar, searchParams);
        searchBar.setOnQueryChangeListener(new SearchBar.OnQueryChangeListener() {
            @Override
            public void onQueryChanged(String query) {
                applyHomeApps(query);
            }
        });

        grid = new HomeGrid(activity, settings);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gridParams.topMargin = Ui.dp(activity, 10);
        column.addView(grid, gridParams);
        grid.setOnAppClickListener(new HomeGrid.OnAppClickListener() {
            @Override
            public void onAppClick(AppInfo app) {
                searchBar.hideKeyboard();
                activity.launchApp(app);
            }
        });
        grid.setOnAllAppsClickListener(new Runnable() {
            @Override
            public void run() {
                openDrawer();
            }
        });

        dock = new DockBar(activity, settings);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dockParams.topMargin = Ui.dp(activity, 10);
        column.addView(dock, dockParams);
        dock.setOnAppClickListener(new DockBar.OnAppClickListener() {
            @Override
            public void onAppClick(AppInfo app) {
                activity.launchApp(app);
            }
        });
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        clockView = new TextView(activity);
        clockView.setTextSize(34f);
        clockView.setIncludeFontPadding(false);
        texts.addView(clockView);

        dateView = new TextView(activity);
        dateView.setTextSize(12f);
        texts.addView(dateView);

        header.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        appsButton = createIconButton(R.drawable.ic_apps);
        appsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        header.addView(appsButton);

        settingsButton = createIconButton(R.drawable.ic_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 44), Ui.dp(activity, 44));
        settingsParams.leftMargin = Ui.dp(activity, 8);
        header.addView(settingsButton, settingsParams);

        return header;
    }

    private ImageView createIconButton(int drawableRes) {
        ImageView view = new ImageView(activity);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(activity, 44), Ui.dp(activity, 44)));
        view.setImageResource(drawableRes);
        view.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12),
                Ui.dp(activity, 12), Ui.dp(activity, 12));
        view.setColorFilter(settings.textColor(activity));
        view.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 22),
                Ui.dp(activity, 1), settings.rowBackground(activity)));
        view.setClickable(true);
        return view;
    }

    // ------------------------------------------------------------------ apps

    private void reloadApps() {
        allApps.clear();
        allApps.addAll(AppInfo.load(activity));
        ensureDefaultDock();
        dock.setApps(allApps);
        applyHomeApps(searchBar.getQuery());
    }

    private void ensureDefaultDock() {
        if (!settings.getDockPackages().isEmpty()) {
            return;
        }
        String[] keywords = {"whatsapp", "telegram", "messagg", "messag", "gmail", "photo", "foto",
                "chrome", "camera", "phone", "telefono", "dialer", "maps", "mappe", "spotify",
                "youtube", "instagram", "calendar", "calendario", "clock", "orologio"};

        List<String> chosen = new ArrayList<>();
        for (String keyword : keywords) {
            if (chosen.size() >= DockBar.MAX_ITEMS) {
                break;
            }
            for (AppInfo app : allApps) {
                if (chosen.size() >= DockBar.MAX_ITEMS) {
                    break;
                }
                String pkg = app.getPackageName().toLowerCase();
                if (pkg.contains(keyword) && !chosen.contains(app.getPackageName())) {
                    chosen.add(app.getPackageName());
                }
            }
        }
        for (AppInfo app : allApps) {
            if (chosen.size() >= DockBar.MAX_ITEMS) {
                break;
            }
            if (!chosen.contains(app.getPackageName())) {
                chosen.add(app.getPackageName());
            }
        }
        settings.setDockPackages(chosen);
    }

    private List<AppInfo> filterApps(List<AppInfo> source, String query) {
        List<AppInfo> result = new ArrayList<>();
        for (AppInfo app : source) {
            if (app.matches(query)) {
                result.add(app);
            }
        }
        return result;
    }

    private void applyHomeApps(String query) {
        if (grid == null) {
            return;
        }
        List<AppInfo> filtered = filterApps(allApps, query);
        List<AppInfo> page = new ArrayList<>();
        for (int i = 0; i < filtered.size() && i < HOME_APP_LIMIT; i++) {
            page.add(filtered.get(i));
        }
        grid.setShowAllAppsTile(filtered.size() > HOME_APP_LIMIT);
        grid.setApps(page);
    }

    // ---------------------------------------------------------------- drawer

    private void openDrawer() {
        if (drawerOpen) {
            return;
        }
        drawerOpen = true;

        drawer = new FrameLayout(activity);
        View backdrop = new View(activity);
        backdrop.setBackgroundColor(settings.scrimColor(activity));
        backdrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        drawer.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        drawerPanel = new LinearLayout(activity);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 14),
                Ui.dp(activity, 14), Ui.dp(activity, 8));
        drawerPanel.setBackground(Ui.rounded(settings.surfaceColor(activity), Ui.dp(activity, 28)));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        panelParams.topMargin = statusBarHeight() + Ui.dp(activity, 14);
        panelParams.bottomMargin = navBarHeight() + Ui.dp(activity, 12);
        drawer.addView(drawerPanel, panelParams);

        drawerSearch = new SearchBar(activity, settings);
        drawerPanel.addView(drawerSearch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 46)));

        drawerGrid = new HomeGrid(activity, settings);
        LinearLayout.LayoutParams drawerGridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        drawerGridParams.topMargin = Ui.dp(activity, 10);
        drawerPanel.addView(drawerGrid, drawerGridParams);
        drawerGrid.setApps(filterApps(allApps, ""));
        drawerGrid.setOnAppClickListener(new HomeGrid.OnAppClickListener() {
            @Override
            public void onAppClick(AppInfo app) {
                closeDrawer();
                activity.launchApp(app);
            }
        });

        drawerSearch.setOnQueryChangeListener(new SearchBar.OnQueryChangeListener() {
            @Override
            public void onQueryChanged(String query) {
                if (drawerGrid != null) {
                    drawerGrid.setApps(filterApps(allApps, query));
                }
            }
        });

        addView(drawer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        float start = activity.getResources().getDisplayMetrics().heightPixels * 0.45f;
        drawerPanel.setTranslationY(start);
        drawer.setAlpha(0f);
        drawer.animate().alpha(1f).setDuration(160L).start();
        drawerPanel.animate().translationY(0f).setDuration(240L)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) {
            return;
        }
        drawerOpen = false;
        final FrameLayout target = drawer;
        drawer = null;

        final LinearLayout panel = drawerPanel;
        drawerPanel = null;
        drawerSearch = null;
        drawerGrid = null;

        float end = activity.getResources().getDisplayMetrics().heightPixels * 0.45f;
        target.animate().alpha(0f).setDuration(150L).start();
        if (panel != null) {
            panel.animate().translationY(end).setDuration(200L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removeView(target);
            }
        }, 220L);
    }

    // -------------------------------------------------------------- settings

    public void showSettings() {
        searchBar.hideKeyboard();
        SettingsPanel.show(activity, settings, allApps, new Runnable() {
            @Override
            public void run() {
                wallpaperPicker.show();
            }
        }, new SettingsDialogs.OnSettingsChanged() {
            @Override
            public void onSettingsChanged() {
                HomeScreen.this.onSettingsChanged();
            }
        });
    }

    public void onSettingsChanged() {
        applyTheme();
        applyBackground();
        dock.applyTheme();
        dock.setApps(allApps);
        applyHomeApps(searchBar.getQuery());
        if (drawerOpen) {
            closeDrawer();
        }
    }

    public boolean handleBack() {
        if (drawerOpen) {
            closeDrawer();
            return true;
        }
        return false;
    }

    public void goHome() {
        if (drawerOpen) {
            closeDrawer();
        }
        searchBar.setQuery("");
        grid.setSelection(0);
    }

    public boolean handleActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        return wallpaperPicker.handleActivityResult(requestCode, resultCode, data);
    }

    public void release() {
        handler.removeCallbacks(clockTick);
    }

    // ----------------------------------------------------------------- theme

    public void applyTheme() {
        if (clockView != null) {
            clockView.setTextColor(settings.textColor(activity));
            dateView.setTextColor(settings.secondaryTextColor(activity));
        }
        if (appsButton != null) {
            appsButton.setColorFilter(settings.textColor(activity));
            appsButton.setBackground(Ui.rounded(settings.surfaceColor(activity),
                    Ui.dp(activity, 22), Ui.dp(activity, 1), settings.rowBackground(activity)));
            settingsButton.setColorFilter(settings.textColor(activity));
            settingsButton.setBackground(Ui.rounded(settings.surfaceColor(activity),
                    Ui.dp(activity, 22), Ui.dp(activity, 1), settings.rowBackground(activity)));
        }
        if (searchBar != null) {
            searchBar.applyTheme();
        }
        if (grid != null) {
            grid.applyTheme();
        }
        if (dock != null) {
            dock.applyTheme();
        }
        activity.applySystemBars();
    }

    public void applyBackground() {
        Drawable background = null;
        int mode = settings.getBackgroundMode();
        if (mode == LauncherSettings.BG_IMAGE) {
            background = loadWallpaperFile();
        } else if (mode == LauncherSettings.BG_SYSTEM) {
            background = loadSystemWallpaper();
        }
        if (background == null) {
            background = defaultBackground();
        }
        wallpaperView.setImageDrawable(background);
        applyScrim();
    }

    private void applyScrim() {
        boolean dark = settings.isDark(activity);
        int top = dark ? 0x4D000000 : 0x33FFFFFF;
        int bottom = dark ? 0x80000000 : 0x4DFFFFFF;
        scrimView.setBackground(Ui.verticalGradient(
                new int[]{top, 0x00000000, 0x00000000, bottom}));
    }

    private Drawable defaultBackground() {
        if (settings.isDark(activity)) {
            return Ui.verticalGradient(new int[]{0xFF1B1233, 0xFF15121F, 0xFF0B0B10});
        }
        return Ui.verticalGradient(new int[]{0xFFE9E0FF, 0xFFF3F1FA, 0xFFF8F8FC});
    }

    private Drawable loadSystemWallpaper() {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(activity);
            return manager.getDrawable();
        } catch (Throwable t) {
            return null;
        }
    }

    private Drawable loadWallpaperFile() {
        String path = settings.getBackgroundPath();
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);

            int target = Math.max(activity.getResources().getDisplayMetrics().widthPixels, 1);
            int sample = 1;
            while (bounds.outWidth > 0 && (bounds.outWidth / sample) > target * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap == null) {
                return null;
            }
            return new BitmapDrawable(activity.getResources(), bitmap);
        } catch (Throwable t) {
            return null;
        }
    }

    // ----------------------------------------------------------------- clock

    private void updateClock() {
        Date now = new Date();
        if (clockView != null) {
            clockView.setText(timeFormat.format(now));
        }
        if (dateView != null) {
            String text = dateFormat.format(now);
            if (!text.isEmpty()) {
                text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
            }
            dateView.setText(text);
        }
        handler.removeCallbacks(clockTick);
        handler.postDelayed(clockTick, CLOCK_INTERVAL_MS);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(activity, 24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(activity, 16);
    }
}
