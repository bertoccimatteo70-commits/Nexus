package com.nexus.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Horizontally swipeable home, built like the iOS pager: the strip follows the finger one to
 * one, keeps the inertia of the gesture, snaps to the closest page and rubber-bands past the
 * first and last page. Pages are bound lazily so only what can be seen exists as views.
 */
public class HomePager extends FrameLayout {

    public interface Listener {
        void onItemTap(HomeItem item, int pageIndex);

        void onWidgetMenu(HomeItem item, int pageIndex);

        /** Nexus Lite asks for a menu instead of starting a drag. */
        default void onItemLongPress(HomeItem item, int pageIndex) {
        }

        /** Fractional page position, used for the wallpaper parallax. */
        default void onPageScrolled(float pagePosition) {
        }
    }

    private static final long EDGE_DELAY_MS = 340L;
    /** Extra travel allowed past the first and last page, as a fraction of the width. */
    private static final float EDGE_RANGE = 0.6f;
    /** Finger travel is reduced by this factor while past the edges. */
    private static final float EDGE_RESISTANCE = 0.55f;

    private final MainActivity activity;
    private final LauncherSettings settings;
    private HomeLayout layout;
    private final WidgetHost widgetHost;
    private final Map<String, AppInfo> appIndex;

    private final Strip strip;
    private final LinearLayout dots;
    private final List<HomePageView> pageViews = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VelocityTracker velocityTracker;
    private final int touchSlop;
    private final int maxFlingVelocity;

    private Listener listener;
    private int currentPage;
    private int boundCenter = Integer.MIN_VALUE;
    private boolean firstLayout = true;
    private ValueAnimator scrollAnimator;

