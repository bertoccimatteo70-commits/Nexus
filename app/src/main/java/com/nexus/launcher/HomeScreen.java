package com.nexus.launcher;

import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Home screen: wallpaper, clock, search, swipeable pages with drag and drop, widgets,
 * dock and the application drawer.
 */
public class HomeScreen extends FrameLayout {

    private static final long CLOCK_INTERVAL_MS = 20000L;

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final WallpaperPickerDialog wallpaperPicker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN);
    private final List<AppInfo> allApps = new ArrayList<>();
    private final Map<String, AppInfo> appIndex = new HashMap<>();

    private HomeLayout layout;
    private WidgetHost widgetHost;

    private ImageView wallpaperView;
    private View scrimView;
    private LinearLayout column;
    private TextView clockView;
    private TextView dateView;
    private ImageView appsButton;
    private ImageView settingsButton;
    private SearchBar searchBar;
    private HomePager pager;
    private DockBar dock;

    private AppDrawer drawer;
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
        this.layout = new HomeLayout(activity);
        this.widgetHost = new WidgetHost(activity, settings);
        this.wallpaperPicker = new WallpaperPickerDialog(activity, settings, new Runnable() {
            @Override
            public void run() {
                onSettingsChanged();
            }
        });

        build();

        widgetHost.setOnWidgetPicked(new WidgetHost.OnWidgetPicked() {
            @Override
            public void onWidgetPicked(int appWidgetId, String label, int spanX, int spanY) {
                HomePageView page = pager.pageView(pager.currentPage());
                int capacity = page != null ? page.capacity() : 0;
                int used = page != null ? page.occupiedCells() : 0;
                if (capacity > 0 && used + spanX * spanY > capacity) {
                    widgetHost.deleteWidget(appWidgetId);
                    toast("Spazio insufficiente: aggiungi una pagina o libera spazio");
                    return;
                }
                HomeItem added = layout.addWidget(pager.currentPage(), label, appWidgetId, spanX, spanY);
                if (added == null) {
                    widgetHost.deleteWidget(appWidgetId);
                    toast("Impossibile aggiungere il widget");
                    return;
                }
                pager.rebuild();
                toast(label + " aggiunto alla home");
            }
        });
        widgetHost.start();

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
        column.setPadding(Ui.dp(activity, 16), Ui.statusBarHeight(activity) + Ui.dp(activity, 10),
                Ui.dp(activity, 16), Ui.navBarHeight(activity) + Ui.dp(activity, 6));
        addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        column.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        searchBar = new SearchBar(activity, settings);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48));
        searchParams.topMargin = Ui.dp(activity, 14);
        column.addView(searchBar, searchParams);
        searchBar.setOnQueryChangeListener(new SearchBar.OnQueryChangeListener() {
            @Override
            public void onQueryChanged(String query) {
                if (query != null && !query.trim().isEmpty() && !drawerOpen) {
                    String text = query.trim();
                    searchBar.setQuery("");
                    openDrawer(text);
                }
            }
        });

        pager = new HomePager(activity, settings, layout, widgetHost, appIndex);
        LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        pagerParams.topMargin = Ui.dp(activity, 8);
        column.addView(pager, pagerParams);
        pager.setListener(new HomePager.Listener() {
            @Override
            public void onItemTap(HomeItem item, int pageIndex) {
                if (item.isApp()) {
                    AppInfo app = appIndex.get(item.getPackageName());
                    if (app != null) {
                        searchBar.hideKeyboard();
                        activity.launchApp(app);
                    } else {
                        toast("App non disponibile");
                    }
                } else if (item.isFolder()) {
                    openFolder(item);
                }
            }

            @Override
            public void onWidgetMenu(HomeItem item, int pageIndex) {
                showWidgetMenu(item);
            }
        });

        dock = new DockBar(activity, settings);
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dockParams.topMargin = Ui.dp(activity, 8);
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
                openDrawer("");
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

    // ------------------------------------------------------------------- apps

    private void reloadApps() {
        allApps.clear();
        allApps.addAll(AppInfo.load(activity));
        appIndex.clear();
        Set<String> installed = new HashSet<>();
        for (AppInfo app : allApps) {
            appIndex.put(app.getPackageName(), app);
            installed.add(app.getPackageName());
        }
        int capacity = defaultCapacity();
        layout.ensureDefaults(allApps, capacity);
        layout.prune(installed);
        if (layout.pageCount() == 0) {
            layout.ensureDefaults(allApps, capacity);
        }
        ensureDefaultDock();
        dock.setApps(allApps);
        pager.rebuild();
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

    private void refreshHome() {
        pager.rebuild();
    }

    /** Conservative page capacity used when creating the very first layout. */
    private int defaultCapacity() {
        return Math.max(4, Math.min(16, settings.getColumns(activity) * 3));
    }

    // ----------------------------------------------------------------- folder

    private void openFolder(HomeItem folder) {
        FolderDialog.show(activity, settings, layout, folder, appIndex, new Runnable() {
            @Override
            public void run() {
                refreshHome();
            }
        });
    }

    // ---------------------------------------------------------------- widgets

    private void showWidgetMenu(final HomeItem item) {
        new AlertDialog.Builder(activity)
                .setTitle(item.getLabel())
                .setItems(new CharSequence[]{"Ridimensiona", "Rimuovi"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showWidgetResize(item);
                        } else {
                            removeWidget(item);
                        }
                    }
                })
                .show();
    }

    private void showWidgetResize(final HomeItem item) {
        final int columns = Math.max(3, settings.getColumns(activity));
        final List<String> labels = new ArrayList<>();
        final List<int[]> sizes = new ArrayList<>();
        for (int spanY = 1; spanY <= 3; spanY++) {
            for (int spanX = 1; spanX <= columns; spanX++) {
                labels.add(spanX + " x " + spanY);
                sizes.add(new int[]{spanX, spanY});
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle("Dimensione widget")
                .setSingleChoiceItems(labels.toArray(new CharSequence[0]), -1,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                item.setSpanX(sizes.get(which)[0]);
                                item.setSpanY(sizes.get(which)[1]);
                                layout.save();
                                pager.rebuild();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void removeWidget(HomeItem item) {
        widgetHost.deleteWidget(item.getAppWidgetId());
        layout.removeItem(item);
        pager.rebuild();
        toast("Widget rimosso");
    }

    // -------------------------------------------------------- drawer and apps

    private void openDrawer(String query) {
        if (drawerOpen) {
            if (drawer != null && query != null && !query.isEmpty()) {
                drawer.setQuery(query);
            }
            return;
        }
        searchBar.hideKeyboard();
        drawerOpen = true;
        drawer = new AppDrawer(activity, settings, allApps, new AppDrawer.Callback() {
            @Override
            public void onRequestClose() {
                closeDrawer();
            }

            @Override
            public void onAppLongPress(AppInfo app) {
                showAddToHomeDialog(app);
            }
        });
        addView(drawer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (query != null && !query.isEmpty()) {
            drawer.setQuery(query);
        }
        drawer.animateIn();
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) {
            return;
        }
        drawerOpen = false;
        final AppDrawer target = drawer;
        drawer = null;
        target.animateOut(new Runnable() {
            @Override
            public void run() {
                if (target.getParent() == HomeScreen.this) {
                    removeView(target);
                }
            }
        });
    }

    private void showAddToHomeDialog(final AppInfo app) {
        if (layout.contains(app.getPackageName())) {
            toast(app.getLabel() + " è già sulla home");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(app.getLabel())
                .setMessage("Aggiungere questa app alla home?")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Aggiungi", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        addAppToHome(app);
                    }
                })
                .show();
    }

    private void addAppToHome(AppInfo app) {
        for (int i = 0; i < layout.pageCount(); i++) {
            HomePageView page = pager.pageView(i);
            int capacity = page != null ? page.capacity() : 0;
            int used = page != null ? page.occupiedCells() : Integer.MAX_VALUE;
            if (capacity > 0 && used < capacity) {
                layout.addApp(i, app.getPackageName());
                pager.setCurrentPage(i, true);
                pager.rebuild();
                toast(app.getLabel() + " aggiunta alla home");
                return;
            }
        }
        toast("Nessuno spazio libero: crea una nuova pagina dalle impostazioni");
    }

    // --------------------------------------------------------------- settings

    public void showSettings() {
        searchBar.hideKeyboard();
        SettingsPanel.show(activity, settings, allApps, new SettingsPanel.Actions() {
            @Override
            public void onWallpaper() {
                wallpaperPicker.show();
            }

            @Override
            public void onAddWidget() {
                widgetHost.pickWidget();
            }

            @Override
            public void onResetHome() {
                confirmResetHome();
            }

            @Override
            public void onPages() {
                showPagesDialog();
            }
        }, new SettingsDialogs.OnSettingsChanged() {
            @Override
            public void onSettingsChanged() {
                HomeScreen.this.onSettingsChanged();
            }
        });
    }

    private void confirmResetHome() {
        new AlertDialog.Builder(activity)
                .setTitle("Ripristinare la home?")
                .setMessage("Verranno ripristinate le pagine iniziali. Le app installate non vengono toccate.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Ripristina", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        layout.reset();
                        layout.ensureDefaults(allApps, defaultCapacity());
                        layout.prune(appIndex.keySet());
                        pager.setCurrentPage(layout.defaultPage(), false);
                        pager.rebuild();
                    }
                })
                .show();
    }

    private void showPagesDialog() {
        String[] items = {"Aggiungi pagina a destra", "Aggiungi pagina a sinistra",
                "Rimuovi pagina corrente"};
        new AlertDialog.Builder(activity)
                .setTitle("Pagine")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            int index = layout.addPage(false);
                            pager.setCurrentPage(index, true);
                            pager.rebuild();
                        } else if (which == 1) {
                            int index = layout.addPage(true);
                            pager.setCurrentPage(index, true);
                            pager.rebuild();
                        } else {
                            int current = pager.currentPage();
                            HomeLayout.Page page = layout.page(current);
                            if (layout.pageCount() <= 1) {
                                toast("Serve almeno una pagina");
                            } else if (page != null && page.items.isEmpty()) {
                                layout.removePage(current);
                                pager.setCurrentPage(Math.max(0, current - 1), false);
                                pager.rebuild();
                            } else {
                                toast("La pagina contiene app: svuotala prima");
                            }
                        }
                    }
                })
                .show();
    }

    public void onSettingsChanged() {
        applyTheme();
        applyBackground();
        dock.applyTheme();
        dock.setApps(allApps);
        pager.rebuild();
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
        pager.setCurrentPage(layout.defaultPage(), true);
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (widgetHost.handleActivityResult(requestCode, resultCode, data)) {
            return true;
        }
        return wallpaperPicker.handleActivityResult(requestCode, resultCode, data);
    }

    public void onHostResume() {
        widgetHost.start();
    }

    public void onHostPause() {
        widgetHost.stop();
    }

    public void release() {
        handler.removeCallbacks(clockTick);
        if (widgetHost != null) {
            widgetHost.release();
        }
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
            if (settings.isLite()) {
                target = Math.max(1, target / 2);
            }
            int sample = 1;
            while (bounds.outWidth > 0 && (bounds.outWidth / sample) > target * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            if (settings.isLite()) {
                options.inPreferredConfig = Bitmap.Config.RGB_565;
            }
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

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
