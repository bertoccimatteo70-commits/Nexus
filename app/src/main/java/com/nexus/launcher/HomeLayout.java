package com.nexus.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persisted home screen model: a list of pages, each holding an ordered list of items.
 * Everything (positions, folders, widgets) is stored as JSON in the launcher preferences.
 */
public class HomeLayout {

    private static final String KEY_LAYOUT = "home_layout_v2";
    private static final int DEFAULT_PAGE_COUNT = 3;
    private static final int CENTER_PAGE = 1;

    /** One home page: an ordered list of items, laid out left to right, top to bottom. */
    public static class Page {
        public final List<HomeItem> items = new ArrayList<>();
    }

    private final SharedPreferences prefs;
    private final List<Page> pages = new ArrayList<>();

    public HomeLayout(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(
                "nexus_launcher_prefs", Context.MODE_PRIVATE);
        load();
    }

    // ------------------------------------------------------------ persistence

    private void load() {
        pages.clear();
        String raw = prefs.getString(KEY_LAYOUT, null);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("pages");
            if (array == null) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONArray pageArray = array.optJSONArray(i);
                Page page = new Page();
                if (pageArray != null) {
                    for (int j = 0; j < pageArray.length(); j++) {
                        JSONObject object = pageArray.optJSONObject(j);
                        if (object != null) {
                            HomeItem item = HomeItem.fromJson(object);
                            if (item.isApp() && (item.getPackageName() == null
                                    || item.getPackageName().isEmpty())) {
                                continue;
                            }
                            page.items.add(item);
                        }
                    }
                }
                pages.add(page);
            }
        } catch (Exception ignored) {
            pages.clear();
        }
    }

    public void save() {
        try {
            JSONArray pageArray = new JSONArray();
            for (Page page : pages) {
                JSONArray items = new JSONArray();
                for (HomeItem item : page.items) {
                    items.put(item.toJson());
                }
                pageArray.put(items);
            }
            JSONObject root = new JSONObject();
            root.put("pages", pageArray);
            prefs.edit().putString(KEY_LAYOUT, root.toString()).apply();
        } catch (Exception ignored) {
            // nothing sensible to do, the in-memory layout stays valid
        }
    }

    // ------------------------------------------------------------------ pages

    public int pageCount() {
        return pages.size();
    }

    public Page page(int index) {
        if (index < 0 || index >= pages.size()) {
            return null;
        }
        return pages.get(index);
    }

    public HomeItem item(int pageIndex, int index) {
        Page page = page(pageIndex);
        if (page == null || index < 0 || index >= page.items.size()) {
            return null;
        }
        return page.items.get(index);
    }

    public int defaultPage() {
        return Math.min(CENTER_PAGE, Math.max(0, pages.size() - 1));
    }

    /** Creates the initial three page layout on first start. */
    public void ensureDefaults(List<AppInfo> apps, int capacity) {
        if (!pages.isEmpty()) {
            return;
        }
        for (int i = 0; i < DEFAULT_PAGE_COUNT; i++) {
            pages.add(new Page());
        }
        Page center = pages.get(CENTER_PAGE);
        int limit = Math.max(4, Math.min(capacity, 16));
        if (apps != null) {
            for (int i = 0; i < apps.size() && center.items.size() < limit; i++) {
                center.items.add(HomeItem.app(apps.get(i).getPackageName()));
            }
        }
        save();
    }

    /** Drops uninstalled applications, empty folders and duplicate shortcuts. */
    public void prune(Set<String> installedPackages) {
        Set<String> seen = new HashSet<>();
        boolean changed = false;
        for (Page page : pages) {
            List<HomeItem> cleaned = new ArrayList<>();
            for (HomeItem item : page.items) {
                if (item.isApp()) {
                    String pkg = item.getPackageName();
                    if (pkg == null || !installedPackages.contains(pkg) || !seen.add(pkg)) {
                        changed = true;
                        continue;
                    }
                    cleaned.add(item);
                } else if (item.isFolder()) {
                    List<String> children = new ArrayList<>();
                    for (String child : item.getChildren()) {
                        if (installedPackages.contains(child) && seen.add(child)) {
                            children.add(child);
                        } else {
                            changed = true;
                        }
                    }
                    item.getChildren().clear();
                    item.getChildren().addAll(children);
                    if (children.size() >= 2) {
                        cleaned.add(item);
                    } else if (children.size() == 1) {
                        cleaned.add(HomeItem.app(children.get(0)));
                        changed = true;
                    } else {
                        changed = true;
                    }
                } else {
                    cleaned.add(item);
                }
            }
            if (cleaned.size() != page.items.size()) {
                page.items.clear();
                page.items.addAll(cleaned);
                changed = true;
            }
        }
        if (pages.isEmpty()) {
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public void reset() {
        pages.clear();
        prefs.edit().remove(KEY_LAYOUT).apply();
    }

    /**
     * Removes every shortcut pointing at the given packages, including folder children.
     * Used when the user hides applications: hidden apps disappear from the home while the
     * package stays installed and can be shown again later.
     */
    public boolean removePackages(Set<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Page page : pages) {
            for (int i = page.items.size() - 1; i >= 0; i--) {
                HomeItem item = page.items.get(i);
                if (item.isApp() && packages.contains(item.getPackageName())) {
                    page.items.remove(i);
                    changed = true;
                } else if (item.isFolder()) {
                    if (item.getChildren().removeAll(packages)) {
                        changed = true;
                    }
                    if (item.getChildren().size() < 2) {
                        if (item.getChildren().size() == 1) {
                            page.items.set(i, HomeItem.app(item.getChildren().get(0)));
                        } else {
                            page.items.remove(i);
                        }
                    }
                }
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    /**
     * Moves items that no longer fit on a page to the following one, creating pages as needed.
     * Used after the grid size or the icon size changes.
     */
    public boolean rebalance(int capacity) {
        if (capacity <= 0) {
            return false;
        }
        boolean changed = false;
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p);
            int used = 0;
            int i = 0;
            while (i < page.items.size()) {
                HomeItem item = page.items.get(i);
                int span = item.isWidget()
                        ? Math.min(item.getSpanX(), 4) * Math.min(item.getSpanY(), 3) : 1;
                if (span > capacity) {
                    used += span;
                    i++;
                    continue;
                }
                if (used + span > capacity) {
                    page.items.remove(i);
                    if (p + 1 >= pages.size()) {
                        pages.add(new Page());
                    }
                    pages.get(p + 1).items.add(0, item);
                    changed = true;
                } else {
                    used += span;
                    i++;
                }
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    /** Adds an empty page and returns its index. */
    public int addPage(boolean atStart) {
        Page page = new Page();
        if (atStart) {
            pages.add(0, page);
            save();
            return 0;
        }
        pages.add(page);
        save();
        return pages.size() - 1;
    }

    /** Removes an empty page; the layout always keeps at least one page. */
    public boolean removePage(int index) {
        if (pages.size() <= 1 || index < 0 || index >= pages.size()) {
            return false;
        }
        if (!pages.get(index).items.isEmpty()) {
            return false;
        }
        pages.remove(index);
        save();
        return true;
    }

    // ------------------------------------------------------------- operations

    public boolean contains(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (Page page : pages) {
            for (HomeItem item : page.items) {
                if (item.isApp() && packageName.equals(item.getPackageName())) {
                    return true;
                }
                if (item.isFolder() && item.getChildren().contains(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean removeItem(HomeItem item) {
        for (Page page : pages) {
            if (page.items.remove(item)) {
                save();
                return true;
            }
        }
        return false;
    }

    /** Moves an item to another position, possibly on another page. */
    public boolean move(int fromPage, int fromIndex, int toPage, int toIndex) {
        Page source = page(fromPage);
        Page target = page(toPage);
        if (source == null || target == null || fromIndex < 0 || fromIndex >= source.items.size()) {
            return false;
        }
        if (fromPage == toPage) {
            int safeTarget = Math.max(0, Math.min(toIndex, source.items.size()));
            if (safeTarget == fromIndex || safeTarget == fromIndex + 1) {
                return false;
            }
            HomeItem item = source.items.remove(fromIndex);
            if (fromIndex < safeTarget) {
                safeTarget--;
            }
            source.items.add(Math.max(0, Math.min(safeTarget, source.items.size())), item);
            save();
            return true;
        }
        HomeItem item = source.items.remove(fromIndex);
        target.items.add(Math.max(0, Math.min(toIndex, target.items.size())), item);
        save();
        return true;
    }

    /** Adds a shortcut to a page, ignoring duplicates. Returns the new index or -1. */
    public int addApp(int pageIndex, String packageName) {
        Page page = page(pageIndex);
        if (page == null || packageName == null || contains(packageName)) {
            return -1;
        }
        page.items.add(HomeItem.app(packageName));
        save();
        return page.items.size() - 1;
    }

    /** Creates a folder from the occupant app and the dragged app, replacing the occupant. */
    public HomeItem createFolder(int pageIndex, int occupantIndex, HomeItem moved) {
        Page page = page(pageIndex);
        if (page == null || moved == null || !moved.isApp()
                || occupantIndex < 0 || occupantIndex >= page.items.size()) {
            return null;
        }
        HomeItem occupant = page.items.get(occupantIndex);
        List<String> children = new ArrayList<>();
        String name;
        if (occupant.isApp()) {
            children.add(occupant.getPackageName());
            children.add(moved.getPackageName());
            name = "Cartella";
        } else if (occupant.isFolder()) {
            children.addAll(occupant.getChildren());
            if (!children.contains(moved.getPackageName())) {
                children.add(moved.getPackageName());
            }
            name = occupant.getLabel();
        } else {
            return null;
        }
        HomeItem folder = HomeItem.folder(name, children);
        page.items.set(occupantIndex, folder);
        removeFromAllPages(moved);
        removeFromAllPages(occupant);
        save();
        return folder;
    }

    /** Merges two folders, keeping all their children. */
    public boolean mergeFolders(HomeItem target, HomeItem other) {
        if (target == null || other == null || !target.isFolder() || !other.isFolder()) {
            return false;
        }
        for (String child : other.getChildren()) {
            if (!target.getChildren().contains(child)) {
                target.getChildren().add(child);
            }
        }
        removeItem(other);
        save();
        return true;
    }

    /** Adds an application to an existing folder. */
    public boolean addToFolder(HomeItem folder, String packageName) {
        if (folder == null || !folder.isFolder() || packageName == null) {
            return false;
        }
        if (folder.getChildren().contains(packageName)) {
            return false;
        }
        folder.getChildren().add(packageName);
        for (Page page : pages) {
            for (int i = page.items.size() - 1; i >= 0; i--) {
                HomeItem item = page.items.get(i);
                if (item.isApp() && packageName.equals(item.getPackageName())) {
                    page.items.remove(i);
                }
            }
        }
        save();
        return true;
    }

    /** Removes an app from a folder, dissolving the folder when it becomes too small. */
    public boolean removeFromFolder(HomeItem folder, String packageName) {
        if (folder == null || !folder.isFolder() || !folder.getChildren().remove(packageName)) {
            return false;
        }
        if (folder.getChildren().isEmpty()) {
            removeItem(folder);
        } else if (folder.getChildren().size() == 1) {
            String remaining = folder.getChildren().get(0);
            for (Page page : pages) {
                int index = page.items.indexOf(folder);
                if (index >= 0) {
                    page.items.set(index, HomeItem.app(remaining));
                    break;
                }
            }
        }
        save();
        return true;
    }

    public HomeItem addWidget(int pageIndex, String label, int appWidgetId, int spanX, int spanY) {
        Page page = page(pageIndex);
        if (page == null || appWidgetId < 0) {
            return null;
        }
        HomeItem item = HomeItem.widget(appWidgetId, label, spanX, spanY);
        page.items.add(item);
        save();
        return item;
    }

    public HomeItem widgetById(int appWidgetId) {
        for (Page page : pages) {
            for (HomeItem item : page.items) {
                if (item.isWidget() && item.getAppWidgetId() == appWidgetId) {
                    return item;
                }
            }
        }
        return null;
    }

    private void removeFromAllPages(HomeItem item) {
        for (Page page : pages) {
            for (int i = page.items.size() - 1; i >= 0; i--) {
                HomeItem candidate = page.items.get(i);
                if (candidate.isApp() && item.isApp()
                        && candidate.getPackageName().equals(item.getPackageName())) {
                    page.items.remove(i);
                }
            }
        }
    }
}
