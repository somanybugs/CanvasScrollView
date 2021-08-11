package lhg.canvasscrollview;

import android.content.Context;
import android.database.Observable;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import androidx.core.view.InputDeviceCompat;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ScrollingView;
import androidx.core.view.ViewCompat;
import androidx.core.widget.EdgeEffectCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


/**
 * 2021.07.15 lhg
 * from androidx.core:core:1.5.0 NestedScrollView
 * no padding
 */
public class CanvasScrollView extends View implements  NestedScrollingChild3, ScrollingView {
    private static final String TAG = "CanvasScrollView";

    private Adapter mAdapter;
    private final Layouter mLayouter = new Layouter();

    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;

    private int mLastMotionY;
    private int mLastMotionX;

    private VelocityTracker mVelocityTracker;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mActivePointerId = INVALID_POINTER;

    private final int[] mScrollOffset = new int[2];
    private final int[] mScrollConsumed = new int[2];
    private int mNestedYOffset;

    private static final int INVALID_POINTER = -1;

    private final NestedScrollingChildHelper mChildHelper;

    private float mVerticalScrollFactor;

    private CanvasBlock draggedBlock = null;
    private final ScrollViewDataObserver mObserver = new ScrollViewDataObserver();

    public CanvasScrollView(Context context) {
        this(context, null);
    }

