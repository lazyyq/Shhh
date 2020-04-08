package kyklab.quiet;

import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Set;

public class Prefs {
    private static SharedPreferences pref;
    private static SharedPreferences.Editor editor;

    private static HashMap<String, Object> map;

    private Prefs() {
        pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());
        editor = pref.edit();
        editor.apply();

        map = new HashMap<>();
        map.put(Key.FIRST_LAUNCH, Key.FIRST_LAUNCH_DEF);
        map.put(Key.SERVICE_ENABLED, Key.SERVICE_ENABLED_DEF);
        map.put(Key.ENABLE_ON_HEADSET, Key.ENABLE_ON_HEADSET_DEF);
        map.put(Key.VOLUME_LEVEL_IN_NOTI_ICON, Key.VOLUME_LEVEL_IN_NOTI_ICON_DEF);
    }

    @SuppressWarnings("SameReturnValue")
    public static Prefs get() {
        return LazyHolder.INSTANCE;
    }

    public String getString(String key) {
        return pref.getString(key, (String) map.get(key));
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key) {
        return pref.getStringSet(key, (Set<String>) map.get(key));
    }

    public int getInt(String key) {
        return pref.getInt(key, (int) map.get(key));
    }

    public long getLong(String key) {
        return pref.getLong(key, (long) map.get(key));
    }

    public float getFloat(String key) {
        return pref.getFloat(key, (float) map.get(key));
    }

    public boolean getBoolean(String key) {
        return pref.getBoolean(key, (boolean) map.get(key));
    }

    public void setString(String key, String s) {
        editor.putString(key, s).apply();
    }

    public void setStringSet(String key, Set<String> ss) {
        editor.putStringSet(key, ss).apply();
    }

    public void setInt(String key, int i) {
        editor.putInt(key, i).apply();
    }

    public void setLong(String key, long l) {
        editor.putLong(key, l).apply();
    }

    public void setFloat(String key, float f) {
        editor.putFloat(key, f).apply();
    }

    public void setBoolean(String key, boolean b) {
        editor.putBoolean(key, b).apply();
    }

    public static void registerPrefChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener) {
        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                .registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterPrefChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener) {
        PreferenceManager.getDefaultSharedPreferences(App.getContext())
                .unregisterOnSharedPreferenceChangeListener(listener);
    }

    public void removePref(String pref) {
        editor.remove(pref).apply();
    }

    private static class LazyHolder {
        static final Prefs INSTANCE = new Prefs();
    }

    public static class Key {
        private static final Resources r = App.getContext().getResources();

        public static final String FIRST_LAUNCH = r.getString(R.string.pref_key_first_launch);
        public static final String SERVICE_ENABLED = r.getString(R.string.pref_key_service_enabled);
        public static final String ENABLE_ON_HEADSET = r.getString(R.string.pref_key_enable_on_headset);
        public static final String VOLUME_LEVEL_IN_NOTI_ICON = r.getString(R.string.pref_key_volume_level_in_noti_icon);
        private static final boolean FIRST_LAUNCH_DEF = r.getBoolean(R.bool.pref_key_first_launch_default);
        private static final boolean SERVICE_ENABLED_DEF = r.getBoolean(R.bool.pref_key_service_enabled_default);
        private static final boolean ENABLE_ON_HEADSET_DEF = r.getBoolean(R.bool.pref_key_enable_on_headset_default);
        private static final boolean VOLUME_LEVEL_IN_NOTI_ICON_DEF = r.getBoolean(R.bool.pref_key_volume_level_in_noti_icon_default);
    }
}
