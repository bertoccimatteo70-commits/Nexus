package com.nexus.launcher;

import android.app.WallpaperManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: wallpaper, clock, search, swipeable pages with drag and drop, widgets,
 * dock, the application drawer and the Nexus UI settings overlay.
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
    private AppDrawer cachedDrawer;
    private boolean drawerOpen;
    private SettingsHub settingsHub;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private int wallpaperRequest;
    private String wallpaperPath;
    private Bitmap wallpaperBitmap;
    private Drawable systemWallpaperDrawable;
    private int drawerGeneration;

    private final Runnable prewarmDrawer = new Runnable() {
        @Override
        public void run() {
            if (!drawerOpen && settingsHub == null && cachedDrawer == null
                    && !allApps.isEmpty()) {
                cachedDrawer = createDrawer();
            }
        }
    };

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
        this.layout = new HomeLayout(activity, settings.isLite());
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
                if (settingsHub != null) {
                    settingsHub.refreshPage();
                }
                toast(label + " aggiunto alla home");
            }
        });
        widgetHost.start();

        reloadApps();
        applyTheme();
        applyBackground();
        updateClock();
        handler.postDelayed(prewarmDrawer, 900L);
    }

    // ------------------------------------------------------------------ build

    private void build() {
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wallpaperView = new ImageView(activity);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setScaleX(1.12f);
        wallpaperView.setScaleY(1.12f);
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
        // Nexus Lite keeps the home uncluttered: search lives in the drawer, where it is labelled.
        searchBar.setVisibility(settings.isLite() ? GONE : VISIBLE);
        searchBar.setOnQueryChangeListener(new SearchBar.OnQueryChangeListener() {
            @Override
            public void onQueryChanged(String query) {
                if (query == null || query.trim().isEmpty() || drawerOpen) {
                    return;
                }
                if (!settings.searchOpensDrawer()) {
                    return;
                }
                String text = query.trim();
                searchBar.setQuery("");
                openDrawer(text);
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

            @Override
            public void onItemLongPress(HomeItem item, int pageIndex) {
                showLiteItemMenu(item);
            }

            @Override
            public void onPageScrolled(float pagePosition) {
                updateWallpaperParallax(pagePosition);
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
        drawerGeneration++;
        cachedDrawer = null;
        if (drawerOpen) {
            closeDrawer();
        }
        allApps.clear();
        appIndex.clear();
        // Nexus Lite always shows every installed app: simplicity means nothing goes missing.
        Set<String> hidden = settings.isLite()
                ? new HashSet<String>() : new HashSet<>(settings.getHiddenPackages());
        for (AppInfo app : AppInfo.load(activity)) {
            if (hidden.contains(app.getPackageName())) {
                continue;
            }
            allApps.add(app);
            appIndex.put(app.getPackageName(), app);
        }
        Set<String> installed = new HashSet<>(appIndex.keySet());
        layout.removePackages(hidden);
        if (settings.isLite()) {
            layout.ensureLiteDefaults(allApps);
        } else {
            layout.ensureDefaults(allApps, defaultCapacity());
        }
        layout.prune(installed);
        if (layout.pageCount() == 0) {
            if (settings.isLite()) {
                layout.ensureLiteDefaults(allApps);
            } else {
                layout.ensureDefaults(allApps, defaultCapacity());
            }
        }
        ensureDefaultDock();
        dock.setApps(allApps);
        pager.rebuild();
    }

    private void ensureDefaultDock() {
        if (!settings.getDockPackages().isEmpty()) {
            return;
        }
        if (settings.isLite()) {
            ensureLiteDock();
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

    /** Lite dock defaults to the four everyday apps, only when installed. */
    private void ensureLiteDock() {
        String[][] slots = {
                {"dialer", "telefono", "phone", "telecom"},
                {"messagg", "message", "sms", "mms"},
                {"camera", "fotocamera"},
                {"settings", "impostazioni"}
        };
        List<String> chosen = new ArrayList<>();
        for (String[] keywords : slots) {
            for (AppInfo app : allApps) {
                String pkg = app.getPackageName().toLowerCase();
                String label = app.getLabel().toLowerCase();
                boolean match = false;
                for (String keyword : keywords) {
                    if (pkg.contains(keyword) || label.contains(keyword)) {
                        match = true;
                        break;
                    }
                }
                if (match && !chosen.contains(app.getPackageName())) {
                    chosen.add(app.getPackageName());
                    break;
                }
            }
        }
        if (!chosen.isEmpty()) {
            settings.setDockPackages(chosen);
        }
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
                pager.rebuild();
            }
        });
    }

    // ---------------------------------------------------------------- widgets

    private void showWidgetMenu(final HomeItem item) {
        SettingsSheet.actions(activity, settings, item.getLabel(), "Widget sulla home",
                new String[]{"Ridimensiona", "Rimuovi"}, new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0) {
                            showWidgetResize(item);
                        } else {
                            removeWidget(item);
                        }
                    }
                });
    }

    private void showWidgetResize(final HomeItem item) {
        final int columns = Math.max(3, settings.getColumns(activity));
        final List<String> labels = new ArrayList<>();
        for (int spanY = 1; spanY <= 3; spanY++) {
            for (int spanX = 1; spanX <= columns; spanX++) {
                labels.add(spanX + " × " + spanY);
            }
        }
        SettingsSheet.choice(activity, settings, "Dimensione del widget",
                "Celle occupate sulla griglia della home", labels.toArray(new String[0]),
                (Math.max(1, item.getSpanY()) - 1) * columns + (Math.max(1, item.getSpanX()) - 1),
                new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        int spanY = index / columns + 1;
                        int spanX = index % columns + 1;
                        resizeWidget(item, spanX, spanY);
                    }
                });
    }

    private void removeWidget(HomeItem item) {
        widgetHost.deleteWidget(item.getAppWidgetId());
        layout.removeItem(item);
        pager.rebuild();
        toast("Widget rimosso");
    }

    // -------------------------------------------------------- drawer and apps

    private AppDrawer createDrawer() {
        return new AppDrawer(activity, settings, allApps, new AppDrawer.Callback() {
            @Override
            public void onRequestClose() {
                closeDrawer();
            }

            @Override
            public void onAppLongPress(AppInfo app) {
                showAddToHomeDialog(app);
            }
        });
    }

    private void openDrawer(String query) {
        if (drawerOpen) {
            if (drawer != null && query != null && !query.isEmpty()) {
                drawer.setQuery(query);
            }
            return;
        }
        searchBar.hideKeyboard();
        drawerOpen = true;
        AppDrawer prepared = cachedDrawer;
        cachedDrawer = null;
        drawer = prepared != null ? prepared : createDrawer();
        addView(drawer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        drawer.setQuery(query == null ? "" : query);
        drawer.animateIn();
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) {
            return;
        }
        drawerOpen = false;
        final AppDrawer target = drawer;
        final int generation = drawerGeneration;
        drawer = null;
        target.animateOut(new Runnable() {
            @Override
            public void run() {
                if (target.getParent() == HomeScreen.this) {
                    removeView(target);
                }
                if (!activity.isFinishing() && generation == drawerGeneration && drawer == null) {
                    cachedDrawer = target;
                }
            }
        });
    }

    private void showAddToHomeDialog(final AppInfo app) {
        if (layout.contains(app.getPackageName())) {
            toast(app.getLabel() + " è già sulla home");
            return;
        }
        SettingsSheet.confirm(activity, settings, app.getLabel(),
                "Aggiungere questa app alla home?", "Aggiungi", new Runnable() {
                    @Override
                    public void run() {
                        addAppToHome(app);
                    }
                });
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
        toast("Nessuno spazio libero: aggiungi una pagina dalle impostazioni");
    }

    // --------------------------------------------------------------- settings

    public void showSettings() {
        searchBar.hideKeyboard();
        if (settingsHub != null) {
            return;
        }
        settingsHub = new SettingsHub(activity, this);
        boolean animate = settings.decorAnimationsEnabled();
        if (animate) {
            settingsHub.setAlpha(0f);
        }
        addView(settingsHub, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (animate) {
            settingsHub.animate().alpha(1f).setDuration(settings.animDuration(200)).start();
        }
    }

    public void closeSettings() {
        if (settingsHub == null) {
            return;
        }
        final SettingsHub target = settingsHub;
        settingsHub = null;
        searchBar.hideKeyboard();
        boolean animate = settings.decorAnimationsEnabled();
        if (animate) {
            target.animate().alpha(0f).setDuration(settings.animDuration(160))
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (target.getParent() == HomeScreen.this) {
                                removeView(target);
                            }
                        }
                    }).start();
        } else if (target.getParent() == HomeScreen.this) {
            removeView(target);
        }
    }

    public LauncherSettings getSettings() {
        return settings;
    }

    public HomeLayout getLayout() {
        return layout;
    }

    public HomePager getPager() {
        return pager;
    }

    public WidgetHost getWidgetHost() {
        return widgetHost;
    }

    public List<AppInfo> getApps() {
        return allApps;
    }

    // ------------------------------------------------------- settings actions

    public void openWallpaperFromGallery() {
        wallpaperPicker.pickFromGallery();
    }

    public void useSystemWallpaper() {
        wallpaperPicker.applySystem();
    }

    public void useDefaultWallpaper() {
        wallpaperPicker.applyDefault();
    }

    public void requestAddWidget() {
        if (settings.isLite()) {
            toast("Nexus Lite non aggiunge widget: passa a Nexus per farlo");
            return;
        }
        widgetHost.pickWidget();
    }

    public void resizeWidget(HomeItem item, int spanX, int spanY) {
        if (item == null || !item.isWidget()) {
            return;
        }
        item.setSpanX(Math.max(1, spanX));
        item.setSpanY(Math.max(1, spanY));
        layout.save();
        pager.rebuild();
    }

    public void moveWidgetToPage(HomeItem item, int targetPage) {
        if (item == null || targetPage < 0 || targetPage >= layout.pageCount()) {
            return;
        }
        for (int p = 0; p < layout.pageCount(); p++) {
            HomeLayout.Page page = layout.page(p);
            if (page == null) {
                continue;
            }
            int index = page.items.indexOf(item);
            if (index < 0) {
                continue;
            }
            if (p != targetPage) {
                HomeLayout.Page target = layout.page(targetPage);
                if (target != null && layout.move(p, index, targetPage, target.items.size())) {
                    pager.rebuild();
                    pager.setCurrentPage(targetPage, true);
                    toast("Widget spostato nella pagina " + (targetPage + 1));
                }
            }
            return;
        }
    }

    public void removeAllWidgets() {
        List<HomeItem> widgets = new ArrayList<>();
        for (int p = 0; p < layout.pageCount(); p++) {
            HomeLayout.Page page = layout.page(p);
            if (page == null) {
                continue;
            }
            for (HomeItem item : page.items) {
                if (item.isWidget()) {
                    widgets.add(item);
                }
            }
        }
        for (HomeItem widget : widgets) {
            widgetHost.deleteWidget(widget.getAppWidgetId());
            layout.removeItem(widget);
        }
        pager.rebuild();
        toast(widgets.isEmpty() ? "Nessun widget presente" : "Widget rimossi");
    }

    public void resetHomeLayout() {
        layout.reset();
        if (settings.isLite()) {
            layout.ensureLiteDefaults(allApps);
        } else {
            layout.ensureDefaults(allApps, defaultCapacity());
        }
        layout.prune(appIndex.keySet());
        pager.setCurrentPage(layout.defaultPage(), false);
        pager.rebuild();
        toast("Layout della home ripristinato");
    }

    public void resetEverything() {
        removeAllWidgets();
        settings.clearEverything();
        layout.reset();
        reloadApps();
        applyTheme();
        applyBackground();
        dock.applyTheme();
        pager.setCurrentPage(layout.defaultPage(), false);
        pager.rebuild();
        toast("Nexus è stato ripristinato");
    }

    /** Reloads applications (visibility included) and repaints the whole launcher. */
    public void refreshEverything() {
        drawerGeneration++;
        cachedDrawer = null;
        reloadApps();
        applyTheme();
        applyBackground();
        dock.applyTheme();
        pager.rebuild();
        if (drawerOpen) {
            closeDrawer();
        }
    }

    public void onSettingsChanged() {
        drawerGeneration++;
        cachedDrawer = null;
        if (layout.isLiteLayout() != settings.isLite()) {
            layout = new HomeLayout(activity, settings.isLite());
            pager.setLayout(layout);
            searchBar.setVisibility(settings.isLite() ? GONE : VISIBLE);
            reloadApps();
        }
        applyTheme();
        applyBackground();
        dock.applyTheme();
        dock.setApps(allApps);
        pager.rebuild();
        activity.applyPreferredRefreshRate();
        if (drawerOpen) {
            closeDrawer();
        }
    }

    /** Nexus Lite: long press offers removal instead of starting a drag. */
    private void showLiteItemMenu(final HomeItem item) {
        if (item == null) {
            return;
        }
        String label = item.isApp()
                ? (appIndex.containsKey(item.getPackageName())
                ? appIndex.get(item.getPackageName()).getLabel()
                : item.getPackageName())
                : item.getLabel();
        SettingsSheet.actions(activity, settings, label,
                "Rimuovi la scorciatoia dalla home: l'app resta installata.",
                new String[]{"Rimuovi dalla home"}, new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0 && layout.removeItem(item)) {
                            pager.rebuild();
                            toast("Scorciatoia rimossa");
                        }
                    }
                });
    }

    /** Wallpaper follows the pages at a fraction of their speed, like on iOS. */
    private void updateWallpaperParallax(float pagePosition) {
        if (wallpaperView == null) {
            return;
        }
        if (settings.isLite()) {
            wallpaperView.setTranslationX(0f);
            return;
        }
        float delta = pagePosition - pager.currentPage();
        float width = pager.pageWidth();
        wallpaperView.setTranslationX(-delta * width * 0.08f);
    }

    public boolean handleBack() {
        if (settingsHub != null) {
            if (settingsHub.handleBack()) {
                return true;
            }
            closeSettings();
            return true;
        }
        if (drawerOpen) {
            closeDrawer();
            return true;
        }
        return false;
    }

    public void goHome() {
        if (settingsHub != null) {
            closeSettings();
        }
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
        if (wallpaperPicker.handleActivityResult(requestCode, resultCode, data)) {
            return true;
        }
        return SettingsBackup.handleActivityResult(activity, settings, requestCode, resultCode,
                data, new Runnable() {
                    @Override
                    public void run() {
                        refreshEverything();
                        if (settingsHub != null) {
                            settingsHub.refreshPage();
                        }
                    }
                });
    }

    public void onHostResume() {
        widgetHost.start();
    }

    public void onHostPause() {
        widgetHost.stop();
    }

    public void release() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(prewarmDrawer);
        ioExecutor.shutdownNow();
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
        if (settings.isLite()) {
            wallpaperRequest++;
            wallpaperPath = null;
            wallpaperBitmap = null;
            wallpaperView.setImageDrawable(defaultBackground());
            applyScrim();
            return;
        }
        int mode = settings.getBackgroundMode();
        if (mode == LauncherSettings.BG_IMAGE) {
            final String path = settings.getBackgroundPath();
            if (path != null && path.equals(wallpaperPath) && wallpaperBitmap != null) {
                wallpaperView.setImageDrawable(
                        new BitmapDrawable(activity.getResources(), wallpaperBitmap));
                applyScrim();
                return;
            }
            wallpaperView.setImageDrawable(defaultBackground());
            applyScrim();
            final int request = ++wallpaperRequest;
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = decodeWallpaperFile(path);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (request != wallpaperRequest
                                    || settings.getBackgroundMode() != LauncherSettings.BG_IMAGE
                                    || path == null
                                    || !path.equals(settings.getBackgroundPath())) {
                                return;
                            }
                            wallpaperPath = path;
                            wallpaperBitmap = bitmap;
                            wallpaperView.setImageDrawable(bitmap != null
                                    ? new BitmapDrawable(activity.getResources(), bitmap)
                                    : defaultBackground());
                            applyScrim();
                        }
                    });
                }
            });
            return;
        }
        wallpaperRequest++;
        wallpaperPath = null;
        wallpaperBitmap = null;
        Drawable background = mode == LauncherSettings.BG_SYSTEM ? systemWallpaper() : null;
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

    /** The system wallpaper is expensive to decode: keep the drawable until it is rebuilt. */
    private Drawable systemWallpaper() {
        if (systemWallpaperDrawable != null) {
            return systemWallpaperDrawable;
        }
        try {
            WallpaperManager manager = WallpaperManager.getInstance(activity);
            systemWallpaperDrawable = manager.getDrawable();
        } catch (Throwable t) {
            systemWallpaperDrawable = null;
        }
        return systemWallpaperDrawable;
    }

    /** Decodes the chosen wallpaper off the main thread, downsampled for the screen size. */
    private Bitmap decodeWallpaperFile(String path) {
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
            return BitmapFactory.decodeFile(path, options);
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
        // Align the next tick with the next minute instead of waking up every 20 seconds.
        long delay = CLOCK_INTERVAL_MS - (System.currentTimeMillis() % CLOCK_INTERVAL_MS);
        handler.postDelayed(clockTick, Math.max(1000L, delay));
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
