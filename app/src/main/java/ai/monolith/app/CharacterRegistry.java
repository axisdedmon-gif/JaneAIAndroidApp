package ai.monolith.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Stores the selected Monolith AI character independently from the app identity. */
public final class CharacterRegistry {
    private static final String PREFS = "monolith.characters";
    private static final String ACTIVE_ID = "active_id";
    private static final String FEMALE_ID = "female_jane";
    private static final String MALE_ID = "male_core";

    private CharacterRegistry() {}

    public static void ensureDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(ACTIVE_ID)) prefs.edit().putString(ACTIVE_ID, FEMALE_ID).apply();
        if (!prefs.contains(FEMALE_ID + ".level")) {
            prefs.edit()
                .putInt(FEMALE_ID + ".level", 1)
                .putLong(FEMALE_ID + ".xp", 0L)
                .putInt(MALE_ID + ".level", 1)
                .putLong(MALE_ID + ".xp", 0L)
                .apply();
        }
    }

    public static String activeId(Context context) {
        ensureDefaults(context);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_ID, FEMALE_ID);
    }

    public static String activeName(Context context) {
        return FEMALE_ID.equals(activeId(context)) ? "Jane" : "Male Core";
    }

    public static boolean setActive(Context context, String id) {
        if (!FEMALE_ID.equals(id) && !MALE_ID.equals(id)) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE_ID, id).apply();
        return true;
    }

    public static void addExperience(Context context, long amount) {
        if (amount <= 0) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = activeId(context);
        long xp = Math.max(0L, prefs.getLong(id + ".xp", 0L) + amount);
        int level = 1;
        long threshold = 100L;
        long remaining = xp;
        while (remaining >= threshold && level < 100) {
            remaining -= threshold;
            level++;
            threshold = Math.round(threshold * 1.18d + 20d);
        }
        prefs.edit().putLong(id + ".xp", xp).putInt(id + ".level", level).apply();
    }

    private static JSONObject profile(SharedPreferences prefs, String id, String name, String sex, boolean ready) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("name", name);
        row.put("sex", sex);
        row.put("status", ready ? "operational" : "provisioned-pending-native-build");
        row.put("level", prefs.getInt(id + ".level", 1));
        row.put("xp", prefs.getLong(id + ".xp", 0L));
        if (FEMALE_ID.equals(id)) {
            row.put("personality", "established-jane-personality");
            row.put("modelSlot", "legacy://jane-active-glb");
            row.put("portraitSlot", "dialog_portraits_webp");
        } else {
            row.put("personality", "characters/male/personality.json");
            row.put("modelSlot", "characters/male/model.glb");
            row.put("portraitSlot", "characters/male/portraits/");
        }
        return row;
    }

    public static String stateJson(Context context) {
        try {
            ensureDefaults(context);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject out = new JSONObject();
            String active = activeId(context);
            out.put("activeId", active);
            out.put("activeName", FEMALE_ID.equals(active) ? "Jane" : "Male Core");
            JSONArray characters = new JSONArray();
            characters.put(profile(prefs, FEMALE_ID, "Jane", "female", true));
            characters.put(profile(prefs, MALE_ID, "Male Core", "male", false));
            out.put("characters", characters);
            return out.toString();
        } catch (Exception error) {
            return "{\"activeId\":\"female_jane\",\"activeName\":\"Jane\",\"characters\":[]}";
        }
    }
}
