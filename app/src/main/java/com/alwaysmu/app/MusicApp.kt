package com.alwaysmu.app

import android.content.pm.PackageManager

/**
 * Un'app che AlwaysMu può avviare. [packages] contiene i nomi di pacchetto possibili
 * (alcune app hanno varianti); viene usato il primo installato.
 */
data class MusicApp(val label: String, val packages: List<String>) {

    /** Nome di pacchetto installato, o null se l'app non è sul telefono. */
    fun installedPackage(pm: PackageManager): String? =
        packages.firstOrNull { pm.getLaunchIntentForPackage(it) != null }

    fun isInstalled(pm: PackageManager): Boolean = installedPackage(pm) != null

    /** Chiave salvata nelle impostazioni. */
    val key: String get() = packages.first()

    companion object {
        val KNOWN: List<MusicApp> = listOf(
            MusicApp("Spotify", listOf("com.spotify.music")),
            MusicApp("Amazon Music", listOf("com.amazon.mp3")),
            MusicApp("Deezer", listOf("deezer.android.app")),
            MusicApp("Tidal", listOf("com.aspiro.tidal")),
            MusicApp("Apple Music", listOf("com.apple.android.music")),
            MusicApp("YouTube Music", listOf("com.google.android.apps.youtube.music")),
            MusicApp("SoundCloud", listOf("com.soundcloud.android")),
            MusicApp("Qobuz", listOf("com.qobuz.music")),
            MusicApp("Idagio", listOf("com.idagio.app")),
            MusicApp("Mixcloud", listOf("com.mixcloud.player")),
            MusicApp("Audiomack", listOf("com.audiomack")),
            MusicApp("Bandcamp", listOf("com.bandcamp.android")),
            MusicApp("Brave Browser", listOf("com.brave.browser")),
        )

        /** Vecchie chiavi (versione 1.0.1) → pacchetto. */
        private val LEGACY = mapOf(
            "SPOTIFY" to "com.spotify.music",
            "YOUTUBE_MUSIC" to "com.google.android.apps.youtube.music",
            "APPLE_MUSIC" to "com.apple.android.music",
            "DEEZER" to "deezer.android.app",
        )

        fun normalizeKey(key: String?): String? = LEGACY[key] ?: key

        fun findKnown(pkg: String): MusicApp? = KNOWN.firstOrNull { pkg in it.packages }

        /** Nome leggibile per un pacchetto (app note) oppure null. */
        fun labelFor(pkg: String): String? = findKnown(pkg)?.label
    }
}
