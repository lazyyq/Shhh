package kyklab.quiet.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.quiet.*
import kyklab.quiet.databinding.ActivityMainBinding
import kyklab.quiet.service.VolumeWatcherService
import kyklab.quiet.utils.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Const.Intents.ACTION_SERVICE_STOPPED ->
                    updateUi(false, true)
                Const.Intents.ACTION_SERVICE_STARTED ->
                    updateUi(true, true)
            }
        }
    }
    private val localIntentFilter = IntentFilter().apply {
        addAction(Const.Intents.ACTION_SERVICE_STARTED)
        addAction(Const.Intents.ACTION_SERVICE_STOPPED)
    }
    private var adView: AdView? = null
    private var adInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Create notification channel
        if (isOreoOrHigher) {
            createNotificationChannel()
        }

        initAd()
        if (!PermissionManager.checkPermission(this)) {
            requestPermissions(REQ_CODE_APP_LAUNCH)
        }

        binding.switchButton.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                VolumeWatcherService.startService(this)
                updateUi(VolumeWatcherService.isRunning(this), false)
            } else {
                VolumeWatcherService.stopService(this)
                updateUi(false, false)
            }
        }
        binding.mainLayout.setOnClickListener { v -> binding.switchButton.toggle() }

        // Check if battery optimization is properly turned off
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkBatteryOptimization()
        }

        // Resume service if it was originally running
        if (Prefs.serviceEnabled && PermissionManager.checkPermission(this)) {
            if (!VolumeWatcherService.isRunning(this)) {
                VolumeWatcherService.startService(this)
            }
        } else {
            Prefs.serviceEnabled = false;
        }
        Prefs.firstLaunch = false
    }

    private fun initAd() {
        if (Prefs.firstLaunch) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_show_ad_title)
                .setMessage(R.string.dialog_show_ad_message)
                .setNegativeButton(R.string.no) { dialog, which ->
                    Prefs.showAd = false
                    hideAd()
                }
                .setPositiveButton(R.string.yes) { dialog, which ->
                    Prefs.showAd = true
                    showAd()
                }
                .setCancelable(false)
                .show()
        } else if (Prefs.showAd) {
            showAd()
        }
    }

    private fun showAd() {
        if (adView == null) {
            adView = AdView(this).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = getString(
                    if (debug) R.string.banner_ad_unit_debug_id else R.string.banner_ad_unit_id
                )
            }
        }
        if (adView!!.parent == null) {
            binding.adContainer.addView(adView)
        }
        lifecycleScope.launch(Dispatchers.Default) {
            if (!adInitialized) {
                MobileAds.initialize(this@MainActivity)
                adInitialized = true
            }
            val adRequest = AdRequest.Builder().build()
            launch(Dispatchers.Main) { adView!!.loadAd(adRequest) }
        }
    }

    private fun hideAd() {
        binding.adContainer.removeView(adView)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channels: Array<NotificationChannel> = arrayOf(
            NotificationChannel(
                Const.Notification.CHANNEL_ONGOING,
                getString(R.string.notification_channel_foreground_service),
                NotificationManager.IMPORTANCE_LOW
            ),  // IMPORTANCE_LOW : no sound
            NotificationChannel(
                Const.Notification.CHANNEL_OUTPUT_DEVICE,
                getString(R.string.notification_channel_output_device),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                Const.Notification.CHANNEL_VOLUME_LEVEL,
                getString(R.string.notification_channel_volume_level),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                Const.Notification.CHANNEL_FORCE_MUTE,
                getString(R.string.notification_channel_force_mute),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val manager = NotificationManagerCompat.from(this)
        channels.forEach { manager.createNotificationChannel(it) }
    }

    private fun updateUi(
        isServiceEnabled: Boolean = Prefs.serviceEnabled,
        updateSwitch: Boolean = true
    ) {
        binding.switchButton.setCheckedNoEvent(isServiceEnabled)
        setServiceStatusText(isServiceEnabled)
    }

    private fun setServiceStatusText(serviceEnabled: Boolean) {
        if (serviceEnabled) {
            binding.tvServiceStatus.setText(R.string.service_status_on)
            binding.tvServiceDesc.setText(R.string.service_desc_on)
        } else {
            binding.tvServiceStatus.setText(R.string.service_status_off)
            binding.tvServiceDesc.setText(R.string.service_desc_off)
        }
    }

    private fun requestPermissions(reqCode: Int = REQ_CODE_NORMAL) {
        val rationale =
            PermissionManager.permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

        if (rationale) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_permission_request_title)
                .setMessage(R.string.dialog_permission_request_message)
                .setCancelable(false)
                .setPositiveButton(
                    android.R.string.ok
                ) { dialog1, which ->
                    ActivityCompat.requestPermissions(
                        this, PermissionManager.permissions, reqCode
                    )
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this, PermissionManager.permissions, reqCode
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkBatteryOptimization() {
        if (isBatteryOptimizationEnabled) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_battery_optimization_title)
                .setMessage(R.string.dialog_battery_optimization_message)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    openIgnoreBatteryOptimizationsSettings()
                }
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_CODE_NORMAL) {
            if (grantResults.isNotEmpty() && grantResults.none { it != PackageManager.PERMISSION_GRANTED}) {
                Toast.makeText(this, R.string.msg_permission_all_granted, Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQ_CODE_APP_LAUNCH) {
            Toast.makeText(this, R.string.msg_necessary_permission_missing, Toast.LENGTH_SHORT).show()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val isNotificationClicked =
            intent?.getBooleanExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED, false) ?: false
        if (isNotificationClicked) {
            if (isOreoOrHigher) {
                showNotificationHelp()
            }
            this@MainActivity.intent.removeExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        Log.d(
            "MainActivity", "intent:" +
                    intent.getBooleanExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED, false)
        )
        val isNotificationClicked =
            intent.getBooleanExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED, false)
        if (isNotificationClicked) {
            if (isOreoOrHigher) {
                showNotificationHelp()
            }
            intent.removeExtra(Const.Intents.EXTRA_NOTIFICATION_CLICKED)
        }
        lbm.registerReceiver(localBroadcastReceiver, localIntentFilter)
        Log.d("MainActivity", "Registered receiver")
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "Unregistered receiver")
        lbm.unregisterReceiver(localBroadcastReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_oss_license -> startActivity(
                Intent(
                    this,
                    OssLicensesMenuActivity::class.java
                )
            )
            R.id.menu_check_permission -> {
                if (PermissionManager.checkPermission(this)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permission_already_granted_title)
                        .setMessage(R.string.dialog_permission_already_granted_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permission_missing_allow_in_following_title)
                        .setMessage(R.string.dialog_permission_missing_allow_in_following_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            openAppSettings()
                        }
                        .show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun showNotificationHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hide_foreground_service_notification_dialog_title)
            .setMessage(R.string.hide_foreground_service_notification_dialog_text)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog1, which ->
                val intent = Intent().apply {
                    action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    putExtra(Settings.EXTRA_CHANNEL_ID, Const.Notification.CHANNEL_ONGOING)
                }
                startActivity(intent)
            }
            .create()
            .show()
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
        private val openNotiSettings: Preference? by lazy { findPreference(Prefs.Key.OPEN_NOTI_SETTINGS) }
        private val forceMuteFrom: TimePreference? by lazy { findPreference(Prefs.Key.FORCE_MUTE_FROM) }
        private val forceMuteTo: TimePreference? by lazy { findPreference(Prefs.Key.FORCE_MUTE_TO) }
        private val showAd: SwitchPreferenceCompat? by lazy { findPreference(Prefs.Key.SHOW_AD) }
        private var batteryOptimizationCategory: PreferenceCategory? = null

        override fun onPause() {
            Prefs.unregisterPrefChangeListener(this)
            super.onPause()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            if (isOreoOrHigher) {
                openNotiSettings!!.onPreferenceClickListener = this
            } else {
                openNotiSettings!!.isVisible = false
            }

            // Manually cause crash to test crashlytics in debug builds
            if (BuildConfig.DEBUG) {
                val category = PreferenceCategory(requireContext()).apply {
                    title = "DEBUG"
                }
                preferenceScreen.addPreference(category)
                val preference = Preference(requireContext()).apply {
                    title = "CRASH!"
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener { p: Preference? ->
                            throw RuntimeException("CRASH!")
                        }
                }
                preferenceScreen.addPreference(preference)
            }

            // Preference for opening battery optimization settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryOptimizationCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(R.string.settings_category_app_setup)
                    order = 0
                    isIconSpaceReserved = false
                    isVisible = false
                }
                val preference = Preference(requireContext()).apply {
                    title = getString(R.string.settings_ignore_battery_optimization_title)
                    summary = getString(R.string.settings_ignore_battery_optimization_summary)
                    isIconSpaceReserved = false
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener { p: Preference ->
                            context.openIgnoreBatteryOptimizationsSettings()
                            true
                        }
                }
                preferenceScreen.addPreference(batteryOptimizationCategory!!)
                batteryOptimizationCategory!!.addPreference(preference)
            }
        }

        override fun onResume() {
            super.onResume()
            Prefs.registerPrefChangeListener(this)
            updateForceMuteFromSummary()
            updateForceMuteToSummary()
            updateForceMuteScheduleVisibility()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryOptimizationCategory?.isVisible =
                    context?.isBatteryOptimizationEnabled == true
            }
        }

        override fun onPreferenceClick(preference: Preference): Boolean {
            if (preference === openNotiSettings) {
                if (isOreoOrHigher) {
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    }
                    startActivity(intent)
                } else {
                    val context = context ?: return false
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.dialog_available_oreo_or_higher_title)
                        .setMessage(R.string.dialog_available_oreo_or_higher_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            return false
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Prefs.Key.SHOW_AD -> {
                    val activity = activity as MainActivity? ?: return
                    if (showAd != null) {
                        if (Prefs.showAd) {
                            activity.showAd()
                            if (!showAd!!.isChecked) showAd!!.isChecked = true
                            Toast.makeText(activity, "♡", Toast.LENGTH_SHORT).show()
                        } else {
                            activity.hideAd()
                            if (showAd!!.isChecked) showAd!!.isChecked = false
                        }
                    }
                }
                Prefs.Key.FORCE_MUTE_WHEN -> {
                    updateForceMuteScheduleVisibility()
                }
                Prefs.Key.FORCE_MUTE_FROM -> {
                    updateForceMuteFromSummary()
                }
                Prefs.Key.FORCE_MUTE_TO -> {
                    updateForceMuteToSummary()
                }
            }
        }

        private fun updateForceMuteFromSummary() {
            forceMuteFrom?.summary = getTimeFromMinutes(
                Prefs.forceMuteFrom, DateFormat.SHORT
            )
        }

        private fun updateForceMuteToSummary() {
            forceMuteTo?.summary = getTimeFromMinutes(
                Prefs.forceMuteTo, DateFormat.SHORT
            )
        }

        private fun updateForceMuteScheduleVisibility(
            visible: Boolean = Prefs.forceMuteWhen == Prefs.Value.FORCE_MUTE_WHEN_SCHEDULED
        ) {
            forceMuteFrom?.isVisible = visible
            forceMuteTo?.isVisible = visible
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            var dialogFragment: DialogFragment? = null
            if (preference is TimePreference) {
                dialogFragment = TimePreferenceDialogFragmentCompat.newInstance(preference.getKey())
            }
            dialogFragment?.apply {
                setTargetFragment(this@SettingsFragment, 0)
                show(requireFragmentManager(), null)
            }
            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0)
                if (fragmentManager != null) {
                    dialogFragment.show(requireFragmentManager(), null)
                }
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        companion object {
            private fun getTimeFromMinutes(
                totalMins: Int,
                style: Int = DateFormat.MEDIUM
            ): CharSequence {
                val hours = totalMins / 60
                val mins = totalMins % 60
                val secs = 0
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hours)
                    set(Calendar.MINUTE, mins)
                    set(Calendar.SECOND, secs)
                }
                val date = cal.time
                val format = SimpleDateFormat.getTimeInstance(style)
                return format.format(date)
            }
        }
    }

    companion object {
        private const val REQ_CODE_NORMAL = 100
        private const val REQ_CODE_APP_LAUNCH = 101
    }
}