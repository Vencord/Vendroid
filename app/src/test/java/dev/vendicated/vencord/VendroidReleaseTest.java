package dev.vendicated.vencord;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VendroidReleaseTest {
    private static final String NOTES = "## Changes\n\n- Fix uploads ✨\n- More fixes";
    private MockWebServer server;

    @Before
    public void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    public void fetchesLatestAssetThroughRedirectAndPinsDownloadToThatRelease() throws Exception {
        var releaseAsset = "/releases/download/v1.10.0/latest.json";
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", releaseAsset));
        serve(200, metadata(5));

        var release = VendroidRelease.fetch(url("/latest/download/latest.json"));
        assertNotNull(release);
        assertEquals(5, release.versionCode);
        assertEquals("1.10.0", release.versionName);
        assertEquals(NOTES, release.changelog);
        assertEquals("https://github.com/Vencord/Vendroid/releases/download/v1.10.0/Vendroid-v1.10.0.apk",
                release.downloadUrl());
        assertTrue(release.shouldPrompt(4, 0));
        assertEquals("/latest/download/latest.json", server.takeRequest(2, TimeUnit.SECONDS).getPath());
        assertEquals(releaseAsset, server.takeRequest(2, TimeUnit.SECONDS).getPath());
    }

    @Test
    public void skipsOnlySelectedReleaseAndStillOffersNewerVersions() throws IOException {
        serve(200, metadata(5));
        var release = VendroidRelease.fetch(url("/latest.json"));

        assertFalse(release.shouldPrompt(4, 5));
        assertTrue(release.shouldPrompt(4, 0)); // Remind later leaves the preference unchanged.
        assertTrue(release.shouldPrompt(4, 3));
        assertFalse(release.shouldPrompt(5, 0));
        assertFalse(release.shouldPrompt(6, 0));

        serve(200, metadata(6));
        assertTrue(VendroidRelease.fetch(url("/next/latest.json")).shouldPrompt(4, 5));
    }

    @Test
    public void handlesOldReleaseWithoutMetadataAndRejectsServerErrors() throws IOException {
        serve(404, "");
        serve(500, "");

        assertNull(VendroidRelease.fetch(url("/missing/latest.json")));
        assertThrows(IOException.class, () -> VendroidRelease.fetch(url("/busy/latest.json")));
    }

    @Test
    public void ignoresMalformedOrOversizedMetadata() throws IOException {
        serve(200, "not json");
        serve(200, " ".repeat(1024 * 1024 + 1));

        assertThrows(IOException.class, () -> VendroidRelease.fetch(url("/invalid/latest.json")));
        assertThrows(IOException.class, () -> VendroidRelease.fetch(url("/oversized/latest.json")));
    }

    @Test
    public void rejectsInvalidCodeNameAndTag() {
        var valid = metadata(5);
        assertThrows(IOException.class, () -> VendroidRelease.parse(metadata(0)));
        assertThrows(IOException.class, () -> VendroidRelease.parse(
                valid.replace("\"versionCode\":5", "\"versionCode\":5.5")));
        assertThrows(IOException.class, () -> VendroidRelease.parse(
                valid.replace("\"versionCode\":5", "\"versionCode\":\"5\"")));
        assertThrows(IOException.class, () -> VendroidRelease.parse(
                valid.replace("\"versionName\":\"1.10.0\"", "\"versionName\":\" \"")));
        assertThrows(IOException.class, () -> VendroidRelease.parse(
                valid.replace("\"tag\":\"v1.10.0\"", "\"tag\":\"v1/../../apk\"")));
    }

    private static String metadata(int code) {
        return "{\"versionCode\":" + code
                + ",\"versionName\":\"1.10.0\""
                + ",\"changelog\":\"## Changes\\n\\n- Fix uploads ✨\\n- More fixes\""
                + ",\"tag\":\"v1.10.0\"}";
    }

    private URL url(String path) {
        return server.url(path).url();
    }

    private void serve(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
    }
}
