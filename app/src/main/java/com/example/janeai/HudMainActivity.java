package com.example.janeai;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Field;

/**
 * QoL hardware-telemetry shell for Jane's command chamber.
 *
 * MainActivity remains the stable application core. This subclass adds a dedicated,
 * read-only telemetry bridge and loads the HUD enhancement layer without changing the
 * knowledge, voice, archive, model, or dialogue implementations in MainActivity.
 */
public class HudMainActivity extends MainActivity {
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private final Object sampleLock = new Object();

    private long lastProcessWallMs = 0L;
    private long lastProcessCpuMs = 0L;
    private long lastSystemTotal = -1L;
    private long lastSystemIdle = -1L;
    private long lastTrafficAtMs = 0L;
    private long lastRxBytes = -1L;
    private long lastTxBytes = -1L;
    private WebView hudWebView;

    private final Runnable injectHud = new Runnable() {
        @Override
        public void run() {
            installHudLayer();
        }
    };

    @SuppressLint("AddJavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hudWebView = resolveWebView();
        if (hudWebView != null) {
            hudWebView.addJavascriptInterface(new HardwareHudBridge(), "AndroidHud");
        }
        scheduleHudInjection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleHudInjection();
    }

    @Override
    protected void onDestroy() {
        hudHandler.removeCallbacks(injectHud);
        super.onDestroy();
    }

