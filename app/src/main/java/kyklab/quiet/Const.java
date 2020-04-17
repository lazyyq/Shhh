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
        public static final String ACTION_START_SERVICE = "kyklab.quiet.action.START_SERVICE";
        public static final String ACTION_STOP_SERVICE = "kyklab.quiet.action.STOP_SERVICE";
        public static final String ACTION_SERVICE_STARTED = "kyklab.quiet.action.SERVICE_STARTED";
        public static final String ACTION_SERVICE_STOPPED = "kyklab.quiet.action.SERVICE_STOPPED";
        public static final String ACTION_MUTE_VOLUME = "kyklab.quite.action.MUTE_VOLUME";

        public static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

        // Extras
        public static final String EXTRA_NOTIFICATION_CLICKED = "kyklab.quiet.extra.NOTIFICATION_CLICKED";

        public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
        public static final String EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";

    }

}
