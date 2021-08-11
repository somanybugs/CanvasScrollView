package lhg.canvasscrollview.blocks;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.DimenUtils;

import java.util.Arrays;

public class TableBlock extends GroupBlock {

    public static final int ALIGNMENT_LEFT = 0;
    public static final int ALIGNMENT_CENTER = 1;
    public static final int ALIGNMENT_RIGHT = 2;

    private int row;
    private int col;
    private int borderWidth = -1;
    private int borderColor = Color.GRAY;
    private static Paint paint = new Paint();
    private int[] rowHeights = null;
    private int[] colWidths = null;
    private int cellPadding = -1;
    private int[] alignments = null;
    private int headerColor = Color.TRANSPARENT;
    private int[] bodyColors = null;

    public TableBlock() {
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public void setBorderWidth(int borderWidth) {
        this.borderWidth = borderWidth;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public void setAlignments(int[] alignments) {
        this.alignments = alignments;
    }

    private int getAlignment(int col) {
        return alignments != null && alignments.length > col ? alignments[col] : ALIGNMENT_LEFT;
    }

    public int[] getAlignments() {
        return alignments;
    }

    public void setCellPadding(int cellPadding) {
        this.cellPadding = cellPadding;
    }

    public void setHeaderColor(int headerColor) {
        this.headerColor = headerColor;
    }

    public void setBodyColors(int[] bodyColors) {
        this.bodyColors = bodyColors;
    }

    protected int getRowColor(int row) {
        if (row == 0) {
            return headerColor;
        } else if (bodyColors != null && bodyColors.length > 0) {
            return bodyColors[(row-1)%bodyColors.length];
        }
        return Color.TRANSPARENT;
    }

    @Override
    public void onMeasure(CanvasScrollView parent, int parentWidth, boolean horizontalScrollable) {
        initProps(parent.getContext());
        for (CanvasScrollView.CanvasBlock b : children) {
            b.setPadding(0,0,0,0);
        }
        int totalWidth = parentWidth - getPaddingLeft() - getPaddingRight() - cellPadding * col * 2 - borderWidth * (col + 1);
        for (int r = 0; r < row; r++) {
            for (int c = 0; c < col; c++) {
                int i = r * col + c;
                CanvasScrollView.CanvasBlock block = children.get(i);
                block.onMeasure(parent, totalWidth);
                colWidths[c] = Math.max(colWidths[c], block.getWidth());
            }
            boolean continueMeasure = false;
            for (int c = 0; c < col; c++) {
                if (colWidths[c] < totalWidth / col) {
                    continueMeasure = true;
                    break;
                }
            }
            if (!continueMeasure) {
                break;
            }
        }

        int averageColWidth = totalWidth / col;
        int minColWidth = Integer.MAX_VALUE, maxColWidth = 0;
        int measuredTotalWidth = 0;
        int largeColCount = 0;
        int smallColWidths = 0;
        for (int c = 0; c < col; c++) {
            minColWidth = Math.min(minColWidth, colWidths[c]);
            maxColWidth = Math.max(maxColWidth, colWidths[c]);
            measuredTotalWidth += colWidths[c];
            if (colWidths[c] > averageColWidth) {
                largeColCount++;
            } else {
                smallColWidths += colWidths[c];
            }
        }
        if (measuredTotalWidth == totalWidth) {
            //do nothing
        } else if (measuredTotalWidth < totalWidth) {
            int delta = (totalWidth - measuredTotalWidth) / col;
            for (int c = 0; c < col; c++) {
                colWidths[c] += delta;
            }
        } else if (minColWidth >= averageColWidth || maxColWidth <= averageColWidth || largeColCount == 0) {
            Arrays.fill(colWidths, averageColWidth);
        } else {
            averageColWidth = (totalWidth - smallColWidths)/largeColCount;
            int unconsumed = (measuredTotalWidth - totalWidth);
            for (int c = 0; c < col; c++) {
                if (colWidths[c] <= averageColWidth) {
                    continue;
                }
                int oldColWidth = colWidths[c];
                colWidths[c] = Math.max(averageColWidth, oldColWidth - unconsumed/largeColCount);
                largeColCount--;
                unconsumed -= (oldColWidth - colWidths[c]);
            }
        }

        int y = getPaddingTop() + borderWidth;
        for (int r = 0; r < row; r++) {
            int x = getPaddingLeft() + borderWidth;
            for (int c = 0; c < col; c++) {
                int i = r * col + c;
                CanvasScrollView.CanvasBlock block = children.get(i);
                if (block.getWidth() != colWidths[c]) {
                    block.onMeasure(parent, colWidths[c]);
                }
                int left = x + cellPadding;
                if (getAlignment(c) == ALIGNMENT_RIGHT) {
                    left += colWidths[c] - block.getWidth();
                } else if (getAlignment(c) == ALIGNMENT_CENTER) {
                    left += (colWidths[c] - block.getWidth())/2;
                }
                block.setLeft(left);
                block.setTop(y + cellPadding);
                rowHeights[r] = Math.max(rowHeights[r], block.getHeight());
                x += colWidths[c] + borderWidth + cellPadding * 2;
            }
            y += rowHeights[r] + borderWidth + cellPadding * 2;
        }
        setWidth(parentWidth);
        setHeight(y + getPaddingBottom());
    }

    private void initProps(Context context) {
        if (borderWidth == -1) {
            borderWidth = DimenUtils.dip2px(context, 0.5f);
        }
        if (cellPadding == -1) {
            cellPadding = DimenUtils.dip2px(context, 8);
        }
        if (rowHeights == null || rowHeights.length < row) {
            rowHeights = new int[row];
        }
        if (colWidths == null || colWidths.length < col) {
            colWidths = new int[col];
        }
        Arrays.fill(rowHeights, 0);
        Arrays.fill(colWidths, 0);
    }

    @Override
    public void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom) {

        int x= 0;
        int y = 0;

        left = Math.max(left, getPaddingLeft());
        top = Math.max(top, getPaddingTop());
        right = Math.min(right, getWidth() - getPaddingRight());
        bottom = Math.min(bottom, getHeight() - getPaddingBottom());

        paint.setStyle(Paint.Style.FILL);
        //draw background
        y = getPaddingTop();
        for (int r = 0; r < row; r++) {
            if (y > bottom) {
                break;
            }
            int t = y;
            y += rowHeights[r] + borderWidth + cellPadding * 2;
            if (y < top) {
                continue;
            }

            paint.setColor(getRowColor(r));
            canvas.drawRect(left, t, right, y, paint);
        }


        paint.setColor(borderColor);
        paint.setStyle(Paint.Style.STROKE);
        //draw |
        //     |
        x = getPaddingLeft();
        for (int c = 0; c <= col; c++) {
            if (x > right) {
                break;
            }

            int l = x;
            if (c < col) {
                x += borderWidth + cellPadding * 2 + colWidths[c];
            }
            if (l+borderWidth < left) {
                continue;
            }

            l += borderWidth / 2;
            canvas.drawLine(l, top, l, bottom, paint);
        }

        //draw ----
        y = getPaddingTop();
        for (int r = 0; r <= row; r++) {
            if (y > bottom) {
                break;
            }
            int t = y;
            if (r < row) {
                y += rowHeights[r] + borderWidth + cellPadding * 2;
            }
            if (t + borderWidth < top) {
                continue;
            }
            t += borderWidth/2;
            canvas.drawLine(left, t, right, t, paint);
        }

        super.onDraw(parent, canvas, left, top, right, bottom);
    }

}
