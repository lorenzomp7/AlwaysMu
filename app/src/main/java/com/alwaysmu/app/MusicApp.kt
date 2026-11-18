package com.alwaysmu.app

import android.content.pm.PackageManager

/** App musicali supportate. */
enum class MusicApp(val label: String, val packageName: String) {
    SPOTIFY("Spotify", "com.spotify.music"),
    YOUTUBE_MUSIC("YouTube Music", "com.google.android.apps.youtube.music"),
    APPLE_MUSIC("Apple Music", "com.apple.android.music"),
    DEEZER("Deezer", "deezer.android.app");

    fun isInstalled(pm: PackageManager): Boolean =
        pm.getLaunchIntentForPackage(packageName) != null

    companion object {
        fun fromName(name: String?): MusicApp =
            entries.firstOrNull { it.name == name } ?: SPOTIFY
    }
}
