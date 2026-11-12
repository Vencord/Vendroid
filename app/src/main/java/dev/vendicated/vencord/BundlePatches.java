package dev.vendicated.vencord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, targeted edits applied to the Vencord bundle text before it is injected: the FyoCord name in the places a
 * user actually reads it, and the "Support the Project" card replaced by the developer's contact card.
 * Everything is matched on exact visible strings so internal identifiers (Vencord.Api, VencordNative, ...) stay intact.
 * If a pattern no longer matches after a Vencord update the bundle is simply left as it is.
 */
public final class BundlePatches {
    private BundlePatches() {}

    private static final String[][] RENAMES = {
            {"title:\"Vencord\",panelTitle:\"Vencord Settings\"", "title:\"FyoCord\",panelTitle:\"FyoCord Settings\""},
            {"\"Vencord Settings\"", "\"FyoCord Settings\""},
            {"\"Vencord Toolbox\"", "\"FyoCord Toolbox\""},
            {"\"Vencord Notifications\"", "\"FyoCord Notifications\""},
            {"\"Vencord Cloud\"", "\"FyoCord Cloud\""},
            {"\"Vencord Buttons\"", "\"FyoCord Buttons\""},
            {"\"Vencord Donor\"", "\"FyoCord Donor\""},
            {"\"Vencord Contributor\"", "\"FyoCord Contributor\""},
            {"\"Vencord Auto-Translate Enabled\"", "\"FyoCord Auto-Translate Enabled\""},
            {"infoLabel:\"Vencord\"", "infoLabel:\"FyoCord\""},
    };

    /** The version line in the settings sidebar, `Vencord ${version}${suffix}`. */
    private static final Pattern VERSION_LINE = Pattern.compile("`Vencord (\\$\\{[^`}]*\\}(?:\\$\\{[^`}]*\\})*)`");

    /** r(Card,{title:"Support the Project",description:"...",cardImage:a,backgroundImage:b,backgroundColor:"#..."},r(DonateButton,null)) */
    private static final Pattern DONATE_CARD = Pattern.compile(
            "([\\w$]+)\\(([\\w$]+),\\{title:\"Support the Project\",description:\"[^\"]*\",cardImage:([\\w$]+),backgroundImage:([\\w$]+),"
                    + "backgroundColor:\"[^\"]*\"\\},[\\w$]+\\([\\w$]+,null\\)\\)");

    /** The "View Source Code" quick action: r(Row,{Icon:x,text:"View Source Code",action:()=>...openExternal("https://github.com/"+y)}) */
    private static final Pattern SOURCE_ROW = Pattern.compile(
            "[\\w$]+\\([\\w$]+,\\{Icon:[\\w$]+,text:\"View Source Code\",action:\\(\\)=>[^}]*\\}\\)");

    public static String apply(String js) {
        int renamed = 0;
        for (var r : RENAMES) {
            if (js.contains(r[0])) {
                js = js.replace(r[0], r[1]);
                renamed++;
            }
        }

        var version = VERSION_LINE.matcher(js);
        boolean versionPatched = version.find();
        if (versionPatched) js = version.replaceFirst(Matcher.quoteReplacement("`FyoCord ") + "$1`");

        var card = DONATE_CARD.matcher(js);
        boolean cardPatched = card.find();
        if (cardPatched) {
            js = card.replaceFirst("null"); // the contact info lives in the About Us page
        }

        var source = SOURCE_ROW.matcher(js);
        boolean sourceRemoved = source.find();
        if (sourceRemoved) js = source.replaceFirst("!1");

        Logger.i("Bundle patches: " + renamed + "/" + RENAMES.length + " renames, version line " + (versionPatched ? "OK" : "not found")
                + ", contact card " + (cardPatched ? "OK" : "NOT FOUND (Vencord changed?)")
                + ", source row " + (sourceRemoved ? "removed" : "not found"));
        return js;
    }
}
