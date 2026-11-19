package dev.vendicated.vencord;

import android.app.Activity;
import android.os.Bundle;

/** A standalone updater entry point used by the launcher shortcut. */
public class UpdaterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        VencordUpdaterDialog.show(this, null, this::finish);
    }
}
