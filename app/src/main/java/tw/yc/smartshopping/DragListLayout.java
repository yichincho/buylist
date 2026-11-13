package tw.yc.smartshopping;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list whose item rows can be dragged to a new position inside their own group,
 * or swiped sideways to delete them.
 * Rows that take part carry a {@link Row} tag; other views do not.
 */
public final class DragListLayout extends LinearLayout {
    public interface OnReorderListener { void onReorder(List<Long> orderedIds); }
    public interface OnSwipeListener { void onSwipe(long id); }

    public static final class Row {
        public final long id;
        public final String group;
        /** The part of the row that slides during a swipe; null slides the whole row. */
        public final View content;
        /** Shown behind the sliding content while swiping; may be null. */
        public final View backdrop;

        public Row(long id, String group) { this(id, group, null, null); }

        public Row(long id, String group, View content, View backdrop) {
            this.id = id; this.group = group; this.content = content; this.backdrop = backdrop;
        }
    }

    private static final float SWIPE_DISTANCE = .4f;
    private static final float FLING_DP_PER_SECOND = 900f;

    private OnReorderListener listener;
    private OnSwipeListener swipeListener;
    private View swiped;
    private View swipeCandidate;
    private boolean pastThreshold;
    private VelocityTracker velocity;
    private float downX;
    private float downY;
    private View dragged;
    private float lastY;
    private boolean moved;

    public DragListLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void setOnReorderListener(OnReorderListener listener) { this.listener = listener; }
    public void setOnSwipeListener(OnSwipeListener listener) { this.swipeListener = listener; }

    public boolean isDragging() { return dragged != null; }

    /** Ids of the rows in their current on-screen order. */
    public List<Long> rowIds() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            Object tag = getChildAt(i).getTag();
            if (tag instanceof Row) ids.add(((Row) tag).id);
        }
        return ids;
    }

    /** Starts dragging a row; call from a long press or a touch on the row's handle. */
    public void startDrag(View row, float rawY) {
        if (dragged != null || swiped != null || !(row.getTag() instanceof Row)) return;
        dragged = row;
        moved = false;
        lastY = toLocalY(rawY);
        row.animate().scaleX(1.03f).scaleY(1.03f).translationZ(dp(10)).setDuration(120).start();
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (dragged == null) return interceptSwipe(event) || super.onInterceptTouchEvent(event);
        int action = event.getActionMasked();
        // An intercepted event never reaches onTouchEvent, so a release without movement ends the drag here.
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) finishDrag();
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (swiped != null) return handleSwipe(event);
        if (dragged == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                dragged.setTranslationY(dragged.getTranslationY() + y - lastY);
                lastY = y;
                swapWithNeighbours();
                autoScroll(event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishDrag();
                return true;
            default:
                return true;
        }
    }

    /** Watches touches on rows and takes over once the finger moves clearly sideways. */
    private boolean interceptSwipe(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                swipeCandidate = rowAt(downY);
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(event);
                if (swipeCandidate == null || swipeListener == null) return false;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    swiped = swipeCandidate;
                    swipeCandidate = null;
                    pastThreshold = false;
                    // Start from the current finger position so the row does not jump by the slop.
                    downX = event.getX() - Math.signum(dx) * slop;
                    Row row = (Row) swiped.getTag();
                    if (row.backdrop != null) row.backdrop.setVisibility(VISIBLE);
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            default:
                swipeCandidate = null;
                return false;
        }
    }

    private boolean handleSwipe(MotionEvent event) {
        Row row = (Row) swiped.getTag();
        View content = row.content != null ? row.content : swiped;
        float dx = event.getX() - downX;
        if (velocity != null) velocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                content.setTranslationX(dx);
                if (row.content == null) content.setAlpha(Math.max(.3f, 1f - Math.abs(dx) / getWidth()));
                boolean past = Math.abs(dx) > getWidth() * SWIPE_DISTANCE;
                if (past != pastThreshold) {
                    pastThreshold = past;
                    swiped.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                }
                if (row.backdrop != null) row.backdrop.setAlpha(past ? 1f : .55f + .45f * Math.abs(dx) / (getWidth() * SWIPE_DISTANCE));
                return true;
            case MotionEvent.ACTION_UP:
                float speed = 0;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    speed = velocity.getXVelocity() / getResources().getDisplayMetrics().density;
                }
                boolean fling = Math.abs(speed) > FLING_DP_PER_SECOND && Math.signum(speed) == Math.signum(dx)
                        && Math.abs(dx) > dp(48);
                if (Math.abs(dx) > getWidth() * SWIPE_DISTANCE || fling) dismiss(swiped, content, dx);
                else settle(swiped, content);
                swiped = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                settle(swiped, content);
                swiped = null;
                return true;
            default:
                return true;
        }
    }

    private void dismiss(View rowView, View content, float dx) {
        final long id = ((Row) rowView.getTag()).id;
        content.animate().translationX(dx > 0 ? getWidth() : -getWidth()).setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> { if (swipeListener != null) swipeListener.onSwipe(id); }).start();
    }

    private void settle(View rowView, View content) {
        final Row row = (Row) rowView.getTag();
        content.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> { if (row.backdrop != null) row.backdrop.setVisibility(INVISIBLE); }).start();
    }

    private View rowAt(float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getTag() instanceof Row && y >= child.getTop() && y < child.getBottom()) return child;
        }
        return null;
    }

    private void swapWithNeighbours() {
        int index = indexOfChild(dragged);
        float offset = dragged.getTranslationY();
        View previous = index > 0 ? getChildAt(index - 1) : null;
        View next = index < getChildCount() - 1 ? getChildAt(index + 1) : null;
        if (offset < 0 && sameGroup(previous) && -offset > previous.getHeight() / 2f) {
            removeViewAt(index - 1);
            addView(previous, index);
            dragged.setTranslationY(offset + previous.getHeight());
            moved = true;
            dragged.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        } else if (offset > 0 && sameGroup(next) && offset > next.getHeight() / 2f) {
            removeViewAt(index + 1);
            addView(next, index);
            dragged.setTranslationY(offset - next.getHeight());
            moved = true;
            dragged.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private boolean sameGroup(View other) {
        return other != null && other.getTag() instanceof Row
                && ((Row) other.getTag()).group.equals(((Row) dragged.getTag()).group);
    }

    private void autoScroll(float rawY) {
        ScrollView scroll = findScrollView();
        if (scroll == null) return;
        int[] location = new int[2];
        scroll.getLocationOnScreen(location);
        float edge = dp(72);
        int step = dp(14);
        if (rawY < location[1] + edge) scroll.scrollBy(0, -step);
        else if (rawY > location[1] + scroll.getHeight() - edge) scroll.scrollBy(0, step);
    }

    private ScrollView findScrollView() {
        ViewParent parent = getParent();
        while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
        return (ScrollView) parent;
    }

    private void finishDrag() {
        View row = dragged;
        dragged = null;
        row.animate().translationY(0).scaleX(1f).scaleY(1f).translationZ(0).setDuration(160).start();
        if (moved && listener != null) listener.onReorder(rowIds());
    }

    private float toLocalY(float rawY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return rawY - location[1];
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
