package lhg.canvasscrollview.blocks;


import lhg.canvasscrollview.CanvasScrollView;


public class VerticalGroupBlock extends GroupBlock {

    @Override
    public void onMeasure(CanvasScrollView parent, int parentWidth, boolean horizontalScrollable) {
        onMeasure(parent, 0, 0, parentWidth, horizontalScrollable);
    }

    public void onMeasure(CanvasScrollView parent, int extraLeftOffset, int extraTopOffset, int parentWidth, boolean horizontalScrollable) {
        int width = 0;
        int top = getPaddingTop() + extraTopOffset;
        int ph = getPaddingLeft() + getPaddingRight() + extraLeftOffset;
        for (CanvasScrollView.CanvasBlock b : children) {
            b.setTop(top);
            b.setLeft(getPaddingLeft() + extraLeftOffset);
            b.onMeasure(parent, parentWidth - ph, horizontalScrollable);
            width = Math.max(width, b.getWidth());
            top += b.getHeight();
        }
        setWidth(Math.min(parentWidth, width + ph));
        setHeight(top + getPaddingBottom());
    }



}
