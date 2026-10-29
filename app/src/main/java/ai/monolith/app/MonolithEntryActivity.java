package ai.monolith.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Public Android entry router for Monolith AI.
 *
 * This launcher path is intentionally redirected to the proven Jane app shell so the app opens
 * reliably instead of crashing while the generated Command Chamber attempts to become visibly
 * painted.
 */
public final class MonolithEntryActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent);
    }

    private void route(Intent source) {
        Intent safeIntent = new Intent(this, com.example.janeai.MainActivity.class);

        if (source != null) {
            if (source.getExtras() != null) safeIntent.putExtras(source.getExtras());
            String action = source.getAction();
            if (action != null) safeIntent.setAction(action);
            Uri data = source.getData();
            if (data != null) safeIntent.setData(data);
            if (source.getClipData() != null) safeIntent.setClipData(source.getClipData());
        }

        safeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(safeIntent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
