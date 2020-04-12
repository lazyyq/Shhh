package kyklab.quiet;

public class Const {

    public static class Notification {

        // Permanent notification for foreground service
        public static final String CHANNEL_ONGOING = "NotificationChannelOngoing";
        public static final int ID_ONGOING = 1;

        // Permanent notification for current output device
        public static final String CHANNEL_OUTPUT_DEVICE = "NotificationChannelOutputDevice";
        public static final int ID_OUTPUT_DEVICE = 3;

        // Permanent notification for current volume level
        public static final String CHANNEL_VOLUME_LEVEL = "NotificationChannelVolumeLevel";
        public static final int ID_VOLUME_LEVEL = 4;

    }

    public static class Intent {

        // Actions
        public static final String ACTION_START_SERVICE = "kyklab.quiet.action.start_service";
        public static final String ACTION_STOP_SERVICE = "kyklab.quiet.action.stop_service";
        public static final String ACTION_UPDATE_SETTINGS = "kyklab.quiet.action.update_settings";
        public static final String ACTION_SERVICE_STARTED = "kyklab.quiet.action.service_started";
        public static final String ACTION_SERVICE_STOPPED = "kyklab.quiet.action.service_stopped";
        public static final String ACTION_MUTE_VOLUME = "kyklab.quite.action.mute_volume";

        public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
        public static final String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
        public static final String ACTION_HEADSET_PLUGGED = "android.intent.action.HEADSET_PLUG";

        public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";

        // Extras
        public static final String EXTRA_ENABLE_ON_HEADSET = "enable_on_headset";
        public static final String EXTRA_NOTIFICATION_CLICKED = "notification_clicked";
        public static final String EXTRA_SHOW_NOTI_OUTPUT_DEVICE = "show_noti_output_device";
        public static final String EXTRA_SHOW_NOTI_VOLUME_LEVEL = "show_noti_volume_level";

        public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

    }

}