    /** Content offset in pixels: 0 is the first page, it may exceed the edges while dragging. */
    private float pagePosition;
    private boolean trackingTouch;
    private boolean pagingTouch;
    private float downX;
    private float downY;
    private float lastTouchX;
    private int lastActiveDot = -1;

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
                setCurrentPage(next, true);
            }
            edgeDirection = 0;
        }
    };

    private final Runnable trimRunnable = new Runnable() {
        @Override
        public void run() {
            trimAround(currentPage);
        }
    };

    private final Runnable bindNeighbours = new Runnable() {
        @Override
        public void run() {
            if (pageViews.isEmpty()) {
                return;
            }
            int from = Math.max(0, currentPage - 1);
            int to = Math.min(pageViews.size() - 1, currentPage + 1);
            for (int i = from; i <= to; i++) {
                HomePageView view = pageViews.get(i);
                if (!view.isBound()) {
                    view.bind();
                }
            }
        }
    };

    private final Runnable restoreScroll = new Runnable() {
        @Override
        public void run() {
            if (pageViews.isEmpty()) {
                return;
            }
            bindAround(currentPage);
            settleAt(currentPage);
            post(bindNeighbours);
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

        ViewConfiguration configuration = ViewConfiguration.get(activity);
        touchSlop = configuration.getScaledTouchSlop();
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        velocityTracker = VelocityTracker.obtain();

        setClipChildren(false);
        setClipToPadding(false);

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

    /** Swaps the persisted layout, used when Nexus and Nexus Lite are switched at runtime. */
    public void setLayout(HomeLayout layout) {
        if (layout == null || layout == this.layout) {
            return;
        }
        this.layout = layout;
        this.currentPage = layout.defaultPage();
        this.firstLayout = true;
        this.boundCenter = Integer.MIN_VALUE;
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
        cancelScrollAnimation();
        strip.removeAllViews();
        strip.setTranslationX(0f);
        pageViews.clear();
        boundCenter = Integer.MIN_VALUE;
        int count = layout.pageCount();
        for (int i = 0; i < count; i++) {
            HomePageView page = new HomePageView(this, settings, i);
            pageViews.add(page);
            strip.addView(page, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (firstLayout && !pageViews.isEmpty()) {
            firstLayout = false;
            currentPage = layout.defaultPage();
        }
        currentPage = Math.max(0, Math.min(currentPage, Math.max(0, pageViews.size() - 1)));
        bindAround(currentPage);
        buildDots();
        strip.requestLayout();
        post(restoreScroll);
    }

    private void buildDots() {
        dots.removeAllViews();
        lastActiveDot = -1;
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
        updateDotsAt(currentPage);
    }

    private void restyleDots(int active) {
        int accent = settings.accentColor(activity);
        int idle = settings.isDark(activity) ? 0x66FFFFFF : 0x66000000;
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackground(Ui.rounded(
                    i == active ? accent : idle, Ui.dp(activity, 3)));
        }
    }

    /** Dots grow while the pages move, so they track the gesture instead of jumping. */
    private void updateDotsAt(float position) {
        int count = dots.getChildCount();
        if (count == 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            View dot = dots.getChildAt(i);
            float proximity = Math.max(0f, 1f - Math.abs(i - position));
            float scale = 1f + 0.5f * proximity;
            if (Math.abs(dot.getScaleX() - scale) > 0.01f) {
                dot.setScaleX(scale);
                dot.setScaleY(scale);
            }
            float alpha = 0.55f + 0.45f * proximity;
            if (Math.abs(dot.getAlpha() - alpha) > 0.01f) {
                dot.setAlpha(alpha);
            }
        }
        int active = Math.max(0, Math.min(count - 1, Math.round(position)));
        if (active != lastActiveDot) {
            lastActiveDot = active;
            restyleDots(active);
        }
    }

    private void updateDots() {
        lastActiveDot = -1;
        updateDotsAt(currentPage);
    }

    public int pageWidth() {
        int width = getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(1, width);
    }

    private int maxScroll() {
        return Math.max(0, (pageViews.size() - 1) * pageWidth());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            cancelScrollAnimation();
            strip.requestLayout();
            pagePosition = currentPage * pageWidth();
            post(restoreScroll);
        }
    }

    public void setCurrentPage(int page, boolean animate) {
        if (pageViews.isEmpty()) {
            currentPage = 0;
            return;
        }
        page = Math.max(0, Math.min(page, pageViews.size() - 1));
        cancelScrollAnimation();
        bindAround(page);
        boolean smooth = animate && settings.animationsEnabled()
                && settings.getPageEffect() != LauncherSettings.PAGE_NONE;
        if (smooth) {
            animateTo(page, 0f);
        } else {
            settleAt(page);
        }
    }

    /**
     * Binds the visible page and its immediate neighbours only. Pages outside that window stay
     * as empty shells until they are needed.
     */
    private void bindAround(int page) {
        if (pageViews.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, Math.min(page, pageViews.size() - 1));
        if (clamped == boundCenter) {
            return;
        }
        boundCenter = clamped;
        int from = Math.max(0, clamped - 1);
        int to = Math.min(pageViews.size() - 1, clamped + 1);
        for (int i = from; i <= to; i++) {
            HomePageView view = pageViews.get(i);
            if (!view.isBound()) {
                view.bind();
            }
        }
    }

    private void trimAround(int page) {
        if (dragging) {
            return;
        }
        for (int i = 0; i < pageViews.size(); i++) {
            if (Math.abs(i - page) > 1) {
                HomePageView view = pageViews.get(i);
                if (view.isBound()) {
                    view.unbind();
                }
            }
        }
    }

    // ---------------------------------------------------------------- gestures

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (pageViews.isEmpty() || dragging) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                lastTouchX = downX;
                trackingTouch = true;
                pagingTouch = false;
                cancelScrollAnimation();
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!trackingTouch) {
                    return false;
                }
                velocityTracker.addMovement(ev);
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!pagingTouch && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    startPaging(ev.getX());
                }
                return pagingTouch;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackingTouch = false;
                return pagingTouch;
            default:
                return false;
        }
    }

    private void startPaging(float x) {
        pagingTouch = true;
        lastTouchX = x;
        for (HomePageView page : pageViews) {
            page.cancelGesture();
        }
        cancelScrollAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (pageViews.isEmpty()) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (dragging) {
                    return false;
                }
                downX = ev.getX();
                downY = ev.getY();
                lastTouchX = downX;
                trackingTouch = true;
                pagingTouch = false;
                cancelScrollAnimation();
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (dragging) {
                    return false;
                }
                velocityTracker.addMovement(ev);
                if (!pagingTouch) {
                    float total = Math.abs(ev.getX() - downX);
                    if (total > touchSlop) {
                        startPaging(ev.getX());
                    }
                }
                if (!pagingTouch) {
                    return true;
                }
                float dx = ev.getX() - lastTouchX;
                lastTouchX = ev.getX();
                if (dx != 0f) {
                    float next = pagePosition - dx;
                    float width = pageWidth();
                    float min = -width * EDGE_RANGE;
                    float max = maxScroll() + width * EDGE_RANGE;
                    next = Math.max(min, Math.min(max, next));
                    renderPosition(next);
                    bindAround(Math.round(next / width));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                float contentVelocity = -velocityTracker.getXVelocity();
                boolean wasPaging = pagingTouch;
                resetTouch();
                finishSwipe(wasPaging, contentVelocity);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                boolean wasPaging = pagingTouch;
                resetTouch();
                finishSwipe(wasPaging, 0f);
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }

    private void resetTouch() {
        trackingTouch = false;
        pagingTouch = false;
        velocityTracker.clear();
    }

    /**
     * Renders the strip at an arbitrary offset. Inside the range the fractional part becomes a
     * translation so movement is pixel smooth; outside it the offset is damped, which is what
     * makes the edge feel elastic.
     */
    private void renderPosition(float positionPx) {
        pagePosition = positionPx;
        int width = pageWidth();
        int max = maxScroll();
        int clamped = Math.round(Math.max(0f, Math.min(max, positionPx)));
        strip.scrollTo(clamped, 0);
        float translation;
        if (positionPx < 0f) {
            translation = Math.max(-width * EDGE_RANGE * EDGE_RESISTANCE,
                    positionPx * EDGE_RESISTANCE);
        } else if (positionPx > max) {
            translation = Math.min(width * EDGE_RANGE * EDGE_RESISTANCE,
                    (positionPx - max) * EDGE_RESISTANCE);
        } else {
            translation = positionPx - clamped;
        }
        strip.setTranslationX(translation);
        updateDotsAt(positionPx / width);
        applyPageAlpha();
        if (listener != null) {
            listener.onPageScrolled(positionPx / width);
        }
    }

    private void finishSwipe(boolean wasPaging, float contentVelocity) {
        if (!wasPaging) {
            return;
        }
        int width = pageWidth();
        int max = maxScroll();
        int target;
        if (pageViews.size() <= 1) {
            target = 0;
        } else if (pagePosition < 0f) {
            target = 0;
        } else if (pagePosition > max) {
            target = pageViews.size() - 1;
        } else {
            float projected = pagePosition + contentVelocity * 0.28f;
            target = Math.round(projected / width);
            target = Math.max(0, Math.min(pageViews.size() - 1, target));
        }
        animateTo(target, contentVelocity);
    }

    /** Kept for callers that used to settle the strip after a custom gesture. */
    public void settlePages() {
        finishSwipe(true, 0f);
    }

    private void animateTo(int page, float velocity) {
        if (pageViews.isEmpty()) {
            return;
        }
        int target = Math.max(0, Math.min(page, pageViews.size() - 1));
        int width = pageWidth();
        final float start = pagePosition;
        final float end = target * width;
        bindAround(target);

        float distance = Math.abs(end - start);
        int duration = 0;
        if (distance > 0.5f) {
            int baseDuration;
            float speed = Math.abs(velocity);
            if (speed > 600f) {
                baseDuration = Math.round(distance / speed * 1000f) + 90;
            } else {
                baseDuration = Math.round(180f + (distance / Math.max(1f, width)) * 220f);
            }
            baseDuration = Math.max(150, Math.min(430, baseDuration));
            duration = settings.animDuration(baseDuration);
        }

        cancelScrollAnimation();
        if (duration <= 0) {
            settleAt(target);
            return;
        }
        scrollAnimator = ValueAnimator.ofFloat(0f, 1f);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = (float) animation.getAnimatedValue();
                renderPosition(start + (end - start) * fraction);
            }
        });
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                settleAt(target);
            }
        });
        scrollAnimator.start();
    }

    private void settleAt(int page) {
        if (pageViews.isEmpty()) {
            return;
        }
        currentPage = Math.max(0, Math.min(page, pageViews.size() - 1));
        pagePosition = currentPage * pageWidth();
        strip.setTranslationX(0f);
        strip.scrollTo(currentPage * pageWidth(), 0);
        if (settings.getPageEffect() != LauncherSettings.PAGE_FADE) {
            for (HomePageView view : pageViews) {
                if (view.getAlpha() != 1f) {
                    view.setAlpha(1f);
                }
            }
        } else {
            applyPageAlpha();
        }
        updateDots();
        bindAround(currentPage);
        trimAround(currentPage);
        applyWidgetSizes();
    }

    private boolean animationsOn() {
        return settings.animationsEnabled();
    }

    /** Cross-fades pages when the user picked the Dissolvenza page transition. */
    private void applyPageAlpha() {
        if (pageViews.isEmpty() || settings.getPageEffect() != LauncherSettings.PAGE_FADE) {
            return;
        }
        float offset = pagePosition / pageWidth();
        for (int i = 0; i < pageViews.size(); i++) {
            if (Math.abs(i - offset) > 1.5f) {
                continue;
            }
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
        cancelScrollAnimation();
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
        float visibleScroll = strip.getScrollX() + strip.getTranslationX();
        int page = Math.max(0, Math.min(pageViews.size() - 1,
                (int) ((visibleScroll + x) / width)));
        HomePageView pageView = pageViews.get(page);
        float pageX = visibleScroll + x - page * width;
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

    void notifyItemLongPress(HomeItem item, int pageIndex) {
        if (listener != null) {
            listener.onItemLongPress(item, pageIndex);
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
            if (!page.isBound()) {
                continue;
            }
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
        handler.removeCallbacks(trimRunnable);
        handler.removeCallbacks(bindNeighbours);
        cancelScrollAnimation();
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
