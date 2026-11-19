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
import android.os.SystemClock
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
    /** Quando AlwaysMu ha aperto l'app l'ultima volta (0 = mai / musica partita). */
    private var lastLaunchAt = 0L

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
        handler.postDelayed(checkRunnable, prefs.intervalSeconds * 1000L)
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
     * Come nella prima versione: prima si preme PLAY senza aprire nulla
     * (sessione multimediale, tasto PLAY all'app). Solo se dopo qualche secondo
     * non suona ancora si apre l'app. Con l'opzione "Apri l'app prima di premere play"
     * si apre subito (utile se il play "al buio" fa partire l'audio come una chiamata).
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

        if (prefs.openAppFirst && !music.hasSession(pkg)) {
            openAndPlay(app, pkg)
            return
        }
        setStatus("Nessuna musica rilevata: invio play a ${app.label}…")
        if (!music.playViaSession(pkg) && !music.playViaMediaButton(pkg)) music.dispatchSystemPlay()
        handler.postDelayed({
            if (music.isAnyMusicPlaying()) finishPlaying(app) else openAndPlay(app, pkg)
        }, FIRST_VERIFY_DELAY_MS)
    }

    /** Apre l'app e poi preme play. Non la riapre se è stata aperta da poco senza successo. */
    private fun openAndPlay(app: MusicApp, pkg: String) {
        val now = SystemClock.elapsedRealtime()
        val openedRecently = lastLaunchAt != 0L && now - lastLaunchAt < RELAUNCH_COOLDOWN_MS
        if (openedRecently) {
            // Evita di riaprire l'app ad ogni controllo: solo tasto PLAY di sistema.
            music.dispatchSystemPlay()
            handler.postDelayed({
                verifyFinal(app, " (${app.label} già aperta da poco: la riapro tra qualche minuto)")
            }, VERIFY_DELAY_MS)
            return
        }
        if (music.canLaunchFromBackground() && music.launchApp(pkg)) {
            lastLaunchAt = now
            setStatus("Nessuna musica: apro ${app.label}…")
            handler.postDelayed({
                if (music.isAnyMusicPlaying()) {
                    finishPlaying(app)
                    return@postDelayed
                }
                if (!music.playViaSession(pkg) && !music.playViaMediaButton(pkg)) music.dispatchSystemPlay()
                handler.postDelayed({
                    if (music.isAnyMusicPlaying()) {
                        finishPlaying(app)
                    } else {
                        music.dispatchSystemPlay()
                        handler.postDelayed({ verifyFinal(app) }, VERIFY_DELAY_MS)
                    }
                }, VERIFY_DELAY_MS)
            }, LAUNCH_DELAY_MS)
            return
        }
        // Senza "Mostra sopra altre app" Android non ci lascia aprire l'app: ultimo tentativo col tasto PLAY.
        if (music.playViaMediaButton(pkg)) {
            handler.postDelayed({ verifyFinal(app) }, VERIFY_DELAY_MS)
        } else {
            finishAttempt("Impossibile avviare ${app.label}: concedi \"Mostra sopra altre app\"")
        }
    }

    private fun verifyFinal(app: MusicApp, extra: String = "") {
        if (music.isAnyMusicPlaying()) {
            finishPlaying(app)
        } else {
            val hint = when {
                !music.canLaunchFromBackground() -> " (concedi \"Mostra sopra altre app\")"
                BuildConfig.HAS_MEDIA_LISTENER && !music.hasNotificationAccess() -> " (attiva l'accesso alle notifiche)"
                else -> ""
            }
            finishAttempt("Nessuna musica rilevata dopo il play su ${app.label}$hint$extra")
        }
    }

    private fun finishPlaying(app: MusicApp) {
        lastLaunchAt = 0L
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
        wl.acquire(prefs.intervalSeconds * 2000L + 60_000L)
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
            .setContentTitle(getString(
                R.string.notif_title, prefs.selectedApp.label, prefs.volumePercent,
                Prefs.formatInterval(prefs.intervalSeconds)
            ))
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
        /** Dopo un'apertura senza musica, non riaprire l'app prima di 5 minuti. */
        private const val RELAUNCH_COOLDOWN_MS = 5 * 60_000L

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
