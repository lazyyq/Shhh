package kyklab.quiet;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kyleduo.switchbutton.SwitchButton;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static kyklab.quiet.Utils.isDebug;
import static kyklab.quiet.Utils.isOreoOrHigher;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_READ_PHONE_STATE = 100;

    private BroadcastReceiver mReceiver;

    private TextView tvServiceStatus, tvServiceDesc;
    private SwitchButton mSwitchButton;

    private FrameLayout mAdContainer;
    private AdView mAdView;
    private Runnable mShowAdRunnable;
    private boolean mAdInitialized;

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

        initAd();
        checkPermission();

        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvServiceDesc = findViewById(R.id.tv_service_desc);

        mSwitchButton = findViewById(R.id.switchButton);
        mSwitchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                VolumeWatcherService.startService(this);
                updateUi(true, false);
            } else {
                VolumeWatcherService.stopService(this);
                updateUi(false, false);
            }
        });

        ConstraintLayout mainLayout = findViewById(R.id.mainLayout);
        mainLayout.setOnClickListener(v -> mSwitchButton.toggle());

        // Resume service if it was originally running
        if (Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED)) {
            if (!VolumeWatcherService.isRunning(this)) {
                VolumeWatcherService.startService(this);
            }
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (TextUtils.equals(action, Const.Intent.ACTION_SERVICE_STOPPED)) {
                    updateUi(false, true);
                } else if (TextUtils.equals(action, Const.Intent.ACTION_SERVICE_STARTED)) {
                    updateUi(true, true);
                }
            }
        };

        Prefs.get().setBoolean(Prefs.Key.FIRST_LAUNCH, false);
    }

    private void initAd() {
        if (Prefs.get().getBoolean(Prefs.Key.FIRST_LAUNCH)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_show_ad_title)
                    .setMessage(R.string.dialog_show_ad_message)
                    .setNegativeButton(R.string.no, (dialog, which) -> {
                        Prefs.get().setBoolean(Prefs.Key.SHOW_AD, false);
                        hideAd();
                    })
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        Prefs.get().setBoolean(Prefs.Key.SHOW_AD, true);
                        showAd();
                    })
                    .setCancelable(false)
                    .show();
        } else if (Prefs.get().getBoolean(Prefs.Key.SHOW_AD)) {
            showAd();
        }
    }

    private void showAd() {
        if (mShowAdRunnable == null) {
            mShowAdRunnable = () -> {
                if (!mAdInitialized) {
                    MobileAds.initialize(MainActivity.this);
                    mAdInitialized = true;
                }
                AdRequest adRequest = new AdRequest.Builder().build();
                runOnUiThread(() -> mAdView.loadAd(adRequest));
            };
        }
        if (mAdContainer == null) {
            mAdContainer = findViewById(R.id.adContainer);
        }
        if (mAdView == null) {
            mAdView = new AdView(this);
            mAdView.setAdSize(AdSize.BANNER);
            mAdView.setAdUnitId(getString(
                    isDebug() ? R.string.banner_ad_unit_debug_id : R.string.banner_ad_unit_id));
        }
        if (mAdView.getParent() == null) {
            mAdContainer.addView(mAdView);
        }
        new Thread(mShowAdRunnable).start();
    }

    private void hideAd() {
        if (mAdContainer != null) {
            mAdContainer.removeView(mAdView);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel[] channels = {
                new NotificationChannel(Const.Notification.CHANNEL_ONGOING,
                        getString(R.string.notification_channel_foreground_service),
                        NotificationManager.IMPORTANCE_LOW), // IMPORTANCE_LOW : no sound
                new NotificationChannel(Const.Notification.CHANNEL_OUTPUT_DEVICE,
                        getString(R.string.notification_channel_output_device),
                        NotificationManager.IMPORTANCE_LOW),
                new NotificationChannel(Const.Notification.CHANNEL_VOLUME_LEVEL,
                        getString(R.string.notification_channel_volume_level),
                        NotificationManager.IMPORTANCE_LOW),
                new NotificationChannel(Const.Notification.CHANNEL_FORCE_MUTE,
                        getString(R.string.notification_channel_force_mute),
                        NotificationManager.IMPORTANCE_LOW)
        };

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        for (NotificationChannel channel : channels) {
            manager.createNotificationChannel(channel);
        }
    }

    private void updateUi() {
        updateUi(Prefs.get().getBoolean(Prefs.Key.SERVICE_ENABLED), true);
    }

    private void updateUi(boolean isServiceEnabled, boolean updateSwitch) {
        mSwitchButton.setCheckedNoEvent(isServiceEnabled);
        setServiceStatusText(isServiceEnabled);
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

        updateUi();
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
            implements Preference.OnPreferenceClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

        private Preference mOpenNotiSettings;
        private TimePreference mForceMuteFrom, mForceMuteTo;

        @Override
        public void onPause() {
            Prefs.unregisterPrefChangeListener(this);
            super.onPause();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            mOpenNotiSettings = findPreference(Prefs.Key.OPEN_NOTI_SETTINGS);
            mForceMuteFrom = findPreference(Prefs.Key.FORCE_MUTE_FROM);
            mForceMuteTo = findPreference(Prefs.Key.FORCE_MUTE_TO);

            if (isOreoOrHigher()) {
                mOpenNotiSettings.setOnPreferenceClickListener(this);
            } else {
                mOpenNotiSettings.setVisible(false);
            }

            // Manually cause crash to test crashlytics in debug builds
            if (BuildConfig.DEBUG) {
                PreferenceCategory category = new PreferenceCategory(requireContext());
                category.setTitle("DEBUG");
                getPreferenceScreen().addPreference(category);
                Preference preference = new Preference(requireContext());
                preference.setTitle("CRASH!");
                preference.setOnPreferenceClickListener(p -> {
                    throw new RuntimeException("CRASH!");
                });
                getPreferenceScreen().addPreference(preference);
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            Prefs.registerPrefChangeListener(this);

            updateForceMuteFromSummary();
            updateForceMuteToSummary();
            updateForceMuteScheduleVisibility(null);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == mOpenNotiSettings) {
                if (isOreoOrHigher()) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
                    startActivity(intent);
                } else {
                    Context context = getContext();
                    if (context == null) {
                        return false;
                    }
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.dialog_available_oreo_or_higher_title)
                            .setMessage(R.string.dialog_available_oreo_or_higher_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (TextUtils.equals(key, Prefs.Key.SHOW_AD)) {
                MainActivity activity = (MainActivity) getActivity();
                if (activity == null) {
                    return;
                }
                SwitchPreferenceCompat pref = findPreference(key);
                if (pref != null) {
                    boolean value = Prefs.get().getBoolean(key);
                    if (value) {
                        activity.showAd();
                        if (!pref.isChecked()) pref.setChecked(true);
                        Toast.makeText(activity, "♡", Toast.LENGTH_SHORT).show();
                    } else {
                        activity.hideAd();
                        if (pref.isChecked()) pref.setChecked(false);
                    }
                }

            } else if (TextUtils.equals(key, Prefs.Key.FORCE_MUTE_WHEN)) {
                updateForceMuteScheduleVisibility(null);

            } else if (TextUtils.equals(key, Prefs.Key.FORCE_MUTE_FROM)) {
                updateForceMuteFromSummary();

            } else if (TextUtils.equals(key, Prefs.Key.FORCE_MUTE_TO)) {
                updateForceMuteToSummary();

            }
        }

        private void updateForceMuteFromSummary() {
            mForceMuteFrom.setSummary(getTimeFromMinutes(
                    Prefs.get().getInt(Prefs.Key.FORCE_MUTE_FROM), DateFormat.SHORT));
        }

        private void updateForceMuteToSummary() {
            mForceMuteTo.setSummary(getTimeFromMinutes(
                    Prefs.get().getInt(Prefs.Key.FORCE_MUTE_TO), DateFormat.SHORT));
        }

        private void updateForceMuteScheduleVisibility(@Nullable Boolean visible) {
            visible = visible != null ? visible : Prefs.get().getString(Prefs.Key.FORCE_MUTE_WHEN)
                    .equals(Prefs.Value.FORCE_MUTE_WHEN_SCHEDULED);
            mForceMuteFrom.setVisible(visible);
            mForceMuteTo.setVisible(visible);
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            DialogFragment dialogFragment = null;
            if (preference instanceof TimePreference) {
                dialogFragment = TimePreferenceDialogFragmentCompat.newInstance(preference.getKey());
            }
            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                if (getFragmentManager() != null) {
                    dialogFragment.show(getFragmentManager(), null);
                }
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        private static CharSequence getTimeFromMinutes(int totalMins, @Nullable Integer style) {
            int hours = totalMins / 60, mins = totalMins % 60, secs = 0;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hours);
            cal.set(Calendar.MINUTE, mins);
            cal.set(Calendar.SECOND, secs);
            Date date = cal.getTime();
            DateFormat format = SimpleDateFormat.getTimeInstance(style != null ? style : DateFormat.MEDIUM);
            return format.format(date);
        }
    }
}