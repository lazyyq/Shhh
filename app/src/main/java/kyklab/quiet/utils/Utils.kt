package kyklab.quiet.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kyklab.quiet.BuildConfig

private const val TAG = "Utils"

val debug = BuildConfig.DEBUG
val isOreoOrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

val Context.activityManager get() = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager
val Context.telephonyManager get() = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
val Context.powerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager

fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    return activityManager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == serviceClass.name
    }
}

val Context.isHeadsetConnected
    get() = this.isWiredHeadsetConnected || this.isBluetoothHeadsetConnected

val Context.isWiredHeadsetConnected
    get() = audioManager.isWiredHeadsetOn

val Context.isBluetoothHeadsetConnected
    get() = audioManager.isBluetoothA2dpOn

val Context.isCallActive
    get() = telephonyManager.callState == TelephonyManager.CALL_STATE_OFFHOOK

fun Context.getStreamVolume(streamType: Int): Int {
    return audioManager.getStreamVolume(streamType)
}

fun Context.muteStreamVolume(streamType: Int) {
    audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
}

val Context.isDarkMode
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            == Configuration.UI_MODE_NIGHT_YES)

fun Handler.postDelayed(delayMillis: Long, r: Runnable) = postDelayed(r, delayMillis)

val Context.lbm: LocalBroadcastManager
    get() = LocalBroadcastManager.getInstance(this)

fun LocalBroadcastManager.sendBroadcast(vararg intents: Intent) {
    intents.forEach { sendBroadcast(it) }
}

fun getBitmapWithText(
    context: Context, width: Int, height: Int,
    @DrawableRes bgVectorDrawableId: Int, @ColorInt bgColor: Int,
    text: String, textSize: Float, @ColorInt textColor: Int
): Bitmap? {
    // Draw vector drawable on canvas
    val drawable = ContextCompat.getDrawable(context, bgVectorDrawableId)
    if (drawable == null) {
        Log.e(TAG, "getBitmapWithText(): drawable is null")
        return null
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    DrawableCompat.setTint(drawable, bgColor) // 40% opacity
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    // Text options
    val textPaint = Paint().apply {
        style = Paint.Style.FILL
        color = textColor
        this.textSize = dpToPx(context, textSize).toFloat()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Draw text on canvas
    val rect = Rect()
    textPaint.getTextBounds(text, 0, text.length, rect)
    canvas.drawText(
        text, bitmap.width / 2f,
        (bitmap.height + Math.abs(rect.top - rect.bottom)) / 2f, textPaint
    )
    return bitmap
}

fun dpToPx(context: Context, dp: Number): Int {
    val dm = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), dm).toInt()
}

fun Intent.extrasToString(): String {
    val sb = StringBuilder()
    extras?.keySet()?.forEach { key ->
        sb.append("$key:${extras!![key]}, ")
    }
    return sb.toString()
}

fun Context.openAppSettings() {
    val uri = Uri.fromParts("package", this@openAppSettings.packageName, null)
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = uri
    }
    startActivity(intent)
}

val Context.isBatteryOptimizationEnabled
    @RequiresApi(Build.VERSION_CODES.M)
    get() = !powerManager.isIgnoringBatteryOptimizations(packageName)

@RequiresApi(Build.VERSION_CODES.M)
fun Context.openIgnoreBatteryOptimizationsSettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    startActivity(intent)
}