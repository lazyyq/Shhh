package kyklab.quiet;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.kennyc.textdrawable.TextDrawable;
import com.kennyc.textdrawable.TextDrawableBuilder;

public class VolumeWatcherService extends Service {
    public static final String ACTION_MUTE_VOLUME = "kyklab.quite.action.mute_volume";
    private static final String TAG = "VolumeWatcherService";

    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
    private static final String ACTION_HEADSET_PLUGGED = "android.intent.action.HEADSET_PLUG";
    private static final String ACTION_STOP_SERVICE = "stop_service";

    private Notification.Builder mVolumeStateNotiBuilder;
    private Notification.Builder mForegroundNotiBuilder;

    // Receiver for broadcast events (volume changed, headset plugged, etc..)
    private BroadcastReceiver mReceiver;

    private boolean mEnableOnHeadset, mVolumeLevelInNotiIcon;

    public VolumeWatcherService() {
    }

    private static boolean isHeadsetConnected(Context context) {
        return isWiredHeadsetConnected(context) || isBluetoothHeadsetConnected(context);
    }

    private static boolean isWiredHeadsetConnected(Context context) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Toast.makeText(context, "isWiredHeadsetConnected(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return audioManager != null && audioManager.isWiredHeadsetOn();
    }

    private static boolean isBluetoothHeadsetConnected(Context context) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Toast.makeText(context, "isBluetoothHeadsetConnected(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return audioManager != null && audioManager.isBluetoothA2dpOn();
    }

    private static boolean isCallActive(Context context) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            Toast.makeText(context, "isCallActive(): telephonyManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return telephonyManager != null &&
                telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
    }

