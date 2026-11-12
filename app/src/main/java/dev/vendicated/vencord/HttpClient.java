package dev.vendicated.vencord;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class HttpClient {
    public static final class HttpException extends IOException {
        private final HttpURLConnection conn;
        private String message;

        public HttpException(HttpURLConnection conn) {
            this.conn = conn;
        }

        @Override
        @NonNull
        public String getMessage() {
            if (message == null) {
                try(var es = conn.getErrorStream()) {
                    message = String.format(
                            Locale.ENGLISH,
                            "%d: %s (%s)\n%s",
                            conn.getResponseCode(),
                            conn.getResponseMessage(),
                            conn.getURL().toString(),
                            readAsText(es)
                    );
                } catch (IOException ex) {
                    message = "Error while building message lmao. Url is " + conn.getURL().toString();
                }
            }
            return message;
        }
    }

    public interface ProgressListener {
        /** Called from the download thread. {@code percent} is 0-100, or -1 if the total size is unknown. */
        void onProgress(int percent);
    }

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    /** Share of the overall progress bar taken up by the (large) JS bundle. The CSS gets the rest. */
    private static final int JS_PROGRESS_END = 85;

    private static final Pattern SCRIPT_CLOSE = Pattern.compile("</script", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_CLOSE = Pattern.compile("</style", Pattern.CASE_INSENSITIVE);

    /** Runs before the bundle: forwards uncaught errors, rejected promises and CSP violations to {@code VencordMobileNative.log}. */
    private static final String PREAMBLE_JS =
            "(function(){"
                    + "var N=window.VencordMobileNative;"
                    + "function s(e){return e&&e.stack?String(e.stack).split('\\n').slice(0,4).join('\\n'):String(e)}"
                    + "function l(lv,m){try{if(N&&N.log)N.log(lv,String(m))}catch(e){}}"
                    + "window.__vdLog=l;"
                    // Android's WebView has no screen capture API. Vencord's ScreenShare plugin does
                    // navigator.mediaDevices.getDisplayMedia.bind(...) while the bundle loads, which throws
                    // "Cannot read properties of undefined (reading 'bind')" and kills the whole bundle.
                    + "try{var md=navigator.mediaDevices;"
                    + "if(md&&typeof md.getDisplayMedia!=='function'){"
                    + "md.getDisplayMedia=function(){return Promise.reject(new DOMException('Screen sharing is not supported in this WebView','NotSupportedError'))};"
                    + "l('W','navigator.mediaDevices.getDisplayMedia is missing in this WebView, added a stub so Vencord can load')}"
                    + "else if(!md){l('W','navigator.mediaDevices is undefined')}"
                    + "}catch(e){l('E','getDisplayMedia stub failed: '+e)}"
                    + "l('I','page script started, native bridge: '+(N&&N.log?'OK':'MISSING')+', url: '+location.href);"
                    + "window.addEventListener('error',function(e){"
                    + "l('E','JS error: '+e.message+' @ '+(e.filename||'?').split('/').slice(-2).join('/')+':'+e.lineno+':'+e.colno+(e.error?'\\n'+s(e.error):''))},true);"
                    + "window.addEventListener('unhandledrejection',function(e){l('E','Unhandled promise rejection: '+s(e.reason))});"
                    + "document.addEventListener('securitypolicyviolation',function(e){"
                    + "l('E','CSP violation: '+e.violatedDirective+' blocked '+e.blockedURI)});"
                    + "})();";

    /** Runs right after the bundle: reports whether {@code window.Vencord} exists and what it exposes. */
    private static final String AFTER_BUNDLE_JS =
            "(function(){"
                    + "var l=window.__vdLog||function(){};"
                    + "if(typeof Vencord==='undefined'){l('E','AFTER BUNDLE: window.Vencord is undefined, the bundle did not finish initialising (see JS error above)');return}"
                    + "l('I','AFTER BUNDLE: Vencord OK, exports: '+Object.keys(Vencord).join(', ')+'; Webpack: '+(Vencord.Webpack?Object.keys(Vencord.Webpack).slice(0,12).join(', '):'MISSING'));"
                    + "})();";

    /** A cached bundle younger than this is used as is, older ones are used immediately too but refreshed in the background. */
    private static final long REFRESH_AFTER_MS = 3L * 60 * 60 * 1000;

    public static volatile String VencordRuntime;
    public static volatile String VencordMobileRuntime;
    public static volatile String VencordCss;

    /**
     * Ready-to-insert HTML ({@code <script>}s + {@code <style>}) that is spliced into the very top of Discord's
     * {@code <head>} so Vencord is running before any of Discord's own scripts. See {@link VWebviewClient}.
     */
    public static volatile byte[] VencordInjection;

    /**
     * Downloads Vencord (falling back to the last cached copy if the network fails) and prepares the injection.
     * Blocks, so never call this on the UI thread.
     */
    public static synchronized void fetchVencord(Context context, ProgressListener listener) throws IOException {
        if (VencordRuntime != null && VencordMobileRuntime != null && VencordInjection != null) return;

        report(listener, 0);
        Logger.i("[1/4] Reading bundled vencord_mobile.js");

        String mobile;
        try (var is = context.getResources().openRawResource(R.raw.vencord_mobile)) {
            mobile = readAsText(is);
        }
        Logger.i("[1/4] vencord_mobile.js OK (" + mobile.length() + " chars)");

        File dir = context.getFilesDir();

        // Fast path: start from the cached copy right away (no waiting for the network), refresh it in the background
        var jsCache = new File(dir, "vencord-browser.js");
        var cssCache = new File(dir, "vencord-browser.css");
        if (jsCache.length() > 100_000 && cssCache.isFile()) {
            try {
                String cachedJs = readFile(jsCache);
                String cachedCss = readFile(cssCache);
                if (cachedJs.contains("Vencord")) {
                    long age = System.currentTimeMillis() - jsCache.lastModified();
                    Logger.i("[2/4] Using cached Vencord (" + (age / 60000) + " min old), no download needed to start");
                    report(listener, 100);
                    publish(cachedJs, mobile, cachedCss);
                    Logger.i("[4/4] Injection built from cache: " + VencordInjection.length + " bytes");
                    if (age > REFRESH_AFTER_MS) refreshInBackground(dir);
                    return;
                }
            } catch (IOException ex) {
                Logger.w("Cached Vencord is unreadable, downloading it again: " + ex.getMessage());
            }
        }

        Logger.i("[2/4] Downloading Vencord JS bundle: " + Constants.JS_BUNDLE_URL);
        String js = download(Constants.JS_BUNDLE_URL, new File(dir, "vencord-browser.js"), listener, 0, JS_PROGRESS_END, true);
        Logger.i("[2/4] JS bundle ready (" + js.length() + " chars)");

        Logger.i("[3/4] Downloading Vencord CSS: " + Constants.CSS_BUNDLE_URL);
        String css;
        try {
            css = download(Constants.CSS_BUNDLE_URL, new File(dir, "vencord-browser.css"), listener, JS_PROGRESS_END, 100, false);
            Logger.i("[3/4] CSS ready (" + css.length() + " chars)");
        } catch (IOException ex) {
            // Non-fatal: vencord_mobile.js falls back to a <link> tag when no inline stylesheet is present
            Logger.w("[3/4] Failed to fetch Vencord CSS, falling back to <link>: " + DebugLog.describe(ex));
            css = "";
        }

        report(listener, 100);

        Logger.i("[4/4] Building injection");
        publish(js, mobile, css);
        Logger.i("[4/4] Injection built: " + VencordInjection.length + " bytes");
    }

    private static void publish(String js, String mobile, String css) {
        js = BundlePatches.apply(js);
        VencordMobileRuntime = mobile;
        VencordCss = css;
        VencordInjection = buildInjection(js, mobile, css);
        VencordRuntime = js;
    }

    /** Downloads the newest Vencord into the cache for the next start. Does not touch the running instance. */
    private static void refreshInBackground(File dir) {
        new Thread(() -> {
            try {
                Logger.i("Refreshing the cached Vencord in the background");
                download(Constants.JS_BUNDLE_URL, new File(dir, "vencord-browser.js"), null, 0, 85, true);
                download(Constants.CSS_BUNDLE_URL, new File(dir, "vencord-browser.css"), null, 85, 100, false);
                Logger.i("Cached Vencord refreshed, it will be used on the next start");
            } catch (IOException ex) {
                Logger.w("Background refresh failed (keeping the old copy): " + ex.getMessage());
            }
        }, "vencord-refresh").start();
    }

    /** Forgets everything so the next {@link #fetchVencord} downloads the latest Vencord from scratch. */
    public static synchronized void invalidate(Context context) {
        VencordRuntime = null;
        VencordMobileRuntime = null;
        VencordCss = null;
        VencordInjection = null;
        for (var name : new String[]{"vencord-browser.js", "vencord-browser.css"}) {
            //noinspection ResultOfMethodCallIgnored
            new File(context.getFilesDir(), name).delete();
        }
    }

    private static String readFile(File f) throws IOException {
        try (var is = new FileInputStream(f)) {
            return readAsText(is);
        }
    }

    /** Where the cached copies live and how big they are, for the diagnostics report. */
    public static String cacheInfo(Context context) {
        var sb = new StringBuilder();
        for (var name : new String[]{"vencord-browser.js", "vencord-browser.css"}) {
            var f = new File(context.getFilesDir(), name);
            sb.append(name).append(": ").append(f.isFile() ? f.length() + " bytes, modified " + new java.util.Date(f.lastModified()) : "not cached").append('\n');
        }
        return sb.toString().trim();
    }

    private static void report(ProgressListener listener, int percent) {
        if (listener != null) listener.onProgress(percent);
    }

    private static String download(String url, File cache, ProgressListener listener, int from, int to, boolean isJs) throws IOException {
        long t0 = System.currentTimeMillis();
        try {
            String text = downloadText(url, listener, from, to);
            Logger.i("Downloaded " + cache.getName() + ": " + text.length() + " chars in " + (System.currentTimeMillis() - t0) + " ms");
            if (text.isEmpty() || (isJs && !text.contains("Vencord"))) {
                var head = text.length() > 200 ? text.substring(0, 200) : text;
                throw new IOException("Unexpected response from " + url + " (" + text.length() + " chars), starts with: " + head.replace('\n', ' '));
            }
            writeCache(cache, text);
            return text;
        } catch (IOException ex) {
            if (cache.isFile()) {
                Logger.w("Download of " + url + " FAILED, using cached copy (" + cache.length() + " bytes)\n" + DebugLog.describe(ex));
                try (var is = new FileInputStream(cache)) {
                    return readAsText(is);
                }
            }
            Logger.e("Download of " + url + " FAILED and there is no cached copy", ex);
            throw ex;
        }
    }

    private static String downloadText(String url, ProgressListener listener, int from, int to) throws IOException {
        var conn = open(url);
        try {
            long total = conn.getContentLength(); // -1 when unknown (e.g. transparently gzipped)
            Logger.i("HTTP " + conn.getResponseCode() + " " + conn.getContentType() + ", length " + total + ", final url " + conn.getURL());
            if (total <= 0) report(listener, -1);

            try (var is = conn.getInputStream(); var baos = new ByteArrayOutputStream(total > 0 ? (int) total : 1 << 20)) {
                byte[] buf = new byte[16384]; // 16 KB
                long read = 0;
                int lastPercent = -2;
                int n;
                while ((n = is.read(buf)) > -1) {
                    baos.write(buf, 0, n);
                    read += n;
                    if (total > 0) {
                        int p = from + (int) Math.min((long) (to - from), (to - from) * read / total);
                        if (p != lastPercent) {
                            lastPercent = p;
                            report(listener, p);
                        }
                    }
                }
                //noinspection CharsetObjectCanBeUsed sdk33 api
                return baos.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void writeCache(File cache, String text) {
        // write to a temp file first so a crash mid-write can never leave a truncated bundle behind
        var tmp = new File(cache.getPath() + ".tmp");
        try (var os = new FileOutputStream(tmp)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ex) {
            Logger.w("Failed to cache " + cache.getName() + ": " + ex.getMessage());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(cache)) {
            Logger.w("Failed to move cached file into place: " + cache.getName());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static byte[] buildInjection(String js, String mobile, String css) {
        var sb = new StringBuilder(js.length() + mobile.length() + css.length() + 2048);
        // Separate script tags: the bundle must stay at the top of its own script so its "use strict" directive applies.
        // 1) error hooks, so anything that goes wrong in the page is reported to the in-app log panel
        sb.append("<script>").append(PREAMBLE_JS).append("</script>");
        // 2) the Vencord bundle itself
        sb.append("<script>").append(escape(SCRIPT_CLOSE, js)).append("</script>");
        // 3) did the bundle actually define window.Vencord?
        sb.append("<script>").append(AFTER_BUNDLE_JS).append("</script>");
        // 4) the mobile glue
        sb.append("<script>").append(escape(SCRIPT_CLOSE, mobile)).append("</script>");
        if (!css.isEmpty()) {
            sb.append("<style id=\"vencord-css\">").append(escape(STYLE_CLOSE, css)).append("</style>");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Prevents the content from prematurely closing the surrounding tag. {@code <\/script} is identical to {@code </script} in JS strings. */
    private static String escape(Pattern closeTag, String content) {
        return closeTag.matcher(content).replaceAll("<\\\\/" + (closeTag == SCRIPT_CLOSE ? "script" : "style"));
    }

    private static HttpURLConnection open(String url) throws IOException {
        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        if (conn.getResponseCode() >= 300) {
            throw new HttpException(conn);
        }
        return conn;
    }

    static String readAsText(InputStream is) throws IOException {
        try (var baos = new ByteArrayOutputStream()) {
            int n;
            byte[] buf = new byte[16384]; // 16 KB
            while ((n = is.read(buf)) > -1) {
                baos.write(buf, 0, n);
            }
            baos.flush();

            //noinspection CharsetObjectCanBeUsed thank you so much android studio but no i do not want to use an sdk33 api ._.
            return baos.toString("UTF-8");
        }
    }
}
