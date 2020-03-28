package kyklab.quiet;

public class Const {

    public class Notification {

        // Permanent notification for foreground service
        public static final String CHANNEL_ONGOING = "NotificationChannelOngoing";
        public static final int ID_ONGOING = 1;

        // Permanent notification for showing whether volume is currently on
        public static final String CHANNEL_STATE = "NotificationChannelState";
        public static final int ID_STATE = 2;

    }

    public class Intent {

        // Actions
        public static final String ACTION_STOP_SERVICE = "kyklab.quiet.action.stop_service";
        public static final String ACTION_SWITCH_OFF = "kyklab.quiet.action.switch_off";
        public static final String ACTION_MUTE_VOLUME = "kyklab.quite.action.mute_volume";

        public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
        public static final String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
        public static final String ACTION_HEADSET_PLUGGED = "android.intent.action.HEADSET_PLUG";

        public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";

        // Extras
        public static final String EXTRA_ENABLE_ON_HEADSET = "enable_on_headset";
        public static final String EXTRA_NOTIFICATION_CLICKED = "notification_clicked";
        public static final String EXTRA_VOLUME_LEVEL_IN_NOTI_ICON = "volume_level_in_noti_icon";

        public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

    }

}
