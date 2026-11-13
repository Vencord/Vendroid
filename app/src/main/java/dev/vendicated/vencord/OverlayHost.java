package dev.vendicated.vencord;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Gives the session's WebView a real (but invisible and untouchable) window while no activity shows it. A WebView
 * with no window is treated as hidden by Chromium: it may not even start loading, and its timers and sockets get
 * throttled, which is why Discord only worked "while the app was open". With this window the page keeps running
 * exactly as if it were on screen. Needs the "Display over other apps" permission.
 */
final class OverlayHost {
    private OverlayHost() {}

    private static FrameLayout holder;
    private static boolean added = false;

    static boolean canUse(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Main thread only. Moves the WebView into the invisible window (adding the window if needed). */
    static void attach(Context context, WebView wv) {
        if (wv == null || !canUse(context)) return;
        var app = context.getApplicationContext();
        try {
            if (holder == null) holder = new FrameLayout(app);
            var parent = wv.getParent();
            if (parent == holder) return;
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(wv);
            holder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (!added) {
                var wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
                var lp = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.alpha = 0f; // invisible, and Android lets touches pass through windows this transparent
                wm.addView(holder, lp);
                added = true;
                Logger.i("Background window attached (the page keeps running without the app on screen)");
            }
        } catch (Exception ex) {
            Logger.e("Could not create the background window", ex);
        }
    }

    /** Removes the window (and the WebView stays wherever it currently is). */
    static void remove(Context context) {
        if (!added || holder == null) return;
        try {
            var wm = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            holder.removeAllViews();
            wm.removeViewImmediate(holder);
        } catch (Exception ex) {
            Logger.w("Could not remove the background window: " + ex.getMessage());
        }
        added = false;
        holder = null;
    }
}