    private static int getStreamVolume(Context context, int streamType) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            return audioManager.getStreamVolume(streamType);
        } else {
            Toast.makeText(context, "getStreamVolume(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private static void muteStreamVolume(Context context, int streamType) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI);
    }

    private static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Nullable
    private static Bitmap getBitmapWithText(Context context, int width, int height,
                                            @DrawableRes int bgVectorDrawableId, @ColorInt int bgColor,
                                            String text, float textSize, @ColorInt int textColor) {
        // Draw vector drawable on canvas
        Drawable drawable = ContextCompat.getDrawable(context, bgVectorDrawableId);
        if (drawable == null) {
            Log.e(TAG, "getBitmapWithText(): drawable is null");
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setTint(bgColor); // 40% opacity
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        // Text options
        Paint textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextSize(getPxFromDp(context, textSize));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        // Draw text on canvas
        Rect rect = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), rect);
        canvas.drawText(text, bitmap.getWidth() / 2f,
                (bitmap.getHeight() + Math.abs(rect.top - rect.bottom)) / 2f, textPaint);

        return bitmap;
    }

    private static float getPxFromDp(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate()");

        /*
         * Volume state notification
         */
        // Intent for stopping foreground service
        Intent stopIntent = new Intent(this, VolumeWatcherService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent pendingStopIntent =
                PendingIntent.getService(this, 0, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for killing media volume
        Intent muteIntent = new Intent(this, VolumeWatcherService.class);
        muteIntent.setAction(ACTION_MUTE_VOLUME);
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
                new Notification.Builder(this, SettingsActivity.NOTI_CHANNEL_STATE)
                        .setContentTitle(getString(R.string.notification_media_volume_on_title))
                        .addAction(stopAction)
                        .addAction(muteAction)
                        .setOngoing(true);

        /*
         * Foreground service notification
         */
        // Notification for foreground service
        Intent notificationIntent = new Intent(this, SettingsActivity.class);
        //notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(SettingsActivity.INTENT_EXTRA_NOTIFICATION_CLICKED, true);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Builder for foreground notification
        mForegroundNotiBuilder =
                new Notification.Builder(this, SettingsActivity.NOTI_CHANNEL_ONGOING)
                        .setContentTitle(getString(R.string.notification_foreground_service_title))
                        .setContentText(getString(R.string.notification_foreground_service_text))
                        .setSmallIcon(R.drawable.ic_speaker)
                        .setContentIntent(pendingIntent);

        // Receiver for volume change, headset connection detection
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_VOLUME_CHANGED);
        intentFilter.addAction(ACTION_HEADSET_PLUGGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(ACTION_MUTE_VOLUME);
        //intentFilter.addAction(EVENT_PHONE_STATE_CHANGED); // TODO: Fix phone state detection
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e(TAG, "Receiver triggered: " + intent.getAction());
                String action = intent.getAction();
                if (TextUtils.equals(action, ACTION_VOLUME_CHANGED)) {
                    // Volume level changed
                    updateVolumeNotification();
                } else if (TextUtils.equals(action, ACTION_HEADSET_PLUGGED)) {
                    // Headset plugged or unplugged
                    if (!mEnableOnHeadset && isWiredHeadsetConnected(context)) {
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
        Log.e(TAG, "onStartCommand()");
        String action = intent.getAction();
        if (TextUtils.equals(action, ACTION_STOP_SERVICE)) {
            // Stop service button in notification clicked
            Log.e(TAG, "Stopping service on notification click");
            sendBroadcast(new Intent(SettingsActivity.INTENT_SWITCH_OFF));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        } else if (TextUtils.equals(action, ACTION_MUTE_VOLUME)) {
            muteStreamVolume(this, AudioManager.STREAM_MUSIC);
            return START_NOT_STICKY;
        }

        mEnableOnHeadset = intent.getBooleanExtra(
                SettingsActivity.INTENT_EXTRA_ENABLE_ON_HEADSET, false);
        mVolumeLevelInNotiIcon = intent.getBooleanExtra(
                SettingsActivity.INTENT_EXTRA_VOLUME_LEVEL_IN_NOTI_ICON, false);
        Toast.makeText(this, "Starting service, enable_on_headset: " + mEnableOnHeadset
                + ", volume_level_in_noti_icon: " + mVolumeLevelInNotiIcon, Toast.LENGTH_SHORT).show();

        // Start foreground service
        startForeground(SettingsActivity.NOTI_ID_ONGOING, mForegroundNotiBuilder.build());

        // Show notification for first time
        //updateActiveState();
        updateVolumeNotification();

        return START_NOT_STICKY;
    }

    private void updateVolumeNotification() {
        //
        // Check if headset is plugged or a call is active
        //
        if ((!mEnableOnHeadset &&
                (isWiredHeadsetConnected(this) || isBluetoothHeadsetConnected(this))) ||
                isCallActive(this)) {
            removeVolumeNotification();
            return;
        }

        int vol = getStreamVolume(this, AudioManager.STREAM_MUSIC);
        if (vol == -1) {
            Toast.makeText(this, "Error while getting current volume", Toast.LENGTH_SHORT).show();
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
                .cancel(SettingsActivity.NOTI_ID_STATE);
        // ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
        //        .cancel(NOTI_ID_STATE);
    }

    private void showVolumeNotification(int vol) {
        String notiText = String.format(getString(R.string.notification_media_volume_on_text),
                getString(isHeadsetConnected(this) ? R.string.output_headset : R.string.output_speaker), vol);
        Notification stateNotification =
                mVolumeStateNotiBuilder
                        .setContentText(notiText)
                        .setSmallIcon(getVolumeNotificationIcon(vol))
                        .build();
        NotificationManagerCompat.from(this).notify(SettingsActivity.NOTI_ID_STATE, stateNotification);
    }

    private Icon getVolumeNotificationIcon(int vol) {
        int iconResId = isHeadsetConnected(this) ? R.drawable.ic_headset : R.drawable.ic_speaker;
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
        NotificationManagerCompat.from(this).cancel(SettingsActivity.NOTI_ID_ONGOING);
        NotificationManagerCompat.from(this).cancel(SettingsActivity.NOTI_ID_STATE);
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
