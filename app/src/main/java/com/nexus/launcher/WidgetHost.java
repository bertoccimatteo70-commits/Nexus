package com.nexus.launcher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Real AppWidgetHost integration: picks, binds, configures, renders, resizes and deletes
 * home screen widgets through the system widget APIs.
 */
public class WidgetHost extends AppWidgetHost {

    public static final int REQ_PICK = 9110;
    public static final int REQ_BIND = 9111;
    public static final int REQ_CONFIGURE = 9112;

    public interface OnWidgetPicked {
        void onWidgetPicked(int appWidgetId, String label, int spanX, int spanY);
    }

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final AppWidgetManager manager;
    private final Map<Integer, AppWidgetHostView> views = new HashMap<>();

    private int pendingId = -1;
    private int pendingSpanX = 1;
    private int pendingSpanY = 1;
    private String pendingLabel = "Widget";
    private ComponentName pendingProvider;
    private OnWidgetPicked callback;

    public WidgetHost(MainActivity activity, LauncherSettings settings) {
        super(activity, hostId(settings));
        this.activity = activity;
        this.settings = settings;
        this.manager = AppWidgetManager.getInstance(activity);
    }

    private static int hostId(LauncherSettings settings) {
        int id = settings.getWidgetHostId();
        if (id <= 0) {
            id = 1000 + (int) (System.currentTimeMillis() % 60000L);
            settings.setWidgetHostId(id);
        }
        return id;
    }

    public void setOnWidgetPicked(OnWidgetPicked callback) {
        this.callback = callback;
    }

    public void start() {
        try {
            startListening();
        } catch (Exception ignored) {
            // the host keeps working for already bound widgets
        }
    }

    public void stop() {
        try {
            stopListening();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    public void release() {
        stop();
        views.clear();
    }

    // ------------------------------------------------------------- widget pick

    public void pickWidget() {
        int appWidgetId;
        try {
            appWidgetId = allocateAppWidgetId();
        } catch (Exception e) {
            toast("Impossibile allocare un widget");
            return;
        }
        pendingId = appWidgetId;

        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        pick.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, new ArrayList<Bundle>());
        pick.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, new ArrayList<Bundle>());
        try {
            activity.startActivityForResult(pick, REQ_PICK);
        } catch (Exception e) {
            try {
                deleteAppWidgetId(appWidgetId);
            } catch (Exception ignored) {
                // already gone
            }
            pendingId = -1;
            toast("Nessun selettore widget disponibile su questo dispositivo");
        }
    }

    /** Returns true when the result belongs to the widget flow. */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
                if (id > 0) {
                    pendingId = id;
                }
                pendingProvider = readProvider(data);
                completeWidget();
            } else {
                discardPending();
            }
            return true;
        }
        if (requestCode == REQ_BIND) {
            if (resultCode == Activity.RESULT_OK) {
                completeWidget();
            } else {
                discardPending();
                toast("Widget non autorizzato");
            }
            return true;
        }
        if (requestCode == REQ_CONFIGURE) {
            deliverPending();
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private ComponentName readProvider(Intent data) {
        try {
            return data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER);
        } catch (Exception e) {
            return null;
        }
    }

    private void completeWidget() {
        if (pendingId <= 0) {
            return;
        }
        AppWidgetProviderInfo info;
        try {
            info = manager.getAppWidgetInfo(pendingId);
        } catch (Exception e) {
            info = null;
        }
        if (info == null) {
            if (pendingProvider != null) {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, pendingProvider);
                try {
                    activity.startActivityForResult(bind, REQ_BIND);
                    return;
                } catch (Exception ignored) {
                    // fall through to the failure path
                }
            }
            discardPending();
            toast("Impossibile aggiungere il widget");
            return;
        }

        pendingProvider = info.provider;
        pendingLabel = info.loadLabel(activity.getPackageManager());

        int columns = Math.max(3, settings.getColumns(activity));
        int screenDp = activity.getResources().getConfiguration().screenWidthDp;
        if (screenDp <= 0) {
            screenDp = 360;
        }
        int cellDp = Math.max(1, screenDp / columns);
        pendingSpanX = Math.max(1, Math.min(columns,
                (int) Math.ceil(info.minWidth / (double) cellDp)));
        pendingSpanY = Math.max(1, Math.min(4,
                (int) Math.ceil(info.minHeight / 110d)));

        if (info.configure != null) {
            try {
                startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null);
                return;
            } catch (Exception ignored) {
                // widget without a usable configuration activity
            }
        }
        deliverPending();
    }

    private void deliverPending() {
        if (pendingId > 0 && callback != null) {
            callback.onWidgetPicked(pendingId, pendingLabel, pendingSpanX, pendingSpanY);
        }
        pendingId = -1;
        pendingProvider = null;
        pendingLabel = "Widget";
        pendingSpanX = 1;
        pendingSpanY = 1;
    }

    private void discardPending() {
        if (pendingId > 0) {
            try {
                deleteAppWidgetId(pendingId);
            } catch (Exception ignored) {
                // already released
            }
        }
        pendingId = -1;
        pendingProvider = null;
        pendingLabel = "Widget";
        pendingSpanX = 1;
        pendingSpanY = 1;
    }

    // -------------------------------------------------------------- host views

    public AppWidgetHostView viewFor(int appWidgetId) {
        if (appWidgetId < 0) {
            return null;
        }
        AppWidgetHostView existing = views.get(appWidgetId);
        if (existing != null) {
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) {
                parent.removeView(existing);
            }
            return existing;
        }
        AppWidgetProviderInfo info;
        try {
            info = manager.getAppWidgetInfo(appWidgetId);
        } catch (Exception e) {
            info = null;
        }
        if (info == null) {
            return null;
        }
        try {
            AppWidgetHostView view = createView(activity, appWidgetId, info);
            views.put(appWidgetId, view);
            return view;
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteWidget(int appWidgetId) {
        views.remove(appWidgetId);
        try {
            deleteAppWidgetId(appWidgetId);
        } catch (Exception ignored) {
            // already deleted
        }
    }

    /** Reports the real pixel size of a placed widget so it can relayout after resizing. */
    public void updateWidgetSize(int appWidgetId, int widthPx, int heightPx) {
        AppWidgetHostView view = views.get(appWidgetId);
        if (view == null) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int widthDp = Math.max(1, Math.round(widthPx / density));
        int heightDp = Math.max(1, Math.round(heightPx / density));
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Math.max(1, widthDp - 8));
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Math.max(1, heightDp - 8));
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
        try {
            view.updateAppWidgetSize(options, Math.max(1, widthDp - 8), Math.max(1, heightDp - 8),
                    widthDp, heightDp);
        } catch (Exception ignored) {
            // provider without size support
        }
        view.requestLayout();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
