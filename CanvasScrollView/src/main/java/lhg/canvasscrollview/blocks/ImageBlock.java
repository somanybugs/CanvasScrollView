package lhg.canvasscrollview.blocks;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.SelectableAdapter;

@Deprecated // you should not use ImageBlock， please extends OneBlock and manage your own memory of bitmap, and be careful of oom
public class ImageBlock extends OneBlock implements SelectableAdapter.Selectable {
    public Bitmap bitmap;
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ImageBlock() {
        paint.setFilterBitmap(true);
        paint.setDither(true);
    }

    @Override
    public void onMeasure(CanvasScrollView parent, int parentWidth, boolean horizontalScrollable) {
        setContentSize(bitmap.getWidth(), bitmap.getHeight());
        super.onMeasure(parent, parentWidth, horizontalScrollable);
    }

    @Override
    public void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom) {
        Rect srcRt = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Rect destRt = new Rect(
                getLeftOffset() + getPaddingLeft(), getPaddingTop(),
                getLeftOffset() + getPaddingLeft() + getContentWidth(), getHeight() - getPaddingBottom());
        canvas.drawBitmap(bitmap, srcRt, destRt, paint);
    }

}