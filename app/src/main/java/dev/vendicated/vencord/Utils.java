package dev.vendicated.vencord;

import android.net.Uri;

public final class Utils {
    static boolean isDiscordUrl(Uri url) {
        var authority = url.getAuthority();
        if (authority == null) return false;

        switch (authority) {
            case "discord.com":
            case "ptb.discord.com":
            case "canary.discord.com":
                return true;
            default:
                return false;
        }
    }
}
