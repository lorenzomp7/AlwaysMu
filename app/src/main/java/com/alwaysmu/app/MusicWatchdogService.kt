package com.alwaysmu.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servizio in primo piano: ogni 30 secondi controlla se c'è musica in riproduzione.
 * Se non c'è, avvia l'app musicale scelta, manda "play" e imposta il volume scelto.
 */
class MusicWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var music: MusicController
    private var wakeLock: PowerManager.WakeLock? = null
    private var attemptInProgress = false

    private val checkRunnable = Runnable { runCheck() }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        music = MusicController(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.enabled = false
            setStatus("Servizio fermato")
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(getString(R.string.notif_starting))
        prefs.enabled = true

        // Avvio, riavvio (START_STICKY → intent null) o "Controlla ora": controllo subito.
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // Ciclo di controllo
    // ------------------------------------------------------------------

    private fun scheduleNext() {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, Prefs.CHECK_INTERVAL_MS)
    }

    private fun runCheck() {
        updateWakeLock()
        scheduleNext()
        if (attemptInProgress) return

        if (prefs.enforceVolume) music.applyVolume(prefs.volumePercent)

        when {
            music.isInCall() -> setStatus("Chiamata in corso: non avvio la musica")
            music.isAnyMusicPlaying() -> {
                val who = music.playingPackage()?.let { MusicApp.labelFor(it) ?: it }
                setStatus(if (who != null) "Musica in riproduzione ($who)" else "Musica in riproduzione")
            }
            else -> startPlayback()
        }
    }

    /**
     * Se l'app scelta è già aperta basta premere play sulla sua sessione.
     * Altrimenti la apriamo come farebbe l'utente e solo dopo premiamo play:
     * avviarla "al buio" col tasto PLAY (broadcast) su alcune app, es. Spotify,
     * fa partire l'audio come una chiamata (suono chiuso / Bluetooth HFP).
     */
    private fun startPlayback() {
        val app = prefs.selectedApp
        val pkg = app.installedPackage(packageManager)
        if (pkg == null) {
            setStatus("${app.label} non è installata")
            return
        }
        attemptInProgress = true
        music.applyVolume(prefs.volumePercent)

        if (music.hasSession(pkg)) {
            setStatus("Nessuna musica: premo play su ${app.label}…")
            music.playViaSession(pkg)
            handler.postDelayed({
                if (music.isAnyMusicPlaying()) finishPlaying(app) else coldStart(app, pkg)
            }, FIRST_VERIFY_DELAY_MS)
        } else {
            coldStart(app, pkg)
        }
    }

    private fun coldStart(app: MusicApp, pkg: String) {
        if (music.canLaunchFromBackground() && music.launchApp(pkg)) {
            setStatus("Nessuna musica: apro ${app.label}…")
            handler.postDelayed({
                if (music.isAnyMusicPlaying()) {
                    finishPlaying(app)
                    return@postDelayed
                }
                // L'app ora è aperta: play sulla sua sessione (versione completa) o al suo receiver
                if (!music.playViaSession(pkg) && !music.playViaMediaButton(pkg)) music.dispatchSystemPlay()
                handler.postDelayed({
                    if (music.isAnyMusicPlaying()) {
                        finishPlaying(app)
                    } else {
                        // Ultimo tentativo: tasto PLAY di sistema (va all'app multimediale appena aperta)
                        music.dispatchSystemPlay()
                        handler.postDelayed({ verifyFinal(app) }, VERIFY_DELAY_MS)
                    }
                }, VERIFY_DELAY_MS)
            }, LAUNCH_DELAY_MS)
            return
        }
        // Senza "Mostra sopra altre app" Android non ci lascia aprire l'app: ultimo tentativo col tasto PLAY.
        if (music.playViaMediaButton(pkg)) {
            setStatus("Nessuna musica: invio play a ${app.label}…")
            handler.postDelayed({ verifyFinal(app) }, LAUNCH_DELAY_MS)
        } else {
            finishAttempt("Impossibile avviare ${app.label}: concedi \"Mostra sopra altre app\"")
        }
    }

    private fun verifyFinal(app: MusicApp) {
        if (music.isAnyMusicPlaying()) {
            finishPlaying(app)
        } else {
            val hint = when {
                !music.canLaunchFromBackground() -> " (concedi \"Mostra sopra altre app\")"
                BuildConfig.HAS_MEDIA_LISTENER && !music.hasNotificationAccess() -> " (attiva l'accesso alle notifiche)"
                else -> ""
            }
            finishAttempt("${app.label} non suona ancora$hint")
        }
    }

    private fun finishPlaying(app: MusicApp) {
        music.applyVolume(prefs.volumePercent)
        val warn = if (music.isCallLikeAudio()) " ⚠ audio in modalità chiamata (Bluetooth HFP)" else ""
        finishAttempt("${app.label} avviata$warn")
    }

    private fun finishAttempt(status: String) {
        attemptInProgress = false
        setStatus(status)
    }

    // ------------------------------------------------------------------
    // Wakelock
    // ------------------------------------------------------------------

    private fun updateWakeLock() {
        if (!prefs.keepAwake) {
            releaseWakeLock()
            return
        }
        val wl = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlwaysMu:watchdog")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // Rinnovato ad ogni controllo: se il servizio muore si rilascia da solo.
        wl.acquire(Prefs.CHECK_INTERVAL_MS * 3)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------------
    // Notifica / stato
    // ------------------------------------------------------------------

    private fun setStatus(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date())
        val line = "[$time] $text"
        prefs.lastStatus = line
        if (prefs.enabled) {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(line))
        }
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(text), type)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MusicWatchdogService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title, prefs.selectedApp.label, prefs.volumePercent))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "watchdog"
        private const val NOTIF_ID = 1
        private const val FIRST_VERIFY_DELAY_MS = 3_000L
        private const val LAUNCH_DELAY_MS = 6_000L
        private const val VERIFY_DELAY_MS = 4_000L

        const val ACTION_STOP = "com.alwaysmu.app.STOP"
        const val ACTION_CHECK_NOW = "com.alwaysmu.app.CHECK_NOW"

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, MusicWatchdogService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            Prefs(context).enabled = false
            context.stopService(Intent(context, MusicWatchdogService::class.java))
        }
    }
}
