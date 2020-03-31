package kyklab.quiet;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class Prefs {
    private static SharedPreferences pref;
    private static SharedPreferences.Editor editor;

    private Prefs() {
        pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());
        editor = pref.edit();
        editor.apply();
    }

    @SuppressWarnings("SameReturnValue")
    public static Prefs get() {
        return LazyHolder.INSTANCE;
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

    public boolean getFirstLaunch() {
        return pref.getBoolean(Key.FIRST_LAUNCH, true);
    }

    public void setFirstLaunch(boolean b) {
        editor.putBoolean(Key.FIRST_LAUNCH, b).apply();
    }

    public boolean getServiceEnabled() {
        return pref.getBoolean(Key.SERVICE_ENABLED, false);
    }

    public void setServiceEnabled(boolean b) {
        editor.putBoolean(Key.SERVICE_ENABLED, b).apply();
    }

    public boolean getEnableOnHeadset() {
        return pref.getBoolean(Key.ENABLE_ON_HEADSET, false);
    }

    public void setEnableOnHeadset(boolean b) {
        editor.putBoolean(Key.ENABLE_ON_HEADSET, b).apply();
    }

    public boolean getVolumeLevelInNotiIcon() {
        return pref.getBoolean(Key.VOLUME_LEVEL_IN_NOTI_ICON, false);
    }

    public void setVolumeLevelInNotiIcon(boolean b) {
        editor.putBoolean(Key.VOLUME_LEVEL_IN_NOTI_ICON, b).apply();
    }

    public void removePref(String pref) {
        editor.remove(pref).apply();
    }

    private static class LazyHolder {
        static final Prefs INSTANCE = new Prefs();
    }

    public class Key {
        public static final String FIRST_LAUNCH = "first_launch";
        public static final String SERVICE_ENABLED = "service_enabled";
        public static final String ENABLE_ON_HEADSET = "enable_on_headset";
        public static final String VOLUME_LEVEL_IN_NOTI_ICON = "volume_level_in_noti_icon";
    }
}
