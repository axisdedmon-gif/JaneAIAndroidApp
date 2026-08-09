package ai.monolith.app;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-level crash capture for Monolith AI.
 *
 * The launcher process remains alive while the full Monolith UI runs in :core. If :core or :safe
 * terminates because of an uncaught Java/ART exception, this class persists the complete stack
 * trace and returns the user to the bootstrap screen instead of leaving startup as a black box.
 */
public final class MonolithApplication extends Application {
    public static final String EXTRA_SHOW_DIAGNOSTIC = "monolith_show_diagnostic";
    private static final String DIAGNOSTIC_DIR = "runtime_diagnostics";
    private static final String CRASH_FILE = "last_runtime_crash.txt";
    private static final AtomicBoolean HANDLING = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashCapture();
    }

    private void installCrashCapture() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        final String processName = currentProcessName(this);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (!HANDLING.compareAndSet(false, true)) {
                if (previous != null) previous.uncaughtException(thread, error);
                return;
            }

            try {
                writeCrashReport(this, processName, thread, error);
            } catch (Throwable ignored) {
                // The crash path must never recursively fail while trying to persist diagnostics.
            }

            if (isIsolatedUiProcess(processName)) {
                try {
                    Intent recovery = new Intent(this, MonolithBootstrapActivity.class);
                    recovery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    recovery.putExtra(EXTRA_SHOW_DIAGNOSTIC, true);
                    startActivity(recovery);
                } catch (Throwable ignored) {
                    // If recovery launch itself is unavailable, terminate only this isolated process.
                }
                Process.killProcess(Process.myPid());
                System.exit(10);
                return;
            }

            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static boolean isIsolatedUiProcess(String processName) {
        return processName != null && (processName.endsWith(":core") || processName.endsWith(":safe"));
    }

    public static File crashFile(Context context) {
        File dir = new File(context.getFilesDir(), DIAGNOSTIC_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, CRASH_FILE);
    }

    public static long crashTimestamp(Context context) {
        File file = crashFile(context);
        return file.isFile() ? file.lastModified() : 0L;
    }

    public static String readCrashReport(Context context) {
        File file = crashFile(context);
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            return "Unable to read persisted runtime diagnostic: " + error.getClass().getSimpleName();
        }
    }

    public static void clearCrashReport(Context context) {
        File file = crashFile(context);
        if (file.isFile()) file.delete();
    }

    private static void writeCrashReport(
        Context context,
        String processName,
        Thread thread,
        Throwable error
    ) throws Exception {
        File target = crashFile(context);
        File temp = new File(target.getParentFile(), CRASH_FILE + ".tmp");

        StringWriter stackBuffer = new StringWriter();
        PrintWriter stackWriter = new PrintWriter(stackBuffer);
        if (error != null) error.printStackTrace(stackWriter);
        stackWriter.flush();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());

        StringBuilder report = new StringBuilder(8192);
        report.append("MONOLITH AI RUNTIME DIAGNOSTIC\n");
        report.append("capturedAt=").append(format.format(new Date())).append('\n');
        report.append("process=").append(processName == null ? "unknown" : processName).append('\n');
        report.append("thread=").append(thread == null ? "unknown" : thread.getName()).append('\n');
        report.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        report.append("release=").append(Build.VERSION.RELEASE).append('\n');
        report.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        report.append("abi=").append(Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]).append('\n');
        report.append("exception=")
            .append(error == null ? "unknown" : error.getClass().getName())
            .append('\n');
        report.append("\nSTACK TRACE\n");
        report.append(stackBuffer);

        byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not replace previous Monolith diagnostic.");
        }
        if (!temp.renameTo(target)) {
            throw new IllegalStateException("Could not commit Monolith diagnostic.");
        }
    }

    private static String currentProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String name = Application.getProcessName();
            if (name != null && !name.trim().isEmpty()) return name;
        }
        int pid = Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo info : processes) {
                    if (info != null && info.pid == pid) return info.processName;
                }
            }
        }
        return context.getPackageName();
    }
}
