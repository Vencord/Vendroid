package dev.vendicated.vencord;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Pattern;

public class VendroidSettings {
    private static final String PREFS_NAME = "vendroid_settings";
    private static final String KEY_DESKTOP_UI = "desktop_ui";
    private static final String KEY_AUTO_UPDATES = "auto_updates";
    private static final String KEY_INSTALLED_HASH = "installed_hash";
    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/[\\d.]+");

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isDesktopUiEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DESKTOP_UI, false);
    }

    public static void setDesktopUiEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_DESKTOP_UI, enabled).apply();
    }

    public static boolean isAutoUpdatesEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_UPDATES, true);
    }

    public static void setAutoUpdatesEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_UPDATES, enabled).apply();
    }

    public static String getInstalledHash(Context ctx) {
        return prefs(ctx).getString(KEY_INSTALLED_HASH, null);
    }

    public static void setInstalledHash(Context ctx, String hash) {
        prefs(ctx).edit().putString(KEY_INSTALLED_HASH, hash).apply();
    }

    public static String desktopUserAgent(String currentUserAgent) {
        var matcher = CHROME_VERSION.matcher(currentUserAgent);
        var chromeVersion = matcher.find() ? matcher.group() : "Chrome/120.0.0.0";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + chromeVersion + " Safari/537.36";
    }
}
