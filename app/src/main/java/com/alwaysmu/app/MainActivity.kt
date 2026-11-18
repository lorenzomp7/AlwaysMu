package com.alwaysmu.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var music: MusicController

    private lateinit var appGroup: RadioGroup
    private lateinit var volumeLabel: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var enforceVolume: CheckBox
    private lateinit var keepAwake: CheckBox
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var notifAccessButton: Button
    private lateinit var overlayButton: Button
    private lateinit var batteryButton: Button

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_STATUS || key == Prefs.KEY_ENABLED) runOnUiThread { refreshStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        music = MusicController(this)

        appGroup = findViewById(R.id.appGroup)
        volumeLabel = findViewById(R.id.volumeLabel)
        volumeBar = findViewById(R.id.volumeBar)
        enforceVolume = findViewById(R.id.enforceVolume)
        keepAwake = findViewById(R.id.keepAwake)
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        notifAccessButton = findViewById(R.id.notifAccessButton)
        overlayButton = findViewById(R.id.overlayButton)
        batteryButton = findViewById(R.id.batteryButton)

        setupAppChoice()
        setupVolume()

        keepAwake.isChecked = prefs.keepAwake
        keepAwake.setOnCheckedChangeListener { _, checked -> prefs.keepAwake = checked }

        toggleButton.setOnClickListener {
            if (prefs.enabled) {
                MusicWatchdogService.stop(this)
            } else {
                askNotificationPermission()
                MusicWatchdogService.start(this)
            }
            refreshStatus()
        }
        findViewById<Button>(R.id.checkNowButton).setOnClickListener {
            MusicWatchdogService.start(this, MusicWatchdogService.ACTION_CHECK_NOW)
        }

        notifAccessButton.setOnClickListener { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        overlayButton.setOnClickListener {
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        }
        batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        findViewById<Button>(R.id.appInfoButton).setOnClickListener {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        refreshAppList()
        refreshPermissions()
        refreshStatus()
    }

    override fun onPause() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    // ---------- Scelta app ----------

    private var buildingList = false

    private fun setupAppChoice() {
        appGroup.setOnCheckedChangeListener { group, checkedId ->
            if (buildingList) return@setOnCheckedChangeListener
            val rb = group.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            // "Altra app…" (tag null) è gestita dal suo click listener
            val app = rb.tag as? MusicApp ?: return@setOnCheckedChangeListener
            if (app.key == prefs.selectedApp.key) return@setOnCheckedChangeListener
            prefs.selectedApp = app
            restartIfRunning()
        }
    }

    /** Ricostruisce l'elenco: prima le app installate, poi quelle mancanti, poi "Altra app…". */
    private fun refreshAppList() {
        buildingList = true
        appGroup.removeAllViews()
        val selected = prefs.selectedApp
        val (installed, missing) = MusicApp.KNOWN.partition { it.isInstalled(packageManager) }
        var nextId = 1000
        var checkedId = -1
        for (app in installed + missing) {
            val ok = app in installed
            val rb = RadioButton(this).apply {
                id = nextId++
                tag = app
                textSize = 16f
                text = if (ok) app.label else getString(R.string.not_installed, app.label)
                isEnabled = ok
            }
            appGroup.addView(rb)
            if (app.key == selected.key) checkedId = rb.id
        }
        val custom = MusicApp.findKnown(selected.key) == null
        val other = RadioButton(this).apply {
            id = nextId
            tag = null
            textSize = 16f
            text = if (custom) getString(R.string.other_app_selected, selected.label) else getString(R.string.other_app)
            setOnClickListener { pickOtherApp() }
        }
        appGroup.addView(other)
        if (custom) checkedId = other.id
        if (checkedId != -1) appGroup.check(checkedId)
        buildingList = false
    }

    /** Elenco di tutte le app installate, per scegliere un'app non presente nella lista. */
    private fun pickOtherApp() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this)
            .setTitle(R.string.other_app_title)
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                val (pkg, label) = apps[which]
                prefs.selectedApp = MusicApp.findKnown(pkg) ?: MusicApp(label, listOf(pkg))
                restartIfRunning()
                refreshAppList()
            }
            .setOnCancelListener { refreshAppList() }
            .show()
    }

    // ---------- Volume ----------

    private fun setupVolume() {
        volumeBar.max = 100
        volumeBar.progress = prefs.volumePercent
        updateVolumeLabel(prefs.volumePercent)
        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                updateVolumeLabel(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                prefs.volumePercent = sb.progress
                restartIfRunning()
            }
        })

        enforceVolume.isChecked = prefs.enforceVolume
        enforceVolume.setOnCheckedChangeListener { _, checked -> prefs.enforceVolume = checked }

        findViewById<Button>(R.id.applyVolumeButton).setOnClickListener {
            prefs.volumePercent = volumeBar.progress
            val ok = music.applyVolume(volumeBar.progress)
            val msg = if (ok) getString(R.string.volume_applied, music.currentVolumePercent())
            else getString(R.string.volume_failed)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateVolumeLabel(p: Int) {
        volumeLabel.text = getString(R.string.volume_label, p)
    }

    // ---------- Permessi ----------

    private fun refreshPermissions() {
        notifAccessButton.text = getString(
            if (music.hasNotificationAccess()) R.string.perm_notif_access_ok else R.string.perm_notif_access
        )
        overlayButton.text = getString(
            if (music.canLaunchFromBackground()) R.string.perm_overlay_ok else R.string.perm_overlay
        )
        val pm = getSystemService(PowerManager::class.java)
        batteryButton.text = getString(
            if (pm.isIgnoringBatteryOptimizations(packageName)) R.string.perm_battery_ok else R.string.perm_battery
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        }
    }

    private fun openSettings(action: String, data: Uri? = null) {
        try {
            startActivity(Intent(action).apply { if (data != null) this.data = data })
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ---------- Stato servizio ----------

    private fun restartIfRunning() {
        if (prefs.enabled) MusicWatchdogService.start(this, MusicWatchdogService.ACTION_CHECK_NOW)
    }

    private fun refreshStatus() {
        toggleButton.text = getString(if (prefs.enabled) R.string.stop_service else R.string.start_service)
        val status = prefs.lastStatus.ifBlank { getString(R.string.no_status) }
        statusText.text = getString(
            if (prefs.enabled) R.string.status_running else R.string.status_stopped, status
        )
    }
}
