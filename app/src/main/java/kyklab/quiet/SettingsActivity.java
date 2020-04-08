package kyklab.quiet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();

        // Create notification channel if first launch
        if (true/*Prefs.get().getFirstLaunch()*/) {
            Prefs.get().setBoolean(Prefs.Key.FIRST_LAUNCH, false);
            createNotificationChannel();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel =
                new NotificationChannel(Const.Notification.CHANNEL_ONGOING,
                        getString(R.string.notification_channel_foreground_service),
                        NotificationManager.IMPORTANCE_LOW); // IMPORTANCE_LOW : no sound

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        notificationManager.createNotificationChannel(channel);
        channel = new NotificationChannel(Const.Notification.CHANNEL_STATE,
                getString(R.string.notification_channel_current_volume),
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        // NotificationManagerCompat.from(getApplicationContext()).createNotificationChannel(channel);

        //NotificationManager manager =
        //        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //manager.createNotificationChannel(channel);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        boolean isNotificationClicked =
                intent.getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false);
        if (isNotificationClicked) {
            showNotificationHelp();
            getIntent().removeExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.e("SettingsActivity", "onResume called");
        Log.e("SettingsActivity", "intent:" +
                getIntent().getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false));

        boolean isNotificationClicked =
                getIntent().getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false);
        if (isNotificationClicked) {
            showNotificationHelp();
            getIntent().removeExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED);
        }
    }

    private void showNotificationHelp() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setTitle("제목")
                .setMessage("메시지")
                .setPositiveButton("긍정", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(Const.Intent.ACTION_APP_NOTIFICATION_SETTINGS);

                        //for Android 5-7
                        //intent.putExtra("app_package", getPackageName());
                        //intent.putExtra("app_uid", getApplicationInfo().uid);

                        // for Android 8 and above
                        intent.putExtra(Const.Intent.EXTRA_APP_PACKAGE, App.getContext().getPackageName());

                        startActivity(intent);
                    }
                });
        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();
    }


    public static class SettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener {
        private BroadcastReceiver mReceiver;

        private SwitchPreferenceCompat mServiceEnabled;

        private Intent mServiceIntent;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Service enabled switch
            mServiceEnabled = findPreference(Prefs.Key.SERVICE_ENABLED);
            if (mServiceEnabled != null) {
                mServiceEnabled.setOnPreferenceClickListener(this);
            }

            // Set intents for service
            mServiceIntent = new Intent(App.getContext(), VolumeWatcherService.class);
            mServiceIntent.putExtra(Const.Intent.EXTRA_ENABLE_ON_HEADSET,
                    Prefs.get().getBoolean(Prefs.Key.ENABLE_ON_HEADSET));
            mServiceIntent.putExtra(Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON,
                    Prefs.get().getBoolean(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON));

            // Resume service if it was originally running
            if (Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED) && !Utils.isServiceRunning(VolumeWatcherService.class)) {
                startService();
            }


            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (TextUtils.equals(intent.getAction(), Const.Intent.ACTION_SWITCH_OFF)) {
                        mServiceEnabled.setOnPreferenceClickListener(null);
                        mServiceEnabled.setChecked(false);
                        mServiceEnabled.setOnPreferenceClickListener(SettingsFragment.this);
                    }
                }
            };

            // Enable on headset switch
            SwitchPreferenceCompat enableOnHeadsetSwitch = findPreference(Prefs.Key.ENABLE_ON_HEADSET);
            if (enableOnHeadsetSwitch != null) {
                enableOnHeadsetSwitch.setOnPreferenceClickListener(this);
            }

            // Show volume level in notification icon switch
            SwitchPreferenceCompat volumeLevelInNotiIconSwitch = findPreference(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON);
            if (volumeLevelInNotiIconSwitch != null) {
                volumeLevelInNotiIconSwitch.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            IntentFilter intentFilter = new IntentFilter(Const.Intent.ACTION_SWITCH_OFF);
            Activity activity = getActivity();
            if (activity != null) {
                activity.registerReceiver(mReceiver, intentFilter);
                Log.e("SettingsFragment", "Registered receiver");
            }

        }

        @Override
        public void onPause() {
            super.onPause();
            Log.e("SettingsFragment", "Unregistered receiver");
            Activity activity = getActivity();
            if (activity != null) {
                activity.unregisterReceiver(mReceiver);
            }
        }

        private void startService() {
            Activity activity = getActivity();
            if (activity != null) {
                activity.startForegroundService(mServiceIntent);
            }
        }

        private void stopService() {
            Activity activity = getActivity();
            if (activity != null) {
                activity.stopService(mServiceIntent);
            }
        }


        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == findPreference(Prefs.Key.SERVICE_ENABLED)) {
                boolean value =
                        preference.getSharedPreferences().getBoolean(preference.getKey(), false);
                if (value) {
                    startService();
                } else {
                    stopService();
                }
            } else if (preference == findPreference(Prefs.Key.ENABLE_ON_HEADSET)) {
                boolean value =
                        preference.getSharedPreferences().getBoolean(preference.getKey(), false);
                mServiceIntent.putExtra(Const.Intent.EXTRA_ENABLE_ON_HEADSET, value);
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(VolumeWatcherService.class)) {
                    startService();
                }
            } else if (preference == findPreference(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON)) {
                boolean value =
                        preference.getSharedPreferences().getBoolean(preference.getKey(), false);
                mServiceIntent.putExtra(Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON, value);
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(VolumeWatcherService.class)) {
                    startService();
                }
            }
            return false;
        }
    }
}