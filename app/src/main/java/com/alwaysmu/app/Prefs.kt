package com.alwaysmu.app

import android.content.Context
import android.content.SharedPreferences

/** Impostazioni persistenti dell'app. */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** App scelta: una delle app note oppure un'app qualsiasi scelta con "Altra app…". */
    var selectedApp: MusicApp
        get() {
            val pkg = MusicApp.normalizeKey(sp.getString(KEY_APP, null)) ?: return MusicApp.KNOWN.first()
            MusicApp.findKnown(pkg)?.let { return it }
            return MusicApp(sp.getString(KEY_CUSTOM_LABEL, null) ?: pkg, listOf(pkg))
        }
        set(v) = sp.edit()
            .putString(KEY_APP, v.key)
            .putString(KEY_CUSTOM_LABEL, if (MusicApp.findKnown(v.key) == null) v.label else null)
            .apply()

    /** Volume desiderato in percentuale (0-100) del volume multimediale massimo. */
    var volumePercent: Int
        get() = sp.getInt(KEY_VOLUME, 50)
        set(v) = sp.edit().putInt(KEY_VOLUME, v.coerceIn(0, 100)).apply()

    /** Se true il volume viene reimpostato ad ogni controllo, non solo quando la musica viene avviata. */
    var enforceVolume: Boolean
        get() = sp.getBoolean(KEY_ENFORCE_VOLUME, false)
        set(v) = sp.edit().putBoolean(KEY_ENFORCE_VOLUME, v).apply()

    /** Tiene la CPU sveglia per controllare ogni 30 s anche a schermo spento. */
    var keepAwake: Boolean
        get() = sp.getBoolean(KEY_KEEP_AWAKE, true)
        set(v) = sp.edit().putBoolean(KEY_KEEP_AWAKE, v).apply()

    /** Il servizio deve essere attivo (anche dopo un riavvio del telefono). */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    var lastStatus: String
        get() = sp.getString(KEY_STATUS, "") ?: ""
        set(v) = sp.edit().putString(KEY_STATUS, v).apply()

    companion object {
        private const val NAME = "alwaysmu"
        const val KEY_APP = "selected_app"
        const val KEY_CUSTOM_LABEL = "custom_label"
        const val KEY_VOLUME = "volume_percent"
        const val KEY_ENFORCE_VOLUME = "enforce_volume"
        const val KEY_KEEP_AWAKE = "keep_awake"
        const val KEY_ENABLED = "enabled"
        const val KEY_STATUS = "last_status"

        const val CHECK_INTERVAL_MS = 30_000L
    }
}
