package ai.monolith.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Coordinates runtime and special-access states without repeatedly forcing settings screens. */
public final class PermissionCoordinator {
    public static final int REQUEST_RUNTIME = 8401;

    private PermissionCoordinator() {}

    public static void requestRuntimePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        List<String> wanted = new ArrayList<>();
        addIfMissing(activity, wanted, Manifest.permission.RECORD_AUDIO);
        addIfMissing(activity, wanted, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(activity, wanted, Manifest.permission.READ_CONTACTS);
        addIfMissing(activity, wanted, Manifest.permission.READ_CALENDAR);
        addIfMissing(activity, wanted, Manifest.permission.READ_PHONE_STATE);
        addIfMissing(activity, wanted, Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(activity, wanted, Manifest.permission.BLUETOOTH_CONNECT);
            addIfMissing(activity, wanted, Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_IMAGES);
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_AUDIO);
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_VIDEO);
        }
        if (!wanted.isEmpty()) activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_RUNTIME);
    }

    private static void addIfMissing(Activity activity, List<String> list, String permission) {
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) list.add(permission);
    }

    public static boolean overlayGranted(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    public static boolean notificationPolicyGranted(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationPolicyAccessGranted();
    }

    public static boolean accessibilityGranted(Context context) {
        String expected = new ComponentName(context, MonolithAccessibilityService.class).flattenToString();
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (expected.equalsIgnoreCase(splitter.next())) return true;
        }
        return false;
    }

    public static void openOverlaySettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }

    public static void openPolicySettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    public static void openAccessibilitySettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    public static void openAssistantSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception first) {
            try { activity.startActivity(new Intent("android.settings.VOICE_CONTROL_SETTINGS")); }
            catch (Exception ignored) { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }
    }

    private static JSONObject permission(Context context, String name, String androidPermission, boolean special) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id", name);
        row.put("special", special);
        boolean granted;
        if (special) {
            if ("accessibility".equals(name)) granted = accessibilityGranted(context);
            else if ("overlay".equals(name)) granted = overlayGranted(context);
            else if ("notification_policy".equals(name)) granted = notificationPolicyGranted(context);
            else granted = false;
        } else if (androidPermission == null) {
            granted = true;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            granted = true;
        } else {
            granted = context.checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED;
        }
        row.put("granted", granted);
        return row;
    }

    public static String stateJson(Context context) {
        try {
            JSONArray states = new JSONArray();
            states.put(permission(context, "accessibility", null, true));
            states.put(permission(context, "record_audio", Manifest.permission.RECORD_AUDIO, false));
            states.put(permission(context, "overlay", null, true));
            states.put(permission(context, "notification_policy", null, true));
            states.put(permission(context, "fine_location", Manifest.permission.ACCESS_FINE_LOCATION, false));
            states.put(permission(context, "contacts", Manifest.permission.READ_CONTACTS, false));
            states.put(permission(context, "calendar", Manifest.permission.READ_CALENDAR, false));
            states.put(permission(context, "phone_state", Manifest.permission.READ_PHONE_STATE, false));
            states.put(permission(context, "call_log", Manifest.permission.READ_CALL_LOG, false));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                states.put(permission(context, "bluetooth_connect", Manifest.permission.BLUETOOTH_CONNECT, false));
                states.put(permission(context, "bluetooth_scan", Manifest.permission.BLUETOOTH_SCAN, false));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                states.put(permission(context, "media_images", Manifest.permission.READ_MEDIA_IMAGES, false));
                states.put(permission(context, "media_audio", Manifest.permission.READ_MEDIA_AUDIO, false));
                states.put(permission(context, "media_video", Manifest.permission.READ_MEDIA_VIDEO, false));
            }
            JSONObject out = new JSONObject();
            out.put("states", states);
            out.put("wifiState", true);
            return out.toString();
        } catch (Exception error) {
            return "{\"states\":[]}";
        }
    }
}
