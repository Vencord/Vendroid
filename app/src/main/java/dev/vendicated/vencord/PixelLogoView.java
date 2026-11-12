package dev.vendicated.vencord;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Draws a word in a 5x7 pixel font (letters of FYOCORD / FYODOR only) with a drop shadow and a blinking cursor. */
public class PixelLogoView extends View {
    private static final String[] F = {"11111", "10000", "10000", "11110", "10000", "10000", "10000"};
    private static final String[] Y = {"10001", "10001", "01010", "00100", "00100", "00100", "00100"};
    private static final String[] O = {"01110", "10001", "10001", "10001", "10001", "10001", "01110"};
    private static final String[] D = {"11110", "10001", "10001", "10001", "10001", "10001", "11110"};
    private static final String[] R = {"11110", "10001", "10001", "11110", "10100", "10010", "10001"};
    private static final String[] C = {"01110", "10001", "10000", "10000", "10000", "10001", "01110"};

    private final Paint paint = new Paint();
    private String text = "FYOCORD";
    private float cell;
    private boolean cursorOn = true;

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursorOn = !cursorOn;
            invalidate();
            postDelayed(this, 520);
        }
    };

    public PixelLogoView(Context c) {
        super(c);
        init();
    }

    public PixelLogoView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        paint.setAntiAlias(false);
    }

    public void setText(String t) {
        text = t;
        requestLayout();
        invalidate();
    }

    private static String[] glyph(char ch) {
        switch (ch) {
            case 'F': return F;
            case 'Y': return Y;
            case 'O': return O;
            case 'D': return D;
            case 'R': return R;
            case 'C': return C;
            default: return null;
        }
    }

    private int columns() {
        return text.length() * 6 - 1 + 2; // letters + 1 cell gaps + room for the cursor
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float d = getResources().getDisplayMetrics().density;
        int avail = MeasureSpec.getSize(wSpec);
        float want = 7 * d;
        float fit = avail > 0 ? (avail - getPaddingLeft() - getPaddingRight()) / (float) (columns() + 1) : want;
        cell = (float) Math.floor(Math.max(2, Math.min(want, fit)));
        int w = (int) (cell * (columns() + 1)) + getPaddingLeft() + getPaddingRight();
        int h = (int) (cell * 8) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x0 = (getWidth() - cell * (columns() + 1)) / 2f;
        float y0 = getPaddingTop();
        float shadow = Math.max(1, cell / 2f);
        for (int pass = 0; pass < 2; pass++) {
            paint.setColor(pass == 0 ? 0xFF4A4A4A : 0xFFFFFFFF);
            float off = pass == 0 ? shadow : 0;
            for (int i = 0; i < text.length(); i++) {
                var g = glyph(text.charAt(i));
                if (g == null) continue;
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < 5; col++) {
                        if (g[row].charAt(col) != '1') continue;
                        float x = x0 + (i * 6 + col) * cell + off;
                        float y = y0 + row * cell + off;
                        canvas.drawRect(x, y, x + cell, y + cell, paint);
                    }
                }
            }
        }
        if (cursorOn) {
            paint.setColor(0xFFFFFFFF);
            float x = x0 + (text.length() * 6) * cell;
            canvas.drawRect(x, y0 + 5 * cell, x + cell * 2, y0 + 7 * cell, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(blink, 520);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(blink);
        super.onDetachedFromWindow();
    }
}
