package ai.monolith.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Public Android entry router for Monolith AI.
 *
 * All compatibility entry points route directly to MonolithCoreActivity. The BIOS is the sole
 * pre-launch boundary; this router must never insert another access or verification screen.
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
        String mode = readMode(source);
        Intent core = new Intent(this, MonolithCoreActivity.class);
        if (source != null) {
            if (source.getExtras() != null) core.putExtras(source.getExtras());
            String action = source.getAction();
            if (action != null) core.setAction(action);
            Uri data = source.getData();
            if (data != null) core.setData(data);
            if (source.getClipData() != null) core.setClipData(source.getClipData());
        }
        core.putExtra("monolith_mode", normalizeMode(mode));
        core.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(core);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String normalizeMode(String mode) {
        return "home".equals(mode) ? "command" : mode;
    }

    private String readMode(Intent intent) {
        if (intent == null) return "home";
        String monolith = intent.getStringExtra("monolith_mode");
        if (monolith != null && !monolith.trim().isEmpty()) return monolith.trim();
        String legacy = intent.getStringExtra("jane_mode");
        return legacy == null || legacy.trim().isEmpty() ? "home" : legacy.trim();
    }
}
