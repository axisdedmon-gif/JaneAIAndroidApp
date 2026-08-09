package ai.monolith.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Stable launcher boundary for Monolith AI.
 *
 * The full application UI runs in the isolated :core process. A runtime failure in that process
 * therefore cannot take down this launcher, and the persisted exception can be shown without ADB.
 * The proven legacy shell is also available in a separate :safe process for recovery testing.
 */
public final class MonolithBootstrapActivity extends Activity {
    private static final long AUTO_LAUNCH_DELAY_MS = 220L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusView;
    private TextView diagnosticView;
    private Button launchButton;
    private Button safeButton;
    private Button clearButton;
    private boolean launchInFlight;
    private boolean autoLaunchScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null && getIntent().getBooleanExtra(MonolithApplication.EXTRA_SHOW_DIAGNOSTIC, false)) {
            showPersistedDiagnostic("CORE PROCESS TERMINATED");
            return;
        }

        if (launchInFlight) {
            launchInFlight = false;
            long crashAt = MonolithApplication.crashTimestamp(this);
            long launchedAt = getPreferences(MODE_PRIVATE).getLong("last_launch_wall", 0L);
            if (crashAt >= launchedAt && crashAt > 0L) {
                showPersistedDiagnostic("CORE PROCESS TERMINATED");
            } else {
                setStatus("CORE CLOSED // READY TO RELAUNCH");
                showControls(true);
            }
            return;
        }

        if (!autoLaunchScheduled && MonolithApplication.readCrashReport(this).trim().isEmpty()) {
            autoLaunchScheduled = true;
            setStatus("BOOTSTRAP VERIFIED // STARTING CORE");
            handler.postDelayed(this::launchCore, AUTO_LAUNCH_DELAY_MS);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        boolean showDiagnostic = intent != null
            && intent.getBooleanExtra(MonolithApplication.EXTRA_SHOW_DIAGNOSTIC, false);
        if (showDiagnostic) {
            autoLaunchScheduled = true;
            showPersistedDiagnostic("CORE PROCESS TERMINATED");
            intent.removeExtra(MonolithApplication.EXTRA_SHOW_DIAGNOSTIC);
            return;
        }

        String report = MonolithApplication.readCrashReport(this);
        if (!report.trim().isEmpty()) {
            autoLaunchScheduled = true;
            showPersistedDiagnostic("PREVIOUS RUNTIME FAILURE DETECTED");
        }
    }

    private void buildInterface() {
        getWindow().setStatusBarColor(Color.rgb(2, 6, 10));
        getWindow().setNavigationBarColor(Color.rgb(2, 6, 10));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(2, 6, 10));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, dp(44), pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("MONOLITH AI");
        title.setTextColor(Color.rgb(197, 255, 248));
        title.setTextSize(26f);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(dp(0), dp(8)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DETERMINISTIC STARTUP BOUNDARY // BETA 2.0.03");
        subtitle.setTextColor(Color.rgb(89, 209, 198));
        subtitle.setTextSize(11f);
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap(dp(0), dp(28)));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(220, 235, 236));
        statusView.setTextSize(13f);
        statusView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusView.setBackgroundColor(Color.rgb(8, 22, 28));
        root.addView(statusView, matchWrap(dp(0), dp(18)));

        launchButton = button("LAUNCH MONOLITH CORE", this::launchCore);
        root.addView(launchButton, matchWrap(dp(0), dp(10)));

        safeButton = button("OPEN SAFE BASE UI", this::launchSafeBase);
        root.addView(safeButton, matchWrap(dp(0), dp(10)));

        clearButton = button("CLEAR DIAGNOSTIC + RETRY", () -> {
            MonolithApplication.clearCrashReport(this);
            diagnosticView.setText("");
            diagnosticView.setVisibility(View.GONE);
            setStatus("DIAGNOSTIC CLEARED // READY");
            showControls(true);
        });
        root.addView(clearButton, matchWrap(dp(0), dp(18)));

        diagnosticView = new TextView(this);
        diagnosticView.setTextColor(Color.rgb(235, 238, 220));
        diagnosticView.setTextSize(10f);
        diagnosticView.setTypeface(Typeface.MONOSPACE);
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setPadding(dp(14), dp(14), dp(14), dp(14));
        diagnosticView.setBackgroundColor(Color.rgb(13, 15, 18));
        diagnosticView.setVisibility(View.GONE);
        root.addView(diagnosticView, matchWrap(dp(0), dp(0)));

        setContentView(scroll);
        setStatus("BOOTSTRAP INITIALIZED");
        showControls(false);
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.rgb(205, 255, 249));
        button.setTextSize(12f);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setBackgroundColor(Color.rgb(12, 42, 48));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private void setStatus(String message) {
        if (statusView != null) statusView.setText(message == null ? "" : message);
    }

    private void showControls(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        if (launchButton != null) launchButton.setVisibility(state);
        if (safeButton != null) safeButton.setVisibility(state);
        if (clearButton != null) clearButton.setVisibility(state);
    }

    private void showPersistedDiagnostic(String state) {
        handler.removeCallbacksAndMessages(null);
        autoLaunchScheduled = true;
        launchInFlight = false;
        setStatus(state);
        String report = MonolithApplication.readCrashReport(this);
        if (report.trim().isEmpty()) {
            report = "No Java/ART stack trace was captured. The failure may have occurred below the Java exception boundary, such as a native linker or process-level termination.";
        }
        diagnosticView.setText(report);
        diagnosticView.setVisibility(View.VISIBLE);
        showControls(true);
    }

    private void launchCore() {
        handler.removeCallbacksAndMessages(null);
        autoLaunchScheduled = true;
        MonolithApplication.clearCrashReport(this);
        getPreferences(MODE_PRIVATE).edit()
            .putLong("last_launch_wall", System.currentTimeMillis())
            .apply();
        setStatus("STARTING MONOLITH CORE // ISOLATED PROCESS");
        showControls(false);
        launchInFlight = true;
        try {
            Intent intent = new Intent(this, MonolithActivity.class);
            intent.putExtra("monolith_mode", "home");
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            launchInFlight = false;
            setStatus("CORE ACTIVITY NOT FOUND");
            diagnosticView.setText(error.toString());
            diagnosticView.setVisibility(View.VISIBLE);
            showControls(true);
        } catch (RuntimeException error) {
            launchInFlight = false;
            setStatus("CORE LAUNCH FAILED");
            diagnosticView.setText(android.util.Log.getStackTraceString(error));
            diagnosticView.setVisibility(View.VISIBLE);
            showControls(true);
        }
    }

    private void launchSafeBase() {
        handler.removeCallbacksAndMessages(null);
        MonolithApplication.clearCrashReport(this);
        getPreferences(MODE_PRIVATE).edit()
            .putLong("last_launch_wall", System.currentTimeMillis())
            .apply();
        setStatus("STARTING SAFE BASE UI // ISOLATED PROCESS");
        showControls(false);
        launchInFlight = true;
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "ai.monolith.app.legacy.HudMainActivity");
            startActivity(intent);
        } catch (RuntimeException error) {
            launchInFlight = false;
            setStatus("SAFE BASE LAUNCH FAILED");
            diagnosticView.setText(android.util.Log.getStackTraceString(error));
            diagnosticView.setVisibility(View.VISIBLE);
            showControls(true);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
