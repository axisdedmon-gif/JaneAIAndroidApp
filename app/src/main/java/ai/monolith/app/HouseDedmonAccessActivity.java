package ai.monolith.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;

/**
 * Native House Dedmon access gate.
 *
 * This is deliberately not implemented inside WebView. The access gate is its own Android scene,
 * so the ENTER control cannot be broken by DOM overlays, CSS hit-testing, WebView focus, or the
 * JavaScript scene router. Once access is granted, Monolith Core starts directly in Command.
 */
public final class HouseDedmonAccessActivity extends Activity {
    public static final String EXTRA_NATIVE_ACCESS_GRANTED = "monolith_native_access_granted";

    private static final int VOID = Color.rgb(1, 5, 8);
    private static final int PANEL = Color.rgb(4, 15, 20);
    private static final int PANEL_DEEP = Color.rgb(2, 9, 13);
    private static final int CYAN = Color.rgb(92, 238, 225);
    private static final int CYAN_BRIGHT = Color.rgb(191, 255, 248);
    private static final int BRASS = Color.rgb(167, 112, 62);
    private static final int BRASS_BRIGHT = Color.rgb(221, 153, 78);
    private static final int TEXT = Color.rgb(231, 244, 243);
    private static final int MUTED = Color.rgb(118, 153, 157);

