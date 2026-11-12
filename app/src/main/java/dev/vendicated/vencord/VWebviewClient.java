package dev.vendicated.vencord;

import android.content.Intent;
import android.net.Uri;
import android.webkit.*;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VWebviewClient extends WebViewClient {
    private static final Pattern HEAD_TAG = Pattern.compile("<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<html(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        var url = request.getUrl();
        if ("discord.com".equals(url.getAuthority()) || "about:blank".equals(url.toString())) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        // the WebView may be detached from any activity while the app runs in the background
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            view.getContext().startActivity(intent);
        } catch (Exception ex) {
            Logger.w("Could not open " + url + ": " + ex.getMessage());
        }
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Logger.i("Page started: " + url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Logger.i("Page finished: " + url);
        Session.onPageFinished();
        injectLateIfMissing(view, url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        boolean main = request.isForMainFrame();
        // Sub-resource failures (images, trackers, ...) are normal noise, only the main frame and Discord's own scripts matter
        if (main || isDiscordHost(request.getUrl().getHost())) {
            Logger.e((main ? "MAIN FRAME" : "Resource") + " load error " + error.getErrorCode() + " (" + error.getDescription() + ") for " + request.getUrl());
        }
        if (main) Session.onMainFrameError();
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        if (request.isForMainFrame() || isDiscordHost(request.getUrl().getHost())) {
            Logger.w("HTTP " + errorResponse.getStatusCode() + " for " + request.getUrl());
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
        Logger.e("SSL error: " + error + " (the page will be cancelled)");
        super.onReceivedSslError(view, handler, error);
    }

    @android.annotation.TargetApi(26)
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Logger.e("WebView render process gone (didCrash=" + detail.didCrash() + "). The page is dead, restart the app.");
        Session.showHub();
        return true; // keep the app alive so the problem can be reported
    }

    /**
     * Safety net. Vencord is normally injected into the HTML response itself (see {@link #doFetch}), which is the only
     * point that is guaranteed to run before Discord's webpack loads. If that did not happen for some reason
     * (request not intercepted, unexpected charset/encoding, no usable insertion point), inject now so that at
     * least the page doesn't end up without Vencord entirely.
     */
    private void injectLateIfMissing(WebView view, String url) {
        if (!isDiscordUrl(url)) return;

        final String js = HttpClient.VencordRuntime;
        final String mobile = HttpClient.VencordMobileRuntime;
        if (js == null || mobile == null) return;

        view.evaluateJavascript("typeof window.Vencord", result -> {
            Logger.i("Check after page load: typeof window.Vencord = " + result);
            if (!"\"undefined\"".equals(result)) return;

            Logger.w("Vencord was not present after page load, injecting late (some plugins may not work)");
            view.evaluateJavascript(js, r -> Logger.i("Late injection: bundle evaluated, result = " + r));
            view.evaluateJavascript(mobile, r -> Logger.i("Late injection: vencord_mobile.js evaluated, result = " + r));

            final String css = HttpClient.VencordCss;
            if (css != null && !css.isEmpty()) {
                view.evaluateJavascript(
                        "(()=>{const s=document.createElement('style');s.id='vencord-css';s.textContent="
                                + JSONObject.quote(css)
                                + ";document.documentElement.appendChild(s)})()",
                        null
                );
            }
        });
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        var uri = req.getUrl();
        var scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) return null;

        boolean mainFrame = req.isForMainFrame();
        // Only needed for the Vencord stylesheet on GitHub (wrong content type). Discord's own CSS is served fine by the WebView,
        // which also caches it, so intercepting it would only make every launch slower.
        boolean css = isCss(uri) && !isDiscordHost(uri.getHost());
        if (!mainFrame && !css) return null;
        // We can't forward a request body, so leave form posts etc. to the WebView
        if (mainFrame && !"GET".equalsIgnoreCase(req.getMethod())) return null;

        try {
            return doFetch(req, mainFrame, css);
        } catch (Exception ex) { // IOException, or IllegalArgumentException from an unrepresentable response
            Logger.e("Error during shouldInterceptRequest (" + (mainFrame ? "main frame" : "css") + " " + uri + ")", ex);
        }
        if (mainFrame) Logger.w("Main frame " + uri + " was NOT intercepted, Vencord will only be injected late");
        return null;
    }

    private WebResourceResponse doFetch(WebResourceRequest req, boolean mainFrame, boolean css) throws IOException {
        var url = req.getUrl().toString();
        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestMethod(req.getMethod());
        for (var h : req.getRequestHeaders().entrySet()) {
            // For the document we need the body as plain text to be able to modify it, so let HttpURLConnection
            // negotiate and transparently decompress gzip itself instead of forwarding the WebView's Accept-Encoding
            if (mainFrame && "Accept-Encoding".equalsIgnoreCase(h.getKey())) continue;
            conn.setRequestProperty(h.getKey(), h.getValue());
        }

        var code = conn.getResponseCode();
        if (mainFrame) Logger.i("Main frame " + req.getMethod() + " " + url + " -> HTTP " + code + ", type=" + conn.getContentType() + ", encoding=" + conn.getContentEncoding());
        // WebResourceResponse can't represent redirects (3xx), let the WebView handle those itself
        if (code >= 300 && code < 400) {
            if (mainFrame) Logger.i("Redirect to " + conn.getHeaderField("Location") + ", letting the WebView follow it");
            conn.disconnect();
            return null;
        }
        var msg = conn.getResponseMessage();
        if (msg == null || msg.isEmpty()) msg = code >= 200 && code < 300 ? "OK" : "Status " + code;

        var headers = conn.getHeaderFields();
        var modifiedHeaders = new HashMap<String, String>(headers.size());
        for (var header : headers.entrySet()) {
            var key = header.getKey();
            if (key == null || header.getValue().isEmpty()) continue; // null key is the status line
            if (!"Content-Security-Policy".equalsIgnoreCase(key)) {
                modifiedHeaders.put(key, header.getValue().get(0));
            }
        }

        String contentType = conn.getContentType();
        if (css) contentType = "text/css";
        String mime = contentType == null ? "application/octet-stream" : contentType.split(";")[0].trim();
        String charset = parseCharset(contentType);
        if (css) modifiedHeaders.put("Content-Type", "text/css");

        InputStream body = conn.getInputStream();

        if (mainFrame && "text/html".equalsIgnoreCase(mime) && isDiscordHost(req.getUrl().getHost())) {
            var encoding = conn.getContentEncoding();
            boolean plainBody = encoding == null || "identity".equalsIgnoreCase(encoding);
            boolean utf8 = charset == null || "utf-8".equalsIgnoreCase(charset) || "utf8".equalsIgnoreCase(charset);
            var injection = HttpClient.VencordInjection;

            if (plainBody && utf8 && injection != null) {
                byte[] original = readAll(body);
                byte[] injected = inject(original, injection);
                if (injected != null) {
                    removeHeader(modifiedHeaders, "Content-Length");
                    removeHeader(modifiedHeaders, "Content-Encoding");
                    body = new ByteArrayInputStream(injected);
                    Logger.i("Injected Vencord into the HTML: " + original.length + " -> " + injected.length + " bytes");
                } else {
                    Logger.w("No <head> or <html> tag found in document (" + original.length + " bytes), Vencord could not be injected up front");
                    body = new ByteArrayInputStream(original);
                }
            } else {
                Logger.w("NOT injecting into HTML: plainBody=" + plainBody + " (encoding=" + encoding + "), utf8=" + utf8 + " (charset=" + charset + "), injection ready=" + (injection != null));
            }
        } else if (mainFrame) {
            Logger.i("Main frame is not injectable: mime=" + mime + ", host=" + req.getUrl().getHost());
        }

        return new WebResourceResponse(mime, charset != null ? charset : "utf-8", code, msg, modifiedHeaders, body);
    }

    /** Splices the injection in right after the opening {@code <head>} tag. Returns null if there is no usable spot. */
    @Nullable
    private static byte[] inject(byte[] document, byte[] injection) throws IOException {
        var html = new String(document, StandardCharsets.UTF_8);

        Matcher m = HEAD_TAG.matcher(html);
        if (!m.find()) {
            m = HTML_TAG.matcher(html);
            if (!m.find()) return null;
        }
        int at = m.end();

        var out = new ByteArrayOutputStream(document.length + injection.length);
        out.write(html.substring(0, at).getBytes(StandardCharsets.UTF_8));
        out.write(injection);
        out.write(html.substring(at).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream is) throws IOException {
        try (var in = is; var baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    private static void removeHeader(Map<String, String> headers, String name) {
        for (var it = headers.keySet().iterator(); it.hasNext(); ) {
            if (name.equalsIgnoreCase(it.next())) it.remove();
        }
    }

    @Nullable
    private static String parseCharset(@Nullable String contentType) {
        if (contentType == null) return null;
        var m = CHARSET.matcher(contentType);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isCss(Uri uri) {
        var path = uri.getPath();
        return path != null && path.endsWith(".css");
    }

    private static boolean isDiscordHost(@Nullable String host) {
        return host != null && (host.equals("discord.com") || host.endsWith(".discord.com"));
    }

    private static boolean isDiscordUrl(@Nullable String url) {
        return url != null && isDiscordHost(Uri.parse(url).getHost());
    }
}
