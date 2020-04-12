package kyklab.quiet;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.kennyc.textdrawable.TextDrawable;
import com.kennyc.textdrawable.TextDrawableBuilder;

import static kyklab.quiet.Utils.getStreamVolume;
import static kyklab.quiet.Utils.isCallActive;
import static kyklab.quiet.Utils.isDebug;
import static kyklab.quiet.Utils.isHeadsetConnected;
import static kyklab.quiet.Utils.muteStreamVolume;

public class VolumeWatcherService extends Service {
    private static final String TAG = "VolumeWatcherService";

    private Notification.Builder mForegroundNotiBuilder,
            mOutputDeviceNotiBuilder, mVolumeLevelNotiBuilder;

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private BroadcastReceiver mReceiver;

    private boolean mEnableOnHeadset, mShowNotiOutputDevice, mShowNotiVolumeLevel;

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
        Notification.Action stopAction =
                new Notification.Action.Builder(Icon.createWithResource(this, 0),
                        getString(R.string.notification_action_stop_service), pendingStopIntent)
                        .build();
        Notification.Action muteAction =
                new Notification.Action.Builder(Icon.createWithResource(this, 0),
                        getString(R.string.notification_action_mute_volume), pendingMuteIntent)
                        .build();

        // Notification for output device
        mOutputDeviceNotiBuilder =
                new Notification.Builder(this, Const.Notification.CHANNEL_OUTPUT_DEVICE)
                        .addAction(stopAction)
                        .addAction(muteAction)
                        .setOngoing(true);

        // Notification for volume level
        mVolumeLevelNotiBuilder =
                new Notification.Builder(this, Const.Notification.CHANNEL_VOLUME_LEVEL)
                        .addAction(stopAction)
                        .addAction(muteAction)
                        .setOngoing(true);

        /*
         * Foreground service notification
         */
        // Notification for foreground service
        Intent notificationIntent = new Intent(this, MainActivity.class);
        //notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, true);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Builder for foreground notification
        mForegroundNotiBuilder =
                new Notification.Builder(this, Const.Notification.CHANNEL_ONGOING)
                        .setContentTitle(getString(R.string.notification_foreground_service_title))
                        .setContentText(getString(R.string.notification_foreground_service_text))
                        .setSmallIcon(R.drawable.ic_speaker)
                        .setContentIntent(pendingIntent);

