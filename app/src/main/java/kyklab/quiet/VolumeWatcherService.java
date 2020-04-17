package kyklab.quiet;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.kennyc.textdrawable.TextDrawable;
import com.kennyc.textdrawable.TextDrawableBuilder;

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

    private NotificationCompat.Builder mForegroundNotiBuilder,
            mOutputDeviceNotiBuilder, mVolumeLevelNotiBuilder;
    private Notification.Builder mOutputDeviceNotiBuilderOreo, mVolumeLevelNotiBuilderOreo;

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private BroadcastReceiver mReceiver;

    private Handler mHandler;
    private Runnable mNotifyVolumeRunnable; // Show media volume notification

    private boolean
            mEnableOnHeadset, mShowNotiOutputDevice, mShowNotiVolumeLevel, // App settings
            mCallActive, // There's an active call
            mHeadsetConnected; // Headset connection status

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
                PendingIntent.getService(this, 0, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for killing media volume
        Intent muteIntent = new Intent(this, VolumeWatcherService.class);
        muteIntent.setAction(Const.Intent.ACTION_MUTE_VOLUME);
        PendingIntent pendingMuteIntent =
                PendingIntent.getService(this, 0, muteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Notification for actions for output device / volume level notification
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
                            .addAction(stopActionOreo)
                            .addAction(muteActionOreo)
                            .setOngoing(true);
            mVolumeLevelNotiBuilderOreo =
                    new Notification.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
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
                            .addAction(stopAction)
                            .addAction(muteAction)
                            .setOngoing(true);
            mVolumeLevelNotiBuilder =
                    new NotificationCompat.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
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
                    PendingIntent.getActivity(this, 0, notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            mForegroundNotiBuilder
                    .setContentText(getString(R.string.notification_foreground_service_text))
                    .setContentIntent(pendingIntent);
        }

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
                        updateVolumeNotification();
                    }
                } else if (TextUtils.equals(action, Intent.ACTION_HEADSET_PLUG) ||
                        TextUtils.equals(action, BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                    updateMediaVolume(null);
                    updateHeadsetStatus(intent);
                    updateVolumeNotification();
                } else if (TextUtils.equals(action, TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    updateCallStatus(intent);
                    updateVolumeNotification();
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
            // Initialize status
            updateMediaVolume(null);
            updateHeadsetStatus(null);
            updateCallStatus(null);

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
        }

        return START_NOT_STICKY;
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
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.cancel(Const.Notification.ID_ONGOING);
        manager.cancel(Const.Notification.ID_OUTPUT_DEVICE);
        manager.cancel(Const.Notification.ID_VOLUME_LEVEL);
        Toast.makeText(this, R.string.stopping_service, Toast.LENGTH_SHORT).show();
        super.onDestroy();
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
        }
    }

    /*private class VolumeChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    }*/
}
