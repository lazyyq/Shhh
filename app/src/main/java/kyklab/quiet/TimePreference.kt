package kyklab.quiet;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

public class TimePreference extends DialogPreference {

    private int mTime;

    public TimePreference(Context context) {
        super(context, null);
    }

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.dialogPreferenceStyle);
    }

    public TimePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, defStyleAttr);
    }

    public TimePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public int getTime() {
        return mTime;
    }

    public void setTime(int time) {
        mTime = time;
        // Save to Shared Preferences
        persistInt(time);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue,
                                     Object defaultValue) {
        setTime(restorePersistedValue ?
                getPersistedInt(mTime) : (int) defaultValue);
    }

    @Override
    public int getDialogLayoutResource() {
        return R.layout.pref_timepicker;
    }
}
