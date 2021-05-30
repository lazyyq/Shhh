package kyklab.quiet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private val TAG = this::class.java.simpleName
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start service on boot complete
            if (context != null && Prefs.serviceEnabled) {
                VolumeWatcherService.startService(context)
            }
        }
    }
}