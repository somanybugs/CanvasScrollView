package lhg.canvasscrollview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.PopupWindow;

/**
 * 2021.07.15 lhg
 */
public class SelectionHandle extends PopupWindow {

    SelectionView selectionView;
    private int showX, showY;
    private OnDraggingCallback onDraggingCallback;
    private int touchSlop;


    public SelectionHandle(Context context) {
        super(context);
        init(context);
    }

    public SelectionHandle(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SelectionHandle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public SelectionHandle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
        final ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
    }

    private float power(float d) {
        return d * d;
    }

    private void init(Context context) {
        if (selectionView != null) {
            return;
        }

//        setIsClippedToScreen(true);
        selectionView = new SelectionView(context);
        setContentView(selectionView);
        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        setOutsideTouchable(true);
//        setFocusable(false);
        selectionView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        selectionView.setOnTouchListener(new View.OnTouchListener() {
            int lastX, lastY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int x = (int) event.getRawX();
                        int y = (int) event.getRawY();
                        int distance = (int) (power(x - lastX) + power(y - lastY));
                        if (distance > touchSlop*touchSlop) {
                            showX = showX + x - lastX;
                            showY = showY + y - lastY;
                            lastX = x;
                            lastY = y;
                            update(showX, showY, -1, -1);
                            dispatchDragged(showX, showY);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dispatchRelease(showX, showY);
                        break;
                }
                return true;
            }
        });
    }

    protected void dispatchDragged(int newX, int newY) {
        if (!isShowing()) {
            return;
        }
        if (onDraggingCallback != null) {
            onDraggingCallback.onDraggedTo(this, newX, newY);
        }
    }

    protected void dispatchRelease(int newX, int newY) {
        if (!isShowing()) {
            return;
        }
        if (onDraggingCallback != null) {
            onDraggingCallback.onRelease(this, newX, newY);
        }
    }

    public void setOnDraggingCallback(OnDraggingCallback onDraggingCallback) {
        this.onDraggingCallback = onDraggingCallback;
    }

    public interface OnDraggingCallback {
        void onDraggedTo(SelectionHandle handle, int newX, int newY);
        void onRelease(SelectionHandle handle,int newX, int newY);
    }



    public void show(View parent, int x, int y) {
        showX = x;
        showY = y;
        if (isShowing()) {
            update(showX, showY, -1, -1);
        } else {
            showAtLocation(parent, Gravity.NO_GRAVITY, showX, showY);
        }
    }

    @Override
    public void dismiss() {
//            super.dismiss();
    }

    public void dismiss2() {
        super.dismiss();
    }
}
