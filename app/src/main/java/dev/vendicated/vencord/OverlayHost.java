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
        if (wv == null) return;
        var app = context.getApplicationContext();
        if (!canUse(app)) {
            attachVirtual(app, wv);
            return;
        }
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

    // ---- fallback without the "display over other apps" permission: a window on a private virtual display ----
    private static android.hardware.display.VirtualDisplay vdisplay;
    private static android.app.Presentation presentation;
    private static FrameLayout vholder;

    private static void attachVirtual(Context app, WebView wv) {
        try {
            if (vholder == null) vholder = new FrameLayout(app);
            var parent = wv.getParent();
            if (parent == vholder) return;
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(wv);
            vholder.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (presentation == null) {
                var dm = (android.hardware.display.DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
                var m = app.getResources().getDisplayMetrics();
                vdisplay = dm.createVirtualDisplay("fyocord-bg", Math.max(320, m.widthPixels), Math.max(480, m.heightPixels), m.densityDpi,
                        null, android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
                presentation = new android.app.Presentation(app, vdisplay.getDisplay());
                presentation.setContentView(vholder);
                presentation.getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);
                presentation.show();
                Logger.i("Background window attached via a virtual display (no overlay permission needed)");
            }
        } catch (Throwable t) {
            Logger.w("Virtual-display background window failed: " + t);
            removeVirtual();
        }
    }

    private static void removeVirtual() {
        try {
            if (vholder != null) vholder.removeAllViews();
            if (presentation != null) presentation.dismiss();
        } catch (Throwable ignored) {
        }
        try {
            if (vdisplay != null) vdisplay.release();
        } catch (Throwable ignored) {
        }
        presentation = null;
        vdisplay = null;
        vholder = null;
    }

    /** Removes the window (and the WebView stays wherever it currently is). */
    static void remove(Context context) {
        removeVirtual();
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
