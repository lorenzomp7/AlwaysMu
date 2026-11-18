package com.alwaysmu.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt

/**
 * Tutte le operazioni "a basso livello": capire se c'è musica, impostare il volume,
 * mandare il comando play all'app scelta, aprire l'app.
 */
class MusicController(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)

    // ---------- Permessi ----------

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun canLaunchFromBackground(): Boolean = Settings.canDrawOverlays(context)

    // ---------- Stato riproduzione ----------

    private fun activeControllers(): List<MediaController> {
        if (!hasNotificationAccess()) return emptyList()
        return try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** true se una qualsiasi app sta riproducendo audio multimediale. */
    fun isAnyMusicPlaying(): Boolean {
        if (audio.isMusicActive) return true
        return activeControllers().any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    /** Nome dell'app che sta suonando, se ricavabile dalle sessioni multimediali. */
    fun playingPackage(): String? =
        activeControllers().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.packageName

    /** Durante chiamate / suoneria non vogliamo far partire la musica. */
    fun isInCall(): Boolean = when (audio.mode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    // ---------- Volume ----------

    fun applyVolume(percent: Int): Boolean {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0, 100) / 100.0).roundToInt().coerceIn(0, max)
        return try {
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != target) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
            true
        } catch (e: SecurityException) {
            // Es. modalità "Non disturbare" attiva
            false
        }
    }

    fun currentVolumePercent(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max == 0) return 0
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100.0 / max).roundToInt()
    }

    // ---------- Avvio riproduzione ----------

    /** Play tramite la sessione multimediale dell'app (richiede accesso alle notifiche). */
    fun playViaSession(pkg: String): Boolean {
        val controller = activeControllers().firstOrNull { it.packageName == pkg } ?: return false
        controller.transportControls.play()
        return true
    }

    /** Invia il tasto PLAY direttamente al MediaButtonReceiver dell'app scelta. */
    fun playViaMediaButton(pkg: String): Boolean {
        val probe = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
        val receivers = context.packageManager.queryBroadcastReceivers(probe, 0)
        val info = receivers.firstOrNull()?.activityInfo ?: return false
        val component = ComponentName(info.packageName, info.name)
        sendKeyBroadcast(component, KeyEvent.ACTION_DOWN)
        sendKeyBroadcast(component, KeyEvent.ACTION_UP)
        return true
    }

    private fun sendKeyBroadcast(component: ComponentName, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(component)
            .putExtra(Intent.EXTRA_KEY_EVENT, event)
        context.sendBroadcast(intent)
    }

    /** Tasto PLAY "di sistema": va all'ultima app multimediale attiva. */
    fun dispatchSystemPlay() {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }

    /** Apre l'app musicale. Da background funziona solo con il permesso "mostra sopra altre app". */
    fun launchApp(pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
