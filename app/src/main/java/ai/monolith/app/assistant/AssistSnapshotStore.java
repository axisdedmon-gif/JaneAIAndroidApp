package ai.monolith.app.assistant;

import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persists only the most recent explicit Android Assist hierarchy for handoff to Monolith. */
public final class AssistSnapshotStore {
    private static final String PREFS = "monolith.assist";
    private static final String KEY = "latest";
    private static final int MAX_NODES = 320;
    private static final int MAX_TEXT = 24000;

    private AssistSnapshotStore() {}

    private static void walk(AssistStructure.ViewNode node, JSONArray nodes, Counter chars, int depth) throws Exception {
        if (node == null || nodes.length() >= MAX_NODES || chars.value >= MAX_TEXT || depth > 40) return;
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String hint = node.getHint() == null ? "" : node.getHint().toString();
        String id = node.getIdEntry();
        if (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(description) || !hint.isEmpty()) {
            JSONObject row = new JSONObject();
            if (!TextUtils.isEmpty(text)) row.put("text", trim(text.toString(), chars));
            if (!TextUtils.isEmpty(description)) row.put("description", trim(description.toString(), chars));
            if (!hint.isEmpty()) row.put("hint", trim(hint, chars));
            if (id != null) row.put("id", id);
            row.put("depth", depth);
            nodes.put(row);
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChildAt(i), nodes, chars, depth + 1);
    }

    private static String trim(String value, Counter chars) {
        String clean = value.replaceAll("\\s+", " ").trim();
        int remaining = Math.max(0, MAX_TEXT - chars.value);
        if (clean.length() > remaining) clean = clean.substring(0, remaining);
        chars.value += clean.length();
        return clean;
    }

    static void save(Context context, AssistStructure structure, boolean focused) {
        try {
            JSONObject out = new JSONObject();
            out.put("capturedAt", System.currentTimeMillis());
            out.put("focused", focused);
            JSONArray windows = new JSONArray();
            if (structure != null) {
                out.put("activity", structure.getActivityComponent() == null ? "" : structure.getActivityComponent().flattenToShortString());
                Counter chars = new Counter();
                for (int i = 0; i < structure.getWindowNodeCount(); i++) {
                    AssistStructure.WindowNode window = structure.getWindowNodeAt(i);
                    JSONObject win = new JSONObject();
                    CharSequence title = window.getTitle();
                    if (title != null) win.put("title", title.toString());
                    JSONArray nodes = new JSONArray();
                    walk(window.getRootViewNode(), nodes, chars, 0);
                    win.put("nodes", nodes);
                    windows.put(win);
                    if (chars.value >= MAX_TEXT) break;
                }
            }
            out.put("windows", windows);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY, "{\"capturedAt\":0,\"windows\":[]}");
    }

    private static final class Counter { int value = 0; }
}
