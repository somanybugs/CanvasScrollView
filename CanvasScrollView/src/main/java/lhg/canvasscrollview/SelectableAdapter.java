package lhg.canvasscrollview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.Toast;

/**
 * 2021.07.15 lhg
 * @param <T>
 */
public abstract class SelectableAdapter<T extends CanvasScrollView.CanvasBlock> extends CanvasScrollView.Adapter<T> {
    private static final String TAG = "SelectableAdapter";
    SelectionHandle2 handle1, handle2;
    Paint selectPaint;
    SelectionMenu selectionMenu;

    int[] tmpInts2 = new int[2];

    public static class SelectPoint {
        public static final int NO_SELECTION_POSITION = -2;
        public static final int INVALID_MIN_POSITION = -1;
        public static final int INVALID_MAX_POSITION = Integer.MAX_VALUE - 100000;
        private int position = NO_SELECTION_POSITION;
        public int offset = 0;
        public int x = 0;
        public int y = 0;
        public int h = 0;//the selectionHandle height

        public boolean isBiggerThan(SelectPoint o) {
            return position > o.position || (position == o.position && offset > o.offset);
        }
        public boolean isBiggerOrEqualThan(SelectPoint o) {
            return isBiggerThan(o) || equals(o);
        }
        public boolean equals(SelectPoint o) {
            return position == o.position && offset == o.offset;
        }

        @Override
        public String toString() {
            return "SelectPoint{" +
                    "position=" + position +
                    ", offset=" + offset +
                    '}';
        }

        public void copyTo(SelectPoint dest) {
            dest.position = position;
            dest.x = x;
            dest.y = y;
            dest.h = h;
            dest.offset = offset;
        }

        public void reset() {
            position = NO_SELECTION_POSITION;
            offset = 0;
            x = 0;
            y = 0;
            h = 0;//the selectionHandle height
        }
    }

    private static class SelectionCompare {
        int oldBeginPosition;
        int oldBeginOffset;
        int oldEndPosition;
        int oldEndOffset;

        void init(SelectPoint beginPoint, SelectPoint endPoint) {
            oldBeginPosition = beginPoint.position;
            oldBeginOffset = beginPoint.offset;
            oldEndPosition = endPoint.position;
            oldEndOffset = endPoint.offset;
        }
        boolean equals(SelectPoint beginPoint, SelectPoint endPoint) {
            return oldBeginPosition == beginPoint.position && oldBeginOffset == beginPoint.offset
                    && oldEndPosition == endPoint.position && oldEndOffset == endPoint.offset;
        }
    }


