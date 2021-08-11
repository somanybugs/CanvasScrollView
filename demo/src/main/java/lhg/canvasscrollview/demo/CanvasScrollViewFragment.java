package lhg.canvasscrollview.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import lhg.canvasscrollview.CanvasScrollView;
import lhg.canvasscrollview.SelectableAdapter;
import lhg.canvasscrollview.blocks.ImageBlock;
import lhg.canvasscrollview.blocks.TextBlock;


public class CanvasScrollViewFragment extends Fragment {

    CanvasScrollView canvasScrollView;
    Adapter adapter;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return new CanvasScrollView(inflater.getContext());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        canvasScrollView = (CanvasScrollView) getView();
        canvasScrollView.setAdapter(adapter = new Adapter(getContext(), null));
        testLoadData();
    }

    void testLoadData() {
        Single.fromCallable(() -> {
            List<CanvasScrollView.CanvasBlock> list = new ArrayList<>();
            loadText(list);
            list.add(5, loadImage("file:///android_asset/1.jpg"));
            return list;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(drawHolders -> {
                    adapter.datas = drawHolders;
                    adapter.notifyDataSetChanged();
                });
    }

    private void loadText(List<CanvasScrollView.CanvasBlock> list) {
        String encode = "utf-8";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContext().getAssets().open("test.txt"), encode))) {
            String line = null;
            int startLine = 10;
            int endLine = 100;
            int i = 0;
            StringBuilder lines = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                i++;
                if (i >= startLine && i <= endLine) {
                    lines.append(line).append("\n");
                    if (i == endLine) {
                        list.add(new ScrollTextBlock(lines));
                    }
                } else {
                    list.add(new TextBlock2(line));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
   

    private CanvasScrollView.CanvasBlock loadImage(String url) throws ExecutionException, InterruptedException {
        Bitmap bitmap = Glide.with(getActivity()).asBitmap().load(url).submit().get();
        ImageBlock vh = new ImageBlock2();
        vh.bitmap = bitmap;
        return vh;
    }

    TextPaint textPaint = new TextPaint(); {
        textPaint.setTextSize(36);
        textPaint.setColor(Color.BLACK);
        textPaint.setAntiAlias(true);
    }

    static int PaddingH = 48;
    static class TextBlock2 extends TextBlock {
        public TextBlock2(CharSequence text) {
            super(text);
            setPadding(PaddingH, 0, PaddingH, 0);
        }
    }

    static class ScrollTextBlock extends TextBlock {
        Paint bgPaint = new Paint();
        public ScrollTextBlock(CharSequence text) {
            super(text);
            setPadding(PaddingH, PaddingH, PaddingH, PaddingH);
            bgPaint.setColor(Color.GRAY);
        }

        @Override
        public void onDraw(CanvasScrollView parent, Canvas canvas, int left, int top, int right, int bottom) {
            canvas.drawRect(left, top, right, bottom, bgPaint);
            super.onDraw(parent, canvas, left, top, right, bottom);
        }

        @Override
        protected int getMaxLineWidth(int parentWidth) {
            return parentWidth * 2 - 60;
        }
    }

    static class ImageBlock2 extends ImageBlock {
        public ImageBlock2() {
            super();
            setPadding(PaddingH, PaddingH /2, PaddingH, PaddingH /2);
        }
    }

    static class Adapter extends SelectableAdapter {

        List<CanvasScrollView.CanvasBlock> datas;

        public Adapter(Context context, List<CanvasScrollView.CanvasBlock> datas) {
            super(context);
            this.datas = datas;
        }

        @Override
        public int getItemCount() {
            return datas == null ? 0 : datas.size();
        }

        @Override
        public CanvasScrollView.CanvasBlock getItem(CanvasScrollView parent, int position) {
            return datas.get(position);
        }
    }
}