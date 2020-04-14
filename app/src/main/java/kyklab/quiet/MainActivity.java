package kyklab.quiet;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kyleduo.switchbutton.SwitchButton;

import static kyklab.quiet.Utils.isOreoOrHigher;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_READ_PHONE_STATE = 100;

    private BroadcastReceiver mReceiver;

    private TextView tvServiceStatus, tvServiceDesc;

    private boolean mPermissionGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        // Create notification channel
        if (isOreoOrHigher()) {
            createNotificationChannel();
        }

        checkPermission();

        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvServiceDesc = findViewById(R.id.tv_service_desc);

        SwitchButton switchButton = findViewById(R.id.switchButton);
        Runnable startServiceRunnable = () -> {
            startWatcherService();
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, true);
            runOnUiThread(() -> {
                setServiceStatusText(true);
                switchButton.setEnabled(true);
            });
        };
        Runnable stopServiceRunnable = () -> {
            stopWatcherService();
            Prefs.get().setBoolean(Prefs.Key.SERVICE_ENABLED, false);
            runOnUiThread(() -> {
                setServiceStatusText(false);
                switchButton.setEnabled(true);
            });
        };
        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new Thread(startServiceRunnable).start();
            } else {
                new Thread(stopServiceRunnable).start();
            }
        });

        ConstraintLayout mainLayout = findViewById(R.id.mainLayout);
        mainLayout.setOnClickListener(v -> switchButton.toggle());

        // Resume service if it was originally running
        if (Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED)) {
            switchButton.setCheckedNoEvent(true);
            setServiceStatusText(true);
            if (!Utils.isServiceRunning(this, VolumeWatcherService.class)) {
                startWatcherService();
            }
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (TextUtils.equals(action, Const.Intent.ACTION_SERVICE_STOPPED)) {
                    switchButton.setCheckedNoEvent(false);
                    setServiceStatusText(false);
                } else if (TextUtils.equals(action, Const.Intent.ACTION_SERVICE_STARTED)) {
                    switchButton.setCheckedNoEvent(true);
                    setServiceStatusText(true);
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel;
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);

        channel = new NotificationChannel(Const.Notification.CHANNEL_ONGOING,
                getString(R.string.notification_channel_foreground_service),
                NotificationManager.IMPORTANCE_LOW); // IMPORTANCE_LOW : no sound
        manager.createNotificationChannel(channel);

        channel = new NotificationChannel(Const.Notification.CHANNEL_OUTPUT_DEVICE,
                getString(R.string.notification_channel_output_device),
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);

        channel = new NotificationChannel(Const.Notification.CHANNEL_VOLUME_LEVEL,
                getString(R.string.notification_channel_volume_level),
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
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
        ContextCompat.startForegroundService(this, intent);
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

    private void checkPermission() {
        String permission = Manifest.permission.READ_PHONE_STATE;

        // Check if permission is already granted
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d("checkPermission()", "Permission all granted");
            mPermissionGranted = true;
        } else {
            // Check if we should show permission request explanation
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permission_request_title)
                        .setMessage(R.string.dialog_permission_request_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog1, which) -> {
                            ActivityCompat.requestPermissions(this, new String[]{permission},
                                    PERMISSION_REQUEST_READ_PHONE_STATE);
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{permission},
                        PERMISSION_REQUEST_READ_PHONE_STATE);
            }
            mPermissionGranted = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_READ_PHONE_STATE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mPermissionGranted = true;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        boolean isNotificationClicked =
                intent.getBooleanExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED, false);
        if (isNotificationClicked) {
            if (isOreoOrHigher()) {
                showNotificationHelp();
            }
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
            if (isOreoOrHigher()) {
                showNotificationHelp();
            }
            getIntent().removeExtra(Const.Intent.EXTRA_NOTIFICATION_CLICKED);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Const.Intent.ACTION_SERVICE_STARTED);
        intentFilter.addAction(Const.Intent.ACTION_SERVICE_STOPPED);
        registerReceiver(mReceiver, intentFilter);
        Log.d("MainActivity", "Registered receiver");
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d("MainActivity", "Unregistered receiver");
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_oss_license:
                startActivity(new Intent(this, OssLicensesMenuActivity.class));
                break;
            case R.id.menu_check_permission:
                checkPermission();
                if (mPermissionGranted) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.dialog_permission_already_granted_title)
                            .setMessage(R.string.dialog_permission_already_granted_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void showNotificationHelp() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hide_foreground_service_notification_dialog_title)
                .setMessage(R.string.hide_foreground_service_notification_dialog_text)
                .setPositiveButton(android.R.string.ok, (dialog1, which) -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, Const.Notification.CHANNEL_ONGOING);

                    startActivity(intent);
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

            // Show output device in notification icon switch
            SwitchPreferenceCompat showNotiOutputDevice = findPreference(Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE);
            if (showNotiOutputDevice != null) {
                showNotiOutputDevice.setOnPreferenceClickListener(this);
            }

            // Show volume level in notification icon switch
            SwitchPreferenceCompat showNotiVolumeLevel = findPreference(Prefs.Key.SHOW_NOTI_VOL_LEVEL);
            if (showNotiVolumeLevel != null) {
                showNotiVolumeLevel.setOnPreferenceClickListener(this);
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
                if (Utils.isServiceRunning(activity, VolumeWatcherService.class)) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Const.Intent.EXTRA_ENABLE_ON_HEADSET,
                            Prefs.get().getBoolean(Prefs.Key.ENABLE_ON_HEADSET));
                    activity.startWatcherService(Const.Intent.ACTION_UPDATE_SETTINGS, bundle);
                }
            } else if (preference == findPreference(Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE)) {
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(activity, VolumeWatcherService.class)) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Const.Intent.EXTRA_SHOW_NOTI_OUTPUT_DEVICE,
                            Prefs.get().getBoolean(Prefs.Key.SHOW_NOTI_OUTPUT_DEVICE));
                    activity.startWatcherService(Const.Intent.ACTION_UPDATE_SETTINGS, bundle);
                }
            }
            if (preference == findPreference(Prefs.Key.SHOW_NOTI_VOL_LEVEL)) {
                // If the service is already running, pass data to service
                if (Utils.isServiceRunning(activity, VolumeWatcherService.class)) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Const.Intent.EXTRA_SHOW_NOTI_VOLUME_LEVEL,
                            Prefs.get().getBoolean(Prefs.Key.SHOW_NOTI_VOL_LEVEL));
                    activity.startWatcherService(Const.Intent.ACTION_UPDATE_SETTINGS, bundle);
                }
            }
            return false;
        }
    }
}