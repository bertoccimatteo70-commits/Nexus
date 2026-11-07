package com.nexus.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A single item placed on a home page: an application shortcut, a folder or a widget.
 * Instances are serialised to JSON so the layout survives restarts.
 */
public class HomeItem {

    public static final int TYPE_APP = 0;
    public static final int TYPE_FOLDER = 1;
    public static final int TYPE_WIDGET = 2;

    private final int type;

    private String packageName;
    private String label;
    private final List<String> children = new ArrayList<>();

    private int appWidgetId = -1;
    private int spanX = 1;
    private int spanY = 1;

    private HomeItem(int type) {
        this.type = type;
    }

    public static HomeItem app(String packageName) {
        HomeItem item = new HomeItem(TYPE_APP);
        item.packageName = packageName;
        return item;
    }

    public static HomeItem folder(String label, List<String> children) {
        HomeItem item = new HomeItem(TYPE_FOLDER);
        item.label = label == null || label.trim().isEmpty() ? "Cartella" : label.trim();
        if (children != null) {
            item.children.addAll(children);
        }
        return item;
    }

    public static HomeItem widget(int appWidgetId, String label, int spanX, int spanY) {
        HomeItem item = new HomeItem(TYPE_WIDGET);
        item.appWidgetId = appWidgetId;
        item.label = label == null ? "Widget" : label;
        item.spanX = Math.max(1, spanX);
        item.spanY = Math.max(1, spanY);
        return item;
    }

    public int getType() {
        return type;
    }

    public boolean isApp() {
        return type == TYPE_APP;
    }

    public boolean isFolder() {
        return type == TYPE_FOLDER;
    }

    public boolean isWidget() {
        return type == TYPE_WIDGET;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getChildren() {
        return children;
    }

    public int getAppWidgetId() {
        return appWidgetId;
    }

    public int getSpanX() {
        return spanX;
    }

    public void setSpanX(int spanX) {
        this.spanX = Math.max(1, spanX);
    }

    public int getSpanY() {
        return spanY;
    }

    public void setSpanY(int spanY) {
        this.spanY = Math.max(1, spanY);
    }

    /** Number of grid cells occupied by this item. */
    public int cellSpan() {
        return isWidget() ? spanX * spanY : 1;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("t", type);
        if (type == TYPE_APP) {
            object.put("p", packageName == null ? "" : packageName);
        } else if (type == TYPE_FOLDER) {
            object.put("l", label == null ? "" : label);
            JSONArray array = new JSONArray();
            for (String child : children) {
                array.put(child);
            }
            object.put("c", array);
        } else {
            object.put("id", appWidgetId);
            object.put("l", label == null ? "" : label);
            object.put("sx", spanX);
            object.put("sy", spanY);
            object.put("p", packageName == null ? "" : packageName);
        }
        return object;
    }

    public static HomeItem fromJson(JSONObject object) {
        int type = object.optInt("t", TYPE_APP);
        if (type == TYPE_FOLDER) {
            List<String> children = new ArrayList<>();
            JSONArray array = object.optJSONArray("c");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String child = array.optString(i, "");
                    if (!child.isEmpty()) {
                        children.add(child);
                    }
                }
            }
            return folder(object.optString("l", "Cartella"), children);
        }
        if (type == TYPE_WIDGET) {
            return widget(object.optInt("id", -1), object.optString("l", "Widget"),
                    object.optInt("sx", 1), object.optInt("sy", 1));
        }
        return app(object.optString("p", ""));
    }
}
