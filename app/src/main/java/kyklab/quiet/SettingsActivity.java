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
            Prefs.get().setFirstLaunch(false);
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

        private SwitchPreferenceCompat mMasterSwitch;

        private Intent mServiceIntent;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            mMasterSwitch = findPreference(Prefs.Key.MASTER_SWITCH);

            mServiceIntent = new Intent(App.getContext(), VolumeWatcherService.class);
            mServiceIntent.putExtra(Const.Intent.EXTRA_ENABLE_ON_HEADSET,
                    Prefs.get().getEnableOnHeadset());
            mServiceIntent.putExtra(Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON,
                    Prefs.get().getVolumeLevelInNotiIcon());


            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (TextUtils.equals(intent.getAction(), Const.Intent.ACTION_SWITCH_OFF)) {
                        mMasterSwitch.setOnPreferenceClickListener(null);
                        mMasterSwitch.setChecked(false);
                        mMasterSwitch.setOnPreferenceClickListener(SettingsFragment.this);
                    }
                }
            };

            SwitchPreferenceCompat enableOnHeadsetSwitch = findPreference(Prefs.Key.ENABLE_ON_HEADSET);
            if (enableOnHeadsetSwitch != null) {
                enableOnHeadsetSwitch.setOnPreferenceClickListener(this);
            }

            SwitchPreferenceCompat volumeLevelInNotiIconSwitch = findPreference(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON);
            if (volumeLevelInNotiIconSwitch != null) {
                volumeLevelInNotiIconSwitch.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            mMasterSwitch.setOnPreferenceClickListener(null);
            if (Utils.isServiceRunning(VolumeWatcherService.class)) {
                mMasterSwitch.setChecked(true);
            } else {
                mMasterSwitch.setChecked(false);
            }
            mMasterSwitch.setOnPreferenceClickListener(this);

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
            if (preference == findPreference(Prefs.Key.MASTER_SWITCH)) {
                boolean value =
                        preference.getSharedPreferences().getBoolean(preference.getKey(), false);
                if (value) {
                    Prefs.get().setAutoStartOnBoot(true);
                    startService();
                } else {
                    Prefs.get().setAutoStartOnBoot(false);
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