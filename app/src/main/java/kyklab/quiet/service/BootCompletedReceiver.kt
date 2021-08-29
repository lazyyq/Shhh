package kyklab.quiet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kyklab.quiet.utils.Prefs

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private val TAG = this::class.java.simpleName
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Start service on boot complete or package replaced
            if (context != null && Prefs.serviceEnabled) {
                VolumeWatcherService.startService(context)
            }
        }
    }
}