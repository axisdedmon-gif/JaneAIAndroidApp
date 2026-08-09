package ai.monolith.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
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
 * Persistent Monolith AI startup boundary.
 *
 * This activity intentionally remains a small native launcher process while the full Monolith UI
 * runs in :core. It provides a deterministic pre-launch boot deck on every start and retains the
 * crash/recovery boundary when the isolated core process fails. The proven base HUD remains
 * available in :safe for recovery testing.
 */
public final class MonolithBootstrapActivity extends Activity {
    private static final long BOOT_STAGE_1_MS = 180L;
    private static final long BOOT_STAGE_2_MS = 430L;
    private static final long BOOT_STAGE_3_MS = 760L;
    private static final long CORE_LAUNCH_MS = 1180L;

    private static final int BG = Color.rgb(2, 6, 10);
    private static final int CYAN = Color.rgb(197, 255, 248);
    private static final int TEAL = Color.rgb(89, 209, 198);
    private static final int TEXT = Color.rgb(220, 235, 236);
    private static final int MUTED = Color.rgb(111, 153, 157);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusView;
    private TextView bootLogView;
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
                setBootLog(
                    "> monolith.bootstrap --resume\n" +
                    "[OK] launcher boundary active\n" +
                    "[IDLE] core process not running"
                );
                showControls(true);
            }
            return;
        }

        if (!autoLaunchScheduled && MonolithApplication.readCrashReport(this).trim().isEmpty()) {
            scheduleBootSequence();
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

    private void scheduleBootSequence() {
        handler.removeCallbacksAndMessages(null);
        autoLaunchScheduled = true;
        showControls(false);
        diagnosticView.setVisibility(View.GONE);
        setStatus("BOOTSTRAP INITIALIZED");
        setBootLog(
            "> monolith.bootstrap --cold-start\n" +
            "[....] verifying process boundary"
        );

        handler.postDelayed(() -> {
            setStatus("VERIFYING RUNTIME BOUNDARY");
            setBootLog(
                "> monolith.bootstrap --cold-start\n" +
                "[ OK ] process isolation\n" +
                "[....] crash telemetry channel"
            );
        }, BOOT_STAGE_1_MS);

        handler.postDelayed(() -> {
            setStatus("RUNTIME CHANNEL ONLINE");
            setBootLog(
                "> monolith.bootstrap --cold-start\n" +
                "[ OK ] process isolation\n" +
                "[ OK ] crash telemetry channel\n" +
                "[....] Android API " + android.os.Build.VERSION.SDK_INT + " compatibility"
            );
        }, BOOT_STAGE_2_MS);

        handler.postDelayed(() -> {
            setStatus("CORE HANDSHAKE // STANDBY");
            setBootLog(
                "> monolith.bootstrap --cold-start\n" +
                "[ OK ] process isolation\n" +
                "[ OK ] crash telemetry channel\n" +
                "[ OK ] Android API " + android.os.Build.VERSION.SDK_INT + " compatibility\n" +
                "[EXEC] ai.monolith.app:core"
            );
        }, BOOT_STAGE_3_MS);

        handler.postDelayed(this::launchCore, CORE_LAUNCH_MS);
    }

    private void buildInterface() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, dp(42), pad, dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("MONOLITH AI");
        title.setTextColor(CYAN);
        title.setTextSize(28f);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.06f);
        root.addView(title, matchWrap(0, dp(6)));

        TextView subtitle = new TextView(this);
        subtitle.setText("DETERMINISTIC STARTUP BOUNDARY // BETA 2.0.04");
        subtitle.setTextColor(TEAL);
        subtitle.setTextSize(11f);
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.04f);
        root.addView(subtitle, matchWrap(0, dp(18)));

        bootLogView = new TextView(this);
        bootLogView.setTextColor(Color.rgb(145, 224, 215));
        bootLogView.setTextSize(10f);
        bootLogView.setTypeface(Typeface.MONOSPACE);
        bootLogView.setGravity(Gravity.START);
        bootLogView.setLineSpacing(0f, 1.12f);
        bootLogView.setPadding(dp(16), dp(14), dp(16), dp(14));
        bootLogView.setBackground(new TechPanelDrawable(
            Color.rgb(5, 16, 21),
            Color.rgb(31, 92, 98),
            Color.rgb(78, 225, 210),
            dp(12)
        ));
        root.addView(bootLogView, matchWrap(0, dp(14)));

        statusView = new TextView(this);
        statusView.setTextColor(TEXT);
        statusView.setTextSize(13f);
        statusView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(16), dp(15), dp(16), dp(15));
        statusView.setBackground(new TechPanelDrawable(
            Color.rgb(8, 22, 28),
            Color.rgb(37, 101, 108),
            Color.rgb(104, 245, 229),
            dp(14)
        ));
        root.addView(statusView, matchWrap(0, dp(19)));

        launchButton = button("LAUNCH MONOLITH CORE", this::launchCore, ButtonTone.PRIMARY);
        root.addView(launchButton, matchWrap(0, dp(12)));

        safeButton = button("OPEN SAFE BASE UI", this::launchSafeBase, ButtonTone.SECONDARY);
        root.addView(safeButton, matchWrap(0, dp(12)));

        clearButton = button("CLEAR DIAGNOSTIC + RETRY", () -> {
            MonolithApplication.clearCrashReport(this);
            diagnosticView.setText("");
            diagnosticView.setVisibility(View.GONE);
            setStatus("DIAGNOSTIC CLEARED // READY");
            setBootLog(
                "> monolith.bootstrap --diagnostic-reset\n" +
                "[ OK ] runtime diagnostic cleared\n" +
                "[IDLE] awaiting operator command"
            );
            showControls(true);
        }, ButtonTone.UTILITY);
        root.addView(clearButton, matchWrap(0, dp(19)));

        diagnosticView = new TextView(this);
        diagnosticView.setTextColor(Color.rgb(235, 238, 220));
        diagnosticView.setTextSize(10f);
        diagnosticView.setTypeface(Typeface.MONOSPACE);
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setLineSpacing(0f, 1.08f);
        diagnosticView.setPadding(dp(16), dp(16), dp(16), dp(16));
        diagnosticView.setBackground(new TechPanelDrawable(
            Color.rgb(13, 15, 18),
            Color.rgb(54, 73, 76),
            Color.rgb(109, 207, 199),
            dp(12)
        ));
        diagnosticView.setVisibility(View.GONE);
        root.addView(diagnosticView, matchWrap(0, 0));

        setContentView(scroll);
        setStatus("BOOTSTRAP INITIALIZED");
        setBootLog("> monolith.bootstrap --init\n[ OK ] native launcher process");
        showControls(false);
    }

    private Button button(String text, Runnable action, ButtonTone tone) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(ColorStateList.valueOf(CYAN));
        button.setTextSize(12f);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(60));
        button.setPadding(dp(22), dp(12), dp(22), dp(15));
        button.setStateListAnimator(null);
        button.setBackground(buttonBackground(tone));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Drawable buttonBackground(ButtonTone tone) {
        int top;
        int bottom;
        int edge;
        int accent;
        switch (tone) {
            case SECONDARY:
                top = Color.rgb(12, 45, 51);
                bottom = Color.rgb(5, 25, 31);
                edge = Color.rgb(47, 133, 137);
                accent = Color.rgb(95, 226, 215);
                break;
            case UTILITY:
                top = Color.rgb(19, 38, 43);
                bottom = Color.rgb(7, 20, 24);
                edge = Color.rgb(67, 111, 115);
                accent = Color.rgb(123, 193, 188);
                break;
            case PRIMARY:
            default:
                top = Color.rgb(13, 59, 66);
                bottom = Color.rgb(4, 29, 36);
                edge = Color.rgb(50, 154, 157);
                accent = Color.rgb(125, 255, 240);
                break;
        }

        StateListDrawable states = new StateListDrawable();
        states.addState(
            new int[] { android.R.attr.state_pressed },
            new SciFiButtonDrawable(top, bottom, edge, accent, dp(13), dp(3), true)
        );
        states.addState(
            new int[] {},
            new SciFiButtonDrawable(top, bottom, edge, accent, dp(13), dp(5), false)
        );
        return states;
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

    private void setBootLog(String message) {
        if (bootLogView != null) bootLogView.setText(message == null ? "" : message);
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
        setBootLog(
            "> monolith.bootstrap --recovery\n" +
            "[FAIL] isolated UI process terminated\n" +
            "[ OK ] launcher boundary survived\n" +
            "[READ] persisted runtime diagnostic"
        );
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
        setBootLog(
            "> monolith.bootstrap --launch-core\n" +
            "[EXEC] ai.monolith.app:core\n" +
            "[WAIT] application handshake"
        );
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
        setBootLog(
            "> monolith.bootstrap --safe-base\n" +
            "[EXEC] ai.monolith.app:safe\n" +
            "[WAIT] legacy HUD handshake"
        );
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

    private enum ButtonTone {
        PRIMARY,
        SECONDARY,
        UTILITY
    }

    /**
     * Angular dual-layer chassis button. The bottom plate remains visible as a mechanical
     * extrusion; the face shifts downward while pressed instead of relying on a rounded-card
     * ripple. All geometry is drawn directly so the control remains deterministic across OEMs.
     */
    private static final class SciFiButtonDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int topColor;
        private final int bottomColor;
        private final int edgeColor;
        private final int accentColor;
        private final float chamfer;
        private final float extrusion;
        private final boolean pressed;
        private int alpha = 255;

        SciFiButtonDrawable(
            int topColor,
            int bottomColor,
            int edgeColor,
            int accentColor,
            float chamfer,
            float extrusion,
            boolean pressed
        ) {
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.edgeColor = edgeColor;
            this.accentColor = accentColor;
            this.chamfer = chamfer;
            this.extrusion = extrusion;
            this.pressed = pressed;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            float pressShift = pressed ? extrusion * 0.62f : 0f;
            float bottomTop = Math.max(0f, extrusion);

            buildChamfer(path, b.left + 1f, b.top + bottomTop, b.right - 1f, b.bottom - 1f, chamfer);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(withAlpha(bottomColor));
            canvas.drawPath(path, paint);

            float faceBottom = b.bottom - extrusion + pressShift;
            buildChamfer(path, b.left + 1f, b.top + pressShift + 1f, b.right - 1f, faceBottom, chamfer);
            paint.setShader(new LinearGradient(
                0f,
                b.top + pressShift,
                0f,
                faceBottom,
                withAlpha(pressed ? darken(topColor, 0.82f) : lighten(topColor, 1.12f)),
                withAlpha(bottomColor),
                Shader.TileMode.CLAMP
            ));
            canvas.drawPath(path, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setColor(withAlpha(edgeColor));
            canvas.drawPath(path, paint);

            float inset = 4f;
            buildChamfer(
                path,
                b.left + inset,
                b.top + pressShift + inset,
                b.right - inset,
                faceBottom - inset,
                Math.max(4f, chamfer - inset)
            );
            paint.setStrokeWidth(0.9f);
            paint.setColor(withAlpha(Color.argb(150, 140, 255, 243)));
            canvas.drawPath(path, paint);

            paint.setStrokeWidth(2.2f);
            paint.setColor(withAlpha(accentColor));
            float y = b.top + pressShift + 4f;
            canvas.drawLine(b.left + chamfer + 6f, y, b.right - chamfer - 30f, y, paint);
            canvas.drawLine(b.right - chamfer - 23f, y, b.right - chamfer - 7f, y, paint);

            paint.setStrokeWidth(1.3f);
            paint.setColor(withAlpha(Color.argb(165, 3, 10, 13)));
            float lowerY = faceBottom - 3f;
            canvas.drawLine(b.left + chamfer, lowerY, b.right - chamfer, lowerY, paint);

            paint.setStyle(Paint.Style.FILL);
        }

        private static void buildChamfer(Path path, float left, float top, float right, float bottom, float cut) {
            float c = Math.min(cut, Math.max(0f, Math.min((right - left) * 0.16f, (bottom - top) * 0.35f)));
            path.reset();
            path.moveTo(left + c, top);
            path.lineTo(right, top);
            path.lineTo(right, bottom - c);
            path.lineTo(right - c, bottom);
            path.lineTo(left, bottom);
            path.lineTo(left, top + c);
            path.close();
        }

        private int withAlpha(int color) {
            int base = Color.alpha(color);
            int resolved = Math.round(base * (alpha / 255f));
            return Color.argb(resolved, Color.red(color), Color.green(color), Color.blue(color));
        }

        private static int lighten(int color, float factor) {
            return Color.rgb(
                Math.min(255, Math.round(Color.red(color) * factor)),
                Math.min(255, Math.round(Color.green(color) * factor)),
                Math.min(255, Math.round(Color.blue(color) * factor))
            );
        }

        private static int darken(int color, float factor) {
            return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * factor)),
                Math.max(0, Math.round(Color.green(color) * factor)),
                Math.max(0, Math.round(Color.blue(color) * factor))
            );
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Angular technical panel used for boot/status/diagnostic surfaces. */
    private static final class TechPanelDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int fillColor;
        private final int edgeColor;
        private final int accentColor;
        private final float chamfer;
        private int alpha = 255;

        TechPanelDrawable(int fillColor, int edgeColor, int accentColor, float chamfer) {
            this.fillColor = fillColor;
            this.edgeColor = edgeColor;
            this.accentColor = accentColor;
            this.chamfer = chamfer;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            SciFiButtonDrawable.buildChamfer(path, b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f, chamfer);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(applyAlpha(fillColor));
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.3f);
            paint.setColor(applyAlpha(edgeColor));
            canvas.drawPath(path, paint);

            paint.setStrokeWidth(2f);
            paint.setColor(applyAlpha(accentColor));
            float y = b.top + 3f;
            canvas.drawLine(b.left + chamfer + 5f, y, b.left + chamfer + 38f, y, paint);
            canvas.drawLine(b.right - chamfer - 21f, b.bottom - 3f, b.right - chamfer - 5f, b.bottom - 3f, paint);
        }

        private int applyAlpha(int color) {
            return Color.argb(
                Math.round(Color.alpha(color) * (alpha / 255f)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            );
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
