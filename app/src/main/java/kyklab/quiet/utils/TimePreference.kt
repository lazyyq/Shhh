package kyklab.quiet.utils

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.DialogPreference
import kyklab.quiet.R

class TimePreference : DialogPreference {
    private var mTime = 0

    constructor(context: Context) :
            super(context)

    constructor(context: Context, attrs: AttributeSet) :
            super(context, attrs, R.attr.dialogPreferenceStyle)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    // Save to Shared Preferences
    var time: Int
        get() = mTime
        set(time) {
            mTime = time
            // Save to Shared Preferences
            persistInt(time)
        }

    override fun onGetDefaultValue(a: TypedArray?, index: Int): Any {
        return a?.getInt(index, 0) ?: 0
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        time = if (restorePersistedValue) getPersistedInt(mTime) else defaultValue as Int
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.pref_timepicker
    }
}