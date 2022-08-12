package kyklab.quiet.utils

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TimePicker
import androidx.preference.PreferenceDialogFragmentCompat
import kyklab.quiet.R

class TimePreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    private var mTimePicker: TimePicker? = null

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        mTimePicker = view.findViewById(R.id.timePicker)
        checkNotNull(mTimePicker) { "Dialog view must contain a TimePicker with id 'timePicker'" }
        var totalMins: Int? = null
        val preference = preference
        if (preference is TimePreference) {
            totalMins = preference.time
        }
        if (totalMins != null) {
            val hours = totalMins / 60
            val minutes = totalMins % 60
            val is24hour = DateFormat.is24HourFormat(context)
            mTimePicker!!.setIs24HourView(is24hour)
            mTimePicker!!.currentHour = hours
            mTimePicker!!.currentMinute = minutes
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val hours: Int
            val minutes: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hours = mTimePicker!!.hour
                minutes = mTimePicker!!.minute
            } else {
                hours = mTimePicker!!.currentHour
                minutes = mTimePicker!!.currentMinute
            }
            val totalMins = hours * 60 + minutes
            val preference = preference
            if (preference is TimePreference) {
                if (preference.callChangeListener(totalMins)) {
                    preference.time = totalMins
                }
            }
        }
    }

    companion object {
        fun newInstance(key: String?): TimePreferenceDialogFragmentCompat {
            val fragment = TimePreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}