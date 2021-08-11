package lhg.canvasscrollview.blocks;

import android.graphics.Canvas;
import android.graphics.Paint;

import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.SelectableAdapter;

import java.util.ArrayList;
import java.util.List;

//must be left to right, top to bottom
public abstract class GroupBlock extends CanvasScrollView.CanvasBlock implements SelectableAdapter.Selectable, CanvasScrollView.CanvasBlockParent {

    protected List<CanvasScrollView.CanvasBlock> children = new ArrayList();

    public void addBlock(CanvasScrollView.CanvasBlock block) {
        children.add(block);
        block.onAttachedToParent(this);

    }
    public void removeBlock(CanvasScrollView.CanvasBlock block) {
        children.remove(block);
        block.onDetachedFromParent(this);
    }
    public void removeBlock(int index) {
        CanvasScrollView.CanvasBlock block = children.remove(index);
        if (block != null) {
            block.onDetachedFromParent(this);
        }
    }

    public List<CanvasScrollView.CanvasBlock> getChildren() {
        return children;
    }

    @Override
    public void invalidate(CanvasScrollView.CanvasBlock child) {
        CanvasScrollView.CanvasBlockParent parent = getParent();
        if (parent != null) {
            parent.invalidate(this);
        }
    }

    @Override
    public void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom) {
        for (CanvasScrollView.CanvasBlock b : children) {
            if (b.getBottom() < top || b.getTop() > bottom || b.getLeft() > right || b.getRight() < left) {
                continue;
            }
            canvas.save();
            canvas.translate(b.getLeft(), b.getTop());
            b.onDraw(parent, canvas,
                    Math.max(left-b.getLeft(), 0), Math.max(top - b.getTop(), 0),
                    Math.min(right, b.getRight()) - b.getLeft(), Math.min(bottom, b.getBottom()) - b.getTop());
            canvas.restore();
        }
    }

    protected CanvasScrollView.CanvasBlock findChild(int x, int y) {
        for (CanvasScrollView.CanvasBlock b : children) {
            if (b.getTop() <= y && b.getBottom() >= y && b.getLeft() <= x && b.getRight() >= x) {
                return b;
            }
        }
        return null;
    }

    protected CanvasScrollView.CanvasBlock findNearestChildLT(int x, int y) {
        CanvasScrollView.CanvasBlock block = null;
        int distance = Integer.MAX_VALUE;
        int tmp = 0;
        for (CanvasScrollView.CanvasBlock b : children) {
            if (b.getTop() < y && b.getLeft() < x) {
                tmp = y - b.getTop() + x - b.getLeft();
                if (tmp < distance) {
                    block = b;
                    distance = tmp;
                }
            }
        }
        return block;
    }

    @Override
    public void getSelectionRange(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint begin, SelectableAdapter.SelectPoint end) {
        CanvasScrollView.CanvasBlock child = findChild(x, y);
        if (child == null || !(child instanceof SelectableAdapter.Selectable)) {
            begin.reset();
            end.reset();
            return;
        }
        ((SelectableAdapter.Selectable)child).getSelectionRange(parent, x-child.getLeft(), y - child.getTop(), begin, end);
        translateSelectPointToMe(child, begin);
        translateSelectPointToMe(child, end);
    }

    private void translateSelectPointToMe(CanvasScrollView.CanvasBlock child, SelectableAdapter.SelectPoint point) {
        point.y += child.getTop();
        point.x += child.getLeft();
        for (CanvasScrollView.CanvasBlock b : children) {
            if (b == child) {
                break;
            }
            if (b instanceof SelectableAdapter.Selectable) {
                point.offset += ((SelectableAdapter.Selectable) b).getSelectableSize();
            }
        }
    }

    @Override
    public void getSelectionIndex(CanvasScrollView parent, int x, int y, SelectableAdapter.SelectPoint point) {
        CanvasScrollView.CanvasBlock child = findNearestChildLT(x, y);
        if (child == null && children.size() > 0) {
            child = children.get(0);
        }
        if (child == null || !(child instanceof SelectableAdapter.Selectable)) {
            point.reset();
            return;
        }
        ((SelectableAdapter.Selectable)child).getSelectionIndex(parent, x - child.getLeft(), y - child.getTop(), point);
        translateSelectPointToMe(child, point);
    }

    @Override
    public int getSelectableSize() {
        int count = 0;
        for (CanvasScrollView.CanvasBlock b : children) {
            if (b instanceof SelectableAdapter.Selectable) {
                count += ((SelectableAdapter.Selectable) b).getSelectableSize();
            }
        }
        return count;
    }

    @Override
    public void onDrawSelection(CanvasScrollView parent, Canvas canvas, Paint selectPaint, int begin, int end) {
        int childBegin = 0;
        for (CanvasScrollView.CanvasBlock b : children) {
            if (!(b instanceof SelectableAdapter.Selectable)) {
                continue;
            }
            int size = ((SelectableAdapter.Selectable) b).getSelectableSize();
            getChildSelction(range, begin, end, childBegin, childBegin += size);
            if (!range.isEmpty()) {
                canvas.save();
                canvas.translate(b.getLeft(), b.getTop());
                ((SelectableAdapter.Selectable) b).onDrawSelection(parent, canvas, selectPaint, range.begin, range.end);
                canvas.restore();
            } else if (childBegin >= end) {
                break;
            }
        }
    }

    private void getChildSelction(Range range, int begin, int end, int childBegin, int childEnd) {
        range.begin = Math.max(begin - childBegin, 0);
        range.end = Math.min(childEnd, end) - childBegin;
    }

    final Range range = new Range();
    @Override
    public String getSelectionText(int begin, int end) {
        int childBegin = 0;
        StringBuilder sb = new StringBuilder();
        for (CanvasScrollView.CanvasBlock b : children) {
            if (!(b instanceof SelectableAdapter.Selectable)) {
                continue;
            }
            int size = ((SelectableAdapter.Selectable) b).getSelectableSize();
            getChildSelction(range, begin, end, childBegin, childBegin += size);
            if (!range.isEmpty()) {
                sb.append(((SelectableAdapter.Selectable) b).getSelectionText(range.begin, range.end));
            } else if (childBegin >= end) {
                break;
            }
        }
        return sb.toString();
    }

    private static class Range {
        int begin;
        int end;
        boolean isEmpty() {
            return end <= begin;
        }
    }


    @Override
    public void onClicked(CanvasScrollView parent, int x, int y) {
        CanvasScrollView.CanvasBlock block = findChild(x, y);
        if (block != null) {
            block.onClicked(parent, x - block.getLeft(), y - block.getTop());
        }
    }

    @Override
    public boolean onLongClicked(CanvasScrollView parent, int x, int y) {
        boolean result = false;
        CanvasScrollView.CanvasBlock block = findChild(x, y);
        if (block != null) {
            result = block.onLongClicked(parent, x - block.getLeft(), y - block.getTop());
        }
        return result || super.onLongClicked(parent, x, y);
    }
}