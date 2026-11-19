package dev.vendicated.vencord;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public final class VencordUpdater {
    private static final String JS_BUNDLE = "browser.js";
    private static final String CSS_BUNDLE = "browser.css";
    private static final String[] BUNDLES = {JS_BUNDLE, CSS_BUNDLE};
    private static final int MAX_ATTEMPTS = 3;

    public static final class Release {
        public final String hash;
        public final String title;
        private final Map<String, String> assets;

        Release(String hash, String title, Map<String, String> assets) {
            this.hash = hash;
            this.title = title;
            this.assets = assets;
        }

        String assetUrl(String name) {
            return assets.get(name);
        }
    }

    public static boolean isInstalled(Context ctx) {
        var dir = bundleDir(ctx);
        var js = new File(dir, JS_BUNDLE);
        return js.isFile() && js.length() > 0;
    }

    /** Installs the bundles if missing. Returns true if it had to download them. */
    public static boolean ensureInstalled(Context ctx) throws IOException {
        if (isInstalled(ctx)) return false;

        Toast.makeText(ctx, R.string.downloading_vencord, Toast.LENGTH_LONG).show();
        installRelease(ctx, withRetries(VencordUpdater::fetchLatestRelease));
        return true;
    }

    /** Downloads the latest release if it differs from the installed one. Never throws. */
    public static void autoUpdate(Context ctx) {
        try {
            var release = fetchLatestRelease();
            if (release.hash.equals(VendroidSettings.getInstalledHash(ctx))) return;

            installRelease(ctx, release);
            Toast.makeText(ctx, ctx.getString(R.string.updated_to, release.title), Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Logger.w("Failed to auto update Vencord: " + ex);
        }
    }

    public static void loadBundles(Context ctx) throws IOException {
        HttpClient.VencordRuntime = readBundle(new File(bundleDir(ctx), JS_BUNDLE));
        var css = new File(bundleDir(ctx), CSS_BUNDLE);
        HttpClient.VencordCss = css.isFile() ? readBundle(css) : "";
    }

    public static Release fetchLatestRelease() throws IOException {
        try (var is = open(Constants.RELEASE_API_URL).getInputStream()) {
            var json = new JSONObject(HttpClient.readAsText(is));

            var title = json.optString("name", "").trim();
            var hash = title.isEmpty()
                    ? json.optString("published_at", "")
                    : title.substring(title.lastIndexOf(' ') + 1);

            var assets = new HashMap<String, String>();
            var jsonAssets = json.optJSONArray("assets");
            if (jsonAssets != null) {
                for (int i = 0; i < jsonAssets.length(); i++) {
                    var asset = jsonAssets.optJSONObject(i);
                    if (asset != null) {
                        assets.put(asset.optString("name"), asset.optString("browser_download_url"));
                    }
                }
            }

            return new Release(hash, title.isEmpty() ? hash : title, assets);
        } catch (JSONException ex) {
            throw new IOException("Failed to parse the GitHub release", ex);
        }
    }

    public static void installRelease(Context ctx, Release release) throws IOException {
        var dir = bundleDir(ctx);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Failed to create " + dir);
        }

        for (var name : BUNDLES) {
            var url = release.assetUrl(name);
            if (url == null || url.isEmpty()) {
                if (name.equals(JS_BUNDLE))
                    throw new IOException("Release " + release.title + " has no " + name);
                continue;
            }

            var dest = new File(dir, name);
            withRetries(() -> {
                download(url, dest);
                return null;
            });
        }

        VendroidSettings.setInstalledHash(ctx, release.hash);
    }

    private static File bundleDir(Context ctx) {
        return new File(ctx.getFilesDir(), "vencord");
    }

    private static HttpURLConnection open(String url) throws IOException {
        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", Constants.USER_AGENT);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (conn.getResponseCode() >= 300) {
            throw new HttpClient.HttpException(conn);
        }
        return conn;
    }

    private static void download(String url, File dest) throws IOException {
        var tmp = new File(dest.getPath() + ".tmp");
        try (var is = open(url).getInputStream(); var os = new FileOutputStream(tmp)) {
            copy(is, os);
        } catch (IOException ex) {
            tmp.delete();
            throw ex;
        }

        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("Failed to move downloaded file to " + dest);
        }
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        var buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) > -1) {
            os.write(buf, 0, n);
        }
    }

    private static String readBundle(File file) throws IOException {
        try (var is = new FileInputStream(file)) {
            return HttpClient.readAsText(is);
        }
    }

    private static <T> T withRetries(Callable<T> task) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return task.call();
            } catch (Exception ex) {
                lastError = ex instanceof IOException ? (IOException) ex : new IOException(ex);
                Logger.w("Vencord download attempt " + attempt + " failed: " + ex);
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw lastError == null ? new IOException("Vencord download failed") : lastError;
    }
}
