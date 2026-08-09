package ai.monolith.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.role.RoleManager;
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

/**
 * Coordinates Monolith runtime and special-access permissions.
 *
 * Permissions are intentionally user-triggered instead of being fired from Activity.onCreate().
 * READ_CALL_LOG is hard-restricted by Android and is requested only after Monolith holds the
 * assistant role. Special access permissions always use their dedicated Settings/role surfaces.
 */
public final class PermissionCoordinator {
    public static final int REQUEST_RUNTIME = 8401;
    public static final int REQUEST_RESTRICTED_ASSISTANT = 8402;
    public static final int REQUEST_ASSISTANT_ROLE = 8403;

    private static volatile String lastRequestError = "";

    private PermissionCoordinator() {}

    /** Requests ordinary dangerous permissions that are valid for a normal third-party app. */
    public static boolean requestRuntimePermissions(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        List<String> wanted = new ArrayList<>();
        addIfMissing(activity, wanted, Manifest.permission.RECORD_AUDIO);
        addIfMissing(activity, wanted, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(activity, wanted, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(activity, wanted, Manifest.permission.READ_CONTACTS);
        addIfMissing(activity, wanted, Manifest.permission.READ_CALENDAR);
        addIfMissing(activity, wanted, Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(activity, wanted, Manifest.permission.BLUETOOTH_CONNECT);
            addIfMissing(activity, wanted, Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_IMAGES);
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_AUDIO);
            addIfMissing(activity, wanted, Manifest.permission.READ_MEDIA_VIDEO);
        }

        if (wanted.isEmpty()) {
            lastRequestError = "";
            return true;
        }
        try {
            activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_RUNTIME);
            lastRequestError = "";
            return true;
        } catch (RuntimeException error) {
            lastRequestError = safeMessage(error, "Android rejected the runtime permission request.");
            return false;
        }
    }

    /**
     * Requests permissions Android reserves for an eligible assistant role holder.
     * This method never attempts READ_CALL_LOG before the role exists.
     */
    public static boolean requestAssistantRestrictedPermissions(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (!assistantGranted(activity)) {
            lastRequestError = "Monolith must be selected as the device assistant before call-log access can be requested.";
            openAssistantSettings(activity);
            return false;
        }
        List<String> wanted = new ArrayList<>();
        addIfMissing(activity, wanted, Manifest.permission.READ_CALL_LOG);
        if (wanted.isEmpty()) {
            lastRequestError = "";
            return true;
        }
        try {
            activity.requestPermissions(wanted.toArray(new String[0]), REQUEST_RESTRICTED_ASSISTANT);
            lastRequestError = "";
            return true;
        } catch (RuntimeException error) {
            lastRequestError = safeMessage(error, "Android rejected the assistant-only permission request.");
            return false;
        }
    }

    private static String safeMessage(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
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

    public static boolean assistantGranted(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
            return roleManager != null
                && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                && roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        }
        String assistant = Settings.Secure.getString(context.getContentResolver(), "assistant");
        return assistant != null && assistant.startsWith(context.getPackageName() + "/");
    }

    public static void openOverlaySettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            lastRequestError = safeMessage(error, "Overlay settings are unavailable on this device.");
        }
    }

    public static void openPolicySettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (RuntimeException error) {
            lastRequestError = safeMessage(error, "Notification-policy settings are unavailable on this device.");
        }
    }

    public static void openAccessibilitySettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException error) {
            lastRequestError = safeMessage(error, "Accessibility settings are unavailable on this device.");
        }
    }

    public static void openAssistantSettings(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        lastRequestError = "";
                        return;
                    }
                    activity.startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                        REQUEST_ASSISTANT_ROLE
                    );
                    return;
                }
            }
            activity.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (RuntimeException first) {
            try {
                activity.startActivity(new Intent("android.settings.VOICE_CONTROL_SETTINGS"));
            } catch (RuntimeException second) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (RuntimeException terminal) {
                    lastRequestError = safeMessage(terminal, "Assistant settings are unavailable on this device.");
                }
            }
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
            else if ("assistant".equals(name)) granted = assistantGranted(context);
            else granted = false;
        } else if (androidPermission == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            granted = true;
        } else {
            granted = context.checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED;
        }
        row.put("granted", granted);
        if ("call_log".equals(name)) row.put("eligible", assistantGranted(context));
        return row;
    }

    public static String stateJson(Context context) {
        try {
            JSONArray states = new JSONArray();
            states.put(permission(context, "assistant", null, true));
            states.put(permission(context, "accessibility", null, true));
            states.put(permission(context, "record_audio", Manifest.permission.RECORD_AUDIO, false));
            states.put(permission(context, "overlay", null, true));
            states.put(permission(context, "notification_policy", null, true));
            states.put(permission(context, "coarse_location", Manifest.permission.ACCESS_COARSE_LOCATION, false));
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
            out.put("assistantRoleHeld", assistantGranted(context));
            out.put("lastRequestError", lastRequestError);
            return out.toString();
        } catch (Exception error) {
            return "{\"states\":[],\"assistantRoleHeld\":false,\"lastRequestError\":\"permission-state-unavailable\"}";
        }
    }
}
