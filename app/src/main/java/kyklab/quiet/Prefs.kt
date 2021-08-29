package kyklab.quiet

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.PreferenceManager

object Prefs {
    /* Beginning of common stuffs */

    private val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(App.context)
    private var editor = pref.edit()
    private val r = App.context.resources

    fun registerPrefChangeListener(listener: OnSharedPreferenceChangeListener) {
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefChangeListener(listener: OnSharedPreferenceChangeListener) {
        pref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    init {
        editor.apply()
    }

    private inline fun gets(key: String, defValue: String) =
        pref.getString(key, defValue)!!

    private inline fun getss(key: String, defValues: Set<String>) =
        pref.getStringSet(key, defValues)!!

    private inline fun geti(key: String, defValue: Int) =
        pref.getInt(key, defValue)

    private inline fun getl(key: String, defValue: Long) =
        pref.getLong(key, defValue)

    private inline fun getf(key: String, defValue: Float) =
        pref.getFloat(key, defValue)

    private inline fun getb(key: String, defValue: Boolean) =
        pref.getBoolean(key, defValue)

    private inline fun puts(key: String, value: String) =
        editor.putString(key, value).apply()

    private inline fun putss(key: String, values: Set<String>) =
        editor.putStringSet(key, values).apply()

    private inline fun puti(key: String, value: Int) =
        editor.putInt(key, value).apply()

    private inline fun putl(key: String, value: Long) =
        editor.putLong(key, value).apply()

    private inline fun putf(key: String, value: Float) =
        editor.putFloat(key, value).apply()

    private inline fun putb(key: String, value: Boolean) =
        editor.putBoolean(key, value).apply()

    /* End of common stuffs */

    var firstLaunch: Boolean
        get() = getb(Key.FIRST_LAUNCH, Key.FIRST_LAUNCH_DEF)
        set(value) = putb(Key.FIRST_LAUNCH, value)

    var serviceEnabled: Boolean
        get() = getb(Key.SERVICE_ENABLED, Key.SERVICE_ENABLED_DEF)
        set(value) = putb(Key.SERVICE_ENABLED, value)

    var enableOnHeadset: Boolean
        get() = getb(Key.ENABLE_ON_HEADSET, Key.ENABLE_ON_HEADSET_DEF)
        set(value) = putb(Key.ENABLE_ON_HEADSET, value)

    var showNotiOutputDevice: Boolean
        get() = getb(Key.SHOW_NOTI_OUTPUT_DEVICE, Key.SHOW_NOTI_OUTPUT_DEVICE_DEF)
        set(value) = putb(Key.SHOW_NOTI_OUTPUT_DEVICE, value)

    var showNotiVolLevel: Boolean
        get() = getb(Key.SHOW_NOTI_VOL_LEVEL, Key.SHOW_NOTI_VOL_LEVEL_DEF)
        set(value) = putb(Key.SHOW_NOTI_VOL_LEVEL, value)

    var showAd: Boolean
        get() = getb(Key.SHOW_AD, Key.SHOW_AD_DEF)
        set(value) = putb(Key.SHOW_AD, value)

    var forceMute: Boolean
        get() = getb(Key.FORCE_MUTE, Key.FORCE_MUTE_DEF)
        set(value) = putb(Key.FORCE_MUTE, value)

    var forceMuteWhen: String
        get() = gets(Key.FORCE_MUTE_WHEN, Key.FORCE_MUTE_WHEN_DEF)
        set(value) = puts(Key.FORCE_MUTE_WHEN, value)

    var forceMuteFrom: Int
        get() = geti(Key.FORCE_MUTE_FROM, Key.FORCE_MUTE_FROM_DEF)
        set(value) = puti(Key.FORCE_MUTE_FROM, value)

    var forceMuteTo: Int
        get() = geti(Key.FORCE_MUTE_TO, Key.FORCE_MUTE_TO_DEF)
        set(value) = puti(Key.FORCE_MUTE_TO, value)


    object Key {
        val FIRST_LAUNCH = r.getString(R.string.pref_key_first_launch)
        val SERVICE_ENABLED = r.getString(R.string.pref_key_service_enabled)
        val ENABLE_ON_HEADSET = r.getString(R.string.pref_key_enable_on_headset)
        val SHOW_NOTI_OUTPUT_DEVICE = r.getString(R.string.pref_key_show_noti_output_device)
        val SHOW_NOTI_VOL_LEVEL = r.getString(R.string.pref_key_show_noti_vol_level)
        val OPEN_NOTI_SETTINGS = r.getString(R.string.pref_key_open_notification_settings)
        val SHOW_AD = r.getString(R.string.pref_key_show_ad)
        val FORCE_MUTE = r.getString(R.string.pref_key_force_mute)
        val FORCE_MUTE_WHEN = r.getString(R.string.pref_key_force_mute_when)
        val FORCE_MUTE_FROM = r.getString(R.string.pref_key_force_mute_from)
        val FORCE_MUTE_TO = r.getString(R.string.pref_key_force_mute_to)

        val FIRST_LAUNCH_DEF = r.getBoolean(R.bool.pref_key_first_launch_default)
        val SERVICE_ENABLED_DEF = r.getBoolean(R.bool.pref_key_service_enabled_default)
        val ENABLE_ON_HEADSET_DEF = r.getBoolean(R.bool.pref_key_enable_on_headset_default)
        val SHOW_NOTI_OUTPUT_DEVICE_DEF =
            r.getBoolean(R.bool.pref_key_show_noti_output_device_default)
        val SHOW_NOTI_VOL_LEVEL_DEF = r.getBoolean(R.bool.pref_key_show_noti_vol_level_default)
        val SHOW_AD_DEF = r.getBoolean(R.bool.pref_key_show_ad_default)
        val FORCE_MUTE_DEF = r.getBoolean(R.bool.pref_key_force_mute_default)
        val FORCE_MUTE_WHEN_DEF = r.getString(R.string.pref_key_force_mute_when_default)
        val FORCE_MUTE_FROM_DEF = r.getInteger(R.integer.pref_key_force_mute_from_default)
        val FORCE_MUTE_TO_DEF = r.getInteger(R.integer.pref_key_force_mute_to_default)
    }

    object Value {
        val FORCE_MUTE_WHEN_ALWAYS_ON = r.getStringArray(R.array.force_mute_when_values)[0]
        val FORCE_MUTE_WHEN_SCHEDULED = r.getStringArray(R.array.force_mute_when_values)[1]
    }
}