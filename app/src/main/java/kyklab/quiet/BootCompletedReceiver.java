package kyklab.quiet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TextUtils.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            // Start service on boot complete
            if (Prefs.get().getAutoStartOnBoot()) {
                Intent serviceIntent = new Intent(context, VolumeWatcherService.class);
                serviceIntent.putExtra(Const.Intent.EXTRA_ENABLE_ON_HEADSET,
                        Prefs.get().getEnableOnHeadset());
                serviceIntent.putExtra(Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON,
                        Prefs.get().getVolumeLevelInNotiIcon());
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
