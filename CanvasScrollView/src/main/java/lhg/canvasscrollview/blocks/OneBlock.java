package lhg.canvasscrollview.blocks;

import android.graphics.Canvas;
import android.graphics.Paint;

import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.SelectableAdapter;

public abstract class OneBlock extends CanvasScrollView.CanvasBlock implements SelectableAdapter.Selectable {

    //the size for draw and selection
    int contentWidth, contentHeight;

    @Override
    public void onMeasure(CanvasScrollView parent, int parentWidth, boolean h) {
        int ph = getPaddingLeft() + getPaddingRight();
        setWidth(Math.max(parentWidth - ph, getContentWidth()) + ph);
        setHeight(getContentHeight() + getPaddingTop() + getPaddingBottom());
    }

    protected void setContentSize(int contentWidth, int contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
    }

    @Override
    public void getSelectionRange(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint begin, SelectableAdapter.SelectPoint end) {
        getSelectionIndex(parent, 0, 0, begin);
        getSelectionIndex(parent, getWidth(), getHeight(), end);
    }

    @Override
    public void getSelectionIndex(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint point) {
        if (y < getHeight() / 2) {
            point.offset = 0;
            point.x = getPaddingLeft() + getLeftOffset();
            point.y = getPaddingTop();
            point.h = 0;
        } else {
            point.offset = 1;
            point.x = getPaddingLeft() + getLeftOffset() + getContentWidth();
            point.y = getHeight() - getPaddingBottom();
            point.h = point.y - getPaddingTop();
        }
    }


    @Override
    public void onDrawSelection(CanvasScrollView parent, Canvas canvas, Paint selectPaint, int begin, int end) {
        if (begin < end) {
            canvas.drawRect(getLeftOffset() + getPaddingLeft(), getPaddingTop(),
                    getLeftOffset() + getPaddingLeft() + getContentWidth(), getHeight() - getPaddingBottom(), selectPaint);
        }
    }

    public int getLeftOffset() {
        return Math.max(0, (getWidth()-getPaddingLeft()-getPaddingRight())/2 - getContentWidth() / 2);
    }

    @Override
    public int getSelectableSize() {
        return 1;
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getContentWidth() {
        return contentWidth;
    }

    @Override
    public String getSelectionText(int begin, int end) {
        return " ";
    }
}