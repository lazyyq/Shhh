package kyklab.quiet;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.kennyc.textdrawable.TextDrawable;
import com.kennyc.textdrawable.TextDrawableBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static kyklab.quiet.Utils.extrasToString;
import static kyklab.quiet.Utils.getStreamVolume;
import static kyklab.quiet.Utils.isCallActive;
import static kyklab.quiet.Utils.isDebug;
import static kyklab.quiet.Utils.isHeadsetConnected;
import static kyklab.quiet.Utils.isOreoOrHigher;
import static kyklab.quiet.Utils.muteStreamVolume;

public class VolumeWatcherService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "VolumeWatcherService";

    private static final int PENDING_REQ_CODE_STOP = 0,
            PENDING_REQ_CODE_MUTE = 1,
            PENDING_REQ_CODE_OPEN_APP = 2,
            PENDING_REQ_CODE_FOREGROUND = 3,
            PENDING_REQ_CODE_START_FORCE_MUTE = 10,
            PENDING_REQ_CODE_STOP_FORCE_MUTE = 11,
            PENDING_REQ_CODE_STOP_FORCE_MUTE_USER = 12;

    private static final int SERVICE_RESTART_DELAY = 3000;

    // Check if service is stopped by user or force killed by system
    private static boolean mStopTriggeredByUser = false;

    private PendingIntent mStartForceMuteIntent, mStopForceMuteIntent;

    private NotificationCompat.Builder mForegroundNotiBuilder,
            mOutputDeviceNotiBuilder, mVolumeLevelNotiBuilder,
            mForceMuteNotiBuiler;
    private Notification.Builder mOutputDeviceNotiBuilderOreo, mVolumeLevelNotiBuilderOreo;

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private BroadcastReceiver mReceiver;

    private Handler mHandler;
    private Runnable mNotifyVolumeRunnable; // Show media volume notification

    private boolean
            mEnableOnHeadset, mShowNotiOutputDevice, mShowNotiVolumeLevel, // App settings
            mCallActive, // There's an active call
            mHeadsetConnected, // Headset connection status
            mForceMute = false; // Force mute mode

    private int mVol; // Current media volume

    public VolumeWatcherService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        /*
         * Volume state notification
         */
        // Intent for stopping foreground service
        Intent stopIntent = new Intent(this, VolumeWatcherService.class);
        stopIntent.setAction(Const.Intent.ACTION_STOP_SERVICE);
        PendingIntent pendingStopIntent =
                PendingIntent.getService(this, PENDING_REQ_CODE_STOP, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for killing media volume
        Intent muteIntent = new Intent(this, VolumeWatcherService.class);
        muteIntent.setAction(Const.Intent.ACTION_MUTE_VOLUME);
        PendingIntent pendingMuteIntent =
                PendingIntent.getService(this, PENDING_REQ_CODE_MUTE, muteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Notification for actions for output device / volume level notification
        PendingIntent pendingOpenAppIntent =
                PendingIntent.getActivity(this, PENDING_REQ_CODE_OPEN_APP,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        if (isOreoOrHigher()) {
            // Notification action is actually compatible with API >= 23(M)
            // so TODO: Consider notification action for API 23~25
            Notification.Action stopActionOreo = new Notification.Action.Builder(null,
                    getString(R.string.notification_action_stop_service), pendingStopIntent)
                    .build();
            Notification.Action muteActionOreo = new Notification.Action.Builder(null,
                    getString(R.string.notification_action_mute_volume), pendingMuteIntent)
                    .build();

            mOutputDeviceNotiBuilderOreo =
                    new Notification.Builder(this, Const.Notification.CHANNEL_OUTPUT_DEVICE)
                            .setContentIntent(pendingOpenAppIntent)
                            .addAction(stopActionOreo)
                            .addAction(muteActionOreo)
                            .setOngoing(true);
            mVolumeLevelNotiBuilderOreo =
                    new Notification.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
                            .setContentIntent(pendingOpenAppIntent)
                            .addAction(stopActionOreo)
                            .addAction(muteActionOreo)
                            .setOngoing(true);
        } else {
            NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(null,
                    getString(R.string.notification_action_stop_service), pendingStopIntent)
                    .build();
            NotificationCompat.Action muteAction = new NotificationCompat.Action.Builder(null,
                    getString(R.string.notification_action_mute_volume), pendingMuteIntent)
                    .build();

            // Notification for output device
            mOutputDeviceNotiBuilder =
                    new NotificationCompat.Builder(this, Const.Notification.CHANNEL_OUTPUT_DEVICE)
                            .setContentIntent(pendingOpenAppIntent)
                            .addAction(stopAction)
                            .addAction(muteAction)
                            .setOngoing(true);
            mVolumeLevelNotiBuilder =
                    new NotificationCompat.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
                            .setContentIntent(pendingOpenAppIntent)
                            .addAction(stopAction)
                            .addAction(muteAction)
                            .setOngoing(true);
        }

        /*
         * Foreground service notification
         */

        // Builder for foreground notification
        mForegroundNotiBuilder =
                new NotificationCompat.Builder(this, Const.Notification.CHANNEL_ONGOING)
                        .setContentTitle(getString(R.string.notification_foreground_service_title))
                        .setSmallIcon(R.drawable.ic_speaker);
        if (isOreoOrHigher()) {
            // Notification channels are available for Oreo or higher,
            // so show guidance about disabling notification only for them.

            // Notification for foreground service
            Intent notificationIntent = new Intent(this, MainActivity.class);
            //notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            notificationIntent.putExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, true);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, PENDING_REQ_CODE_FOREGROUND, notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            mForegroundNotiBuilder
                    .setContentText(getString(R.string.notification_foreground_service_text))
                    .setContentIntent(pendingIntent);
        }

        /*
         * Force mute mode notification
         */
        Intent stopForceMuteIntent = new Intent(this, VolumeWatcherService.class);
        stopForceMuteIntent.setAction(Const.Intent.ACTION_STOP_FORCE_MUTE_USER);
        PendingIntent pendingStopForceMuteIntent =
                PendingIntent.getService(this, PENDING_REQ_CODE_STOP_FORCE_MUTE_USER,
                        stopForceMuteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mForceMuteNotiBuiler =
                new NotificationCompat.Builder(this, Const.Notification.CHANNEL_FORCE_MUTE)
                        .setContentTitle(getString(R.string.notification_force_mute_title))
                        .setContentText(getString(R.string.notification_force_mute_text))
                        .setStyle(new NotificationCompat.BigTextStyle())
                        .setSmallIcon(R.drawable.ic_block)
                        .setContentIntent(pendingStopForceMuteIntent)
                        .setOngoing(true);


        // Receiver for volume change, headset connection detection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Const.Intent.ACTION_VOLUME_CHANGED);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Receiver triggered");
                Log.d(TAG, "intent action: " + action + "\nintent extras: " + extrasToString(intent));
                if (TextUtils.equals(action, Const.Intent.ACTION_VOLUME_CHANGED)) {
                    Bundle extras = intent.getExtras();
                    if (extras != null &&
                            extras.getInt(Const.Intent.EXTRA_VOLUME_STREAM_TYPE, -1)
                                    == AudioManager.STREAM_MUSIC) {
                        updateMediaVolume(intent);
                        if (mForceMute && !mHeadsetConnected && isMediaVolumeOn()) {
                            Utils.muteStreamVolume(
                                    VolumeWatcherService.this, AudioManager.STREAM_MUSIC);
                        } else {
                            updateVolumeNotification();
                        }
                    }

                } else if (TextUtils.equals(action, Intent.ACTION_HEADSET_PLUG) ||
                        TextUtils.equals(action, BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                    updateMediaVolume(null);
                    updateHeadsetStatus(intent);
                    updateVolumeNotification();

                } else if (TextUtils.equals(action, TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    updateCallStatus(intent);
                    updateVolumeNotification();

                } else if (TextUtils.equals(action, Const.Intent.ACTION_UPDATE_FORCE_MUTE_ALARMS)) {
                    updateForceMuteAlarms();

                }
            }
        };
        registerReceiver(mReceiver, intentFilter);

        mHandler = new Handler(Looper.getMainLooper());
        mNotifyVolumeRunnable = () -> {
            // Hide all notifications during call
            if (mCallActive) {
                removeOutputDeviceNotification();
                removeVolumeLevelNotification();
                return;
            }

            // Decide whether to show output device notification
            if (mShowNotiOutputDevice)
                if (mHeadsetConnected)
                    if (mEnableOnHeadset) showOutputDeviceNotification();
                    else removeOutputDeviceNotification();
                else showOutputDeviceNotification();
            else removeOutputDeviceNotification();

            // Decide whether to show volume level notification
            if (mShowNotiVolumeLevel && isMediaVolumeOn())
                if (mHeadsetConnected)
                    if (mEnableOnHeadset) showVolumeLevelNotification();
                    else removeVolumeLevelNotification();
                else showVolumeLevelNotification();
            else removeVolumeLevelNotification();
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand()" +
                "\nintent action:" + action + "\nintent extras: " + extrasToString(intent) +
                "\nflags:" + flags + "\nstartId:" + startId);

        if (TextUtils.equals(action, Const.Intent.ACTION_START_SERVICE)) {
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, true);

            // Initialize status
            updateMediaVolume(null);
            updateHeadsetStatus(null);
            updateCallStatus(null);
            updateForceMuteStatus(null);
            updateForceMuteAlarms();

            // Notify we started service
            sendBroadcast(new Intent(Const.Intent.ACTION_SERVICE_STARTED));
            // Start foreground service
            mEnableOnHeadset = Prefs.get().getBoolean(Prefs.Key.ENABLE_ON_HEADSET);
            mShowNotiOutputDevice = Prefs.get().getBoolean(Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE);
            mShowNotiVolumeLevel = Prefs.get().getBoolean(Prefs.Key.SHOW_NOTI_VOL_LEVEL);
            if (!isDebug()) {
                Toast.makeText(this, R.string.starting_service, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Starting service, enable_on_headset: " + mEnableOnHeadset +
                        ", show_noti_output_device: " + mShowNotiOutputDevice +
                        ", show_noti_volume_level: " + mShowNotiVolumeLevel, Toast.LENGTH_SHORT).show();
            }

            // Start foreground service
            startForeground(Const.Notification.ID_ONGOING, mForegroundNotiBuilder.build());

            // Show notification for first time
            updateVolumeNotification();

            Prefs.registerPrefChangeListener(this);

        } else if (TextUtils.equals(action, Const.Intent.ACTION_STOP_SERVICE)) {
            // Stop service button in notification clicked
            Log.d(TAG, "Stopping service on notification click");
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, false);
            sendBroadcast(new Intent(Const.Intent.ACTION_SERVICE_STOPPED));
            stopForeground(true);
            stopSelf();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_MUTE_VOLUME)) {
            muteStreamVolume(this, AudioManager.STREAM_MUSIC);

        } else if (TextUtils.equals(action, Const.Intent.ACTION_START_FORCE_MUTE)) {
            updateForceMuteStatus(true);
            Log.d(TAG, "start alarm triggered");

        } else if (TextUtils.equals(action, Const.Intent.ACTION_STOP_FORCE_MUTE)) {
            updateForceMuteStatus(false);
            Log.d(TAG, "stop alarm triggered");

        } else if (TextUtils.equals(action, Const.Intent.ACTION_STOP_FORCE_MUTE_USER)) {
            updateForceMuteStatus(false);
            Log.d(TAG, "stop by user");

        }

        return START_STICKY;
    }

    private void updateVolumeNotification() {
        mNotifyVolumeRunnable.run();
        // NotificationManager.notify() seems to get ignored
        // when volume has changed very rapidly.
        // Our workaround is to trigger notification update 1s after last update
        // to ensure that notification icon is up to date.
        mHandler.removeCallbacks(mNotifyVolumeRunnable);
        mHandler.postDelayed(mNotifyVolumeRunnable, 1000);
    }

    private boolean isMediaVolumeOn() {
        return mVol > 0;
    }

    private void removeOutputDeviceNotification() {
        NotificationManagerCompat.from(VolumeWatcherService.this)
                .cancel(Const.Notification.ID_OUTPUT_DEVICE);
    }

    private void removeVolumeLevelNotification() {
        NotificationManagerCompat.from(VolumeWatcherService.this)
                .cancel(Const.Notification.ID_VOLUME_LEVEL);
    }

    private void showOutputDeviceNotification() {
        String title = String.format(getString(R.string.notification_output_device_title),
                getCurrentOutputDevice());
        String text;
        if (!mShowNotiVolumeLevel) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            text = String.format(getString(R.string.notification_unified_text),
                    getCurrentOutputDevice(), mVol);
        } else {
            text = String.format(getString(R.string.notification_output_device_text),
                    getCurrentOutputDevice());
        }

        Notification notification;
        if (isOreoOrHigher()) {
            notification = mOutputDeviceNotiBuilderOreo
                    .setContentTitle(title).setContentText(text)
                    .setSmallIcon(getCurrentOutputDeviceIconRes()).build();
        } else {
            notification = mOutputDeviceNotiBuilder
                    .setContentTitle(title).setContentText(text)
                    .setSmallIcon(getCurrentOutputDeviceIconRes()).build();
        }

        NotificationManagerCompat.from(this).notify(Const.Notification.ID_OUTPUT_DEVICE, notification);
    }

    private void showVolumeLevelNotification() {
        String title = String.format(getString(R.string.notification_volume_level_title), mVol);
        String text;
        if (!mShowNotiOutputDevice) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            text = String.format(getString(R.string.notification_unified_text),
                    getCurrentOutputDevice(), mVol);
        } else {
            text = getString(R.string.notification_volume_level_text);
        }

        Notification notification;
        if (isOreoOrHigher()) {
            Icon smallIcon = getVolumeLevelIcon(mVol);
            notification = mVolumeLevelNotiBuilderOreo
                    .setContentTitle(title).setContentText(text)
                    .setSmallIcon(smallIcon).build();
        } else {
            notification = mVolumeLevelNotiBuilder
                    .setContentTitle(title).setContentText(text)
                    .setLargeIcon(getVolumeLevelBitmap(mVol))
                    .setSmallIcon(R.drawable.ic_volume_level, mVol)
                    .build();
        }

        NotificationManagerCompat.from(this).notify(Const.Notification.ID_VOLUME_LEVEL, notification);
    }

    /**
     * Update current media volume value from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from AudioManager instead.
     */
    private void updateMediaVolume(@Nullable Intent intent) {
        if (intent == null) {
            mVol = getStreamVolume(this, AudioManager.STREAM_MUSIC);
        } else {
            int streamType = intent.getIntExtra(Const.Intent.EXTRA_VOLUME_STREAM_TYPE, -1);
            if (streamType == AudioManager.STREAM_MUSIC) {
                mVol = intent.getIntExtra(Const.Intent.EXTRA_VOLUME_STREAM_VALUE, -1);
            }
        }
    }

    /**
     * Update headset connection status from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from AudioManager instead.
     */
    private void updateHeadsetStatus(@Nullable Intent intent) {
        if (intent == null) {
            mHeadsetConnected = isHeadsetConnected(this);
        } else {
            String action = intent.getAction();
            Bundle extras = intent.getExtras();
            if (TextUtils.equals(action, Intent.ACTION_HEADSET_PLUG)) {
                mHeadsetConnected = extras != null && extras.getInt("state", 0) == 1;
            } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                mHeadsetConnected = extras != null &&
                        extras.getInt(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                                BluetoothAdapter.STATE_DISCONNECTED) ==
                                BluetoothAdapter.STATE_CONNECTED;
            }
        }
    }

    /**
     * Update call active status from intent received.
     *
     * @param intent Intent passed to receiver. If null, get value from TelephonyManager instead.
     */
    private void updateCallStatus(@Nullable Intent intent) {
        if (intent == null) {
            mCallActive = isCallActive(this);
        } else {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mCallActive = TextUtils.equals(extras.getString(
                        TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE),
                        TelephonyManager.EXTRA_STATE_OFFHOOK);
            }
        }
    }

    /**
     * Set force mute mode status
     *
     * @param enabled New status for force mute mode.
     *                If null, automatically set current status based on preferences.
     */
    private void updateForceMuteStatus(@Nullable Boolean enabled) {
        if (enabled != null) {
            mForceMute = enabled;
        } else {
            Calendar cal = Calendar.getInstance();
            int curHour = cal.get(Calendar.HOUR_OF_DAY), curMin = cal.get(Calendar.MINUTE);
            int totalMins = curHour * 60 + curMin;
            boolean forceMuteOn = Prefs.get().getBoolean(Prefs.Key.FORCE_MUTE);
            boolean forceMuteAlwaysOn = Prefs.get().getString(Prefs.Key.FORCE_MUTE_WHEN)
                    .equals(Prefs.Value.FORCE_MUTE_WHEN_ALWAYS_ON);
            int forceMuteFrom = Prefs.get().getInt(Prefs.Key.FORCE_MUTE_FROM),
                    forceMuteTo = Prefs.get().getInt(Prefs.Key.FORCE_MUTE_TO);

            Log.d(TAG, "forceMuteFrom:" + forceMuteFrom + ", forceMuteTo:" + forceMuteTo + ", totalMins:" + totalMins);

            boolean shouldCurrentlyBeActive;
            if (forceMuteFrom < forceMuteTo) {
                shouldCurrentlyBeActive = forceMuteFrom <= totalMins && totalMins < forceMuteTo;
            } else if (forceMuteFrom > forceMuteTo) {
                shouldCurrentlyBeActive = forceMuteFrom <= totalMins || totalMins < forceMuteTo;
            } else {
                shouldCurrentlyBeActive = false;
            }

            mForceMute = forceMuteOn && (forceMuteAlwaysOn || shouldCurrentlyBeActive);
        }
        Log.d(TAG, "new force_mute status:" + mForceMute);
        if (mForceMute) {
            NotificationManagerCompat.from(this).notify(
                    Const.Notification.ID_FORCE_MUTE, mForceMuteNotiBuiler.build());
            if (!mHeadsetConnected && isMediaVolumeOn()) {
                muteStreamVolume(this, AudioManager.STREAM_MUSIC);
            }
        } else {
            NotificationManagerCompat.from(this).cancel(Const.Notification.ID_FORCE_MUTE);
        }
    }

    /**
     * Set/unset alarms for start/stopping force mute mode based on preferences
     */
    private void updateForceMuteAlarms() {
        if (!Prefs.get().getBoolean(Prefs.Key.FORCE_MUTE)) {
            cancelForceMuteAlarms();
            return;
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Calendar cal;
        Intent intent;
        int start = Prefs.get().getInt(Prefs.Key.FORCE_MUTE_FROM);
        int end = Prefs.get().getInt(Prefs.Key.FORCE_MUTE_TO);
        if (start == end) {
            cancelForceMuteAlarms();
        }

        int hours, mins, secs = 0;

        // Set start force mute mode alarm
        hours = start / 60;
        mins = start % 60;
        cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hours);
        cal.set(Calendar.MINUTE, mins);
        cal.set(Calendar.SECOND, secs);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        intent = new Intent(this, VolumeWatcherService.class);
        intent.setAction(Const.Intent.ACTION_START_FORCE_MUTE);
        if (isDebug()) {
            intent.putExtra("triggered", Calendar.getInstance(Locale.getDefault()).getTime().toString());
            intent.putExtra("target", cal.getTime().toString());
        }
        mStartForceMuteIntent = PendingIntent.getService(this,
                PENDING_REQ_CODE_START_FORCE_MUTE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, mStartForceMuteIntent);

        Log.d(TAG, "registered start alarm for " + cal.getTime().toString());

        // Set stop force mute mode alarm
        hours = end / 60;
        mins = end % 60;
        cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hours);
        cal.set(Calendar.MINUTE, mins);
        cal.set(Calendar.SECOND, secs);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        intent = new Intent(this, VolumeWatcherService.class);
        intent.setAction(Const.Intent.ACTION_STOP_FORCE_MUTE);
        if (isDebug()) {
            intent.putExtra("triggered", Calendar.getInstance(Locale.getDefault()).getTime().toString());
            intent.putExtra("target", cal.getTime().toString());
        }
        mStopForceMuteIntent = PendingIntent.getService(this,
                PENDING_REQ_CODE_STOP_FORCE_MUTE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, mStopForceMuteIntent);

        Log.d(TAG, "registered stop alarm for " + cal.getTime().toString());
    }

    private void cancelForceMuteAlarms() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (mStartForceMuteIntent != null) {
                alarmManager.cancel(mStartForceMuteIntent);
            }
            if (mStopForceMuteIntent != null) {
                alarmManager.cancel(mStopForceMuteIntent);
            }
        }
    }

    private String getCurrentOutputDevice() {
        return getString(mHeadsetConnected ? R.string.output_headset : R.string.output_speaker);
    }

    @DrawableRes
    private int getCurrentOutputDeviceIconRes() {
        return mHeadsetConnected ? R.drawable.ic_headset :
                isMediaVolumeOn() ? R.drawable.ic_speaker : R.drawable.ic_speaker_mute;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        Prefs.unregisterPrefChangeListener(this);
        mHandler.removeCallbacks(mNotifyVolumeRunnable);
        cancelForceMuteAlarms();
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.cancel(Const.Notification.ID_ONGOING);
        manager.cancel(Const.Notification.ID_OUTPUT_DEVICE);
        manager.cancel(Const.Notification.ID_VOLUME_LEVEL);
        manager.cancel(Const.Notification.ID_FORCE_MUTE);
        Toast.makeText(this, R.string.stopping_service, Toast.LENGTH_SHORT).show();

        if (!mStopTriggeredByUser) {
            // It's not the user that wanted the service to die, so restart it
            scheduleRestart();
        }
        mStopTriggeredByUser = false;

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        if (!mStopTriggeredByUser) {
            // It's not the user that wanted the service to die, so restart it
            scheduleRestart();
        }
        mStopTriggeredByUser = false;
        stopSelf();
    }

    /**
     * Restart service after a certain amount of time
     * Original idea from https://wendys.tistory.com/80
     */
    private void scheduleRestart() {
        if (BuildConfig.DEBUG) {
            String filename =
                    getExternalFilesDir(null).toString() + File.separator + new Date() + ".txt";
            String msg = "Unwanted service kill at " + new Date();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                writer.write(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long restartSchedule = System.currentTimeMillis() + SERVICE_RESTART_DELAY;
        Intent intent = new Intent(this, ServiceKilledReceiver.class);
        intent.setAction(Const.Intent.ACTION_START_SERVICE);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, restartSchedule, pendingIntent);
    }

    private static TextDrawable getVolumeLevelDrawable(int vol) {
        return new TextDrawableBuilder(TextDrawable.DRAWABLE_SHAPE_OVAL)
                .setHeight(100)
                .setWidth(100)
                .setColor(0x010000000)
                .setText(String.valueOf(vol))
                .setTextColor(Color.BLACK)
                .setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                .setTextSize(90f)
                .build();
    }

    private static Icon getVolumeLevelIcon(int vol) {
        return IconCompat.createWithBitmap(getVolumeLevelDrawable(vol).toBitmap()).toIcon();
    }

    private static Bitmap getVolumeLevelBitmap(int vol) {
        return getVolumeLevelDrawable(vol).toBitmap();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (TextUtils.equals(key, Prefs.Key.ENABLE_ON_HEADSET)) {
            mEnableOnHeadset = Prefs.get().getBoolean(key);
            updateVolumeNotification();

        } else if (TextUtils.equals(key, Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE)) {
            mShowNotiOutputDevice = Prefs.get().getBoolean(key);
            updateVolumeNotification();

        } else if (TextUtils.equals(key, Prefs.Key.SHOW_NOTI_VOL_LEVEL)) {
            mShowNotiVolumeLevel = Prefs.get().getBoolean(key);
            updateVolumeNotification();

        } else if (key.contains("force_mute")) {
            Log.d(TAG, "contains force_mute triggered");
            updateForceMuteStatus(null);
            updateForceMuteAlarms();

        }
    }

    public static void startService(@NonNull Context context) {
        startService(context, Const.Intent.ACTION_START_SERVICE, null);
    }

    public static void startService(@NonNull Context context, @Nullable String action, @Nullable Bundle extras) {
        Intent intent = new Intent(context, VolumeWatcherService.class);
        if (action != null) {
            intent.setAction(action);
        }
        if (extras != null) {
            intent.putExtras(extras);
        }
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stopService(@NonNull Context context) {
        mStopTriggeredByUser = true;
        startService(context, Const.Intent.ACTION_STOP_SERVICE, null);
    }

    public static boolean isRunning(Context context) {
        return Utils.isServiceRunning(context, VolumeWatcherService.class);
    }
}
