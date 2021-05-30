package kyklab.quiet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceKilledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { VolumeWatcherService.startService(it) }
    }
}