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

import static kyklab.quiet.Utils.isDebug;

public class VolumeWatcherService extends Service {
    private static final String TAG = "VolumeWatcherService";


    private Notification.Builder mVolumeStateNotiBuilder;
    private Notification.Builder mForegroundNotiBuilder;

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private BroadcastReceiver mReceiver;

    private boolean mEnableOnHeadset, mVolumeLevelInNotiIcon;

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

        // Notification for current volume
        Notification.Action stopAction =
                new Notification.Action.Builder(Icon.createWithResource(this, 0),
                        getString(R.string.notification_action_stop_service), pendingStopIntent)
                        .build();
        Notification.Action muteAction =
                new Notification.Action.Builder(Icon.createWithResource(this, 0),
                        getString(R.string.notification_action_mute_volume), pendingMuteIntent)
                        .build();

        mVolumeStateNotiBuilder =
                new Notification.Builder(this, Const.Notification.CHANNEL_STATE)
                        .setContentTitle(getString(R.string.notification_media_volume_on_title))
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
        //intentFilter.addAction(EVENT_PHONE_STATE_CHANGED); // TODO: Fix phone state detection
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Receiver triggered: " + intent.getAction());
                String action = intent.getAction();
                if (TextUtils.equals(action, Const.Intent.ACTION_VOLUME_CHANGED)) {
                    // Volume level changed
                    updateVolumeNotification();
                } else if (TextUtils.equals(action, Const.Intent.ACTION_HEADSET_PLUGGED)) {
                    // Headset plugged or unplugged
                    if (!mEnableOnHeadset && Utils.isWiredHeadsetConnected(context)) {
                        removeVolumeNotification();
                    } else {
                        updateVolumeNotification();
                    }
                } else if (TextUtils.equals(action, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                    // Bluetooth connection state changed
                    int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                            BluetoothHeadset.STATE_DISCONNECTED);
                    if (!mEnableOnHeadset && state == BluetoothHeadset.STATE_CONNECTED) {
                        removeVolumeNotification();
                    } else {
                        updateVolumeNotification();
                    }
                }
                // TODO: Fix phone state detection
                /* else if (TextUtils.equals(action, EVENT_PHONE_STATE_CHANGED)) {
                    // Phone call state changed
                    //updateActiveState();
                    updateVolumeNotification();
                }*/
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
            mVolumeLevelInNotiIcon = Prefs.get().getBoolean(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON);
            if (!isDebug()) {
                Toast.makeText(this, R.string.starting_service, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Starting service, enable_on_headset: " + mEnableOnHeadset
                        + ", volume_level_in_noti_icon: " + mVolumeLevelInNotiIcon, Toast.LENGTH_SHORT).show();
            }

            // Start foreground service
            startForeground(Const.Notification.ID_ONGOING, mForegroundNotiBuilder.build());

            // Show notification for first time
            updateVolumeNotification();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_UPDATE_SETTINGS)) {
            // Update settings as per new preference changed by user
            mEnableOnHeadset = intent.getBooleanExtra(
                    Const.Intent.EXTRA_ENABLE_ON_HEADSET, mEnableOnHeadset);
            mVolumeLevelInNotiIcon = intent.getBooleanExtra(
                    Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON, mVolumeLevelInNotiIcon);

            updateVolumeNotification();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_STOP_SERVICE)) {
            // Stop service button in notification clicked
            Log.d(TAG, "Stopping service on notification click");
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, false);
            sendBroadcast(new Intent(Const.Intent.ACTION_SWITCH_OFF));
            stopForeground(true);
            stopSelf();

        } else if (TextUtils.equals(action, Const.Intent.ACTION_MUTE_VOLUME)) {
            Utils.muteStreamVolume(this, AudioManager.STREAM_MUSIC);
        }

        return START_NOT_STICKY;
    }

    private void updateVolumeNotification() {
        //
        // Check if headset is plugged or a call is active
        //
        if ((!mEnableOnHeadset &&
                (Utils.isWiredHeadsetConnected(this) ||
                        Utils.isBluetoothHeadsetConnected(this))) ||
                Utils.isCallActive(this)) {
            removeVolumeNotification();
            return;
        }

        int vol = Utils.getStreamVolume(this, AudioManager.STREAM_MUSIC);
        if (vol == -1) {
            Log.e(TAG, "Error while getting current volume");
        } else if (vol <= 0) {
            // Remove notification
            removeVolumeNotification();
        } else {
            // Show notification
            showVolumeNotification(vol);
        }
    }

    private void removeVolumeNotification() {
        NotificationManagerCompat.from(VolumeWatcherService.this)
                .cancel(Const.Notification.ID_STATE);
        // ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
        //        .cancel(NOTI_ID_STATE);
    }

    private void showVolumeNotification(int vol) {
        String notiText = String.format(getString(R.string.notification_media_volume_on_text),
                getString(Utils.isHeadsetConnected(this) ? R.string.output_headset : R.string.output_speaker), vol);
        Notification stateNotification =
                mVolumeStateNotiBuilder
                        .setContentText(notiText)
                        .setSmallIcon(getVolumeNotificationIcon(vol))
                        .build();
        NotificationManagerCompat.from(this).notify(Const.Notification.ID_STATE, stateNotification);
    }

    private Icon getVolumeNotificationIcon(int vol) {
        int iconResId = Utils.isHeadsetConnected(this) ? R.drawable.ic_headset : R.drawable.ic_speaker;
        if (!mVolumeLevelInNotiIcon) {
            // Return speaker or headset icon
            return Icon.createWithResource(this, iconResId);
        } else {
            // Return volume level text
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
            /*int bgColor = isDarkMode(this) ? 0x99FFFFFF : 0x66000000,
                    textColor = isDarkMode(this) ? 0xFFFFFFFF : 0xFF000000,
                    iconSize = (int) getPxFromDp(this, 100);
            float textSize = getPxFromDp(this, 25);
            return Icon.createWithBitmap(getBitmapWithText(this, iconSize, iconSize,
                    iconResId, bgColor, String.valueOf(vol), textSize, textColor));*/
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        NotificationManagerCompat.from(this).cancel(Const.Notification.ID_ONGOING);
        NotificationManagerCompat.from(this).cancel(Const.Notification.ID_STATE);
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