        // Receiver for volume change, headset connection detection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Const.Intent.ACTION_VOLUME_CHANGED);
        intentFilter.addAction(Const.Intent.ACTION_HEADSET_PLUGGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(Const.Intent.ACTION_MUTE_VOLUME);
        //intentFilter.addAction(EVENT_PHONE_STATE_CHANGED); // TODO: Fix phone state detection
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Receiver triggered: " + intent.getAction());
                String action = intent.getAction();
                if (TextUtils.equals(action, Const.Intent.ACTION_VOLUME_CHANGED) ||
                        TextUtils.equals(action, Const.Intent.ACTION_HEADSET_PLUGGED) ||
                        TextUtils.equals(action, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                    // Update notification status
                    updateVolumeNotification();
                }
            }
        };
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()" +
                "\nintents:" + intent.toString() + "\nflags:" + flags + "\nstartId:" + startId);
        String action = intent.getAction();

        if (TextUtils.equals(action, Const.Intent.ACTION_START_SERVICE)) {
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

        } else if (TextUtils.equals(action, Const.Intent.ACTION_UPDATE_SETTINGS)) {
            // Update settings as per new preference changed by user
            mEnableOnHeadset = intent.getBooleanExtra(
                    Const.Intent.EXTRA_ENABLE_ON_HEADSET, mEnableOnHeadset);
            mShowNotiOutputDevice = intent.getBooleanExtra(
                    Const.Intent.EXTRA_SHOW_NOTI_OUTPUT_DEVICE, mShowNotiOutputDevice);
            mShowNotiVolumeLevel = intent.getBooleanExtra(
                    Const.Intent.EXTRA_SHOW_NOTI_VOLUME_LEVEL, mShowNotiVolumeLevel);

            updateVolumeNotification();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_STOP_SERVICE)) {
            // Stop service button in notification clicked
            Log.d(TAG, "Stopping service on notification click");
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, false);
            sendBroadcast(new Intent(Const.Intent.ACTION_SWITCH_OFF));
            stopForeground(true);
            stopSelf();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_MUTE_VOLUME)) {
            muteStreamVolume(this, AudioManager.STREAM_MUSIC);
        }

        return START_NOT_STICKY;
    }

    private void updateVolumeNotification() {
        // Hide all notifications during call
        if (isCallActive(this)) {
            removeOutputDeviceNotification();
            removeVolumeLevelNotification();
            return;
        }

        // Decide whether to show output device notification
        if (mShowNotiOutputDevice) {
            if (isHeadsetConnected(this)) {
                // if headset connected
                if (mEnableOnHeadset) {
                    showOutputDeviceNotification();
                } else {
                    removeOutputDeviceNotification();
                }
            } else {
                showOutputDeviceNotification();
            }
        } else {
            removeOutputDeviceNotification();
        }

        // Decide whether to show volume level notification
        if (mShowNotiVolumeLevel && isMediaVolumeOn()) {
            if (isHeadsetConnected(this)) {
                // if headset connected
                if (mEnableOnHeadset) {
                    showVolumeLevelNotification();
                } else {
                    removeVolumeLevelNotification();
                }
            } else {
                showVolumeLevelNotification();
            }
        } else {
            removeVolumeLevelNotification();
        }
    }

    private boolean isMediaVolumeOn() {
        int vol = getStreamVolume(this, AudioManager.STREAM_MUSIC);
        if (vol == -1) {
            Log.e(TAG, "Error while getting current volume");
            return false;
        } else {
            return vol != 0;
        }
    }

    private void showOutputDeviceNotification() {
        int vol = getStreamVolume(this, AudioManager.STREAM_MUSIC);
        String title = String.format(getString(R.string.notification_output_device_title),
                getCurrentOutputDevice(this));
        String text;
        if (!mShowNotiVolumeLevel) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            text = String.format(getString(R.string.notification_unified_text),
                    getCurrentOutputDevice(this), vol);
        } else {
            text = String.format(getString(R.string.notification_output_device_text),
                    getCurrentOutputDevice(this));
        }
        Icon smallIcon = getCurrentOutputDeviceIcon(this);
        Notification notification = mOutputDeviceNotiBuilder
                .setContentTitle(title).setContentText(text).setSmallIcon(smallIcon).build();

        NotificationManagerCompat.from(this).notify(Const.Notification.ID_OUTPUT_DEVICE, notification);
    }

    private void removeOutputDeviceNotification() {
        NotificationManagerCompat.from(VolumeWatcherService.this)
                .cancel(Const.Notification.ID_OUTPUT_DEVICE);
    }

    private void showVolumeLevelNotification() {
        int vol = getStreamVolume(this, AudioManager.STREAM_MUSIC);
        String title = String.format(getString(R.string.notification_volume_level_title), vol);
        String text;
        if (!mShowNotiOutputDevice) {
            // if this is the only notification enabled,
            // show info for both output device and volume level
            text = String.format(getString(R.string.notification_unified_text),
                    getCurrentOutputDevice(this), vol);
        } else {
            text = getString(R.string.notification_volume_level_text);
        }
        Icon smallIcon = getVolumeLevelIcon(vol);
        Notification notification = mVolumeLevelNotiBuilder
                .setContentTitle(title).setContentText(text).setSmallIcon(smallIcon).build();

        NotificationManagerCompat.from(this).notify(Const.Notification.ID_VOLUME_LEVEL, notification);
    }

    private void removeVolumeLevelNotification() {
        NotificationManagerCompat.from(VolumeWatcherService.this)
                .cancel(Const.Notification.ID_VOLUME_LEVEL);
    }

    private Icon getVolumeLevelIcon(int vol) {
        TextDrawable drawable = new TextDrawableBuilder(TextDrawable.DRAWABLE_SHAPE_OVAL)
                .setHeight(100)
                .setWidth(100)
                .setColor(0x010000000)
                .setText(String.valueOf(vol))
                .setTextColor(Color.BLACK)
                .setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                .setTextSize(90f)
                .build();
        return Icon.createWithBitmap(drawable.toBitmap());
    }

    private String getCurrentOutputDevice(Context context) {
        return getString(isHeadsetConnected(this) ?
                R.string.output_headset : R.string.output_speaker);
    }

    private Icon getCurrentOutputDeviceIcon(Context context) {
        return Icon.createWithResource(context,
                isHeadsetConnected(context) ? R.drawable.ic_headset : R.drawable.ic_speaker);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.cancel(Const.Notification.ID_ONGOING);
        manager.cancel(Const.Notification.ID_OUTPUT_DEVICE);
        manager.cancel(Const.Notification.ID_VOLUME_LEVEL);
        Toast.makeText(this, "Stopping service", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*private class VolumeChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    }*/
}
