package ai.monolith.app;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Native recovery console for Monolith AI.
 *
 * Safe Base intentionally does not instantiate WebView, the GLB renderer, Piper, MediaPipe, or the
 * primary scene graph. Its only job is to remain available when :core cannot mount and to expose
 * enough deterministic state to recover without falling back to the legacy application UI.
 */
public final class MonolithSafeBaseActivity extends Activity {
    private static final int BG = Color.rgb(1, 5, 9);
    private static final int PANEL = Color.rgb(4, 16, 22);
    private static final int PANEL_DEEP = Color.rgb(2, 10, 15);
    private static final int CYAN = Color.rgb(120, 255, 240);
    private static final int TEAL = Color.rgb(67, 202, 193);
    private static final int ORANGE = Color.rgb(255, 126, 32);
    private static final int TEXT = Color.rgb(222, 247, 247);
    private static final int MUTED = Color.rgb(105, 150, 158);

    private TextView diagnosticView;
    private TextView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildInterface();
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackground(new TechPanelDrawable(PANEL, Color.rgb(30, 99, 105), CYAN, dp(12)));
        root.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(72)
        ));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("MONOLITH SAFE BASE", 20f, CYAN, true);
        title.setLetterSpacing(0.08f);
        identity.addView(title);
        TextView subtitle = text("NATIVE RECOVERY CONSOLE // NO WEBVIEW // NO GLB // NO MODEL RUNTIME", 8f, MUTED, false);
        subtitle.setLetterSpacing(0.06f);
        identity.addView(subtitle);
        header.addView(identity, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text(installedVersionName(), 10f, ORANGE, true);
        version.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(version, new LinearLayout.LayoutParams(dp(340), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        bodyParams.topMargin = dp(10);
        root.addView(body, bodyParams);

        LinearLayout left = panel();
        LinearLayout center = panel();
        LinearLayout right = panel();

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.27f);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.48f);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.25f);
        centerParams.leftMargin = dp(8);
        rightParams.leftMargin = dp(8);
        body.addView(left, leftParams);
        body.addView(center, centerParams);
        body.addView(right, rightParams);

        left.addView(sectionTitle("RECOVERY STATE", "SAFE PROCESS TELEMETRY"));
        stateView = text("READING STATE…", 9f, TEXT, false);
        stateView.setTextIsSelectable(true);
        stateView.setLineSpacing(0f, 1.22f);
        left.addView(stateView, fillRemaining());

        center.addView(sectionTitle("LAST CORE DIAGNOSTIC", "PERSISTED STARTUP PAYLOAD"));
        ScrollView diagnosticScroll = new ScrollView(this);
        diagnosticScroll.setFillViewport(true);
        diagnosticScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        diagnosticView = text("NO PERSISTED CORE DIAGNOSTIC", 9f, Color.rgb(223, 232, 215), false);
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setLineSpacing(0f, 1.18f);
        diagnosticView.setPadding(dp(10), dp(8), dp(10), dp(8));
        diagnosticScroll.addView(diagnosticView, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));
        center.addView(diagnosticScroll, fillRemaining());

        right.addView(sectionTitle("RECOVERY COMMANDS", "OPERATOR CONTROL"));
        right.addView(actionButton("CLEAR STATE + RETRY CORE", this::clearAndRetry, ButtonTone.PRIMARY), buttonParams());
        right.addView(actionButton("RETURN TO STARTUP BOUNDARY", this::returnToBootstrap, ButtonTone.SECONDARY), buttonParams());
        right.addView(actionButton("COPY DIAGNOSTIC", this::copyDiagnostic, ButtonTone.UTILITY), buttonParams());

        TextView note = text(
            "SAFE BASE intentionally does not render the main application. If Core fails again, return here and the newest mount/crash payload will be preserved.",
            8f,
            MUTED,
            false
        );
        note.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        noteParams.topMargin = dp(14);
        right.addView(note, noteParams);

        setContentView(root);
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(new TechPanelDrawable(PANEL_DEEP, Color.rgb(24, 75, 84), TEAL, dp(11)));
        return panel;
    }

    private TextView sectionTitle(String title, String subtitle) {
        TextView view = text(title + "\n" + subtitle, 10f, CYAN, true);
        view.setLetterSpacing(0.05f);
        view.setLineSpacing(0f, 1.12f);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private LinearLayout.LayoutParams fillRemaining() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        );
        params.bottomMargin = dp(10);
        return params;
    }

    private Button actionButton(String label, Runnable action, ButtonTone tone) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(ColorStateList.valueOf(TEXT));
        button.setTextSize(9f);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setLetterSpacing(0.04f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(9));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
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
                top = Color.rgb(10, 44, 51);
                bottom = Color.rgb(3, 19, 26);
                edge = Color.rgb(42, 122, 130);
                accent = CYAN;
                break;
            case UTILITY:
                top = Color.rgb(23, 34, 39);
                bottom = Color.rgb(7, 15, 19);
                edge = Color.rgb(77, 100, 104);
                accent = ORANGE;
                break;
            case PRIMARY:
            default:
                top = Color.rgb(12, 61, 66);
                bottom = Color.rgb(3, 25, 31);
                edge = Color.rgb(49, 154, 157);
                accent = Color.rgb(135, 255, 241);
                break;
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(
            new int[] { android.R.attr.state_pressed },
            new TechButtonDrawable(top, bottom, edge, accent, dp(10), dp(3), true)
        );
        states.addState(
            new int[] {},
            new TechButtonDrawable(top, bottom, edge, accent, dp(10), dp(5), false)
        );
        return states;
    }

    private void refreshState() {
        String processName = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
            ? Application.getProcessName()
            : getPackageName() + ":safe";
        String guard = MonolithCrashGuard.diagnosticJson(this);
        String report = MonolithApplication.readCrashReport(this);
        if (report == null || report.trim().isEmpty()) report = "NO PERSISTED CORE DIAGNOSTIC";

        if (stateView != null) {
            stateView.setText(
                "process=" + processName + "\n" +
                "package=" + getPackageName() + "\n" +
                "version=" + installedVersionName() + "\n" +
                "sdk=" + android.os.Build.VERSION.SDK_INT + "\n" +
                "device=" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" +
                "renderer=DISABLED\n" +
                "webview=DISABLED\n" +
                "model_runtime=DISABLED\n\n" +
                "CRASH GUARD\n" + guard
            );
        }
        if (diagnosticView != null) diagnosticView.setText(report);
    }

    private void clearAndRetry() {
        MonolithApplication.clearCrashReport(this);
        MonolithCrashGuard.clearStartupState(this);
        Intent intent = new Intent(this, MonolithActivity.class);
        intent.putExtra("monolith_mode", "home");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void returnToBootstrap() {
        Intent intent = new Intent(this, MonolithBootstrapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void copyDiagnostic() {
        String value = diagnosticView == null ? "" : diagnosticView.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Monolith diagnostic", value));
            Toast.makeText(this, "Diagnostic copied", Toast.LENGTH_SHORT).show();
        }
    }

    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String value = info.versionName;
            return value == null || value.trim().isEmpty() ? "unknown" : value;
        } catch (Throwable ignored) {
            return "unknown";
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

    private static final class TechPanelDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int fill;
        private final int edge;
        private final int accent;
        private final float chamfer;
        private int alpha = 255;

        TechPanelDrawable(int fill, int edge, int accent, float chamfer) {
            this.fill = fill;
            this.edge = edge;
            this.accent = accent;
            this.chamfer = chamfer;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            buildChamfer(path, b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f, chamfer);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                b.left,
                b.top,
                b.right,
                b.bottom,
                withAlpha(lighten(fill, 1.18f)),
                withAlpha(fill),
                Shader.TileMode.CLAMP
            ));
            canvas.drawPath(path, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(withAlpha(edge));
            canvas.drawPath(path, paint);

            buildChamfer(path, b.left + 5f, b.top + 5f, b.right - 5f, b.bottom - 5f, Math.max(3f, chamfer - 4f));
            paint.setStrokeWidth(0.8f);
            paint.setColor(withAlpha(Color.argb(95, 120, 255, 240)));
            canvas.drawPath(path, paint);

            paint.setStrokeWidth(2.2f);
            paint.setColor(withAlpha(accent));
            canvas.drawLine(b.left + chamfer + 8f, b.top + 4f, b.left + chamfer + 52f, b.top + 4f, paint);
            canvas.drawLine(b.right - chamfer - 34f, b.bottom - 4f, b.right - chamfer - 8f, b.bottom - 4f, paint);
        }

        private static void buildChamfer(Path path, float left, float top, float right, float bottom, float cut) {
            float c = Math.min(cut, Math.max(0f, Math.min((right - left) * 0.14f, (bottom - top) * 0.32f)));
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

    private static final class TechButtonDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int top;
        private final int bottom;
        private final int edge;
        private final int accent;
        private final float chamfer;
        private final float extrusion;
        private final boolean pressed;
        private int alpha = 255;

        TechButtonDrawable(int top, int bottom, int edge, int accent, float chamfer, float extrusion, boolean pressed) {
            this.top = top;
            this.bottom = bottom;
            this.edge = edge;
            this.accent = accent;
            this.chamfer = chamfer;
            this.extrusion = extrusion;
            this.pressed = pressed;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float shift = pressed ? extrusion * 0.65f : 0f;

            TechPanelDrawable.buildChamfer(path, b.left + 1f, b.top + extrusion, b.right - 1f, b.bottom - 1f, chamfer);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(withAlpha(bottom));
            canvas.drawPath(path, paint);

            float faceBottom = b.bottom - extrusion + shift;
            TechPanelDrawable.buildChamfer(path, b.left + 1f, b.top + shift + 1f, b.right - 1f, faceBottom, chamfer);
            paint.setShader(new LinearGradient(
                0f,
                b.top + shift,
                0f,
                faceBottom,
                withAlpha(pressed ? darken(top, .82f) : TechPanelDrawable.lighten(top, 1.12f)),
                withAlpha(bottom),
                Shader.TileMode.CLAMP
            ));
            canvas.drawPath(path, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(withAlpha(edge));
            canvas.drawPath(path, paint);

            paint.setStrokeWidth(2.1f);
            paint.setColor(withAlpha(accent));
            float y = b.top + shift + 4f;
            canvas.drawLine(b.left + chamfer + 8f, y, b.right - chamfer - 28f, y, paint);
            canvas.drawLine(b.right - chamfer - 21f, y, b.right - chamfer - 7f, y, paint);
        }

        private int withAlpha(int color) {
            int base = Color.alpha(color);
            int resolved = Math.round(base * (alpha / 255f));
            return Color.argb(resolved, Color.red(color), Color.green(color), Color.blue(color));
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
}
