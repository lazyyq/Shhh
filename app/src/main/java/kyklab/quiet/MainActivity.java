package kyklab.quiet;

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
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.kyleduo.switchbutton.SwitchButton;

public class MainActivity extends AppCompatActivity {

    private BroadcastReceiver mReceiver;

    private TextView tvServiceStatus, tvServiceDesc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create notification channel if first launch
        if (true/*Prefs.get().getFirstLaunch()*/) {
            Prefs.get().setBoolean(Prefs.Key.FIRST_LAUNCH, false);
            createNotificationChannel();
        }

        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvServiceDesc = findViewById(R.id.tv_service_desc);

        final SwitchButton switchButton = findViewById(R.id.switchButton);
        switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startWatcherService();
                    setServiceStatusText(true);
                    Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, true);
                } else {
                    stopWatcherService();
                    setServiceStatusText(false);
                    Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, false);
                }
            }
        });

        ConstraintLayout mainLayout = findViewById(R.id.mainLayout);
        mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchButton.toggle();
            }
        });

        // Resume service if it was originally running
        if (Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED)) {
            switchButton.setCheckedNoEvent(true);
            setServiceStatusText(true);
            if (!Utils.isServiceRunning(VolumeWatcherService.class)) {
                startWatcherService();
            }
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TextUtils.equals(intent.getAction(), Const.Intent.ACTION_SWITCH_OFF)) {
                    switchButton.setCheckedNoEvent(false);
                }
            }
        };
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

    private void startWatcherService() {
        startWatcherService(Const.Intent.ACTION_START_SERVICE, null);
    }

    private void startWatcherService(@Nullable String action, @Nullable Bundle extras) {
        Intent intent = new Intent(this, VolumeWatcherService.class);
        if (action != null) {
            intent.setAction(action);
        }
        if (extras != null) {
            intent.putExtras(extras);
        }
        startForegroundService(intent);
    }

    private void stopWatcherService() {
        startWatcherService(Const.Intent.ACTION_STOP_SERVICE, null);
    }

    private void setServiceStatusText(boolean serviceEnabled) {
        if (serviceEnabled) {
            tvServiceStatus.setText(R.string.service_status_on);
            tvServiceDesc.setText(R.string.service_desc_on);
        } else {
            tvServiceStatus.setText(R.string.service_status_off);
            tvServiceDesc.setText(R.string.service_desc_off);
        }
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

        Log.d("MainActivity", "onResume called");
        Log.d("MainActivity", "intent:" +
                getIntent().getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false));

        boolean isNotificationClicked =
                getIntent().getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false);
        if (isNotificationClicked) {
            showNotificationHelp();
            getIntent().removeExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED);
        }

        IntentFilter intentFilter = new IntentFilter(Const.Intent.ACTION_SWITCH_OFF);
        registerReceiver(mReceiver, intentFilter);
        Log.d("MainActivity", "Registered receiver");
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d("MainActivity", "Unregistered receiver");
        unregisterReceiver(mReceiver);
    }

    public void showNotificationHelp() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.hide_foreground_service_notification_dialog_title)
                .setMessage(R.string.hide_foreground_service_notification_dialog_text)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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
                })
                .create();
        dialog.show();
    }


    public static class SettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

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
        public boolean onPreferenceClick(Preference preference) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity == null) {
                return false;
            }

            if (preference == findPreference(Prefs.Key.ENABLE_ON_HEADSET)) {
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(VolumeWatcherService.class)) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Const.Intent.EXTRA_ENABLE_ON_HEADSET,
                            Prefs.get().getBoolean(Prefs.Key.ENABLE_ON_HEADSET));
                    activity.startWatcherService(Const.Intent.ACTION_UPDATE_SETTINGS, bundle);
                }
            } else if (preference == findPreference(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON)) {
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(VolumeWatcherService.class)) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Const.Intent.EXTRA_VOLUME_LEVEL_IN_NOTI_ICON,
                            Prefs.get().getBoolean(Prefs.Key.VOLUME_LEVEL_IN_NOTI_ICON));
                    activity.startWatcherService(Const.Intent.ACTION_UPDATE_SETTINGS, bundle);
                }
            }
            return false;
        }
    }
}