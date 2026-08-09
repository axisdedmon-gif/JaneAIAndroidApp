package ai.monolith.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * User-enabled accessibility integration. It does not continuously archive screen content.
 * A snapshot is read only when the Monolith UI explicitly requests one.
 */
public class MonolithAccessibilityService extends AccessibilityService {
    private static WeakReference<MonolithAccessibilityService> active = new WeakReference<>(null);

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        active = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally no background collection.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (active.get() == this) active.clear();
        super.onDestroy();
    }

    private static void walk(AccessibilityNodeInfo node, JSONArray rows, int depth) throws Exception {
        if (node == null || rows.length() >= 220 || depth > 30) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if ((text != null && text.length() > 0) || (desc != null && desc.length() > 0)) {
            JSONObject row = new JSONObject();
            if (text != null) row.put("text", text.toString());
            if (desc != null) row.put("description", desc.toString());
            CharSequence className = node.getClassName();
            if (className != null) row.put("class", className.toString());
            rows.put(row);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            try { walk(child, rows, depth + 1); }
            finally { if (child != null) child.recycle(); }
        }
    }

    public static String snapshotJson() {
        MonolithAccessibilityService service = active.get();
        if (service == null) return "{\"available\":false,\"nodes\":[]}";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "{\"available\":true,\"nodes\":[]}";
        try {
            JSONArray rows = new JSONArray();
            walk(root, rows, 0);
            JSONObject out = new JSONObject();
            out.put("available", true);
            out.put("nodes", rows);
            return out.toString();
        } catch (Exception error) {
            return "{\"available\":true,\"nodes\":[]}";
        } finally {
            root.recycle();
        }
    }
}
