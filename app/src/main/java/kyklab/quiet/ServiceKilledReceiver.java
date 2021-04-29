package kyklab.quiet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ServiceKilledReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeWatcherService.startService(context);
    }
}
