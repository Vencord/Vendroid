package dev.vendicated.vencord;

import android.content.Context;

public class DiscordBranch {
    public static int getRadioButtonId(Context ctx) {
        switch (VendroidSettings.getBranch(ctx)) {
            case VendroidSettings.BRANCH_PTB:
                return R.id.branch_ptb;
            case VendroidSettings.BRANCH_CANARY:
                return R.id.branch_canary;
            default:
                return R.id.branch_stable;
        }
    }

    public static void setFromRadioButtonId(Context ctx, int radioButtonId) {
        String branch;
        if (radioButtonId == R.id.branch_ptb) {
            branch = VendroidSettings.BRANCH_PTB;
        } else if (radioButtonId == R.id.branch_canary) {
            branch = VendroidSettings.BRANCH_CANARY;
        } else {
            branch = VendroidSettings.BRANCH_STABLE;
        }
        VendroidSettings.setBranch(ctx, branch);
    }

    public static String getWebUrl(Context ctx) {
        switch (VendroidSettings.getBranch(ctx)) {
            case VendroidSettings.BRANCH_PTB:
                return "https://ptb.discord.com/";
            case VendroidSettings.BRANCH_CANARY:
                return "https://canary.discord.com/";
            default:
                return "https://discord.com/";
        }
    }
}
