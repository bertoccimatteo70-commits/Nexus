package com.nexus.launcher;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One home page. Children are positioned on a real cell grid so applications, folders and
 * widgets share the same layout. Handles taps, horizontal page swipes and long press drag.
 */
public class HomePageView extends ViewGroup {

    private static final long LONG_PRESS_MS = 380L;

    private final HomePager pager;
    private final LauncherSettings settings;
    private final int pageIndex;
    private final int touchSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<HomeItem> items = new ArrayList<>();
    private final List<RectF> itemBounds = new ArrayList<>();
    private final List<Integer> itemStartCells = new ArrayList<>();

    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightRect = new RectF();

    private int columns = 4;
    private int iconPx = 128;
    private int cellW;
    private int cellH;
    private int rows = 4;

    private int draggedIndex = -1;
    private int highlightCell = -1;
    private boolean bound;

    private float downX;
    private float downY;
    private float lastX;
    private boolean moved;
    private int pressedIndex = -1;
    private boolean intercepting;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (moved || pressedIndex < 0 || pressedIndex >= items.size()) {
                return;
            }
            HomeItem item = items.get(pressedIndex);
            if (item.isWidget()) {
                pager.notifyWidgetMenu(item, pageIndex);
                return;
            }
            intercepting = true;
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
            pager.beginDrag(HomePageView.this, pressedIndex);
        }
    };

    public HomePageView(HomePager pager, LauncherSettings settings, int pageIndex) {
        super(pager.getContext());
        this.pager = pager;
        this.settings = settings;
        this.pageIndex = pageIndex;
        this.touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor((settings.accentColor(getContext()) & 0x00FFFFFF) | 0x40000000);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    /** Rebuilds every child from the persisted layout. */
    public void bind() {
        removeAllViews();
        items.clear();
        itemBounds.clear();
        itemStartCells.clear();
        HomeLayout.Page page = pager.getLayout().page(pageIndex);
        if (page != null) {
            items.addAll(page.items);
        }
        for (HomeItem item : items) {
            addView(createChild(item));
        }
        bound = true;
        setAlpha(1f);
        requestLayout();
    }

    /** True when the page currently owns its child views. */
    public boolean isBound() {
        return bound;
    }

    /**
     * Releases the views of a page that is far from the visible one. The widgets stay bound to
     * the system host, so their state is kept and they are reattached on the next bind().
     */
    public void unbind() {
        if (!bound) {
            return;
        }
        removeAllViews();
        items.clear();
        itemBounds.clear();
        itemStartCells.clear();
        bound = false;
        draggedIndex = -1;
        highlightCell = -1;
        setAlpha(1f);
    }

    // ------------------------------------------------------------------ views

    private View createChild(HomeItem item) {
        if (item.isWidget()) {
            AppWidgetHostView hostView = pager.getWidgetHost().viewFor(item.getAppWidgetId());
            if (hostView != null) {
                return hostView;
            }
            return buildWidgetPlaceholder(item);
        }
        if (item.isFolder()) {
            return buildFolderCell(item);
        }
        return buildAppCell(item);
    }

    private View buildAppCell(HomeItem item) {
        Context context = getContext();
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClickable(false);
        cell.setFocusable(false);

        AppInfo app = pager.getApp(item.getPackageName());
        ImageView icon = new ImageView(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (app != null) {
            icon.setImageDrawable(app.getRoundedIcon(iconPx, settings.getIconShape()));
        }
        cell.addView(icon);

        if (settings.showLabels()) {
            TextView label = new TextView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Ui.dp(context, 4);
            label.setLayoutParams(params);
            label.setText(app != null ? app.getLabel() : item.getPackageName());
            label.setTextSize((settings.isLite() ? 10f : 11f) * settings.labelScale());
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(settings.textColor(context));
            if (!settings.isLite()) {
                label.setShadowLayer(3f, 0f, 1f, 0x66000000);
            }
            cell.addView(label);
        }
        return cell;
    }

    private View buildFolderCell(HomeItem item) {
        Context context = getContext();
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClickable(false);
        cell.setFocusable(false);

        FrameLayout preview = new FrameLayout(context);
        preview.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
        int accent = settings.accentColor(context);
        int folderColor;
        int folderStroke;
        int style = settings.getFolderStyle();
        if (style == LauncherSettings.FOLDER_ACCENT) {
            folderColor = (accent & 0x00FFFFFF) | 0x33000000;
            folderStroke = (accent & 0x00FFFFFF) | 0x66000000;
        } else if (style == LauncherSettings.FOLDER_SOLID) {
            folderColor = settings.surfaceColor(context);
            folderStroke = settings.rowBackground(context);
        } else {
            folderColor = settings.isDark(context) ? 0x33FFFFFF : 0x55FFFFFF;
            folderStroke = settings.isDark(context) ? 0x33FFFFFF : 0x66000000;
        }
        preview.setBackground(Ui.rounded(folderColor,
                Ui.dp(context, 16), Ui.dp(context, 1), folderStroke));

        LinearLayout mini = new LinearLayout(context);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setPadding(Ui.dp(context, 8), Ui.dp(context, 8),
                Ui.dp(context, 8), Ui.dp(context, 8));
        mini.setGravity(Gravity.CENTER);

        List<String> children = item.getChildren();
        int miniSize = Math.max(Ui.dp(context, 12), (iconPx - Ui.dp(context, 20)) / 2);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int colIndex = 0; colIndex < 2; colIndex++) {
                int index = rowIndex * 2 + colIndex;
                ImageView icon = new ImageView(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(miniSize, miniSize);
                params.setMargins(Ui.dp(context, 1), Ui.dp(context, 1), Ui.dp(context, 1), Ui.dp(context, 1));
                icon.setLayoutParams(params);
                if (index < children.size()) {
                    AppInfo app = pager.getApp(children.get(index));
                    if (app != null) {
                        icon.setImageDrawable(app.getRoundedIcon(miniSize, settings.getIconShape()));
                    }
                }
                row.addView(icon);
            }
            mini.addView(row);
        }
        preview.addView(mini, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cell.addView(preview);

        if (settings.showLabels()) {
            TextView label = new TextView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Ui.dp(context, 4);
            label.setLayoutParams(params);
            label.setText(item.getLabel());
            label.setTextSize((settings.isLite() ? 10f : 11f) * settings.labelScale());
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(settings.textColor(context));
            if (!settings.isLite()) {
                label.setShadowLayer(3f, 0f, 1f, 0x66000000);
            }
            cell.addView(label);
        }
        return cell;
    }

    private View buildWidgetPlaceholder(HomeItem item) {
        Context context = getContext();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(context, 10), Ui.dp(context, 10),
                Ui.dp(context, 10), Ui.dp(context, 10));
        box.setBackground(Ui.rounded(settings.surfaceColor(context), Ui.dp(context, 20),
                Ui.dp(context, 1), settings.rowBackground(context)));
        box.setClickable(false);

        TextView title = new TextView(context);
        title.setText(item.getLabel());
        title.setTextSize(12f);
        title.setTextColor(settings.textColor(context));
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView hint = new TextView(context);
        hint.setText("Widget non disponibile - tieni premuto per rimuoverlo");
        hint.setTextSize(10f);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(settings.secondaryTextColor(context));
        box.addView(hint);
        return box;
    }

    /** Small copy of an item used as the floating drag preview. */
    public View createGhostView(int index) {
        Context context = getContext();
        LinearLayout ghost = new LinearLayout(context);
        ghost.setOrientation(LinearLayout.VERTICAL);
        ghost.setGravity(Gravity.CENTER_HORIZONTAL);
        ghost.setAlpha(0.92f);

        HomeItem item = items.get(index);
        ImageView icon = new ImageView(context);
        int size = settings.getIconSizePx(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (item.isWidget()) {
            icon.setImageResource(R.drawable.ic_apps);
            icon.setColorFilter(settings.textColor(context));
        } else if (item.isFolder()) {
            icon.setImageResource(R.drawable.ic_apps);
            icon.setColorFilter(settings.accentColor(context));
        } else {
            AppInfo app = pager.getApp(item.getPackageName());
            if (app != null) {
                icon.setImageDrawable(app.getRoundedIcon(size, settings.getIconShape()));
            }
        }
        ghost.addView(icon);

        TextView label = new TextView(context);
        label.setText(item.isFolder() ? item.getLabel()
                : item.isWidget() ? item.getLabel()
                : appLabel(item));
        label.setTextSize(10f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(settings.textColor(context));
        label.setShadowLayer(3f, 0f, 1f, 0x66000000);
        ghost.addView(label);
        return ghost;
    }

    private String appLabel(HomeItem item) {
        AppInfo app = pager.getApp(item.getPackageName());
        return app != null ? app.getLabel() : item.getPackageName();
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        columns = Math.max(3, settings.getColumns(getContext()));
        iconPx = settings.getIconSizePx(getContext());
        cellW = Math.max(1, width / columns);
        int labelHeight = settings.showLabels()
                ? Ui.dp(getContext(), 16 * settings.labelScale()) : 0;
        cellH = iconPx + labelHeight + Ui.dp(getContext(), 8);
        rows = Math.max(1, height / Math.max(1, cellH));

        int gap = Ui.dp(getContext(), 4);
        itemBounds.clear();
        itemStartCells.clear();

        int row = 0;
        int col = 0;
        for (int i = 0; i < getChildCount(); i++) {
            HomeItem item = i < items.size() ? items.get(i) : null;
            if (item == null) {
                continue;
            }
            int spanX = item.isWidget() ? Math.min(item.getSpanX(), columns) : 1;
            int spanY = item.isWidget() ? Math.min(item.getSpanY(), rows) : 1;
            if (col + spanX > columns) {
                row++;
                col = 0;
            }
            if (row + spanY > rows) {
                getChildAt(i).setVisibility(GONE);
                itemStartCells.add(-1);
                itemBounds.add(new RectF());
                continue;
            }
            getChildAt(i).setVisibility(VISIBLE);
            float left = col * cellW + gap / 2f;
            float top = row * cellH + gap / 2f;
            float right = (col + spanX) * cellW - gap / 2f;
            float bottom = (row + spanY) * cellH - gap / 2f;
            itemStartCells.add(row * columns + col);
            itemBounds.add(new RectF(left, top, right, bottom));

            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(Math.round(right - left), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(bottom - top), MeasureSpec.EXACTLY));

            col += spanX;
            if (col >= columns) {
                col = 0;
                row++;
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount() && i < itemBounds.size(); i++) {
            RectF bounds = itemBounds.get(i);
            if (bounds.width() <= 0f || bounds.height() <= 0f) {
                continue;
            }
            View child = getChildAt(i);
            child.layout(Math.round(bounds.left), Math.round(bounds.top),
                    Math.round(bounds.right), Math.round(bounds.bottom));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (highlightCell < 0) {
            return;
        }
        int column = highlightCell % columns;
        int row = highlightCell / columns;
        int gap = Ui.dp(getContext(), 4);
        highlightRect.set(column * cellW + gap / 2f, row * cellH + gap / 2f,
                (column + 1) * cellW - gap / 2f, (row + 1) * cellH - gap / 2f);
        float radius = Ui.dp(getContext(), 18);
        int accent = settings.accentColor(getContext());
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor((accent & 0x00FFFFFF) | 0x40000000);
        canvas.drawRoundRect(highlightRect, radius, radius, highlightPaint);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Ui.dp(getContext(), 2));
        highlightPaint.setColor((accent & 0x00FFFFFF) | 0xCC000000);
        canvas.drawRoundRect(highlightRect, radius, radius, highlightPaint);
    }

    // --------------------------------------------------------------- geometry

    public int capacity() {
        return Math.max(columns * rows, columns);
    }

    public int occupiedCells() {
        java.util.List<HomeItem> source = items;
        if (!bound) {
            HomeLayout.Page page = pager.getLayout().page(pageIndex);
            source = page != null ? page.items : items;
        }
        int total = 0;
        for (HomeItem item : source) {
            total += item.isWidget() ? Math.min(item.getSpanX(), columns) * item.getSpanY() : 1;
        }
        return total;
    }

    public int cellIndexAt(float x, float y) {
        if (cellW <= 0 || cellH <= 0) {
            return 0;
        }
        int column = Math.max(0, Math.min(columns - 1, (int) (x / cellW)));
        int row = Math.max(0, Math.min(Math.max(rows - 1, 0), (int) (y / cellH)));
        return row * columns + column;
    }

    public int itemIndexAt(float x, float y) {
        for (int i = 0; i < itemBounds.size(); i++) {
            if (itemBounds.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    public int itemIndexForCell(int cell) {
        if (cell < 0) {
            return -1;
        }
        for (int i = 0; i < itemStartCells.size(); i++) {
            int start = itemStartCells.get(i);
            if (start < 0) {
                continue;
            }
            int span = items.get(i).isWidget()
                    ? Math.min(items.get(i).getSpanX(), columns) * items.get(i).getSpanY() : 1;
            if (cell >= start && cell < start + span) {
                return i;
            }
        }
        return -1;
    }

    public int insertionIndexForCell(int cell) {
        if (cell < 0) {
            return items.size();
        }
        for (int i = 0; i < itemStartCells.size(); i++) {
            int start = itemStartCells.get(i);
            if (start >= 0 && start >= cell) {
                return i;
            }
        }
        return items.size();
    }

    public void setHighlightCell(int cell) {
        if (highlightCell != cell) {
            highlightCell = cell;
            invalidate();
        }
    }

    public void setDraggedIndex(int index) {
        draggedIndex = index;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setAlpha(i == draggedIndex ? 0.3f : 1f);
        }
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                intercepting = false;
                downX = ev.getX();
                downY = ev.getY();
                lastX = downX;
                moved = false;
                pressedIndex = itemIndexAt(downX, downY);
                handler.removeCallbacks(longPressRunnable);
                if (pressedIndex >= 0) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (intercepting) {
                    return true;
                }
                if (Math.abs(ev.getX() - downX) > touchSlop
                        || Math.abs(ev.getY() - downY) > touchSlop) {
                    moved = true;
                    handler.removeCallbacks(longPressRunnable);
                }
                return false;
            default:
                handler.removeCallbacks(longPressRunnable);
                return intercepting;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (pager.isDragging()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    pager.dragMove(ev.getRawX(), ev.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    pager.dragEnd();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pager.dragCancel();
                    return true;
                default:
                    return true;
            }
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                lastX = downX;
                moved = false;
                pressedIndex = itemIndexAt(downX, downY);
                handler.removeCallbacks(longPressRunnable);
                if (pressedIndex >= 0) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - lastX;
                if (!moved && Math.abs(ev.getX() - downX) > touchSlop) {
                    moved = true;
                    handler.removeCallbacks(longPressRunnable);
                }
                if (moved) {
                    pager.scrollByPixels(-dx);
                }
                lastX = ev.getX();
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (moved) {
                    pager.settlePages();
                } else if (pressedIndex >= 0 && pressedIndex < items.size()) {
                    pager.notifyItemTap(items.get(pressedIndex), pageIndex);
                }
                moved = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                if (moved) {
                    pager.settlePages();
                }
                moved = false;
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }

    public int itemCount() {
        return items.size();
    }

    public HomeItem itemAt(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    public View childViewAt(int index) {
        if (index < 0 || index >= getChildCount()) {
            return null;
        }
        return getChildAt(index);
    }

    public void cancelGesture() {
        handler.removeCallbacks(longPressRunnable);
    }
}
