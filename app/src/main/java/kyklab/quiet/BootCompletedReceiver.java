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
            if (Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED)) {
                VolumeWatcherService.startService(context);
            }
        }
    }
}