    private static int getThemeAccentColor(Context context) {
        final TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.colorAccent});
        try {
            return a.getColor(0, 0);
        } catch (Exception e) {
            return Color.DKGRAY;
        } finally {
            a.recycle();
        }
    }

    private static class VisibleRect {
        public final Rect rect = new Rect();
        public final Rect clip1 = new Rect();
        public final Rect clip2 = new Rect();
        public void reset() {
            rect.set(0,0,0,0);
            clip1.set(0,0,0,0);
            clip2.set(0,0,0,0);
        }
        public boolean contains(int x, int y) {
            return rect.contains(x, y) && !clip1.contains(x, y) &&!clip2.contains(x, y);
        }
    }


    public interface Selectable {
        void getSelectionRange(CanvasScrollView parent, int x, int y, SelectPoint begin, SelectPoint end);
        void getSelectionIndex(CanvasScrollView parent, int x, int y, SelectPoint point);
        int getSelectableSize();
        void onDrawSelection(CanvasScrollView parent, Canvas canvas, Paint selectPaint, int begin, int end);
        String getSelectionText(int begin, int end);
    }


    ///////////////////////////////////////////////adapter//////////////////////////////////////////////////////
    public SelectableAdapter(Context context) {
        selectPaint = new Paint();
        int color = 0x90000000 | (getThemeAccentColor(context) & 0x00ffffff);
        selectPaint.setColor(color);
        selectPaint.setAntiAlias(true);
        selectPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onBlockClicked(CanvasScrollView parent, T block, int x, int y) {
        super.onBlockClicked(parent, block, x, y);
        if (clearSelection()) {
            parent.invalidate();
        }
    }

    @Override
    public boolean onBlockLongClicked(CanvasScrollView parent, T block, int x, int y) {
        boolean result = super.onBlockLongClicked(parent, block, x, y);
        if (result) {
            return result;
        }
        if (block instanceof Selectable) {
            initSelectionViews(parent);
            if (!isSelectionRangeContains(block, x, y)) {
                handle1.point.position = handle2.point.position = block.getPosition();
                ((Selectable) block).getSelectionRange(parent, x, y, handle1.point, handle2.point);
                parent.invalidate(block.getLeft(), block.getTop(), block.getRight(), block.getBottom());
            }
            updateSelectHandles(null);
            updateSelectionMenu();
            return true;
        }
        if (clearSelection()) {
            parent.invalidate();
        }
        return true;
    }

    @Override
    public void onScrollStateChanged(CanvasScrollView scrollView, int newState) {
        super.onScrollStateChanged(scrollView, newState);
        if (newState != CanvasScrollView.SCROLL_STATE_IDLE) {
            dismissSelectHandles();
        } else {
            updateSelectHandles(null);
            updateSelectionMenu();
        }
    }

    @Override
    public void onDrawBlock(CanvasScrollView parent, T block, Canvas canvas, int left, int top, int right, int bottom) {
        super.onDrawBlock(parent, block, canvas, left, top, right, bottom);
        if (block instanceof Selectable && handle1 != null) {
            SelectPoint begin = getSelectionBegin().point;
            SelectPoint end = getSelectionEnd().point;
            if ((!begin.equals(end)) && block.getPosition() >= begin.position && block.getPosition() <= end.position) {
                int offset1 = begin.position < block.getPosition() ? 0 : begin.offset;
                int offset2 = end.position > block.getPosition() ? ((Selectable) block).getSelectableSize() : end.offset;
                ((Selectable) block).onDrawSelection(parent, canvas, selectPaint, offset1, offset2);
            }
        }
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////



    //////////////////////////////////////////selecthandle///////////////////////////////////////////////////////////
    private static class SelectionHandle2 extends SelectionHandle {
        final SelectPoint point = new SelectPoint();

        public SelectionHandle2(Context context) {
            super(context);
        }
    }

    private void initSelectionViews(final CanvasScrollView parent) {
        if (handle1 != null) {
            return;
        }
        handle1 = new SelectionHandle2(parent.getContext());
        handle2 = new SelectionHandle2(parent.getContext());
        handle1.setOnDraggingCallback(onDraggingCallback);
        handle2.setOnDraggingCallback(onDraggingCallback);
    }

    public boolean clearSelection() {
        if (handle1 == null) {
            return false;
        }
        dismissSelectionMenu();
        dismissSelectHandles();
        boolean ret = handle2.point.position != SelectPoint.NO_SELECTION_POSITION;
        handle1.point.position = SelectPoint.NO_SELECTION_POSITION;
        handle2.point.position = SelectPoint.NO_SELECTION_POSITION;
        return ret;
    }
    private static boolean isSelectHandleOutOfScrollView(SelectionHandle2 handle, CanvasScrollView.CanvasBlock block, CanvasScrollView scrollView) {
        if (block == null || handle == null) {
            return true;
        }
        int x = handle.point.x + block.getLeft();
        int y = handle.point.y + block.getTop();
        return x < 0 || x > scrollView.getWidth() || y < 0 || y> scrollView.getHeight();
    }

    public void updateSelectHandles(SelectionHandle2 draggedHandle) {
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || handle1 == null || handle1.point.position == SelectPoint.NO_SELECTION_POSITION) {
            dismissSelectHandles();
            return;
        }

        {//beginhandle
            SelectionHandle2 handle = getSelectionBegin();
            handle.selectionView.setDirection(SelectionView.DIRECTION_LEFT_BOTTOM);
            updateSelectHandle(scrollView, handle, handle == draggedHandle);
        }

        {//endHandle
            SelectionHandle2 handle = getSelectionEnd();
            handle.selectionView.setDirection(SelectionView.DIRECTION_RIGHT_BOTTOM);
            updateSelectHandle(scrollView, handle, handle == draggedHandle);
        }
    }
    private void updateSelectHandle(CanvasScrollView scrollView, SelectionHandle2 handle, boolean isBegingDragged) {
        if (isBegingDragged) {
            return;
        }
        if (scrollView.getFirstVisiblePosition() == 0 && handle.point.position == SelectPoint.INVALID_MIN_POSITION) {
            CanvasScrollView.CanvasBlock block = scrollView.getFirstVisibleBlock();
            if (block != null && block instanceof Selectable && block.getTop() >= 0) {
                getSelectionIndex(block, scrollView, 0, 0, handle.point);
            }
        }
        if (scrollView.getLastVisiblePosition() == getItemCount()-1 && handle.point.position == SelectPoint.INVALID_MAX_POSITION) {
            CanvasScrollView.CanvasBlock block = scrollView.getLastVisibleBlock();
            if (block != null && block instanceof Selectable && block.getBottom() <= scrollView.getHeight()) {
                getSelectionIndex(block, scrollView, Integer.MAX_VALUE/2, Integer.MAX_VALUE/2, handle.point);
            }
        }
        CanvasScrollView.CanvasBlock block = scrollView.getBlockAtPosition(handle.point.position);
        if (isSelectHandleOutOfScrollView(handle, block, scrollView)) {
            handle.dismiss2();
        } else {
            scrollView.getLocationInWindow(tmpInts2);
            int x = tmpInts2[0] + handle.point.x + block.getLeft();
            int y = tmpInts2[1] + handle.point.y + block.getTop();
            getSelectHandleOffset(handle, tmpInts2);
            x -= tmpInts2[0];
            y -= tmpInts2[1];
            handle.show(scrollView, x, y);
        }
    }

    private void dismissSelectHandles() {
        if (handle1 == null) {
            return;
        }
        handle1.dismiss2();
        handle2.dismiss2();
    }


    private SelectionHandle2 getSelectionBegin() {
        if (handle1 == null) {
            return null;
        }
        if (handle1.point.isBiggerThan(handle2.point)) {
            return handle2;
        } else {
            return handle1;
        }
    }
    private SelectionHandle2 getSelectionEnd() {
        return getSelectionBegin() == handle1 ? handle2 : handle1;
    }

    private static void getSelectHandleOffset(SelectionHandle handle, int[] out) {
        out[0] = out[1] = 0;
        int direction = handle.selectionView.getDirection();
        if (direction == SelectionView.DIRECTION_LEFT_BOTTOM || direction==SelectionView.DIRECTION_LEFT_TOP) {
            out[0] = handle.selectionView.getWidth();
            if (out[0] <= 0) {
                out[0] = handle.selectionView.getMeasuredWidth();
            }
        }
        if (direction == SelectionView.DIRECTION_LEFT_TOP || direction==SelectionView.DIRECTION_RIGHT_TOP) {
            out[1] = handle.selectionView.getHeight();
            if (out[1] <= 0) {
                out[1] = handle.selectionView.getMeasuredHeight();
            }
        }
    }

    static void getSelectionIndex(CanvasScrollView.CanvasBlock block, CanvasScrollView parent, int x, int y, SelectPoint point) {
        if (block instanceof Selectable) {
            ((Selectable) block).getSelectionIndex(parent, x, y, point);
        }
        point.position = block.getPosition();
    }

    private SelectionHandle.OnDraggingCallback onDraggingCallback = new SelectionHandle.OnDraggingCallback() {
        final int[] tmpInts2 = new int[2];
        final SelectionCompare selectionCompare = new SelectionCompare();
        @Override
        public void onDraggedTo(SelectionHandle handle, int newX, int newY) {
            CanvasScrollView parent = getCanvasScrollView();
            if (parent == null) {
                return;
            }
            getSelectHandleOffset(handle, tmpInts2);
            newX += tmpInts2[0];
            newY += tmpInts2[1];

            parent.getLocationInWindow(tmpInts2);
            int x = Math.max(0, Math.min(parent.getRight(), newX - tmpInts2[0]));
            int y = Math.max(0, Math.min(parent.getBottom(), newY - tmpInts2[1]));
            CanvasScrollView.CanvasBlock lastVisibleBlock = parent.getLastVisibleBlock();
            CanvasScrollView.CanvasBlock block = parent.getBlockAtY(y);
            if (block == null && lastVisibleBlock != null && y >= lastVisibleBlock.getBottom()) {
                block = lastVisibleBlock;
            }
            if (block == null || !(block instanceof Selectable)) {
                return;
            }

            selectionCompare.init(getSelectionBegin().point, getSelectionEnd().point);
            x -= block.getLeft();
            y -= block.getTop();
            getSelectionIndex(block, parent, x, y, ((SelectionHandle2) handle).point);
            SelectPoint beginPoint = getSelectionBegin().point;
            SelectPoint endPoint = getSelectionEnd().point;
            //左边handle在一个block最后位置，不可取，放到下一个block起始位置
            {
                CanvasScrollView.CanvasBlock beginBlock = parent.getBlockAtPosition(beginPoint.position);
                if (beginBlock != null && beginBlock instanceof Selectable && beginPoint.offset == ((Selectable) beginBlock).getSelectableSize()) {
                    for (int pos = beginPoint.position + 1; pos <= endPoint.position; pos++) {
                        CanvasScrollView.CanvasBlock b = parent.getBlockAtPosition(pos);
                        if (b == null) {
                            break;
                        }
                        if (b instanceof Selectable) {
                            getSelectionIndex(b, parent, 0, 0, beginPoint);
                            break;
                        }
                    }
                }
            }

            //右边handle在一个block的begin位置，不可取，放到上一个block的end位置
            if (endPoint.offset == 0) {
                for (int pos = endPoint.position - 1; pos >= beginPoint.position; pos--) {
                    CanvasScrollView.CanvasBlock b = parent.getBlockAtPosition(pos);
                    if (b == null) {
                        break;
                    }
                    if (b instanceof Selectable) {
                        getSelectionIndex(b, parent, b.getWidth(), b.getHeight(), endPoint);
                        break;
                    }
                }
            }
            if (!selectionCompare.equals(getSelectionBegin().point, getSelectionEnd().point)) {
                parent.invalidate();
            }
            updateSelectHandles((SelectionHandle2) handle);
        }

        @Override
        public void onRelease(SelectionHandle handle, int newX, int newY) {
            updateSelectHandles(null);
            updateSelectionMenu();
        }
    };

    /////////////////////////////////////////////////////////////////////////////////////////////////////





    /////////////////////////////////////////////selection menu////////////////////////////////////////////////////////

    private final VisibleRect visibleRect = new VisibleRect();
    private void updateSelectionMenu() {
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || !getVisibleSelectionRect(visibleRect)) {
            dismissSelectionMenu();
            return;
        }

        scrollView.getLocationInWindow(tmpInts2);
        int showY = visibleRect.rect.top + tmpInts2[1];
        int showX = visibleRect.clip1.right/2 + visibleRect.clip2.left/2 + tmpInts2[0];
        onShowSelectionMenu(scrollView, showX, showY);
    }

    protected void onShowSelectionMenu(View parent, int x, int y) {
        if (selectionMenu == null) {
            selectionMenu = new SelectionMenu(parent.getContext());
            selectionMenu.setOnMenuCallback(new SelectionMenu.OnMenuCallback() {
                @Override
                public void onCopy() {
                    doCopySelection();
                }

                @Override
                public void onSelectAll() {
                    doSelectAll();
                }
            });
        }
        selectionMenu.show(parent, x, y);
    }

    protected void doSelectAll() {
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || handle1 == null) {
            return;
        }

        SelectPoint beginPoint = getSelectionBegin().point;
        beginPoint.position = SelectPoint.INVALID_MIN_POSITION;
        beginPoint.offset = 0;
        beginPoint.x = 0;
        beginPoint.y = 0;
        beginPoint.h = 0;

        SelectPoint endPoint = getSelectionEnd().point;
        endPoint.position = SelectPoint.INVALID_MAX_POSITION;
        endPoint.offset = 0;
        endPoint.x = 0;
        endPoint.y = 0;
        endPoint.h = 0;

        scrollView.invalidate();
        updateSelectionMenu();
        updateSelectHandles(null);
    }

    protected void doCopySelection() {
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || handle1 == null || handle1.point.position == SelectPoint.NO_SELECTION_POSITION) {
            return;
        }
        SelectPoint beginPoint = getSelectionBegin().point;
        SelectPoint endPoint = getSelectionEnd().point;
        StringBuilder sb = new StringBuilder();
        int i1 = Math.max(0 , beginPoint.position);
        int i2 = Math.min(getItemCount(), endPoint.position + 1);
        for (int i = i1; i < i2; i++) {
            CanvasScrollView.CanvasBlock block = getItem(scrollView, i);
            if (block instanceof Selectable) {
                int offset1 = beginPoint.position < block.getPosition() ? 0 : beginPoint.offset;
                int offset2 = endPoint.position > block.getPosition() ? ((Selectable) block).getSelectableSize() : endPoint.offset;
                sb.append(((Selectable) block).getSelectionText(offset1, offset2));
            }
        }
        try {
            ClipboardManager cmb = (ClipboardManager) scrollView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cmb.setPrimaryClip(ClipData.newPlainText(null, sb.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(scrollView.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }

        clearSelection();
        scrollView.invalidate();
        updateSelectionMenu();
        updateSelectHandles(null);
    }

    private boolean isSelectionRangeContains(CanvasScrollView.CanvasBlock block, int blockX, int blockY) {
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || !getVisibleSelectionRect(visibleRect)) {
            return false;
        }
        int x = blockX + block.getLeft();
        int y = blockY + block.getTop();
        return visibleRect.contains(x, y);
    }

    private boolean getVisibleSelectionRect(VisibleRect visibleRect) {
        visibleRect.reset();
        CanvasScrollView scrollView = getCanvasScrollView();
        if (scrollView == null || handle1 == null || handle1.point.position == SelectPoint.NO_SELECTION_POSITION) {
            return false;
        }

        SelectPoint beginPoint = getSelectionBegin().point;
        SelectPoint endPoint = getSelectionEnd().point;
        if (beginPoint.equals(endPoint)) {
            return false;
        }
        CanvasScrollView.CanvasBlock beginBlock = scrollView.getBlockAtPosition(beginPoint.position);
        if (beginPoint.position > scrollView.getLastVisiblePosition() || (beginBlock != null && beginBlock.getTop() + beginPoint.y >= scrollView.getHeight())) {
            //bellow screen
            return false;
        }
        CanvasScrollView.CanvasBlock endBlock = scrollView.getBlockAtPosition(endPoint.position);
        if (endPoint.position < scrollView.getFirstVisiblePosition() || (endBlock != null && endBlock.getTop() + endPoint.y <= 0)) {
            //above screen
            return false;
        }

        Rect rect = visibleRect.rect;
        if (beginBlock != null ) {
            visibleRect.clip1.left = 0;
            visibleRect.clip1.top = Math.max(0, beginBlock.getTop() + beginPoint.y - beginPoint.h);
            visibleRect.clip1.right = Math.max(0, beginBlock.getLeft() + beginPoint.x);
            visibleRect.clip1.bottom = Math.max(0, beginBlock.getTop() + beginPoint.y);
            rect.left = 0;
            rect.top = Math.max(0, beginBlock.getTop() + beginPoint.y - beginPoint.h);
        }
        if (endBlock != null) {
            visibleRect.clip2.left = Math.min(scrollView.getWidth(), endBlock.getLeft() + endPoint.x);
            visibleRect.clip2.top = Math.min(scrollView.getHeight(), endBlock.getTop() + endPoint.y - endPoint.h);
            visibleRect.clip2.right = scrollView.getWidth();
            visibleRect.clip2.bottom = Math.min(scrollView.getHeight(), endBlock.getTop() + endPoint.y);
            rect.bottom = visibleRect.clip2.bottom;
            rect.right = scrollView.getWidth();
        } else {
            rect.bottom = scrollView.getHeight();
            rect.right = scrollView.getWidth();
        }
        return !(rect.width() == 0 && rect.height() == 0);
    }

    private void dismissSelectionMenu() {
        if (selectionMenu == null) {
            return;
        }
        selectionMenu.dismiss();
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////
}