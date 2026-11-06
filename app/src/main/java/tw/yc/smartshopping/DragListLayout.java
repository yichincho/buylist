package tw.yc.smartshopping;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list whose item rows can be dragged to a new position inside their own group.
 * Rows can also be swiped sideways to delete them.
 * Rows that take part carry a {@link Row} tag; group headers and other views do not.
 */
public final class DragListLayout extends LinearLayout {
    public interface OnReorderListener { void onReorder(List<Long> orderedIds); }
    public interface OnSwipeListener { void onSwipe(long id); }

    public static final class Row {
        public final long id;
        public final String group;
        public Row(long id, String group) { this.id = id; this.group = group; }
    }

    private OnReorderListener listener;
    private OnSwipeListener swipeListener;
    private View swiped;
    private View swipeCandidate;
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

    /** Starts dragging a row; call from a long press or a touch on the row's handle. */
    public void startDrag(View row, float rawY) {
        if (dragged != null || !(row.getTag() instanceof Row)) return;
        dragged = row;
        moved = false;
        lastY = toLocalY(rawY);
        row.setAlpha(.85f);
        row.setScaleX(1.02f);
        row.setScaleY(1.02f);
        row.setElevation(12 * getResources().getDisplayMetrics().density);
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
                return false;
            case MotionEvent.ACTION_MOVE:
                if (swipeCandidate == null || swipeListener == null) return false;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 2) {
                    swiped = swipeCandidate;
                    swipeCandidate = null;
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
        float dx = event.getX() - downX;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                swiped.setTranslationX(dx);
                swiped.setAlpha(Math.max(.25f, 1f - Math.abs(dx) / getWidth()));
                return true;
            case MotionEvent.ACTION_UP:
                final View row = swiped;
                swiped = null;
                if (Math.abs(dx) > getWidth() * .35f) {
                    final long id = ((Row) row.getTag()).id;
                    row.animate().translationX(dx > 0 ? getWidth() : -getWidth()).alpha(0f).setDuration(150)
                            .withEndAction(() -> { if (swipeListener != null) swipeListener.onSwipe(id); }).start();
                } else {
                    row.animate().translationX(0).alpha(1f).setDuration(150).start();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                swiped.animate().translationX(0).alpha(1f).setDuration(150).start();
                swiped = null;
                return true;
            default:
                return true;
        }
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
        } else if (offset > 0 && sameGroup(next) && offset > next.getHeight() / 2f) {
            removeViewAt(index + 1);
            addView(next, index);
            dragged.setTranslationY(offset - next.getHeight());
            moved = true;
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
        float edge = 72 * getResources().getDisplayMetrics().density;
        int step = (int) (14 * getResources().getDisplayMetrics().density);
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
        row.setTranslationY(0);
        row.setAlpha(1f);
        row.setScaleX(1f);
        row.setScaleY(1f);
        row.setElevation(0);
        if (moved && listener != null) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++) {
                Object tag = getChildAt(i).getTag();
                if (tag instanceof Row) ids.add(((Row) tag).id);
            }
            listener.onReorder(ids);
        }
    }

    private float toLocalY(float rawY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return rawY - location[1];
    }
}
