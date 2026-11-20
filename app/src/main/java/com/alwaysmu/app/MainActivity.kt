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
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var music: MusicController

    private lateinit var appMenu: MaterialAutoCompleteTextView
    private lateinit var appMenuNote: TextView
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

        appMenu = findViewById(R.id.appMenu)
        appMenuNote = findViewById(R.id.appMenuNote)
        applyInsets()
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

        setupInterval()

        findViewById<CheckBox>(R.id.openAppFirst).apply {
            isChecked = prefs.openAppFirst
            setOnCheckedChangeListener { _, checked -> prefs.openAppFirst = checked }
        }

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

        if (!BuildConfig.HAS_MEDIA_LISTENER) notifAccessButton.visibility = View.GONE
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

    /** Voci del menù: app installate (+ eventuale app scelta con "Altra app…"); null = "Altra app…". */
    private var menuEntries: List<MusicApp?> = emptyList()

    private fun setupAppChoice() {
        appMenu.setOnItemClickListener { _, _, position, _ ->
            val app = menuEntries.getOrNull(position)
            if (app == null) {
                pickOtherApp()
                return@setOnItemClickListener
            }
            if (app.key != prefs.selectedApp.key) {
                prefs.selectedApp = app
                restartIfRunning()
            }
            refreshAppList()
        }
    }

    /** Ricostruisce il menù a tendina con le app installate e mostra la scelta attuale. */
    private fun refreshAppList() {
        val selected = prefs.selectedApp
        val (installed, missing) = MusicApp.KNOWN.partition { it.isInstalled(packageManager) }
        val entries = mutableListOf<MusicApp?>()
        entries += installed
        if (installed.none { it.key == selected.key }) entries += selected
        entries += null
        menuEntries = entries

        val labels = entries.map { app ->
            when {
                app == null -> getString(R.string.other_app)
                MusicApp.findKnown(app.key) == null -> getString(R.string.other_app_selected, app.label)
                !app.isInstalled(packageManager) -> getString(R.string.not_installed, app.label)
                else -> app.label
            }
        }
        appMenu.setSimpleItems(labels.toTypedArray())
        val idx = entries.indexOfFirst { it?.key == selected.key }
        appMenu.setText(labels.getOrNull(idx) ?: selected.label, false)

        appMenuNote.text = if (missing.isEmpty()) "" else
            getString(R.string.app_menu_note, missing.joinToString(", ") { it.label })
        appMenuNote.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
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

    /** Android 15 disegna l'app sotto barra di stato e di navigazione: lasciamo lo spazio giusto. */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            findViewById<View>(R.id.scroll).setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    // ---------- Intervallo ----------

    private fun setupInterval() {
        val label = findViewById<TextView>(R.id.intervalLabel)
        val input = findViewById<EditText>(R.id.intervalInput)
        fun show() {
            label.text = getString(R.string.interval_label, Prefs.formatInterval(prefs.intervalSeconds))
            input.setText(prefs.intervalSeconds.toString())
        }
        show()
        input.setOnEditorActionListener { _, _, _ ->
            findViewById<Button>(R.id.intervalButton).performClick()
            true
        }
        findViewById<Button>(R.id.intervalButton).setOnClickListener {
            val v = input.text.toString().trim().toIntOrNull()
            if (v == null || v < Prefs.MIN_INTERVAL_S || v > Prefs.MAX_INTERVAL_S) {
                Toast.makeText(this, R.string.interval_invalid, Toast.LENGTH_LONG).show()
                show()
                return@setOnClickListener
            }
            prefs.intervalSeconds = v
            show()
            hideKeyboard(input)
            Toast.makeText(this, getString(R.string.interval_saved, Prefs.formatInterval(v)), Toast.LENGTH_SHORT).show()
            restartIfRunning()
        }
    }

    /** Chiude la tastiera e toglie il cursore dal campo dei secondi. */
    private fun hideKeyboard(field: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
        findViewById<View>(R.id.root).requestFocus()
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
