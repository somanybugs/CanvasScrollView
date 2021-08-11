package lhg.canvasscrollview.blocks;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.ViewConfiguration;



import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.CharSequenceCharacterIterator;
import lhg.canvasscrollview.SelectableAdapter;

import java.text.BreakIterator;

public class TextBlock extends CanvasScrollView.CanvasBlock implements SelectableAdapter.Selectable {
    protected final Path selectPath = new Path();
    protected final Spannable text;
    protected StaticLayout textLayout;
    protected BreakIterator breakIterator;
    protected TextPaint textPaint;
    protected static int touchSlop = -1;
    private final Invalidator invalidator = span -> TextBlock.this.invalidate();

    static void initTouchSlop(Context context) {
        if (touchSlop == -1) {
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }
    }

    private boolean isValid = true;
    @Override
    public void invalidate() {
        isValid = true;
        super.invalidate();
    }

    public void setTextPaint(TextPaint textPaint) {
        this.textPaint = textPaint;
    }

    public TextBlock(CharSequence text) {
        if (text instanceof Spannable) {
            this.text = (Spannable) text;
        } else {
            this.text = new SpannableString(text);
        }
        breakIterator = BreakIterator.getWordInstance();
        breakIterator.setText(new CharSequenceCharacterIterator(text));
    }

    @Override
    public void onMeasure(CanvasScrollView parent, int parentWidth, boolean horizontalScrollable) {
        initProps(parent.getContext());
        int maxLineWidth = getMaxLineWidth(horizontalScrollable ? Integer.MAX_VALUE / 2: parentWidth);
        if (isValid || textLayout == null || textLayout.getWidth() != maxLineWidth) {
            textLayout = new StaticLayout(text, textPaint, maxLineWidth, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            float realWidth = 0;
            for (int i = 0; i < textLayout.getLineCount(); i++) {
                realWidth = Math.max(realWidth, textLayout.getLineWidth(i));
            }
            setHeight(textLayout.getLineTop(textLayout.getLineCount()) + getPaddingTop() + getPaddingBottom());
            setWidth(Math.min(maxLineWidth, (int)Math.ceil(realWidth)) + getPaddingLeft() + getPaddingRight());
        }
        isValid = false;
        lastSelectPoint.reset();
    }

    protected void initProps(Context context) {
        if (textPaint == null) {
            textPaint = new TextPaint();
            textPaint.setTextSize(sp2px(context, 18));
            textPaint.setColor(Color.BLACK);
            textPaint.setAntiAlias(true);
        }
    }

    protected int getMaxLineWidth(int parentWidth) {
        return parentWidth - getPaddingLeft() - getPaddingRight();
    }

    @Override
    public void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom) {
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        canvas.clipRect(left - getPaddingLeft(), top - getPaddingTop(), right -getPaddingLeft(), bottom - getPaddingTop());
        textLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    public void getSelectionRange(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint begin, SelectableAdapter.SelectPoint end) {
        x -= getPaddingLeft();
        y -= getPaddingTop();
        int line = textLayout.getLineForVertical(y);
        int offset = textLayout.getOffsetForHorizontal(line, x);

        begin.offset = breakIterator.preceding(offset);
        line = textLayout.getLineForOffset(begin.offset);
        begin.x = (int) textLayout.getPrimaryHorizontal(begin.offset) + getPaddingLeft();
        begin.y = textLayout.getLineTop(line + 1) + getPaddingTop();
        begin.h = begin.y - (textLayout.getLineTop(line) + getPaddingTop());

        end.offset = breakIterator.following(offset);
        line = textLayout.getLineForOffset(end.offset);
        end.x = (int) textLayout.getPrimaryHorizontal(end.offset) + getPaddingLeft();
        end.y = textLayout.getLineTop(line + 1) + getPaddingTop();
        end.h = end.y - (textLayout.getLineTop(line) + getPaddingTop());
    }

    private final SelectableAdapter.SelectPoint lastSelectPoint = new SelectableAdapter.SelectPoint();
    @Override
    public void getSelectionIndex(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint point) {
        initTouchSlop(parent.getContext());

        int line = textLayout.getLineForVertical(y - getPaddingTop());
        int lineStart = textLayout.getLineStart(line);
        int lineEnd = textLayout.getLineEnd(line);

        if (lineStart <= lastSelectPoint.offset && lineEnd > lastSelectPoint.offset && Math.abs(x - lastSelectPoint.x) < touchSlop) {
            //filter too small dx
            point.x = lastSelectPoint.x;
            point.y = lastSelectPoint.y;
            point.offset = lastSelectPoint.offset;
            return;
        }

        int offset = textLayout.getOffsetForHorizontal(line, x - getPaddingLeft());
        if (offset == lineEnd - 1 && x - getPaddingLeft() >= textLayout.getLineWidth(line)) {
            //at line end of break word, it cant be selected, so point to end
            offset = lineEnd;
            line++;
        }

        point.offset = offset;
        point.x = (int) textLayout.getPrimaryHorizontal(offset) + getPaddingLeft();
        point.y = textLayout.getLineTop(line + 1) + getPaddingTop();
        point.h = point.y - (textLayout.getLineTop(line) + getPaddingTop());

        point.copyTo(lastSelectPoint);
    }

    @Override
    public void onDrawSelection(CanvasScrollView parent, Canvas canvas, Paint selectPaint, int begin, int end) {
        selectPath.reset();
        textLayout.getSelectionPath(begin, end, selectPath);
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        canvas.drawPath(selectPath, selectPaint);
        canvas.restore();
    }

    @Override
    public String getSelectionText(int begin, int end) {
        StringBuilder sb = new StringBuilder(text.subSequence(begin, end));
        if (end >= text.length() && sb.length() > 0 && sb.charAt(sb.length()-1) != '\n') {
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public int getSelectableSize() {
        return text.length();
    }

    public static int sp2px(Context context, float spValue) {
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    @Override
    public void onClicked(CanvasScrollView parent, int x, int y) {
        SelectableAdapter.SelectPoint point = new SelectableAdapter.SelectPoint();
        point.offset = -1;
        getSelectionIndex(parent, x, y, point);
        if (point.offset != -1) {
            ClickableSpan[] spans = text.getSpans(point.offset, point.offset + 1, ClickableSpan.class);
            if (spans != null) {
                for (ClickableSpan span : spans) {
                    span.onClick(parent);
                }
            }
        }
    }

    public Invalidator getSpanInvalidator() {
        return invalidator;
    }

    public interface Invalidator {
        void invalidate(Object what);
    }

    public interface Invalidateable {
        void setInvalidator(Invalidator invalidator);
    }
}
