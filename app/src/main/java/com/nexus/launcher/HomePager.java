package com.nexus.launcher;

import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Horizontally swipeable home: renders every page of the {@link HomeLayout}, keeps the page
 * indicator in sync and owns the drag and drop gesture between pages.
 */
public class HomePager extends FrameLayout {

    public interface Listener {
        void onItemTap(HomeItem item, int pageIndex);
        void onWidgetMenu(HomeItem item, int pageIndex);
    }

    private static final long EDGE_DELAY_MS = 340L;

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final HomeLayout layout;
    private final WidgetHost widgetHost;
    private final Map<String, AppInfo> appIndex;

    private final Strip strip;
    private final LinearLayout dots;
    private final List<HomePageView> pageViews = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private int currentPage;
    private boolean firstLayout = true;
    private ValueAnimator scrollAnimator;

    private boolean dragging;
    private HomeItem dragItem;
    private HomePageView dragSource;
    private int dragFromPage = -1;
    private int dragIndex = -1;
    private View ghost;
    private int ghostWidth;
    private int ghostHeight;
    private int targetPage = -1;
    private int targetCell = -1;
    private int edgeDirection;

    private final Runnable edgeSwitchRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging || edgeDirection == 0) {
                return;
            }
            int next = currentPage + edgeDirection;
            if (next >= 0 && next < pageViews.size()) {
                setCurrentPage(next, animationsOn());
            }
            edgeDirection = 0;
        }
    };

    private final Runnable restoreScroll = new Runnable() {
        @Override
        public void run() {
            strip.scrollTo(currentPage * pageWidth(), 0);
            applyPageAlpha();
            updateDots();
            applyWidgetSizes();
            int capacity = capacity();
            if (capacity > 0 && layout.rebalance(capacity)) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        rebuild();
                    }
                });
            }
        }
    };

    public HomePager(MainActivity activity, LauncherSettings settings, HomeLayout layout,
                     WidgetHost widgetHost, Map<String, AppInfo> appIndex) {
        super(activity);
        this.activity = activity;
        this.settings = settings;
        this.layout = layout;
        this.widgetHost = widgetHost;
        this.appIndex = appIndex;

        strip = new Strip(activity);
        strip.setClipChildren(false);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        stripParams.bottomMargin = Ui.dp(activity, 18);
        addView(strip, stripParams);

        dots = new LinearLayout(activity);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 16),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotParams.bottomMargin = Ui.dp(activity, 2);
        addView(dots, dotParams);

        currentPage = layout.defaultPage();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public HomeLayout getLayout() {
        return layout;
    }

    public WidgetHost getWidgetHost() {
        return widgetHost;
    }

    public LauncherSettings getSettings() {
        return settings;
    }

    public AppInfo getApp(String packageName) {
        if (packageName == null) {
            return null;
        }
        return appIndex.get(packageName);
    }

    public int currentPage() {
        return currentPage;
    }

    public int pageCount() {
        return pageViews.size();
    }

    public HomePageView pageView(int index) {
        if (index < 0 || index >= pageViews.size()) {
            return null;
        }
        return pageViews.get(index);
    }

    /** Number of cells currently available on a page. */
    public int capacity() {
        HomePageView page = pageView(currentPage);
        if (page == null) {
            page = pageView(pageViews.size() - 1);
        }
        return page != null ? page.capacity() : Math.max(4, settings.getColumns(activity) * 4);
    }

    public int occupiedCells() {
        HomePageView page = pageView(currentPage);
        return page != null ? page.occupiedCells() : 0;
    }

    // ------------------------------------------------------------------ pages

    public void rebuild() {
        if (dragging) {
            finishDrag(dragSource);
        }
        strip.removeAllViews();
        pageViews.clear();
        int count = layout.pageCount();
        for (int i = 0; i < count; i++) {
            HomePageView page = new HomePageView(this, settings, i);
            page.bind();
            pageViews.add(page);
            strip.addView(page, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (firstLayout && !pageViews.isEmpty()) {
            firstLayout = false;
            currentPage = layout.defaultPage();
        }
        currentPage = Math.max(0, Math.min(currentPage, Math.max(0, pageViews.size() - 1)));
        buildDots();
        strip.requestLayout();
        post(restoreScroll);
    }

    private void buildDots() {
        dots.removeAllViews();
        if (pageViews.size() <= 1) {
            dots.setVisibility(GONE);
            return;
        }
        dots.setVisibility(VISIBLE);
        for (int i = 0; i < pageViews.size(); i++) {
            View dot = new View(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 6), Ui.dp(activity, 6));
            params.setMargins(Ui.dp(activity, 3), 0, Ui.dp(activity, 3), 0);
            dot.setLayoutParams(params);
            dots.addView(dot);
        }
        updateDots();
    }

    private void updateDots() {
        int count = dots.getChildCount();
        for (int i = 0; i < count; i++) {
            View dot = dots.getChildAt(i);
            boolean active = i == currentPage;
            dot.setBackground(Ui.rounded(
                    active ? settings.accentColor(activity)
                            : (settings.isDark(activity) ? 0x66FFFFFF : 0x66000000),
                    Ui.dp(activity, 3)));
            if (animationsOn()) {
                dot.setScaleX(active ? 1.35f : 1f);
                dot.setScaleY(active ? 1.35f : 1f);
            }
            dot.setAlpha(active ? 1f : 0.8f);
        }
    }

    public int pageWidth() {
        int width = getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(1, width);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            strip.scrollTo(currentPage * pageWidth(), 0);
            post(restoreScroll);
        }
    }

    public void setCurrentPage(int page, boolean animate) {
        if (pageViews.isEmpty()) {
            currentPage = 0;
            return;
        }
        page = Math.max(0, Math.min(page, pageViews.size() - 1));
        currentPage = page;
        int targetX = page * pageWidth();
        if (animate && animationsOn()) {
            animateScrollTo(targetX);
        } else {
            cancelScrollAnimation();
            strip.scrollTo(targetX, 0);
        }
        applyPageAlpha();
        updateDots();
        applyWidgetSizes();
    }

    public void scrollByPixels(float delta) {
        cancelScrollAnimation();
        int max = Math.max(0, (pageViews.size() - 1) * pageWidth());
        int target = (int) (strip.getScrollX() + delta);
        strip.scrollTo(Math.max(0, Math.min(max, target)), 0);
        applyPageAlpha();
    }

    private void animateScrollTo(int targetX) {
        cancelScrollAnimation();
        final int start = strip.getScrollX();
        final int delta = targetX - start;
        if (delta == 0) {
            return;
        }
        int duration = settings.animDuration(Math.min(280, 150 + Math.abs(delta) / 6));
        if (duration <= 0) {
            strip.scrollTo(targetX, 0);
            applyPageAlpha();
            return;
        }
        scrollAnimator = ValueAnimator.ofFloat(0f, 1f);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new DecelerateInterpolator());
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = (float) animation.getAnimatedValue();
                strip.scrollTo(start + Math.round(delta * fraction), 0);
                applyPageAlpha();
            }
        });
        scrollAnimator.start();
    }

    private boolean animationsOn() {
        return settings.animationsEnabled() && !settings.isLite();
    }

    /** Cross-fades pages when the user picked theDissolvenza page transition. */
    private void applyPageAlpha() {
        if (pageViews.isEmpty()) {
            return;
        }
        if (settings.getPageEffect() != LauncherSettings.PAGE_FADE) {
            for (HomePageView page : pageViews) {
                if (page.getAlpha() != 1f) {
                    page.setAlpha(1f);
                }
            }
            return;
        }
        float width = pageWidth();
        float offset = strip.getScrollX() / width;
        for (int i = 0; i < pageViews.size(); i++) {
            float distance = Math.min(1f, Math.abs(i - offset));
            pageViews.get(i).setAlpha(1f - distance * 0.8f);
        }
    }

    private void cancelScrollAnimation() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    public void settlePages() {
        int page = Math.round((float) strip.getScrollX() / pageWidth());
        setCurrentPage(page, settings.getPageEffect() != LauncherSettings.PAGE_NONE);
    }

    // ------------------------------------------------------------------- drag

    public boolean isDragging() {
        return dragging;
    }

    public void beginDrag(HomePageView source, int index) {
        if (dragging) {
            return;
        }
        int page = pageViews.indexOf(source);
        HomeItem item = layout.item(page, index);
        if (item == null) {
            return;
        }
        dragging = true;
        dragItem = item;
        dragIndex = index;
        dragFromPage = page;
        dragSource = source;

        ghost = source.createGhostView(index);
        ghostWidth = Ui.dp(activity, 66);
        ghostHeight = Ui.dp(activity, 86);
        addView(ghost, new FrameLayout.LayoutParams(ghostWidth, ghostHeight));
        ghost.setElevation(Ui.dp(activity, 10));
        source.setDraggedIndex(index);

        View child = source.childViewAt(index);
        if (child != null) {
            int[] location = new int[2];
            child.getLocationOnScreen(location);
            moveGhost(location[0] + child.getWidth() / 2f, location[1] + child.getHeight() / 2f);
        }
    }

    public void dragMove(float rawX, float rawY) {
        if (dragging) {
            moveGhost(rawX, rawY);
        }
    }

    private void moveGhost(float rawX, float rawY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = rawX - location[0];
        float y = rawY - location[1];

        if (ghost != null) {
            ghost.setX(x - ghostWidth / 2f);
            ghost.setY(y - ghostHeight / 2f);
        }
        if (pageViews.isEmpty()) {
            return;
        }

        int width = pageWidth();
        int page = Math.max(0, Math.min(pageViews.size() - 1,
                (int) ((strip.getScrollX() + x) / width)));
        HomePageView pageView = pageViews.get(page);
        float pageX = strip.getScrollX() + x - page * width;
        int cell = pageView.cellIndexAt(pageX, y);

        targetPage = page;
        targetCell = cell;
        for (int i = 0; i < pageViews.size(); i++) {
            pageViews.get(i).setHighlightCell(i == page ? cell : -1);
        }

        int direction = 0;
        int edge = Ui.dp(activity, 26);
        if (x < edge && currentPage > 0) {
            direction = -1;
        } else if (x > width - edge && currentPage < pageViews.size() - 1) {
            direction = 1;
        }
        if (direction != edgeDirection) {
            edgeDirection = direction;
            handler.removeCallbacks(edgeSwitchRunnable);
            if (direction != 0) {
                handler.postDelayed(edgeSwitchRunnable, EDGE_DELAY_MS);
            }
        }
    }

    public void dragEnd() {
        if (!dragging) {
            return;
        }
        HomeItem item = dragItem;
        int fromPage = dragFromPage;
        int fromIndex = dragIndex;
        int page = targetPage;
        int cell = targetCell;
        HomePageView source = dragSource;
        finishDrag(source);

        if (item == null || page < 0 || page >= pageViews.size()) {
            rebuild();
            return;
        }

        HomePageView targetView = pageViews.get(page);
        boolean changed = false;
        if (targetView != null && cell >= 0) {
            int occupant = targetView.itemIndexForCell(cell);
            HomeItem other = occupant >= 0 ? layout.item(page, occupant) : null;
            int insert = targetView.insertionIndexForCell(cell);
            if (other != null && other != item) {
                if (other.isFolder() && item.isApp()) {
                    changed = layout.addToFolder(other, item.getPackageName());
                } else if (other.isApp() && item.isApp()) {
                    changed = layout.createFolder(page, occupant, item) != null;
                } else if (other.isFolder() && item.isFolder()) {
                    changed = layout.mergeFolders(other, item);
                } else {
                    changed = layout.move(fromPage, fromIndex, page, insert);
                }
            } else {
                changed = layout.move(fromPage, fromIndex, page, insert);
            }
        }
        rebuild();
        if (changed) {
            layout.save();
        }
    }

    public void dragCancel() {
        if (!dragging) {
            return;
        }
        finishDrag(dragSource);
        rebuild();
    }

    private void finishDrag(HomePageView source) {
        handler.removeCallbacks(edgeSwitchRunnable);
        edgeDirection = 0;
        dragging = false;
        dragItem = null;
        dragIndex = -1;
        dragFromPage = -1;
        dragSource = null;
        targetPage = -1;
        targetCell = -1;
        if (ghost != null) {
            removeView(ghost);
            ghost = null;
        }
        if (source != null) {
            source.setDraggedIndex(-1);
        }
        for (HomePageView page : pageViews) {
            page.setHighlightCell(-1);
        }
    }

    // ------------------------------------------------------------------ hooks

    void notifyItemTap(HomeItem item, int pageIndex) {
        if (listener != null) {
            listener.onItemTap(item, pageIndex);
        }
    }

    public void notifyWidgetMenu(HomeItem item, int pageIndex) {
        if (listener != null) {
            listener.onWidgetMenu(item, pageIndex);
        }
    }

    /** Reports the real size of every placed widget so providers can lay themselves out. */
    public void applyWidgetSizes() {
        for (HomePageView page : pageViews) {
            for (int i = 0; i < page.itemCount(); i++) {
                HomeItem item = page.itemAt(i);
                if (item == null || item.getSpanX() <= 0) {
                    continue;
                }
                View child = page.childViewAt(i);
                if (child instanceof AppWidgetHostView && child.getWidth() > 0) {
                    widgetHost.updateWidgetSize(item.getAppWidgetId(),
                            child.getWidth(), child.getHeight());
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(edgeSwitchRunnable);
        super.onDetachedFromWindow();
    }

    /** Horizontal strip that hosts one page per screen width. */
    private static class Strip extends ViewGroup {

        Strip(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = getWidth();
            for (int i = 0; i < getChildCount(); i++) {
                int left = i * width;
                getChildAt(i).layout(left, 0, left + width, getHeight());
            }
        }
    }
}
