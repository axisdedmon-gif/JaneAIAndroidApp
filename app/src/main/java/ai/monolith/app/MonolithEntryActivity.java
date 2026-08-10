package ai.monolith.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Public Android entry router for Monolith AI.
 *
 * Launcher/home opens the native House Dedmon gate. A successful native gate grants access and
 * routes to MonolithCoreActivity. Assistant/search/voice intents bypass the visual gate so Android
 * assistant behavior is not blocked by an owner-launch screen.
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
        boolean granted = source != null
            && source.getBooleanExtra(HouseDedmonAccessActivity.EXTRA_NATIVE_ACCESS_GRANTED, false);

        if (!granted && isOwnerLaunch(mode, source)) {
            Intent gate = new Intent(this, HouseDedmonAccessActivity.class);
            gate.putExtra("monolith_mode", "home");
            startActivity(gate);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }

        Intent core = new Intent(this, MonolithCoreActivity.class);
        if (source != null) {
            if (source.getExtras() != null) core.putExtras(source.getExtras());
            String action = source.getAction();
            if (action != null) core.setAction(action);
            Uri data = source.getData();
            if (data != null) core.setData(data);
            if (source.getClipData() != null) core.setClipData(source.getClipData());
        }
        core.putExtra("monolith_mode", granted ? "command" : mode);
        core.putExtra(HouseDedmonAccessActivity.EXTRA_NATIVE_ACCESS_GRANTED, granted);
        core.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(core);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private boolean isOwnerLaunch(String mode, Intent source) {
        if (!"home".equals(mode) && !"command".equals(mode)) return false;
        if (source == null) return true;
        String action = source.getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)) return false;
        return true;
    }

    private String readMode(Intent intent) {
        if (intent == null) return "home";
        String monolith = intent.getStringExtra("monolith_mode");
        if (monolith != null && !monolith.trim().isEmpty()) return monolith.trim();
        String legacy = intent.getStringExtra("jane_mode");
        return legacy == null || legacy.trim().isEmpty() ? "home" : legacy.trim();
    }
}
