package ai.monolith.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-level startup guard for Monolith Core.
 *
 * It records Java crashes and detects launches that die before the stable checkpoint. Persisted
 * startup state is owned here so the BIOS can atomically clear both the visible diagnostic and the
 * hidden safe-mode/booting flags before an operator-requested retry.
 */
public final class MonolithCrashGuard {
    private static final String PREFS = "monolith.crash_guard";
    private static final String KEY_BOOTING = "booting";
    private static final String KEY_LAUNCH_WALL = "launch_wall";
    private static final String KEY_LAUNCH_ELAPSED = "launch_elapsed";
    private static final String KEY_LAST_CRASH_WALL = "last_crash_wall";
    private static final String KEY_LAST_CRASH = "last_crash";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_SAFE_MODE = "safe_mode";
    private static final long CRASH_LOOP_WINDOW_MS = 5L * 60L * 1000L;
    private static final int MAX_STACK_CHARS = 16000;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private MonolithCrashGuard() {}

    public static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            recordCrash(app, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /**
     * Marks a launch as in progress and returns true when the immediately previous launch appears
     * to have died before reaching the stable checkpoint.
     */
    public static boolean beginLaunch(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean previousBooting = prefs.getBoolean(KEY_BOOTING, false);
        long previousLaunch = prefs.getLong(KEY_LAUNCH_WALL, 0L);
        boolean recentUnstableLaunch = previousBooting
            && previousLaunch > 0L
            && now >= previousLaunch
            && now - previousLaunch <= CRASH_LOOP_WINDOW_MS;
        boolean safeMode = recentUnstableLaunch || prefs.getBoolean(KEY_SAFE_MODE, false);

        prefs.edit()
            .putBoolean(KEY_BOOTING, true)
            .putBoolean(KEY_SAFE_MODE, safeMode)
            .putLong(KEY_LAUNCH_WALL, now)
            .putLong(KEY_LAUNCH_ELAPSED, SystemClock.elapsedRealtime())
            .commit();
        return safeMode;
    }

    public static void markStable(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOTING, false)
            .putBoolean(KEY_SAFE_MODE, false)
            .putInt(KEY_CRASH_COUNT, 0)
            .apply();
    }

    /**
     * Operator-requested recovery reset. This is intentionally stronger than markStable(): it
     * clears every persisted launch/crash-loop flag so a BIOS retry can never inherit stale
     * safe-mode state from an earlier Core attempt.
     */
    public static void clearStartupState(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOTING, false)
            .putBoolean(KEY_SAFE_MODE, false)
            .putLong(KEY_LAUNCH_WALL, 0L)
            .putLong(KEY_LAUNCH_ELAPSED, 0L)
            .putLong(KEY_LAST_CRASH_WALL, 0L)
            .putString(KEY_LAST_CRASH, "")
            .putInt(KEY_CRASH_COUNT, 0)
            .commit();
    }

    public static void recordStartupFailure(Context context, Throwable error) {
        recordCrash(context.getApplicationContext(), error);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAFE_MODE, true)
            .apply();
    }

    private static void recordCrash(Context context, Throwable error) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = Math.max(0, prefs.getInt(KEY_CRASH_COUNT, 0)) + 1;
        String trace = stackTrace(error);
        prefs.edit()
            .putLong(KEY_LAST_CRASH_WALL, System.currentTimeMillis())
            .putString(KEY_LAST_CRASH, trace)
            .putInt(KEY_CRASH_COUNT, count)
            .putBoolean(KEY_SAFE_MODE, true)
            .commit();
    }

    private static String stackTrace(Throwable error) {
        if (error == null) return "unknown startup failure";
        try {
            StringWriter writer = new StringWriter();
            PrintWriter printer = new PrintWriter(writer);
            error.printStackTrace(printer);
            printer.flush();
            String value = writer.toString();
            return value.length() <= MAX_STACK_CHARS ? value : value.substring(0, MAX_STACK_CHARS);
        } catch (RuntimeException ignored) {
            String message = error.getMessage();
            return error.getClass().getName() + (message == null ? "" : ": " + message);
        }
    }

    public static String diagnosticJson(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject out = new JSONObject();
            out.put("booting", prefs.getBoolean(KEY_BOOTING, false));
            out.put("safeMode", prefs.getBoolean(KEY_SAFE_MODE, false));
            out.put("launchWall", prefs.getLong(KEY_LAUNCH_WALL, 0L));
            out.put("lastCrashWall", prefs.getLong(KEY_LAST_CRASH_WALL, 0L));
            out.put("crashCount", prefs.getInt(KEY_CRASH_COUNT, 0));
            out.put("lastCrash", prefs.getString(KEY_LAST_CRASH, ""));
            return out.toString();
        } catch (Exception error) {
            return "{\"safeMode\":false,\"crashCount\":0}";
        }
    }
}
