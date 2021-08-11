package lhg.canvasscrollview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SelectionView extends View {
    private static final int DEFAULT_SIZE_IN_DPS = 26;

    public static final int DIRECTION_LEFT_TOP = 1;
    public static final int DIRECTION_LEFT_BOTTOM = 2;
    public static final int DIRECTION_RIGHT_TOP = 3;
    public static final int DIRECTION_RIGHT_BOTTOM = 4;

    private int mSoldColor = 0;
    private int mDirection = DIRECTION_RIGHT_BOTTOM;
    private Paint mSoldPaint = new Paint();
    private Path mBoderPath = null;


    public SelectionView(Context context) {
        this(context, null);
    }

    public SelectionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SelectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSoldColor = getThemeAccentColor(context);
        mSoldPaint.setStyle(Paint.Style.FILL);
        mSoldPaint.setAntiAlias(true);
    }


    public void setColor(int color) {
        this.mSoldColor = color;
        postInvalidate();
    }

    public void setDirection(int direction) {
        if (mDirection != direction) {
            this.mDirection = direction;
            postInvalidate();
        }
    }

    public int getDirection() {
        return mDirection;
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBoderPath = null;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        switch (mDirection) {
            case DIRECTION_RIGHT_BOTTOM:
                break;
            case DIRECTION_RIGHT_TOP:
                canvas.rotate(-90, cx, cy);
                break;
            case DIRECTION_LEFT_BOTTOM:
                canvas.rotate(90, cx, cy);
                break;
            case DIRECTION_LEFT_TOP:
                canvas.rotate(180, cx, cy);
                break;
        }

        initPath();
        mSoldPaint.setColor(mSoldColor);
        canvas.drawPath(mBoderPath, mSoldPaint);
        canvas.restore();
    }

    private void initPath() {
        if (mBoderPath == null) {
            mBoderPath = createPathRigthBottom(new Rect(0, 0, getWidth(), getHeight()));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int withSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
        } else if (widthMode == MeasureSpec.EXACTLY) {
            heightSize = withSize;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            withSize = heightSize;
        } else {
            heightSize = withSize = dip2px(DEFAULT_SIZE_IN_DPS);
        }
        setMeasuredDimension(withSize, heightSize);
    }


    protected int dip2px(float dipValue) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }


    private Path createPathRigthBottom(Rect r) {
        int radius = Math.min(r.width(), r.height()) / 2;
        Path path = new Path();
        path.moveTo(r.left, r.top);
        path.lineTo(r.left, r.bottom-radius);
        //圆弧
        path.arcTo(new RectF(r.left, r.top, r.right, r.bottom), 180, -270, false);
        path.lineTo(r.left, r.top);
        path.close();
        return path;
    }
}