    private boolean launchingCore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(VOID);
        getWindow().setNavigationBarColor(VOID);
        setContentView(buildScene());
    }

    private View buildScene() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(VOID);

        HardwareBackdrop backdrop = new HardwareBackdrop();
        root.addView(backdrop, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER_VERTICAL);
        columns.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.addView(columns, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        columns.addView(buildLeftRail(), weighted(14f));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(dp(22), dp(14), dp(22), dp(12));
        center.setBackground(new HardwarePanelDrawable(PANEL, PANEL_DEEP, CYAN, BRASS, dp(18)));
        LinearLayout.LayoutParams centerParams = weighted(64f);
        centerParams.leftMargin = dp(12);
        centerParams.rightMargin = dp(12);
        columns.addView(center, centerParams);

        TextView kicker = monoText("IDENTITY GATE // HOUSE DEDMON", 11f, CYAN, Typeface.BOLD);
        kicker.setGravity(Gravity.CENTER);
        kicker.setLetterSpacing(0.18f);
        center.addView(kicker, matchWrap(0, dp(5)));

        ImageView crest = new ImageView(this);
        crest.setAdjustViewBounds(true);
        crest.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        crest.setContentDescription("House Dedmon crest");
        Bitmap processed = loadProcessedCrest();
        if (processed != null) crest.setImageBitmap(processed);
        LinearLayout.LayoutParams crestParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        crestParams.leftMargin = dp(8);
        crestParams.rightMargin = dp(8);
        center.addView(crest, crestParams);

        TextView title = monoText("HOUSE DEDMON ACCESS", 25f, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.01f);
        center.addView(title, matchWrap(dp(2), dp(2)));

        TextView message = monoText(
            "If this is C.J, all is well. If not, I’m filing emotional charges.",
            12f,
            Color.rgb(203, 220, 221),
            Typeface.NORMAL
        );
        message.setGravity(Gravity.CENTER);
        message.setMaxLines(2);
        center.addView(message, matchWrap(0, dp(9)));

        TextView enter = monoText("ENTER\nMONOLITH", 15f, CYAN_BRIGHT, Typeface.BOLD);
        enter.setGravity(Gravity.CENTER);
        enter.setClickable(true);
        enter.setFocusable(true);
        enter.setContentDescription("Enter Monolith AI");
        enter.setMinWidth(dp(184));
        enter.setMinHeight(dp(70));
        enter.setPadding(dp(34), dp(10), dp(34), dp(12));
        enter.setBackground(reactorButtonBackground());
        enter.setOnClickListener(v -> enterMonolith());
        LinearLayout.LayoutParams enterParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        enterParams.gravity = Gravity.CENTER_HORIZONTAL;
        enterParams.bottomMargin = dp(9);
        center.addView(enter, enterParams);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER);
        status.addView(statusPill("OWNER BOUND", CYAN));
        status.addView(statusPill("LOCAL CORE", CYAN));
        status.addView(statusPill("ARCHIVE SEALED", BRASS_BRIGHT));
        center.addView(status, matchWrap(0, 0));

        columns.addView(buildRightRail(), weighted(22f));

        TextView version = monoText(installedVersionName(), 8f, Color.rgb(139, 99, 67), Typeface.BOLD);
        version.setGravity(Gravity.END);
        FrameLayout.LayoutParams versionParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.RIGHT
        );
        versionParams.rightMargin = dp(27);
        versionParams.bottomMargin = dp(18);
        root.addView(version, versionParams);

        return root;
    }

    private View buildLeftRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(9), dp(12), dp(9), dp(12));
        rail.setBackground(new HardwarePanelDrawable(PANEL, PANEL_DEEP, CYAN, BRASS, dp(16)));

        DialView dial = new DialView(CYAN, BRASS);
        rail.addView(dial, fixed(dp(116), dp(116), 0, dp(12)));

        TextView label = monoText("MONOLITH // OWNER CHANNEL", 9f, CYAN, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setRotation(-90f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(240), dp(38));
        labelParams.topMargin = dp(72);
        rail.addView(label, labelParams);

        LinearLayout lamps = new LinearLayout(this);
        lamps.setOrientation(LinearLayout.VERTICAL);
        lamps.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            View lamp = new View(this);
            lamp.setBackground(new LampDrawable(i == 3 ? BRASS_BRIGHT : CYAN));
            lamps.addView(lamp, fixed(dp(8), dp(28), 0, dp(7)));
        }
        rail.addView(lamps, matchWrap(dp(54), dp(4)));

        TextView local = monoText("LOCAL", 9f, BRASS_BRIGHT, Typeface.BOLD);
        local.setGravity(Gravity.CENTER);
        rail.addView(local, matchWrap(dp(4), 0));
        return rail;
    }

    private View buildRightRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.setPadding(dp(10), dp(12), dp(10), dp(12));
        rail.setBackground(new HardwarePanelDrawable(PANEL, PANEL_DEEP, CYAN, BRASS, dp(16)));

        rail.addView(statusModule("ACCESS", "AUTHORIZED", CYAN), matchWeight(1f, 0, dp(5)));
        rail.addView(statusModule("CORE", "STANDBY", Color.rgb(66, 221, 130)), matchWeight(1f, dp(5), dp(5)));
        rail.addView(statusModule("VOICE", "LOCAL", CYAN), matchWeight(1f, dp(5), dp(5)));
        rail.addView(statusModule("VAULT", "PRIVATE", Color.rgb(198, 91, 48)), matchWeight(1f, dp(5), 0));
        return rail;
    }

    private View statusModule(String label, String value, int glow) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(new HardwarePanelDrawable(
            Color.rgb(9, 17, 20),
            Color.rgb(3, 8, 11),
            glow,
            BRASS,
            dp(10)
        ));

        DialView dial = new DialView(glow, BRASS);
        box.addView(dial, fixed(dp(76), dp(76), 0, 0));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(8), 0, 0, 0);
        TextView a = monoText(label, 8f, MUTED, Typeface.BOLD);
        a.setLetterSpacing(0.12f);
        TextView b = monoText(value, 10f, TEXT, Typeface.BOLD);
        b.setLetterSpacing(0.08f);
        text.addView(a);
        text.addView(b);
        box.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    private TextView statusPill(String text, int glow) {
        TextView pill = monoText("●  " + text, 8f, glow, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        pill.setLayoutParams(params);
        return pill;
    }

    private TextView monoText(String text, float sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ColorStateList.valueOf(color));
        view.setTextSize(sizeSp);
        view.setTypeface(Typeface.MONOSPACE, style);
        return view;
    }

    private void enterMonolith() {
        if (launchingCore) return;
        launchingCore = true;
        try {
            Intent intent = new Intent(this, MonolithActivity.class);
            intent.putExtra("monolith_mode", "command");
            intent.putExtra(EXTRA_NATIVE_ACCESS_GRANTED, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (RuntimeException error) {
            launchingCore = false;
            MonolithCrashGuard.recordStartupFailure(this, error);
            Intent fallback = new Intent(this, MonolithBootstrapActivity.class);
            fallback.putExtra(MonolithApplication.EXTRA_SHOW_DIAGNOSTIC, true);
            fallback.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(fallback);
            finish();
        }
    }

    private Bitmap loadProcessedCrest() {
        try (InputStream input = getAssets().open("house_dedmon_crest.webp")) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return removeEdgeConnectedBlack(bitmap);
        } catch (Throwable error) {
            MonolithCrashGuard.recordStartupFailure(this, error);
            return null;
        }
    }

    /** Removes only the near-black field connected to the bitmap edges, preserving dark crest detail. */
    private Bitmap removeEdgeConnectedBlack(Bitmap source) {
        if (source == null) return null;
        Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888, true);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return bitmap;

        int total = width * height;
        int[] pixels = new int[total];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] seen = new byte[total];
        int[] queue = new int[total];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < width; x++) {
            tail = enqueueBackground(pixels, seen, queue, tail, x);
            tail = enqueueBackground(pixels, seen, queue, tail, (height - 1) * width + x);
        }
        for (int y = 0; y < height; y++) {
            tail = enqueueBackground(pixels, seen, queue, tail, y * width);
            tail = enqueueBackground(pixels, seen, queue, tail, y * width + width - 1);
        }

        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int y = index / width;
            pixels[index] &= 0x00FFFFFF;
            if (x > 0) tail = enqueueBackground(pixels, seen, queue, tail, index - 1);
            if (x + 1 < width) tail = enqueueBackground(pixels, seen, queue, tail, index + 1);
            if (y > 0) tail = enqueueBackground(pixels, seen, queue, tail, index - width);
            if (y + 1 < height) tail = enqueueBackground(pixels, seen, queue, tail, index + width);
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private int enqueueBackground(int[] pixels, byte[] seen, int[] queue, int tail, int index) {
        if (index < 0 || index >= pixels.length || seen[index] != 0) return tail;
        seen[index] = 1;
        if (!isEdgeBackground(pixels[index])) return tail;
        queue[tail++] = index;
        return tail;
    }

    private boolean isEdgeBackground(int color) {
        int alpha = Color.alpha(color);
        if (alpha == 0) return true;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max < 40 && (max - min) < 18;
    }

    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = info.versionName;
            return version == null ? "" : version;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private StateListDrawable reactorButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_pressed }, new ReactorButtonDrawable(true));
        states.addState(new int[] {}, new ReactorButtonDrawable(false));
        return states;
    }

    private LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private LinearLayout.LayoutParams matchWeight(float weight, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            weight
        );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private LinearLayout.LayoutParams fixed(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    /**
     * The user-facing layout is percentage based; this helper only sizes hardware details. Scale
     * those details down on narrow high-density landscape phones so fixed dials/bevels cannot
     * overpower the weighted columns.
     */
    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        float hardwareScale;
        if (screenWidthDp <= 600) hardwareScale = 0.50f;
        else if (screenWidthDp <= 800) hardwareScale = 0.62f;
        else if (screenWidthDp <= 1000) hardwareScale = 0.78f;
        else hardwareScale = 1.0f;
        return Math.max(1, Math.round(value * density * hardwareScale));
    }

    private final class HardwareBackdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        HardwareBackdrop() {
            super(HouseDedmonAccessActivity.this);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, h, Color.rgb(1, 5, 8), Color.rgb(3, 13, 18), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setColor(Color.argb(18, 92, 238, 225));
            for (int x = 0; x < w; x += dp(44)) canvas.drawRect(x, 0, x + 1, h, paint);
            for (int y = 0; y < h; y += dp(44)) canvas.drawRect(0, y, w, y + 1, paint);

            line.setStrokeWidth(dp(1));
            line.setColor(Color.argb(95, 92, 238, 225));
            canvas.drawRect(dp(8), dp(8), w - dp(8), h - dp(8), line);
            line.setColor(Color.argb(105, 167, 112, 62));
            canvas.drawRect(dp(13), dp(13), w - dp(13), h - dp(13), line);

            paint.setColor(Color.argb(150, 221, 153, 78));
            for (int i = 0; i < 6; i++) {
                float left = dp(24 + i * 18);
                canvas.drawRect(left, dp(14), left + dp(10), dp(18), paint);
                canvas.drawRect(w - left - dp(10), h - dp(18), w - left, h - dp(14), paint);
            }
        }
    }

    private final class HardwarePanelDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top;
        private final int bottom;
        private final int accent;
        private final int metal;
        private final float cut;

        HardwarePanelDrawable(int top, int bottom, int accent, int metal, float cut) {
            this.top = top;
            this.bottom = bottom;
            this.accent = accent;
            this.metal = metal;
            this.cut = cut;
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            Path p = chamfer(r, cut);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, top, bottom, Shader.TileMode.CLAMP));
            canvas.drawPath(p, paint);
            paint.setShader(null);

            stroke.setStrokeWidth(dp(3));
            stroke.setColor(Color.rgb(18, 31, 36));
            canvas.drawPath(p, stroke);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.argb(145, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawPath(chamfer(new RectF(r.left + dp(5), r.top + dp(5), r.right - dp(5), r.bottom - dp(5)), Math.max(2f, cut - dp(4))), stroke);
            stroke.setColor(Color.argb(120, Color.red(metal), Color.green(metal), Color.blue(metal)));
            canvas.drawPath(chamfer(new RectF(r.left + dp(9), r.top + dp(9), r.right - dp(9), r.bottom - dp(9)), Math.max(2f, cut - dp(7))), stroke);

            paint.setColor(metal);
            canvas.drawRect(r.right - dp(49), r.top + dp(7), r.right - dp(36), r.top + dp(11), paint);
            canvas.drawRect(r.right - dp(32), r.top + dp(7), r.right - dp(19), r.top + dp(11), paint);

            paint.setColor(Color.rgb(48, 56, 58));
            canvas.drawCircle(r.left + dp(9), r.top + dp(9), dp(3), paint);
            canvas.drawCircle(r.right - dp(9), r.top + dp(9), dp(3), paint);
            canvas.drawCircle(r.left + dp(9), r.bottom - dp(9), dp(3), paint);
            canvas.drawCircle(r.right - dp(9), r.bottom - dp(9), dp(3), paint);
        }

        private Path chamfer(RectF r, float c) {
            Path p = new Path();
            p.moveTo(r.left + c, r.top);
            p.lineTo(r.right - c, r.top);
            p.lineTo(r.right, r.top + c);
            p.lineTo(r.right, r.bottom - c);
            p.lineTo(r.right - c, r.bottom);
            p.lineTo(r.left + c, r.bottom);
            p.lineTo(r.left, r.bottom - c);
            p.lineTo(r.left, r.top + c);
            p.close();
            return p;
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private final class ReactorButtonDrawable extends Drawable {
        private final boolean pressed;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        ReactorButtonDrawable(boolean pressed) {
            this.pressed = pressed;
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            float y = pressed ? dp(4) : 0;
            r.offset(0, y);
            Path p = chamfer(r, dp(14));

            paint.setShader(new LinearGradient(r.left, r.top, r.left, r.bottom,
                pressed ? Color.rgb(7, 42, 47) : Color.rgb(16, 74, 79),
                Color.rgb(3, 22, 27),
                Shader.TileMode.CLAMP));
            canvas.drawPath(p, paint);
            paint.setShader(null);

            stroke.setStrokeWidth(dp(4));
            stroke.setColor(Color.rgb(23, 31, 33));
            canvas.drawPath(p, stroke);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(CYAN);
            canvas.drawPath(chamfer(new RectF(r.left + dp(4), r.top + dp(4), r.right - dp(4), r.bottom - dp(4)), dp(11)), stroke);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(BRASS_BRIGHT);
            canvas.drawPath(chamfer(new RectF(r.left + dp(8), r.top + dp(8), r.right - dp(8), r.bottom - dp(8)), dp(8)), stroke);

            paint.setColor(Color.argb(70, 92, 238, 225));
            canvas.drawCircle(r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.34f, paint);
            stroke.setColor(Color.argb(180, 92, 238, 225));
            stroke.setStrokeWidth(dp(1));
            canvas.drawCircle(r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.28f, stroke);
        }

        private Path chamfer(RectF r, float c) {
            Path p = new Path();
            p.moveTo(r.left + c, r.top);
            p.lineTo(r.right - c, r.top);
            p.lineTo(r.right, r.top + c);
            p.lineTo(r.right, r.bottom - c);
            p.lineTo(r.right - c, r.bottom);
            p.lineTo(r.left + c, r.bottom);
            p.lineTo(r.left, r.bottom - c);
            p.lineTo(r.left, r.top + c);
            p.close();
            return p;
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private final class LampDrawable extends Drawable {
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LampDrawable(int color) { this.color = color; }
        @Override public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            paint.setColor(Color.rgb(13, 24, 27));
            canvas.drawRoundRect(r, dp(2), dp(2), paint);
            RectF inner = new RectF(r.left + dp(2), r.top + dp(2), r.right - dp(2), r.bottom - dp(2));
            paint.setShader(new LinearGradient(inner.left, inner.top, inner.right, inner.bottom, color, Color.rgb(5, 38, 39), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(inner, dp(2), dp(2), paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private final class DialView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int glow;
        private final int metal;

        DialView(int glow, int metal) {
            super(HouseDedmonAccessActivity.this);
            this.glow = glow;
            this.metal = metal;
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.43f;

            paint.setColor(Color.rgb(7, 12, 14));
            canvas.drawCircle(cx, cy, radius, paint);
            stroke.setStrokeWidth(dp(7));
            stroke.setColor(Color.rgb(57, 58, 54));
            canvas.drawCircle(cx, cy, radius - dp(2), stroke);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(metal);
            canvas.drawCircle(cx, cy, radius - dp(7), stroke);

            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2d * i / 28d;
                float x1 = cx + (float) Math.cos(a) * (radius - dp(11));
                float y1 = cy + (float) Math.sin(a) * (radius - dp(11));
                float x2 = cx + (float) Math.cos(a) * (radius - dp(21));
                float y2 = cy + (float) Math.sin(a) * (radius - dp(21));
                stroke.setStrokeWidth(dp(3));
                stroke.setColor(i % 7 == 0 ? glow : metal);
                canvas.drawLine(x1, y1, x2, y2, stroke);
            }

            paint.setColor(Color.rgb(6, 21, 23));
            canvas.drawCircle(cx, cy, radius * 0.34f, paint);
            paint.setColor(glow);
            canvas.drawCircle(cx, cy, radius * 0.13f, paint);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(Color.argb(160, Color.red(glow), Color.green(glow), Color.blue(glow)));
            canvas.drawCircle(cx, cy, radius * 0.22f, stroke);
        }
    }
}
