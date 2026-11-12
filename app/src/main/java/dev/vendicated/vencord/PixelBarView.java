package dev.vendicated.vencord;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A loading bar made of square blocks, in the same pixel style as the logo. */
public class PixelBarView extends View {
    private static final int BLOCKS = 20;
    private final Paint paint = new Paint();
    private int progress = 0;

    public PixelBarView(Context c) {
        super(c);
        paint.setAntiAlias(false);
    }

    public PixelBarView(Context c, AttributeSet a) {
        super(c, a);
        paint.setAntiAlias(false);
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int p) {
        progress = Math.max(0, Math.min(100, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float gap = Math.max(2, 2 * d);
        float w = getWidth(), h = getHeight();
        float block = (w - gap * (BLOCKS - 1)) / BLOCKS;
        int filled = Math.round(progress / 100f * BLOCKS);
        for (int i = 0; i < BLOCKS; i++) {
            float x = i * (block + gap);
            if (i < filled) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRect(x, 0, x + block, h, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, d));
                paint.setColor(0xFF4A4A4A);
                canvas.drawRect(x + 0.5f, 0.5f, x + block - 0.5f, h - 0.5f, paint);
            }
        }
    }
}
