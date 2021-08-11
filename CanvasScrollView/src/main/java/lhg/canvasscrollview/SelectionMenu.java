package lhg.canvasscrollview;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public class SelectionMenu extends PopupWindow {

    LinearLayout llContent;
    OnMenuCallback onMenuCallback;
    Drawable mItemBackground;
    int mTextAppearance;

    public SelectionMenu(Context context) {
        this(context, null);
    }

    public SelectionMenu(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.popupMenuStyle);
        init(context);
    }

    public SelectionMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SelectionMenu(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private float power(float d) {
        return d * d;
    }

    public void setOnMenuCallback(OnMenuCallback onMenuCallback) {
        this.onMenuCallback = onMenuCallback;
    }

    private void init(Context context) {
        if (llContent != null) {
            return;
        }

        llContent = new LinearLayout(context);
        llContent.setOrientation(LinearLayout.HORIZONTAL);
        setContentView(llContent);
        setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        setOutsideTouchable(true);
//        setFocusable(false);

        final TypedArray a = context.obtainStyledAttributes(new int[]{
                android.R.attr.textAppearanceLargePopupMenu,
                android.R.attr.selectableItemBackgroundBorderless,
        });
        mTextAppearance = a.getResourceId(0, 0);
        mItemBackground = a.getDrawable(1);
        a.recycle();

        llContent.addView(createMenuItemView(context.getString(android.R.string.selectAll), () -> {
            if (onMenuCallback != null) {
                onMenuCallback.onSelectAll();
            }
        }));
        llContent.addView(createMenuItemView(context.getString(android.R.string.copy), () -> {
            if (onMenuCallback != null) {
                onMenuCallback.onCopy();
            }
        }));
        llContent.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    private TextView createMenuItemView(String text, Runnable runnable) {
        TextView textView = new TextView(llContent.getContext());
        int pad = dip2px(8);
        textView.setBackground(mItemBackground);
        textView.setTextAppearance(llContent.getContext(), mTextAppearance);
        textView.setPadding(pad,pad,pad,pad);
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setText(text);
        textView.setOnClickListener((v)-> {
            dismiss();
            runnable.run();
        });
        return textView;
    }

    public void show(View parent, int x, int y) {
        y = Math.max(0, y - llContent.getMeasuredHeight());
        x = Math.max(0, x - llContent.getMeasuredWidth() / 2);
        if (isShowing()) {
            update(x, y, -1, -1);
        } else {
            showAtLocation(parent, Gravity.NO_GRAVITY, x, y);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    protected int dip2px(float dipValue) {
        final float scale = llContent.getContext().getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public interface OnMenuCallback {
        void onCopy();
        void onSelectAll();
    }
}
