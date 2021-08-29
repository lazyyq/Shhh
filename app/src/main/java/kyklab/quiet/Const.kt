package kyklab.quiet

object Const {
    object Notification {
        // Permanent notification for foreground service
        const val CHANNEL_ONGOING = "NotificationChannelOngoing"
        const val ID_ONGOING = 1

        // Permanent notification for current output device
        const val CHANNEL_OUTPUT_DEVICE = "NotificationChannelOutputDevice"
        const val ID_OUTPUT_DEVICE = 3

        // Permanent notification for current volume level
        const val CHANNEL_VOLUME_LEVEL = "NotificationChannelVolumeLevel"
        const val ID_VOLUME_LEVEL = 4

        // Permanent notification for force mute mode
        const val CHANNEL_FORCE_MUTE = "NotificationChannelForceMute"
        const val ID_FORCE_MUTE = 5
    }

    object Intent {
        // Actions
        const val ACTION_START_SERVICE = "kyklab.quiet.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "kyklab.quiet.action.STOP_SERVICE"
        const val ACTION_SERVICE_STARTED = "kyklab.quiet.action.SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "kyklab.quiet.action.SERVICE_STOPPED"
        const val ACTION_MUTE_VOLUME = "kyklab.quite.action.MUTE_VOLUME"
        const val ACTION_START_FORCE_MUTE = "kyklab.quite.action.START_FORCE_MUTE"
        const val ACTION_STOP_FORCE_MUTE = "kyklab.quite.action.STOP_FORCE_MUTE"
        const val ACTION_STOP_FORCE_MUTE_USER = "kyklab.quite.action.STOP_FORCE_MUTE_USER"
        const val ACTION_UPDATE_FORCE_MUTE_ALARMS = "kyklab.quite.action.UPDATE_FORCE_MUTE_ALARMS"
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

        // Extras
        const val EXTRA_NOTIFICATION_CLICKED = "kyklab.quiet.extra.NOTIFICATION_CLICKED"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    }
}