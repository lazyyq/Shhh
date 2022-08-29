package kyklab.quiet.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.graphics.*
import android.media.AudioManager
import android.os.*
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toIcon
import kyklab.quiet.*
import kyklab.quiet.ui.MainActivity
import kyklab.quiet.utils.PermissionManager
import kyklab.quiet.utils.Prefs
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.math.max

class VolumeWatcherService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private val TAG = VolumeWatcherService::class.java.simpleName

        private const val PENDING_REQ_CODE_STOP = 0
        private const val PENDING_REQ_CODE_MUTE = 1
        private const val PENDING_REQ_CODE_OPEN_APP = 2
        private const val PENDING_REQ_CODE_FOREGROUND = 3
        private const val PENDING_REQ_CODE_START_FORCE_MUTE = 10
        private const val PENDING_REQ_CODE_STOP_FORCE_MUTE = 11
        private const val PENDING_REQ_CODE_STOP_FORCE_MUTE_USER = 12

        private const val SERVICE_RESTART_DELAY = 3000L
        private const val UPDATE_VOLUME_DELAY = 1000L

        // Check if service is stopped by user or force killed by system
        private var mStopTriggeredByUser = false

        @JvmOverloads
        fun startService(
            context: Context,
            action: String? = Const.Intents.ACTION_START_SERVICE,
            extras: Bundle? = null
        ): Boolean {
            if (action == Const.Intents.ACTION_START_SERVICE &&
                !PermissionManager.checkPermission(context)) {
                Toast.makeText(context, R.string.msg_necessary_permission_missing, Toast.LENGTH_SHORT).show()
                return false
            }

            val intent = Intent(context, VolumeWatcherService::class.java)
            if (action != null) {
                intent.action = action
            }
            if (extras != null) {
                intent.putExtras(extras)
            }
            ContextCompat.startForegroundService(context, intent)

            return true
        }

        fun stopService(context: Context) {
            mStopTriggeredByUser = true
            startService(context, Const.Intents.ACTION_STOP_SERVICE, null)
        }

        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(VolumeWatcherService::class.java)
        }
    }

    private var startForceMuteIntent: PendingIntent? = null
    private var stopForceMuteIntent: PendingIntent? = null

    private lateinit var foregroundNotiBuilder: NotificationCompat.Builder
    private lateinit var outputDeviceNotiBuilder: NotificationCompat.Builder
    private lateinit var volumeLevelNotiBuilder: NotificationCompat.Builder
    private lateinit var forceMuteNotiBuiler: NotificationCompat.Builder
    private lateinit var outputDeviceNotiBuilderOreo: Notification.Builder
    private lateinit var volumeLevelNotiBuilderOreo: Notification.Builder

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private lateinit var globalBroadcastReceiver: BroadcastReceiver

    private lateinit var handler: Handler

    // Show media volume notification
    private lateinit var notifyVolumeTask: (() -> Unit)

    // App settings
    private var enableOnHeadset = false
    private var showNotiOutputDevice = false
    private var showNotiVolumeLevel = false

    // There's an active call
    private var callActive = false

    // Headset connection status
    private var headsetConnected = false

    private var forceMute = false // Force mute mode
    private var vol = 0 // Current media volume

    private inline val isMediaVolumeOn
        get() = vol > 0

    private val currentOutputDevice: String
        get() = getString(if (headsetConnected) R.string.output_headset else R.string.output_speaker)

    @get:DrawableRes
    private val currentOutputDeviceIconRes: Int
        get() = when {
            headsetConnected -> R.drawable.ic_headset
            isMediaVolumeOn -> R.drawable.ic_speaker
            else -> R.drawable.ic_speaker_mute
        }

    override fun onCreate() {
        Log.d(TAG, "onCreate()")

        /*
         * Volume state notification
         */
        // Intent for stopping foreground service
        val stopIntent = Intent(this, VolumeWatcherService::class.java)
        stopIntent.action = Const.Intents.ACTION_STOP_SERVICE
        val pendingStopIntent = PendingIntent.getService(
            this, PENDING_REQ_CODE_STOP, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for killing media volume
        val muteIntent = Intent(this, VolumeWatcherService::class.java)
        muteIntent.action = Const.Intents.ACTION_MUTE_VOLUME
        val pendingMuteIntent = PendingIntent.getService(
            this, PENDING_REQ_CODE_MUTE, muteIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification for actions for output device / volume level notification
        val pendingOpenAppIntent: PendingIntent = PendingIntent.getActivity(
            this, PENDING_REQ_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (isOreoOrHigher) {
            // Notification action is actually compatible with API >= 23(M)
            // so TODO: Consider notification action for API 23~25
            val stopActionOreo = Notification.Action.Builder(
                null,
                getString(R.string.notification_action_stop_service), pendingStopIntent
            ).build()
            val muteActionOreo = Notification.Action.Builder(
                null,
                getString(R.string.notification_action_mute_volume), pendingMuteIntent
            ).build()
            outputDeviceNotiBuilderOreo =
                Notification.Builder(this, Const.Notification.CHANNEL_OUTPUT_DEVICE)
                    .setContentIntent(pendingOpenAppIntent)
                    .addAction(stopActionOreo)
                    .addAction(muteActionOreo)
                    .setOngoing(true)
            volumeLevelNotiBuilderOreo =
                Notification.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
                    .setContentIntent(pendingOpenAppIntent)
                    .addAction(stopActionOreo)
                    .addAction(muteActionOreo)
                    .setOngoing(true)
        } else {
            val stopAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
                null, getString(R.string.notification_action_stop_service), pendingStopIntent
            ).build()
            val muteAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
                null, getString(R.string.notification_action_mute_volume), pendingMuteIntent
            ).build()

            // Notification for output device
            outputDeviceNotiBuilder =
                NotificationCompat.Builder(this, Const.Notification.CHANNEL_OUTPUT_DEVICE)
                    .setContentIntent(pendingOpenAppIntent)
                    .addAction(stopAction)
                    .addAction(muteAction)
                    .setOngoing(true)
            volumeLevelNotiBuilder =
                NotificationCompat.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
                    .setContentIntent(pendingOpenAppIntent)
                    .addAction(stopAction)
                    .addAction(muteAction)
                    .setOngoing(true)
        }

        /*
         * Foreground service notification
         */

        // Builder for foreground notification
        foregroundNotiBuilder =
            NotificationCompat.Builder(this, Const.Notification.CHANNEL_ONGOING)
                .setContentTitle(getString(R.string.notification_foreground_service_title))
                .setSmallIcon(R.drawable.ic_speaker)
        if (isOreoOrHigher) {
            // Notification channels are available for Oreo or higher,
            // so show guidance about disabling notification only for them.

            // Notification for foreground service
            val notificationIntent = Intent(this, MainActivity::class.java)
            //notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            notificationIntent.putExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED, true)
            val pendingIntent = PendingIntent.getActivity(
                this, PENDING_REQ_CODE_FOREGROUND, notificationIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT
            )
            foregroundNotiBuilder
                .setContentText(getString(R.string.notification_foreground_service_text))
                .setContentIntent(pendingIntent)
        }

        /*
         * Force mute mode notification
         */
        val stopForceMuteIntent = Intent(this, VolumeWatcherService::class.java)
        stopForceMuteIntent.action = Const.Intents.ACTION_STOP_FORCE_MUTE_USER
        val pendingStopForceMuteIntent = PendingIntent.getService(
            this, PENDING_REQ_CODE_STOP_FORCE_MUTE_USER, stopForceMuteIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        forceMuteNotiBuiler =
            NotificationCompat.Builder(this, Const.Notification.CHANNEL_FORCE_MUTE)
                .setContentTitle(getString(R.string.notification_force_mute_title))
                .setContentText(getString(R.string.notification_force_mute_text))
                .setStyle(NotificationCompat.BigTextStyle())
                .setSmallIcon(R.drawable.ic_block)
                .setContentIntent(pendingStopForceMuteIntent)
                .setOngoing(true)


        registerReceivers()

        handler = Handler(mainLooper)
        notifyVolumeTask = task@{

            // Hide all notifications during call
            if (callActive) {
                removeOutputDeviceNotification()
                removeVolumeLevelNotification()
                return@task
            }

            // Decide whether to show output device notification
            if (showNotiOutputDevice)
                if (headsetConnected)
                    if (enableOnHeadset) showOutputDeviceNotification()
                    else removeOutputDeviceNotification()
                else showOutputDeviceNotification()
            else removeOutputDeviceNotification()

            // Decide whether to show volume level notification
            if (showNotiVolumeLevel && isMediaVolumeOn)
                if (headsetConnected)
                    if (enableOnHeadset) showVolumeLevelNotification()
                    else removeVolumeLevelNotification()
                else showVolumeLevelNotification()
            else removeVolumeLevelNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var action: String? = null
        if (intent == null) {
            if (Prefs.serviceEnabled) {
                action = Const.Intents.ACTION_START_SERVICE
            }
            if (BuildConfig.DEBUG) {
                val filename =
                    getExternalFilesDir(null).toString() + File.separator + Date() + " NULL INTENT.txt"
                val msg = """Null intent received at ${Date()}
 Service status ${
                    if (Prefs.serviceEnabled) "Enabled" else "Disabled"
                }"""
                try {
                    BufferedWriter(FileWriter(filename)).use { writer -> writer.write(msg) }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            action = intent.action
        }
        Log.d(
            TAG, """
     onStartCommand()
     intent action:$action
     intent extras: ${intent?.extrasToString() ?: "NULL INTENT"}
     flags:$flags
     startId:$startId
     """.trimIndent()
        )
        when (action) {
            Const.Intents.ACTION_START_SERVICE -> {
                Prefs.serviceEnabled = true

                // Initialize status
                updateMediaVolume()
                updateHeadsetStatus()
                updateCallStatus()
                updateForceMuteStatus()
                updateForceMuteAlarms()

                // Notify we started service
                lbm.sendBroadcast(Intent(Const.Intents.ACTION_SERVICE_STARTED))
                // Start foreground service
                enableOnHeadset = Prefs.enableOnHeadset
                showNotiOutputDevice = Prefs.showNotiOutputDevice
                showNotiVolumeLevel = Prefs.showNotiVolLevel
                if (!debug) {
                    Toast.makeText(this, R.string.starting_service, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Starting service, enable_on_headset: $enableOnHeadset," +
                                " show_noti_output_device: $showNotiOutputDevice," +
                                " show_noti_volume_level: $showNotiVolumeLevel",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Start foreground service
                startForeground(Const.Notification.ID_ONGOING, foregroundNotiBuilder.build())

                // Show notification for first time
                updateVolumeNotification()
                Prefs.registerPrefChangeListener(this)
            }
            Const.Intents.ACTION_STOP_SERVICE -> {
                // Stop service button in notification clicked
                Log.d(TAG, "Stopping service on notification click")
                Prefs.serviceEnabled = false
                lbm.sendBroadcast(Intent(Const.Intents.ACTION_SERVICE_STOPPED))
                stopForeground(true)
                stopSelf()
            }
            Const.Intents.ACTION_MUTE_VOLUME -> {
                muteStreamVolume(AudioManager.STREAM_MUSIC)
            }
            Const.Intents.ACTION_START_FORCE_MUTE -> {
                updateForceMuteStatus(true)
                Log.d(TAG, "start alarm triggered")
            }
            Const.Intents.ACTION_STOP_FORCE_MUTE -> {
                updateForceMuteStatus(false)
                Log.d(TAG, "stop alarm triggered")
            }
            Const.Intents.ACTION_STOP_FORCE_MUTE_USER -> {
                updateForceMuteStatus(false)
                Log.d(TAG, "stop by user")
            }
        }
        return START_STICKY
    }

    private fun registerReceivers() {
        // Receiver for volume change, headset connection detection
        val globalIntentFilter = IntentFilter().apply {
            addAction(Const.Intents.ACTION_VOLUME_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            priority = 999
        }
        globalBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Receiver triggered")
                Log.d(
                    TAG,
                    "intent action: ${intent?.action}\nintent extras: ${intent?.extrasToString()}"
                )
                when (intent?.action) {
                    Const.Intents.ACTION_VOLUME_CHANGED -> {
                        val extras = intent.extras
                        if (extras != null &&
                            extras.getInt(Const.Intents.EXTRA_VOLUME_STREAM_TYPE, -1)
                            == AudioManager.STREAM_MUSIC
                        ) {
                            updateMediaVolume(intent)
                            if (forceMute && !headsetConnected && isMediaVolumeOn) {
                                muteStreamVolume(AudioManager.STREAM_MUSIC)
                            } else {
                                updateVolumeNotification()
                            }
                        }
                    }
                    Intent.ACTION_HEADSET_PLUG,
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        updateMediaVolume()
                        updateHeadsetStatus(intent)
                        updateVolumeNotification()

                        // On Android 11, Samsung One UI 3.1, media volume doesn't always seem to be
                        // updated immediately. So check for volume once again after some delay.
                        handler.postDelayed(UPDATE_VOLUME_DELAY) {
                            updateMediaVolume()
                            updateVolumeNotification()
                        }
                    }
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                        updateCallStatus(intent)
                        updateVolumeNotification()
                    }

                    // TODO: Check why this is here
                    Const.Intents.ACTION_UPDATE_FORCE_MUTE_ALARMS -> {
                        updateForceMuteAlarms()
                    }
                }
            }
        }
        registerReceiver(globalBroadcastReceiver, globalIntentFilter)
    }

    private fun updateVolumeNotification() {
        notifyVolumeTask()
        // NotificationManager.notify() seems to get ignored
        // when volume has changed very rapidly.
        // Our workaround is to trigger notification update 1s after last update
        // to ensure that notification icon is up to date.
        handler.removeCallbacks(notifyVolumeTask)
        handler.postDelayed(notifyVolumeTask, UPDATE_VOLUME_DELAY)
    }

    // Size of volume level icon in status bar
    private val volumeLevelIconDefaultSize =
        App.context.resources.getDimension(R.dimen.status_bar_volume_level_icon_size).toInt()

    // Text Paint for drawing text on volume level icon in status bar
    @RequiresApi(Build.VERSION_CODES.O)
    private val volumeLevelIconTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = App.context.resources.getColor(android.R.color.black, null)
        textSize =
            App.context.resources.getDimension(R.dimen.status_bar_volume_level_icon_text_size)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createVolumeLevelIconBitmap(): Bitmap {
        val text = vol.toString()

        // Get size of text to be placed inside icon
        val textRect = Rect()
        volumeLevelIconTextPaint.getTextBounds(text, 0, text.length, textRect)
        val textWidth = textRect.width()
        val textHeight = textRect.height()

        val iconSize = max(volumeLevelIconDefaultSize, textWidth)
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textBottom = (iconSize + textHeight) / 2f
        // The lower the coordinate locates on screen, the bigger its y value
        canvas.drawText(text, iconSize / 2f, textBottom, volumeLevelIconTextPaint)

        return bitmap
    }

    private fun removeOutputDeviceNotification() {
        NotificationManagerCompat.from(this@VolumeWatcherService)
            .cancel(Const.Notification.ID_OUTPUT_DEVICE)
    }

    private fun removeVolumeLevelNotification() {
        NotificationManagerCompat.from(this@VolumeWatcherService)
            .cancel(Const.Notification.ID_VOLUME_LEVEL)
    }

    private fun showOutputDeviceNotification() {
        val title = String.format(
            getString(R.string.notification_output_device_title),
            currentOutputDevice
        )
        val text = if (!showNotiVolumeLevel) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            String.format(
                getString(R.string.notification_unified_text),
                currentOutputDevice, vol
            )
        } else {
            String.format(
                getString(R.string.notification_output_device_text),
                currentOutputDevice
            )
        }
        val notification = if (isOreoOrHigher) {
            outputDeviceNotiBuilderOreo
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(currentOutputDeviceIconRes).build()
        } else {
            outputDeviceNotiBuilder
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(currentOutputDeviceIconRes).build()
        }
        NotificationManagerCompat.from(this)
            .notify(Const.Notification.ID_OUTPUT_DEVICE, notification)
    }

    private fun showVolumeLevelNotification() {
        val title = String.format(getString(R.string.notification_volume_level_title), vol)
        val text = if (!showNotiOutputDevice) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            String.format(
                getString(R.string.notification_unified_text),
                currentOutputDevice, vol
            )
        } else {
            getString(R.string.notification_volume_level_text)
        }
        val notification = if (isOreoOrHigher) {
            volumeLevelNotiBuilderOreo
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(createVolumeLevelIconBitmap().toIcon()).build()
        } else {
            volumeLevelNotiBuilder
                .setContentTitle(title).setContentText(text)
                .setLargeIcon(createVolumeLevelIconBitmap())
                .setSmallIcon(R.drawable.ic_volume_level, vol)
                .build()
        }
        NotificationManagerCompat.from(this)
            .notify(Const.Notification.ID_VOLUME_LEVEL, notification)
    }

    /**
     * Update current media volume value from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from AudioManager instead.
     */
    private fun updateMediaVolume(intent: Intent? = null) {
        if (intent == null) {
            vol = getStreamVolume(AudioManager.STREAM_MUSIC)
        } else {
            val streamType = intent.getIntExtra(Const.Intents.EXTRA_VOLUME_STREAM_TYPE, -1)
            if (streamType == AudioManager.STREAM_MUSIC) {
                vol = intent.getIntExtra(Const.Intents.EXTRA_VOLUME_STREAM_VALUE, -1)
            }
        }
    }

    /**
     * Update headset connection status from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from AudioManager instead.
     */
    private fun updateHeadsetStatus(intent: Intent? = null) {
        if (intent == null) {
            headsetConnected = isHeadsetConnected
        } else {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    headsetConnected = intent.extras?.getInt("state", 0) == 1
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    headsetConnected = intent.extras?.getInt(
                        BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED
                    ) == BluetoothAdapter.STATE_CONNECTED
                }
            }
        }
    }

    /**
     * Update call active status from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from TelephonyManager instead.
     */
    private fun updateCallStatus(intent: Intent? = null) {
        if (intent == null) {
            callActive = isCallActive
        } else {
            if (intent.extras != null) {
                callActive = intent.extras!!.getString(
                    TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE
                ) == TelephonyManager.EXTRA_STATE_OFFHOOK
            }
        }
    }

    /**
     * Set force mute mode status
     *
     * @param enabled New status for force mute mode.
     * If null, automatically set current status based on preferences.
     */
    private fun updateForceMuteStatus(enabled: Boolean? = null) {
        if (enabled != null) {
            forceMute = enabled
        } else {
            val cal = Calendar.getInstance()
            val curHour = cal[Calendar.HOUR_OF_DAY]
            val curMin = cal[Calendar.MINUTE]
            val totalMins = curHour * 60 + curMin
            val forceMuteOn = Prefs.forceMute
            val forceMuteAlwaysOn = Prefs.forceMuteWhen == Prefs.Value.FORCE_MUTE_WHEN_ALWAYS_ON
            val forceMuteFrom = Prefs.forceMuteFrom
            val forceMuteTo = Prefs.forceMuteTo
            Log.d(
                TAG,
                "forceMuteFrom:$forceMuteFrom, forceMuteTo:$forceMuteTo, totalMins:$totalMins"
            )
            val shouldCurrentlyBeActive = when {
                forceMuteFrom < forceMuteTo -> {
                    totalMins in forceMuteFrom until forceMuteTo
                }
                forceMuteFrom > forceMuteTo -> {
                    forceMuteFrom <= totalMins || totalMins < forceMuteTo
                }
                else -> {
                    false
                }
            }
            forceMute = forceMuteOn && (forceMuteAlwaysOn || shouldCurrentlyBeActive)
        }
        Log.d(TAG, "new force_mute status:$forceMute")
        if (forceMute) {
            NotificationManagerCompat.from(this).notify(
                Const.Notification.ID_FORCE_MUTE, forceMuteNotiBuiler.build()
            )
            if (!headsetConnected && isMediaVolumeOn) {
                muteStreamVolume(AudioManager.STREAM_MUSIC)
            }
        } else {
            NotificationManagerCompat.from(this).cancel(Const.Notification.ID_FORCE_MUTE)
        }
    }

    /**
     * Set/unset alarms for start/stopping force mute mode based on preferences
     */
    private fun updateForceMuteAlarms() {
        if (!Prefs.forceMute) {
            cancelForceMuteAlarms()
            return
        }
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        var cal: Calendar
        var intent: Intent
        val start: Int = Prefs.forceMuteFrom
        val end: Int = Prefs.forceMuteTo
        if (start == end) {
            cancelForceMuteAlarms()
        }
        var hours: Int
        var mins: Int
        val secs = 0

        // Set start force mute mode alarm
        hours = start / 60
        mins = start % 60
        cal = Calendar.getInstance()
        cal[Calendar.HOUR_OF_DAY] = hours
        cal[Calendar.MINUTE] = mins
        cal[Calendar.SECOND] = secs
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        intent = Intent(this, VolumeWatcherService::class.java).apply {
            action = Const.Intents.ACTION_START_FORCE_MUTE
            if (debug) {
                putExtra(
                    "triggered",
                    Calendar.getInstance(Locale.getDefault()).time.toString()
                )
                putExtra("target", cal.time.toString())
            }
        }
        startForceMuteIntent = PendingIntent.getService(
            this, PENDING_REQ_CODE_START_FORCE_MUTE, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, startForceMuteIntent
            )
        }
        Log.d(TAG, "registered start alarm for " + cal.time.toString())

        // Set stop force mute mode alarm
        hours = end / 60
        mins = end % 60
        cal = Calendar.getInstance()
        cal[Calendar.HOUR_OF_DAY] = hours
        cal[Calendar.MINUTE] = mins
        cal[Calendar.SECOND] = secs
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        intent = Intent(this, VolumeWatcherService::class.java).apply {
            action = Const.Intents.ACTION_STOP_FORCE_MUTE
            if (debug) {
                putExtra(
                    "triggered",
                    Calendar.getInstance(Locale.getDefault()).time.toString()
                )
                putExtra("target", cal.time.toString())
            }
        }
        stopForceMuteIntent = PendingIntent.getService(
            this, PENDING_REQ_CODE_STOP_FORCE_MUTE, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, stopForceMuteIntent
            )
        }
        Log.d(TAG, "registered stop alarm for " + cal.time.toString())
    }

    private fun cancelForceMuteAlarms() {
        with(getSystemService(ALARM_SERVICE) as AlarmManager) {
            startForceMuteIntent?.let { cancel(it) }
            stopForceMuteIntent?.let { cancel(it) }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(globalBroadcastReceiver)
        Prefs.unregisterPrefChangeListener(this)
        handler.removeCallbacks(notifyVolumeTask)
        cancelForceMuteAlarms()
        with(NotificationManagerCompat.from(this)) {
            cancel(Const.Notification.ID_ONGOING)
            cancel(Const.Notification.ID_OUTPUT_DEVICE)
            cancel(Const.Notification.ID_VOLUME_LEVEL)
            cancel(Const.Notification.ID_FORCE_MUTE)
        }
        Toast.makeText(this, R.string.stopping_service, Toast.LENGTH_SHORT).show()
        if (!mStopTriggeredByUser) {
            // It's not the user that wanted the service to die, so restart it
            scheduleRestart()
        }
        mStopTriggeredByUser = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!mStopTriggeredByUser) {
            // It's not the user that wanted the service to die, so restart it
            scheduleRestart()
        }
        mStopTriggeredByUser = false
        stopSelf()
    }

    /**
     * Restart service after a certain amount of time
     * Original idea from https://wendys.tistory.com/80
     */
    private fun scheduleRestart() {
        if (debug) {
            val filename = getExternalFilesDir(null).toString() + File.separator + Date() + ".txt"
            val msg = "Unwanted service kill at " + Date()
            try {
                BufferedWriter(FileWriter(filename)).use { writer -> writer.write(msg) }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val restartSchedule = System.currentTimeMillis() + SERVICE_RESTART_DELAY
        val intent = Intent(this, ServiceKilledReceiver::class.java)
        intent.action = Const.Intents.ACTION_START_SERVICE
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC_WAKEUP, restartSchedule, pendingIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when {
            key == Prefs.Key.ENABLE_ON_HEADSET -> {
                enableOnHeadset = Prefs.enableOnHeadset
                updateVolumeNotification()
            }
            key == Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE -> {
                showNotiOutputDevice = Prefs.showNotiOutputDevice
                updateVolumeNotification()
            }
            key == Prefs.Key.SHOW_NOTI_VOL_LEVEL -> {
                showNotiVolumeLevel = Prefs.showNotiVolLevel
                updateVolumeNotification()
            }
            key?.contains("force_mute") == true -> {
                Log.d(TAG, "contains force_mute triggered")
                updateForceMuteStatus()
                updateForceMuteAlarms()
            }
        }
    }
}