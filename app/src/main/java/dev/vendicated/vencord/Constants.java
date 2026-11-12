package dev.vendicated.vencord;

public class Constants {
    public static final String JS_BUNDLE_URL = "https://github.com/Vendicated/Vencord/releases/download/devbuild/browser.js";
    public static final String CSS_BUNDLE_URL = "https://github.com/Vendicated/Vencord/releases/download/devbuild/browser.css";
    public static final String DISCORD_URL = "https://discord.com/app";

    /** If Vencord has not reported ready this long after the page finished loading, the loading screen is dismissed anyway. */
    public static final long READY_TIMEOUT_MS = 15_000;
}