    public CanvasScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CanvasScrollView(Context context, AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initScrollView();
        mChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    // NestedScrollingChild3

    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                     int dyUnconsumed, int[] offsetInWindow, int type, int[] consumed) {
        mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow, type, consumed);
    }

    // NestedScrollingChild2

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return mChildHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll(int type) {
        mChildHelper.stopNestedScroll(type);
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return mChildHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow, int type) {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow,
                                           int type) {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    private void initScrollView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }


    private void ensureLayouterValid() {
        if (mLayouter.isInValidLayouting) {
            return;
        }
        if (!mLayouter.isInvalid()) {
            return;
        }
        mLayouter.isInValidLayouting = true;
        for (InvalidLayoutHandler h : mLayouter.invalidHandlers) {
            h.layout(this, mLayouter, mAdapter);
        }
        mLayouter.invalidHandlers.clear();
        mLayouter.isInValidLayouting = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mObserver.notifyDataSetChanged();
    }

    private boolean canScroll() {
        ensureLayouterValid();
        if (getChildCount() == 0) {
            return false;
        }

        int childSize = mLayouter.lastBlock().getBottom();
        int parentSpace = getHeight() - getPaddingTop() - getPaddingBottom();
        if (childSize <= parentSpace && mLayouter.getLastBlockPosition() + 1 == mAdapter.getItemCount()) {
            return false;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("with must be EXACTLY");
        }
        if (mAdapter == null || mAdapter.getItemCount() == 0) {
            if (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST) {
                heightSize = 0;
            }
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            //layout all child
            mLayouter.fillWindow(this, mAdapter, widthSize, 0, Integer.MAX_VALUE);
            heightSize = mLayouter.lastBlock().getBottom() - mLayouter.firstBlock().top;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            mLayouter.fillWindow(this, mAdapter, widthSize, 0, heightSize);
            heightSize = Math.min(heightSize, mLayouter.lastBlock().getBottom() - mLayouter.firstBlock().top);
        }
        setMeasuredDimension(widthSize, heightSize);
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    public int getChildCount() {
        return mLayouter.blockCount();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();

        final int actionMasked = ev.getActionMasked();

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0;
        }
        if (draggedBlock != null && !draggedBlock.isAttached()) {
            draggedBlock = null;
        }

        MotionEvent vtev = MotionEvent.obtain(ev);
        vtev.offsetLocation(0, mNestedYOffset);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    draggedBlock = null;
                    setScrollState(SCROLL_STATE_IDLE);
                    return false;
                }
                if (!mScroller.isFinished()) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    setScrollState(SCROLL_STATE_DRAGGING);
                }
                if (!isBeingDragged()) {
                    draggedBlock = null;
                }
                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    abortAnimatedScroll();
                }

                // Remember where the motion event started
                mLastMotionY = (int) ev.getY();
                mLastMotionX = (int) ev.getX();
                mPerformLongClickRunnable.hasHandled = false;
                mPerformLongClickRunnable.x = mLastMotionX;
                mPerformLongClickRunnable.y = mLastMotionY;
                postDelayed(mPerformLongClickRunnable, ViewConfiguration.getLongPressTimeout());
                mPerfermTapRunnable.x = mLastMotionX;
                mPerfermTapRunnable.y = mLastMotionY;
                postDelayed(mPerfermTapRunnable, ViewConfiguration.getTapTimeout());
                mActivePointerId = ev.getPointerId(0);
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int y = (int) ev.getY(activePointerIndex);
                int deltaY = mLastMotionY - y;
                if (!isBeingDragged() && Math.abs(deltaY) > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    if (deltaY > 0) {
                        deltaY -= mTouchSlop;
                    } else {
                        deltaY += mTouchSlop;
                    }
                    setScrollState(SCROLL_STATE_DRAGGING);
                }
                final int x = (int) ev.getX(activePointerIndex);
                int deltaX = mLastMotionX - x;
                if (!isBeingDragged() && Math.abs(deltaX) > mTouchSlop) {
                    CanvasBlock block = findBlock(x, y);
                    if (block != null && block.getWidth() > getWidth()) {
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        draggedBlock = block;
                        if (deltaX > 0) {
                            deltaX -= mTouchSlop;
                        } else {
                            deltaX += mTouchSlop;
                        }
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }

                if (isBeingDragged()) {
                    removeCallbacks(mPerformLongClickRunnable);
                    removeCallbacks(mPerfermTapRunnable);
                }

                if (isBeingDragged() && draggedBlock == null) {
                    // Start with nested pre scrolling
                    if (dispatchNestedPreScroll(0, deltaY, mScrollConsumed, mScrollOffset,
                            ViewCompat.TYPE_TOUCH)) {
                        deltaY -= mScrollConsumed[1];
                        mNestedYOffset += mScrollOffset[1];
                    }

                    // Scroll to follow the motion event
                    mLastMotionY = y - mScrollOffset[1];

                    final int overscrollMode = getOverScrollMode();
                    boolean canOverscroll = overscrollMode == View.OVER_SCROLL_ALWAYS
                            || (overscrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && canScroll());

                    // Calling overScrollByCompat will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    final int scrolledDeltaY = scrollByInternal(0, deltaY);
                    final int unconsumedY = deltaY - scrolledDeltaY;
                    adjustScrollY();

                    mScrollConsumed[1] = 0;

                    dispatchNestedScroll(0, scrolledDeltaY, 0, unconsumedY, mScrollOffset,
                            ViewCompat.TYPE_TOUCH, mScrollConsumed);

                    mLastMotionY -= mScrollOffset[1];
                    mNestedYOffset += mScrollOffset[1];

                    if (canOverscroll) {
                        deltaY = unconsumedY - mScrollConsumed[1];
                        ensureGlows();
                        if (deltaY < 0) {
                            EdgeEffectCompat.onPull(mEdgeGlowTop, (float) deltaY / getHeight(),
                                    ev.getX(activePointerIndex) / getWidth());
                            if (!mEdgeGlowBottom.isFinished()) {
                                mEdgeGlowBottom.onRelease();
                            }
                        } else if (deltaY > 0) {
                            EdgeEffectCompat.onPull(mEdgeGlowBottom, (float) deltaY / getHeight(),
                                    1.f - ev.getX(activePointerIndex)
                                            / getWidth());
                            if (!mEdgeGlowTop.isFinished()) {
                                mEdgeGlowTop.onRelease();
                            }
                        }
                        if (mEdgeGlowTop != null
                                && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }
                }
                if (isBeingDragged() && draggedBlock != null) {
                    scrollChildByInternal(deltaX);
                    mLastMotionX = x;
                }
                break;
            case MotionEvent.ACTION_UP:
                boolean fling = false;
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                if (isBeingDragged() && draggedBlock != null) {
                    int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
                    if ((Math.abs(initialVelocity) >= mMinimumVelocity)) {
                        fling = flingChildX(-initialVelocity);
                    }
                } else {
                    int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                    if ((Math.abs(initialVelocity) >= mMinimumVelocity)) {
                        if (!dispatchNestedPreFling(0, -initialVelocity)) {
                            dispatchNestedFling(0, -initialVelocity, true);
                            fling = fling(-initialVelocity);
                        }
                    }
                }
                if (!mPerformLongClickRunnable.hasHandled && mScrollState == SCROLL_STATE_IDLE) {
                    mPerformClickRunnable.x = mLastMotionX;
                    mPerformClickRunnable.y = mLastMotionY;
                    Log.i(TAG, "onTouch click");
                    if (!post(mPerformClickRunnable)) {
                        mPerformClickRunnable.run();
                    }
                }
                if (!fling) {
                    setScrollState(SCROLL_STATE_IDLE);
                }
                removeCallbacks(mPerformLongClickRunnable);
                mActivePointerId = INVALID_POINTER;
                endDrag();
                break;
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                endDrag();
                setScrollState(SCROLL_STATE_IDLE);
                removeCallbacks(mPerfermTapRunnable);
                removeCallbacks(mPerformLongClickRunnable);
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionY = (int) ev.getY(index);
                mLastMotionX = (int) ev.getX(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();

        return true;
    }

    private boolean isBeingDragged() {
        return mScrollState == SCROLL_STATE_DRAGGING;
    }

    private int scrollChildByInternal(int dx) {
        if (draggedBlock == null || dx == 0) {
            return 0;
        }
        int dxConsumed = 0;
        if (dx < 0) {
            dxConsumed = Math.max(dx, draggedBlock.left);
        } else if (dx > 0) {//手指从右边往左边巴拉， 要把右边的view显示出来
            dxConsumed = Math.min(dx, draggedBlock.getWidth() + draggedBlock.left - getWidth());
        }
        draggedBlock.left -= dxConsumed;
        invalidate();
        return dxConsumed;
    }

    private CanvasBlock findBlock(int x, int y) {
        for (CanvasBlock view : mLayouter.blocks) {
            if (view.getTop() < y && view.getBottom() > y) {
                return view;
            }
        }
        return null;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDeviceCompat.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    if (!isBeingDragged()) {
                        final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        if (vscroll != 0) {
                            final int delta = (int) (vscroll * getVerticalScrollFactorCompat());
                            if (delta != 0) {
                                scrollBy(0, -delta);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private float getVerticalScrollFactorCompat() {
        if (mVerticalScrollFactor == 0) {
            TypedValue outValue = new TypedValue();
            final Context context = getContext();
            if (!context.getTheme().resolveAttribute(
                    android.R.attr.listPreferredItemHeight, outValue, true)) {
                throw new IllegalStateException(
                        "Expected theme to define listPreferredItemHeight.");
            }
            mVerticalScrollFactor = outValue.getDimension(
                    context.getResources().getDisplayMetrics());
        }
        return mVerticalScrollFactor;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
    }

    @Override
    public void scrollBy(int dx, int dy) {
        scrollByInternal(dx, dy);
        adjustScrollY();
    }

    protected int scrollByInternal(int dx, int dy) {
        int dyConsumed = 0;
        ensureLayouterValid();
        if (getChildCount() <= 0) {
            abortAnimatedScroll();
            return dyConsumed;
        }

        if (dy < 0) {//手指从上往下巴拉， 要把上面的view显示出来
            mLayouter.fillWindow(this, mAdapter, getWidth(), dy, getHeight());
            dyConsumed = Math.min(0, Math.max(dy, mLayouter.firstBlock().getTop()));
        } else if (dy > 0) {
            mLayouter.fillWindow(this, mAdapter, getWidth(), 0, dy + getHeight());
            dyConsumed = Math.max(0, Math.min(dy, mLayouter.lastBlock().getBottom() - getHeight()));
        }

        if (dyConsumed == 0) {
            return dyConsumed;
        }

        int oldFirstPosition = mLayouter.getFirstBlockPosition();
        for (int i = mLayouter.blockCount() - 1; i >= 0; i--) {
            CanvasBlock view = mLayouter.blockAt(i);
            view.top -= dyConsumed;
            if (view.top >= getHeight() || view.getBottom() <= 0) {
                mAdapter.onBlockDetachedFromView(this, mLayouter.blocks.remove(i));
            } else {
                mLayouter.setFirstBlockPosition(oldFirstPosition + i);
            }
        }

        dispatchOnScrolled(0, dyConsumed);
        if (!awakenScrollBars()) {
            invalidate();
        }
        return dyConsumed;
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return super.computeHorizontalScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }

    
    @Override
    public int computeVerticalScrollRange() {
        ensureLayouterValid();
        final int count = getChildCount();
        final int parentSpace = getHeight() - getPaddingBottom() - getPaddingTop();
        if (count == 0) {
            return parentSpace;
        }

        int scrollRange = mLayouter.lastBlock().getBottom() - mLayouter.firstBlock().getTop();
        int sizePerRow = scrollRange / mLayouter.blockCount();
        scrollRange = sizePerRow * mAdapter.getItemCount();
        return scrollRange;
    }


    @Override
    public int computeVerticalScrollOffset() {
        ensureLayouterValid();
        final int count = getChildCount();
        if (count == 0) {
            return 0;
        }

        int scrollRange = mLayouter.lastBlock().getBottom() - mLayouter.firstBlock().getTop();
        int sizePerRow = scrollRange / mLayouter.blockCount();
        scrollRange = sizePerRow * mLayouter.getFirstBlockPosition() - mLayouter.firstBlock().getTop();
        return scrollRange;
    }
    
    
    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();//, mLayouter.lastBlock().getBottom() - mLayouter.firstBlock().getTop());
    }

    private void abortAnimatedScroll() {
        mScroller.abortAnimation();
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
    }


    /**
     * Fling the scroll view
     *
     * @param velocityY The initial velocity in the Y direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the top.
     */
    public boolean fling(int velocityY) {
        return flingRunnable.start(velocityY);
    }

    public boolean flingChildX(int velocityX) {
        return flingChildXRunnable.start(velocityX);
    }

    private void endDrag() {
//        mIsBeingDragged = false;

        recycleVelocityTracker();
        stopNestedScroll(ViewCompat.TYPE_TOUCH);

        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This version also clamps the scrolling to the bounds of our child.
     */
    @Override
    public void scrollTo(int x, int y) {

    }

    private void ensureGlows() {
        if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
    }


    public boolean getClipToPadding() {
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ensureLayouterValid();
        if (mLayouter.blockCount() == 0 || mAdapter == null) {
            return;
        }
        adjustScrollY();
        int left = 0;
        int top = 0;
        int height = 0;
        int transX = 0;
        int transY = 0;
        canvas.save();
        for (CanvasBlock view : mLayouter.blocks) {
            canvas.translate(view.left - transX, view.top - transY);
            transX = view.left;
            transY = view.top;
            left = Math.max(-view.left, 0);
            top = Math.max(-view.top, 0);
            height = Math.min(view.getBottom(), getHeight()) - Math.max(view.top, 0);
            boolean isClipPadding = view.isClipPadding();
            if (isClipPadding) {
                canvas.save();
                canvas.clipRect(left + view.getPaddingLeft(), top, left + getWidth() - view.getPaddingRight(), top + height);
            }
            mAdapter.onDrawBlock(this, view, canvas, left, top, left + getWidth(), top + height);
            if (isClipPadding) {
                canvas.restore();
            }
            if (view.getBottom() >= getHeight()) {
                break;
            }
        }
        canvas.restore();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowTop != null) {
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = 0;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation += getPaddingTop();
                }
                canvas.translate(xTranslation, yTranslation);
                mEdgeGlowTop.setSize(width, height);
                if (mEdgeGlowTop.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = height;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation -= getPaddingBottom();
                }
                canvas.translate(xTranslation - width, yTranslation);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    static final int MAX_SCROLL_DURATION = 2000;
    private int computeScrollDuration(int dy) {
        final int absDy = Math.abs(dy);
        final int containerSize = getHeight();

        float absDelta = (float) (absDy);
        final int duration = (int) (((absDelta / containerSize) + 1) * 300);
        return Math.min(duration, MAX_SCROLL_DURATION);
    }

    private void adjustScrollY() {
        if (mLayouter.blockCount() == 0) {
            return;
        }
        if (mLayouter.getFirstBlockPosition() == 0 && mLayouter.firstBlock().getTop() > 0) {
            scrollByInternal(0, -mLayouter.firstBlock().getTop());
            return;
        }
        if (mLayouter.lastBlock().getBottom() < getHeight() && (mLayouter.getFirstBlockPosition() > 0 || mLayouter.firstBlock().getTop() < 0)) {
            //调整底部富裕空间
            scrollByInternal(0, mLayouter.lastBlock().getBottom() - getHeight());
            return;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void postInvalidate() {
        super.postInvalidate();
    }

    @Override
    public int getPaddingBottom() {
        return 0;
    }

    @Override
    public int getPaddingEnd() {
        return 0;
    }

    @Override
    public int getPaddingLeft() {
        return 0;
    }

    @Override
    public int getPaddingRight() {
        return 0;
    }

    @Override
    public int getPaddingStart() {
        return 0;
    }

    @Override
    public int getPaddingTop() {
        return 0;
    }

    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            removeOnScrollListener(mAdapter);
            mAdapter.onAdapterDetachedFromScollView(this);
            mAdapter.unregisterObserver(mObserver);
        }
        this.mAdapter = adapter;
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        if (mAdapter != null) {
            addOnScrollListener(mAdapter);
            mAdapter.onAdapterAttachedToScollView(this);
            mAdapter.registerObserver(mObserver);
            mAdapter.notifyDataSetChanged();
        }
    }

    public CanvasBlock getBlockAtY(int y) {
        for (CanvasBlock cb : mLayouter.blocks) {
            if (cb.getTop() <= y && cb.getBottom() >= y) {
                return cb;
            }
        }
        return null;
    }

    public static abstract class Adapter<CB extends CanvasBlock> extends Observable<AdapterDataObserver> implements OnScrollListener,CanvasBlockParent {
        private WeakReference<CanvasScrollView> canvasScrollView;
        public boolean hasObservers() {
            return !mObservers.isEmpty();
        }

        public void notifyDataSetChanged() {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).notifyDataSetChanged();
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeChanged(positionStart, itemCount);
            }
        }

        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeInserted(positionStart, itemCount);
            }
        }

        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeRemoved(positionStart, itemCount);
            }
        }

        public abstract int getItemCount();
        public abstract CB getItem(CanvasScrollView parent, int position);

        public void onMeasureBlock(CanvasScrollView parent, CB block, int width) {
            block.onMeasure(parent, width);
        }

        public void onDrawBlock(CanvasScrollView parent, CB block,Canvas canvas, int left, int top, int right, int bottom) {
            block.onDraw(parent, canvas, left, top, right, bottom);
        }

        public void onBlockAttachedToView(CanvasScrollView parent, CB block) {
            block.onAttachedToParent(this);
        }

        public void onBlockDetachedFromView(CanvasScrollView parent, CB block) {
            block.onDetachedFromParent(this);
        }

        public void onBlockClicked(CanvasScrollView parent, CB block, int x, int y) {
            block.onClicked(parent, x, y);
        }

        public boolean onBlockLongClicked(CanvasScrollView parent, CB block, int x, int y) {
            return block.onLongClicked(parent, x, y);
        }

        public void onBlockTap(CanvasScrollView parent, CB block, int x, int y) {
        }

        @Override
        public void onScrolled(CanvasScrollView scrollView, int dx, int dy) {

        }

        @Override
        public void onScrollStateChanged(CanvasScrollView scrollView, int newState) {

        }

        public void onAdapterDetachedFromScollView(CanvasScrollView scrollView) {
            canvasScrollView = null;
        }

        public void onAdapterAttachedToScollView(CanvasScrollView scrollView) {
            canvasScrollView = new WeakReference<>(scrollView);
        }

        public void onScrollviewDetachedFromWindow(CanvasScrollView scrollView) {
            canvasScrollView = null;
        }

        public CanvasScrollView getCanvasScrollView() {
            if (canvasScrollView == null) {
                return null;
            }
            return canvasScrollView.get();
        }

        @Override
        public void invalidate(CanvasBlock child) {
            notifyItemRangeChanged(child.position, 1);
        }
    }

    public interface CanvasBlockParent {
        void invalidate(CanvasBlock child);
    }

    public static abstract class CanvasBlock {
        private int position;
        private int width;
        private int height;
        private int top;
        private int left;
        private CanvasBlockParent parent;
        private int paddingLeft;
        private int paddingRight;
        private int paddingTop;
        private int paddingBottom;
        private boolean clipPadding = true;

        public void setPadding(int l, int t, int r, int b) {
            this.paddingLeft = l;
            this.paddingTop = t;
            this.paddingRight = r;
            this.paddingBottom = b;
        }

        public void setClipPadding(boolean clipPadding) {
            this.clipPadding = clipPadding;
        }

        public boolean isClipPadding() {
            return clipPadding;
        }

        public int getPaddingLeft() {
            return paddingLeft;
        }

        public int getPaddingRight() {
            return paddingRight;
        }

        public int getPaddingTop() {
            return paddingTop;
        }

        public int getPaddingBottom() {
            return paddingBottom;
        }

        public CanvasBlockParent getParent() {
            return parent;
        }

        public final int getPosition() {
            return position;
        }

        public final void setWidth(int width) {
            this.width = width;
        }

        public final void setHeight(int height) {
            this.height = height;
        }

        public final int getWidth() {
            return width;
        }

        public final int getHeight() {
            return height;
        }

        public final int getTop() {
            return top;
        }

        public final int getBottom() {
            return top + height;
        }

        public final int getRight() {
            return left + width;
        }

        public void init() {
            width = 0;
            height = 0;
        }

        public void onMeasure(CanvasScrollView parent, int parentWidth) {
            onMeasure(parent, parentWidth, false);
        }
        public abstract void onMeasure(CanvasScrollView parent, int parentWidth, boolean horizontalScrollable);
        // left top right bottom 是view中需要绘制的区域，是相对于自身左顶点的坐标
        public abstract void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom);

        public final CanvasBlock setTop(int top) {
            this.top = top;
            return this;
        }

        public void setLeft(int left) {
            this.left = left;
        }

        public final int getLeft() {
            return left;
        }

        public final boolean isAttached() {
            return parent != null;
        }

        public void invalidate() {
            CanvasBlockParent parent = this.parent;
            if (parent != null) {
                parent.invalidate(this);
            }
        }

        public void onAttachedToParent(CanvasBlockParent parent) {
            this.parent = parent;
        }

        public void onDetachedFromParent(CanvasBlockParent parent) {
            this.parent = null;
        }

        public void onClicked(CanvasScrollView parent, int x, int y) {
        }

        public boolean onLongClicked(CanvasScrollView parent,int x, int y) {
            return false;
        }
    }

    private static class Layouter {
        private boolean isInValidLayouting = false;
        private List<InvalidLayoutHandler> invalidHandlers = new ArrayList<>();
        private int firstBlockPosition = 0;
        private final List<CanvasBlock> blocks = new ArrayList<>();

        public CanvasBlock blockAt(int index) {
            return blocks.get(index);
        }

        public CanvasBlock firstBlock() {
            if (blocks.isEmpty()) {
                return null;
            }
            return blocks.get(0);
        }

        public CanvasBlock lastBlock() {
            if (blocks.isEmpty()) {
                return null;
            }
            return blocks.get(blocks.size() - 1);
        }

        private void setFirstBlockPosition(int firstBlockPosition) {
            this.firstBlockPosition = firstBlockPosition;
        }

        public int getFirstBlockPosition() {
            return firstBlockPosition;
        }

        public int getLastBlockPosition() {
            return firstBlockPosition + blocks.size() - 1;
        }

        public int blockCount() {
            return blocks.size();
        }

        public boolean isInvalid() {
            return invalidHandlers.size() > 0;
        }

        private CanvasBlock createBlock(CanvasScrollView parent, Adapter adapter, int position, int y, boolean yIsTop, int width) {
            CanvasBlock view = adapter.getItem(parent, position);
            view.position = position;
            adapter.onMeasureBlock(parent, view, width);
            view.setTop(yIsTop ? y : y - view.getHeight());
            adapter.onBlockAttachedToView(parent, view);
            blocks.add(Math.max(0, position - firstBlockPosition), view);
            return view;
        }

        /**
         * @param parent
         * @param adapter
         * @param width
         * @param fromTop  <= 0
         * @param toBottom >= 0
         */
        private void fillWindow(CanvasScrollView parent, Adapter adapter, int width, int fromTop, int toBottom) {
            if (adapter == null) {
                return;
            }

            if (getFirstBlockPosition() > 0) {
                int y = blockCount() > 0 ? firstBlock().getTop() : 0;
                for (int i = getFirstBlockPosition() - 1; i >= 0 && y > fromTop; i--) {
                    setFirstBlockPosition(i);
                    y -= createBlock(parent, adapter, i, y, false, width).getHeight();
                }
            }

            if (getLastBlockPosition() < adapter.getItemCount()) {
                int y = blockCount() > 0 ? lastBlock().getBottom() : 0;
                for (int i = getLastBlockPosition() + 1; i < adapter.getItemCount() && y < toBottom; i++) {
                    y += createBlock(parent, adapter, i, y, true, width).getHeight();
                }
            }
        }
    }


    //////////////////////////////////////////////////////


    public abstract static class AdapterDataObserver {
        public void notifyDataSetChanged() {
            // Do nothing
        }
        public void onItemRangeChanged(int positionStart, int itemCount) {
            // do nothing
        }
        public void onItemRangeInserted(int positionStart, int itemCount) {
            // do nothing
        }
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            // do nothing
        }
    }
    
    private static abstract class InvalidLayoutHandler {
        protected void removeBlocks(CanvasScrollView parent, List<CanvasBlock> blocks, int start, int end, Adapter adapter) {
            start = Math.max(0, start);
            end = Math.min(end, blocks.size());
            for (int i = end - 1; i>= start;i--) {
                adapter.onBlockDetachedFromView(parent, blocks.remove(i));
            }
        }
        protected CanvasBlock detachedBlock(CanvasScrollView parent, CanvasBlock block, Adapter adapter) {
            adapter.onBlockDetachedFromView(parent, block);
            return block;
        }
        protected CanvasBlock attachedBlock(CanvasScrollView parent, CanvasBlock block, Adapter adapter) {
            adapter.onBlockAttachedToView(parent, block);
            return block;
        }
        abstract void layout(CanvasScrollView parent, Layouter layouter, Adapter adapter);
    }

    private static class InvalidLayoutHandlerOnChanged extends InvalidLayoutHandler {

        @Override
        public void layout(CanvasScrollView parent, Layouter layouter, Adapter adapter) {
            removeBlocks(parent, layouter.blocks, 0, layouter.blocks.size(), adapter);
            if (adapter == null || adapter.getItemCount() == 0) {
                return;
            }
            if (layouter.firstBlockPosition >= adapter.getItemCount()) {
                layouter.firstBlockPosition = adapter.getItemCount() - 1;
            }
            layouter.fillWindow(parent, adapter, parent.getWidth(), 0, parent.getBottom());
        }
    }

    private static class InvalidLayoutHandlerRangeChanged extends InvalidLayoutHandler {
        final int positionStart;
        final int itemCount;

        private InvalidLayoutHandlerRangeChanged(int positionStart, int itemCount) {
            this.positionStart = positionStart;
            this.itemCount = itemCount;
        }

        @Override
        public void layout(CanvasScrollView parent, Layouter layouter, Adapter adapter) {
            if (adapter == null) {
                return;
            }
            if (positionStart > layouter.getLastBlockPosition() || positionStart + itemCount - 1 < layouter.firstBlockPosition) {
                return;
            }

            int start = Math.max(layouter.firstBlockPosition, positionStart);
            int end = Math.min(layouter.getLastBlockPosition() + 1, positionStart + itemCount);

            int y = layouter.blocks.get(start - layouter.firstBlockPosition).top;
            for (int i = start, k = start - layouter.firstBlockPosition; i < end; i++, k++) {
                CanvasBlock cb = layouter.blocks.get(k);
                cb.onMeasure(parent, parent.getWidth());
                y += cb.setTop(y).getHeight();
                if (y >= parent.getBottom()) {
                    removeBlocks(parent, layouter.blocks, k+1, layouter.blocks.size(), adapter);
                    return;
                }
            }
            for (int i = end, k = i - layouter.firstBlockPosition; k < layouter.blocks.size(); i++, k++) {
                CanvasBlock view = layouter.blocks.get(k);
                view.setTop(y);
                y += view.getHeight();
            }
            layouter.fillWindow(parent, adapter, parent.getWidth(), 0, parent.getBottom());
        }
    }


    private static class InvalidLayoutHandlerRangeInserted extends InvalidLayoutHandler {
        final int positionStart;
        final int itemCount;

        private InvalidLayoutHandlerRangeInserted(int positionStart, int itemCount) {
            this.positionStart = positionStart;
            this.itemCount = itemCount;
        }

        private void removeBlocksIfOutOfScreen(CanvasScrollView parent, List<CanvasBlock> blocks, Adapter adapter, int from, int insertHeight, int bottom) {
            for (int i = blocks.size() -1; i >= from; i--) {
                CanvasBlock view = blocks.get(i);
                if (view.top + insertHeight >= bottom) {
                    adapter.onBlockDetachedFromView(parent, blocks.remove(i));
                } else {
                    break;
                }
            }
        }

        @Override
        public void layout(CanvasScrollView parent, Layouter layouter, Adapter adapter) {
            if (adapter == null) {
                return;
            }
            if (positionStart > layouter.getLastBlockPosition()) {
                return;
            }
            if (positionStart < layouter.getFirstBlockPosition()) {
                layouter.firstBlockPosition += itemCount;
                for (CanvasBlock cb : layouter.blocks) {
                    cb.position += itemCount;
                }
                return;
            }

            int start = positionStart;
            int end = positionStart + itemCount;

            int insertHeight = 0;
            int y = layouter.blocks.get(start - layouter.getFirstBlockPosition()).top;
            for (int i = start; i < end; i++) {
                CanvasBlock view = layouter.createBlock(parent, adapter, i, y, true, parent.getWidth());
                y += view.getHeight();
                insertHeight += view.getHeight();
                if (y >= parent.getBottom()) {
                    removeBlocks(parent, layouter.blocks, i - layouter.getFirstBlockPosition() + 1, layouter.blocks.size(), adapter);
                    return;
                }
                removeBlocksIfOutOfScreen(parent, layouter.blocks, adapter, i+1, insertHeight, parent.getHeight());
            }
            for (int k = end - layouter.getFirstBlockPosition(); k < layouter.blocks.size(); k++) {
                CanvasBlock view = layouter.blocks.get(k);
                view.position += itemCount;
                view.setTop(y);
                y += view.getHeight();
            }
            layouter.fillWindow(parent, adapter, parent.getWidth(), 0, parent.getBottom());
        }
    }


    private static class InvalidLayoutHandlerRangeRemoved extends InvalidLayoutHandler {
        final int positionStart;
        final int itemCount;

        private InvalidLayoutHandlerRangeRemoved(int positionStart, int itemCount) {
            this.positionStart = positionStart;
            this.itemCount = itemCount;
        }

        @Override
        public void layout(CanvasScrollView parent, Layouter layouter, Adapter adapter) {
            if (adapter == null) {
                return;
            }

            int start = Math.max(0, positionStart - layouter.getFirstBlockPosition());
            int end = Math.max(0, Math.min(layouter.blockCount(), positionStart + itemCount - layouter.getFirstBlockPosition()));

            if (start >= layouter.blocks.size()) {
                return;//删除区域完全在 layouter 之后
            }

            removeBlocks(parent, layouter.blocks, start, end, adapter);
            //更新position
            for (int k = start; k < layouter.blocks.size(); k++) {
                layouter.blocks.get(k).position -= itemCount;
            }

            if (end <= 0) {
                layouter.firstBlockPosition -= itemCount;
                return;//删除区域完全在 layouter 之前
            }

            int y = 0;
            if (start == 0) {
                layouter.firstBlockPosition = Math.max(0, Math.min(positionStart, adapter.getItemCount() - 1));
            } else {
                y = layouter.blocks.get(start-1).getBottom();
            }
            for (int i = start; i < layouter.blocks.size(); i++) {
                y += layouter.blocks.get(i).setTop(y).height;
            }
            layouter.fillWindow(parent, adapter, parent.getWidth(), 0, parent.getBottom());
        }
    }
    
    private class ScrollViewDataObserver extends AdapterDataObserver {
        @Override
        public void notifyDataSetChanged() {
            mLayouter.invalidHandlers.add(new InvalidLayoutHandlerOnChanged());
            requestLayout();
            invalidate();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            mLayouter.invalidHandlers.add(new InvalidLayoutHandlerRangeChanged(positionStart, itemCount));
            requestLayout();
            invalidate();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            mLayouter.invalidHandlers.add(new InvalidLayoutHandlerRangeInserted(positionStart, itemCount));
            requestLayout();
            invalidate();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            mLayouter.invalidHandlers.add(new InvalidLayoutHandlerRangeRemoved(positionStart, itemCount));
            requestLayout();
            invalidate();
        }
    }

    ///////////////////////////////////////////////////////
    private List<OnScrollListener> mScrollListeners;
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * The RecyclerView is currently being dragged by outside input such as user touch input.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * The RecyclerView is currently animating to a final position while not under
     * outside control.
     */
    public static final int SCROLL_STATE_SETTLING = 2;
    public interface OnScrollListener {
        /**
         * Callback method to be invoked when CanvasScrollView's scroll state changes.
         *
         * @param scrollView The CanvasScrollView whose scroll state has changed.
         * @param newState     The updated scroll state. One of {@link #SCROLL_STATE_IDLE},
         *                     {@link #SCROLL_STATE_DRAGGING} or {@link #SCROLL_STATE_SETTLING}.
         */
        void onScrollStateChanged(CanvasScrollView scrollView, int newState);
        void onScrolled(CanvasScrollView scrollView, int dx, int dy);
    }

    public void addOnScrollListener(OnScrollListener listener) {
        if (mScrollListeners == null) {
            mScrollListeners = new ArrayList<>();
        }
        mScrollListeners.add(listener);
    }

    /**
     * Remove a listener that was notified of any changes in scroll state or position.
     *
     * @param listener listener to set or null to clear
     */
    public void removeOnScrollListener(OnScrollListener listener) {
        if (mScrollListeners != null) {
            mScrollListeners.remove(listener);
        }
    }

    /**
     * Remove all secondary listener that were notified of any changes in scroll state or position.
     */
    public void clearOnScrollListeners() {
        if (mScrollListeners != null) {
            mScrollListeners.clear();
        }
    }

    void dispatchOnScrollStateChanged(int state) {
        if (mScrollListeners != null) {
            for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
                mScrollListeners.get(i).onScrollStateChanged(this, state);
            }
        }
    }

    void dispatchOnScrolled(int hresult, int vresult) {
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
        onScrollChanged(scrollX, scrollY, scrollX - hresult, scrollY - vresult);
        if (mScrollListeners != null) {
            for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
                mScrollListeners.get(i).onScrolled(this, hresult, vresult);
            }
        }
    }

    private int mScrollState = SCROLL_STATE_IDLE;
    public int getScrollState() {
        return mScrollState;
    }

    void setScrollState(int state) {
        if (state == mScrollState) {
            return;
        }
        mScrollState = state;
        dispatchOnScrollStateChanged(state);
    }

    public void stopScroll() {
        setScrollState(SCROLL_STATE_IDLE);
        if (!mScroller.isFinished()) {
            abortAnimatedScroll();
        }
    }

    private final PerfermClickRunnable mPerformClickRunnable = new PerfermClickRunnable();
    private final class PerfermClickRunnable implements Runnable {
        int x,y;
        @Override
        public void run() {
            Log.i(TAG, "onTouch click run");
            CanvasBlock block = findBlock(x, y);
            if (block != null && mAdapter != null) {
                Log.i(TAG, "onTouch click run block");
                int cbx = x - block.left;
                int cby = y - block.top;
                playSoundEffect(SoundEffectConstants.CLICK);
                mAdapter.onBlockClicked(CanvasScrollView.this, block, cbx, cby);
            }
        }
    }

    private final PerfermTapRunnable mPerfermTapRunnable = new PerfermTapRunnable();
    private final class PerfermTapRunnable implements Runnable {
        int x,y;
        @Override
        public void run() {
            CanvasBlock block = findBlock(x, y);
            if (block != null && mAdapter != null) {
                int cbx = x - block.left;
                int cby = y - block.top;
                playSoundEffect(SoundEffectConstants.CLICK);
                mAdapter.onBlockTap(CanvasScrollView.this, block, cbx, cby);
            }
        }
    }

    private final PerfermLongClickRunnable mPerformLongClickRunnable = new PerfermLongClickRunnable();
    private final class PerfermLongClickRunnable implements Runnable {
        boolean hasHandled = false;
        int x,y;
        @Override
        public void run() {
            CanvasBlock block = findBlock(x, y);
            if (block != null && mAdapter != null) {
                int cbx = x - block.left;
                int cby = y - block.top;
                playSoundEffect(SoundEffectConstants.CLICK);
                hasHandled = mAdapter.onBlockLongClicked(CanvasScrollView.this, block, cbx, cby);
            }
        }
    }

    /////////////////////////////////////////////////////////////

    public CanvasBlock getBlockAtPosition(int pos) {
        if (pos < mLayouter.getFirstBlockPosition()) {
            return null;
        }
        if (pos > mLayouter.getLastBlockPosition()) {
            return null;
        }
        return mLayouter.blockAt(pos - mLayouter.getFirstBlockPosition());
    }

    public CanvasBlock getFirstVisibleBlock() {
        if (mLayouter.blockCount() == 0) {
            return null;
        }
        return mLayouter.blockAt(0);
    }

    public CanvasBlock getLastVisibleBlock() {
        if (mLayouter.blockCount() == 0) {
            return null;
        }
        return mLayouter.blockAt(mLayouter.blocks.size() - 1);
    }

    public int getFirstVisiblePosition() {
        return mLayouter.getFirstBlockPosition();
    }
    public int getLastVisiblePosition() {
        return mLayouter.getLastBlockPosition();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAdapter != null) {
            mAdapter.onScrollviewDetachedFromWindow(this);
        }
        flingRunnable.stop();
        flingChildXRunnable.stop();
    }

    private final FlingChildXRunnable flingChildXRunnable = new FlingChildXRunnable();
    private class FlingChildXRunnable implements Runnable {
        private int mLastScrollerX;
        @Override
        public void run() {
            if (draggedBlock == null || mScroller.isFinished()) {
                stop();
                setScrollState(SCROLL_STATE_IDLE);
                return;
            }
            mScroller.computeScrollOffset();

            final int x = mScroller.getCurrX();
            int unconsumed = x - mLastScrollerX;
            mLastScrollerX = x;
            unconsumed -= scrollChildByInternal(unconsumed);
            if (unconsumed != 0 || mScroller.isFinished()) {
                stop();
                setScrollState(SCROLL_STATE_IDLE);
            } else {
                postInvalidateOnAnimation();
            }
        }

        private void postInvalidateOnAnimation() {
            removeCallbacks(this);
            ViewCompat.postOnAnimation(CanvasScrollView.this, this);
        }


        public boolean start(int velocityX) {
            if (draggedBlock == null) {
                return false;
            }
            mScroller.fling(0, 0, // start
                    velocityX, 0, // velocities
                    Integer.MIN_VALUE, Integer.MAX_VALUE, // x
                    0, 0, // y
                    0, 0); // overscroll
            mLastScrollerX = 0;
            setScrollState(SCROLL_STATE_SETTLING);

            postInvalidateOnAnimation();
            return true;
        }

        public void stop() {
            abortAnimatedScroll();
            removeCallbacks(this);
        }
    }

    private final FlingRunnable flingRunnable = new FlingRunnable();
    private class FlingRunnable implements Runnable {
        private int mLastScrollerY;

        public boolean start(int velocityY) {
            if (getChildCount() == 0) {
                stop();
                return false;
            }
            mScroller.fling(0, 0, // start
                    0, velocityY, // velocities
                    0, 0, // x
                    Integer.MIN_VALUE, Integer.MAX_VALUE, // y
                    0, 0); // overscroll
            runAnimatedScroll(true);
            setScrollState(SCROLL_STATE_SETTLING);

            postInvalidateOnAnimation();
            return true;
        }

        public void stop() {
            abortAnimatedScroll();
            removeCallbacks(this);
        }

        @Override
        public void run() {
            if (mScroller.isFinished()) {
                stop();
                setScrollState(SCROLL_STATE_IDLE);
                return;
            }

            mScroller.computeScrollOffset();

            final int y = mScroller.getCurrY();
            int unconsumed = y - mLastScrollerY;
            mLastScrollerY = y;

            // Nested Scrolling Pre Pass
            mScrollConsumed[1] = 0;
            dispatchNestedPreScroll(0, unconsumed, mScrollConsumed, null,
                    ViewCompat.TYPE_NON_TOUCH);
            unconsumed -= mScrollConsumed[1];

            if (unconsumed != 0) {
                // Internal Scroll
                final int scrolledByMe = scrollByInternal(0, unconsumed);
                unconsumed -= scrolledByMe;
                adjustScrollY();

                // Nested Scrolling Post Pass
                mScrollConsumed[1] = 0;
                dispatchNestedScroll(0, scrolledByMe, 0, unconsumed, mScrollOffset,
                        ViewCompat.TYPE_NON_TOUCH, mScrollConsumed);
                unconsumed -= mScrollConsumed[1];
            }

            if (unconsumed != 0) {
                final int mode = getOverScrollMode();
                final boolean canOverscroll = mode == OVER_SCROLL_ALWAYS
                        || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScroll());
                if (canOverscroll) {
                    ensureGlows();
                    if (unconsumed < 0) {
                        if (mEdgeGlowTop.isFinished()) {
                            mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    } else {
                        if (mEdgeGlowBottom.isFinished()) {
                            mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    }
                }
            }

            if (unconsumed != 0 || mScroller.isFinished()) {
                stop();
                setScrollState(SCROLL_STATE_IDLE);
                stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
            } else {
                postInvalidateOnAnimation();
            }
        }

        private void runAnimatedScroll(boolean participateInNestedScrolling) {
            if (participateInNestedScrolling) {
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH);
            } else {
                stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
            }
            mLastScrollerY = 0;
        }

        private void postInvalidateOnAnimation() {
            removeCallbacks(this);
            ViewCompat.postOnAnimation(CanvasScrollView.this, this);
        }
    }
}