    private WebView resolveWebView() {
        try {
            Field field = MainActivity.class.getDeclaredField("webView");
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof WebView ? (WebView) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void scheduleHudInjection() {
        hudHandler.removeCallbacks(injectHud);
        hudHandler.postDelayed(injectHud, 220L);
        hudHandler.postDelayed(this::installHudLayer, 760L);
        hudHandler.postDelayed(this::installHudLayer, 1650L);
    }

    private void installHudLayer() {
        if (hudWebView == null) hudWebView = resolveWebView();
        if (hudWebView == null) return;
        final String js =
            "(function(){" +
            "if(!document||!document.head)return;" +
            "if(!document.getElementById('jane-qol-css')){" +
            "var l=document.createElement('link');l.id='jane-qol-css';l.rel='stylesheet';" +
            "l.href='file:///android_asset/jane_qol_hud.css';document.head.appendChild(l);}" +
            "if(!document.getElementById('jane-qol-runtime')){" +
            "var s=document.createElement('script');s.id='jane-qol-runtime';" +
            "s.src='file:///android_asset/jane_qol_runtime.js';document.head.appendChild(s);}" +
            "else if(window.JaneQolHud&&window.JaneQolHud.refresh){window.JaneQolHud.refresh();}" +
            "})();";
        hudWebView.evaluateJavascript(js, null);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0d) / 10.0d;
    }

    private double sampleProcessLoadPercent() {
        long wall = SystemClock.elapsedRealtime();
        long cpu = android.os.Process.getElapsedCpuTime();
        synchronized (sampleLock) {
            double percent = -1.0d;
            if (lastProcessWallMs > 0L && wall > lastProcessWallMs && cpu >= lastProcessCpuMs) {
                long wallDelta = wall - lastProcessWallMs;
                long cpuDelta = cpu - lastProcessCpuMs;
                int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                percent = clamp((cpuDelta * 100.0d) / (wallDelta * cores), 0.0d, 100.0d);
            }
            lastProcessWallMs = wall;
            lastProcessCpuMs = cpu;
            return percent < 0.0d ? -1.0d : round1(percent);
        }
    }

    private double sampleSystemLoadPercent() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1.0d;
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 5) return -1.0d;
            long total = 0L;
            for (int i = 1; i < fields.length; i++) total += Long.parseLong(fields[i]);
            long idle = Long.parseLong(fields[4]);
            if (fields.length > 5) idle += Long.parseLong(fields[5]);
            synchronized (sampleLock) {
                double percent = -1.0d;
                if (lastSystemTotal >= 0L && total > lastSystemTotal && idle >= lastSystemIdle) {
                    long totalDelta = total - lastSystemTotal;
                    long idleDelta = idle - lastSystemIdle;
                    if (totalDelta > 0L) {
                        percent = clamp(((totalDelta - idleDelta) * 100.0d) / totalDelta, 0.0d, 100.0d);
                    }
                }
                lastSystemTotal = total;
                lastSystemIdle = idle;
                return percent < 0.0d ? -1.0d : round1(percent);
            }
        } catch (Exception ignored) {
            return -1.0d;
        }
    }

    private JSONObject sampleTraffic() throws Exception {
        JSONObject traffic = new JSONObject();
        long now = SystemClock.elapsedRealtime();
        int uid = android.os.Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid);
        long tx = TrafficStats.getUidTxBytes(uid);
        double rxKbps = 0.0d;
        double txKbps = 0.0d;
        synchronized (sampleLock) {
            if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED &&
                lastRxBytes >= 0L && lastTxBytes >= 0L && now > lastTrafficAtMs) {
                long elapsed = now - lastTrafficAtMs;
                rxKbps = Math.max(0.0d, ((rx - lastRxBytes) * 8.0d) / elapsed);
                txKbps = Math.max(0.0d, ((tx - lastTxBytes) * 8.0d) / elapsed);
            }
            lastTrafficAtMs = now;
            lastRxBytes = rx;
            lastTxBytes = tx;
        }
        traffic.put("rxKbps", round1(rxKbps));
        traffic.put("txKbps", round1(txKbps));
        return traffic;
    }

    private int normalizedSignalPercent(int raw) {
        if (raw == Integer.MIN_VALUE) return -1;
        if (raw >= 0 && raw <= 100) return raw;
        if (raw < 0) return (int) Math.round(clamp(((raw + 120.0d) / 70.0d) * 100.0d, 0.0d, 100.0d));
        return -1;
    }

    private int thermalIndex(int state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1;
        switch (state) {
            case PowerManager.THERMAL_STATUS_NONE: return 8;
            case PowerManager.THERMAL_STATUS_LIGHT: return 24;
            case PowerManager.THERMAL_STATUS_MODERATE: return 44;
            case PowerManager.THERMAL_STATUS_SEVERE: return 68;
            case PowerManager.THERMAL_STATUS_CRITICAL: return 84;
            case PowerManager.THERMAL_STATUS_EMERGENCY: return 95;
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return 100;
            default: return -1;
        }
    }

    private String thermalCode(int state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unverified";
        switch (state) {
            case PowerManager.THERMAL_STATUS_NONE: return "equilibrium";
            case PowerManager.THERMAL_STATUS_LIGHT: return "rising";
            case PowerManager.THERMAL_STATUS_MODERATE: return "high-flux";
            case PowerManager.THERMAL_STATUS_SEVERE: return "guarded";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "cutoff";
            default: return "unverified";
        }
    }

    private String coreHealthCode(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "nominal";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "heat-watch";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "critical";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "surge-watch";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "service";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold-lock";
            default: return "unverified";
        }
    }

    private String coreFeedCode(int status, int plugged) {
        if (plugged != 0) return status == BatteryManager.BATTERY_STATUS_FULL ? "feed-saturated" : "external-feed";
        switch (status) {
            case BatteryManager.BATTERY_STATUS_FULL: return "saturated";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "free-cycle";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "holding";
            default: return "unverified";
        }
    }

    private JSONObject buildHardwareTelemetry() {
        JSONObject out = new JSONObject();
        try {
            out.put("capturedAt", System.currentTimeMillis());
            out.put("uptimeMs", SystemClock.elapsedRealtime());

            Intent powerCell = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = powerCell == null ? -1 : powerCell.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = powerCell == null ? -1 : powerCell.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int health = powerCell == null ? BatteryManager.BATTERY_HEALTH_UNKNOWN
                : powerCell.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            int status = powerCell == null ? BatteryManager.BATTERY_STATUS_UNKNOWN
                : powerCell.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            int plugged = powerCell == null ? 0 : powerCell.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int corePercent = level >= 0 && scale > 0 ? Math.round((level * 100.0f) / scale) : -1;
            out.put("corePercent", corePercent);
            out.put("coreHealth", coreHealthCode(health));
            out.put("coreFeed", coreFeedCode(status, plugged));

            StatFs grid = new StatFs(getFilesDir().getAbsolutePath());
            long gridTotal = grid.getTotalBytes();
            long gridFree = grid.getAvailableBytes();
            long gridUsed = Math.max(0L, gridTotal - gridFree);
            out.put("gridTotalBytes", gridTotal);
            out.put("gridFreeBytes", gridFree);
            out.put("gridUsedBytes", gridUsed);
            out.put("gridDensityPercent", gridTotal > 0L ? round1((gridUsed * 100.0d) / gridTotal) : 0.0d);

            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo reserve = new ActivityManager.MemoryInfo();
            if (manager != null) manager.getMemoryInfo(reserve);
            long reserveUsed = Math.max(0L, reserve.totalMem - reserve.availMem);
            double reserveUsedPercent = reserve.totalMem > 0L ? (reserveUsed * 100.0d) / reserve.totalMem : 0.0d;
            out.put("reserveTotalBytes", reserve.totalMem);
            out.put("reserveFreeBytes", reserve.availMem);
            out.put("reserveUsedPercent", round1(reserveUsedPercent));
            out.put("reserveHeadroomPercent", round1(100.0d - reserveUsedPercent));
            out.put("reservePressure", reserve.lowMemory);

            double systemLoad = sampleSystemLoadPercent();
            double processLoad = sampleProcessLoadPercent();
            out.put("arrayLoadPercent", systemLoad >= 0.0d ? systemLoad : processLoad);
            out.put("localLoadPercent", processLoad);
            out.put("arrayLoadScope", systemLoad >= 0.0d ? "array-total" : "jane-local");
            out.put("arrayCores", Math.max(1, Runtime.getRuntime().availableProcessors()));

            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            int thermal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null
                ? power.getCurrentThermalStatus()
                : -1;
            out.put("thermalCode", thermalCode(thermal));
            out.put("thermalIndex", thermalIndex(thermal));
            out.put("reserveProtocol", power != null && power.isPowerSaveMode());

            boolean linked = false;
            boolean validated = false;
            String relay = "dark";
            int signalPercent = -1;
            int downKbps = 0;
            int upKbps = 0;
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                Network network = connectivity.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
                if (caps != null) {
                    linked = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    downKbps = Math.max(0, caps.getLinkDownstreamBandwidthKbps());
                    upKbps = Math.max(0, caps.getLinkUpstreamBandwidthKbps());
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) relay = "wave";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) relay = "wide";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) relay = "hardline";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) relay = "veil";
                    else relay = linked ? "auxiliary" : "dark";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signalPercent = normalizedSignalPercent(caps.getSignalStrength());
                    }
                }
            }
            out.put("relayLinked", linked);
            out.put("relayValidated", validated);
            out.put("relayKind", relay);
            out.put("relaySignalPercent", signalPercent);
            out.put("relayDownKbps", downKbps);
            out.put("relayUpKbps", upKbps);
            out.put("traffic", sampleTraffic());
        } catch (Exception error) {
            try { out.put("hudError", error.getMessage() == null ? "unavailable" : error.getMessage()); }
            catch (Exception ignored) {}
        }
        return out;
    }

    public class HardwareHudBridge {
        @JavascriptInterface
        public String getTelemetry() {
            return buildHardwareTelemetry().toString();
        }
    }
}
