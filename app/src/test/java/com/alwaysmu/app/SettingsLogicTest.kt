package com.alwaysmu.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logica pura di impostazioni e catalogo app: gira sulla JVM senza telefono. */
class SettingsLogicTest {

    @Test
    fun `intervallo mostrato in forma leggibile`() {
        assertEquals("10 s", Prefs.formatInterval(10))
        assertEquals("45 s", Prefs.formatInterval(45))
        assertEquals("2 min", Prefs.formatInterval(120))
        assertEquals("1 min 30 s", Prefs.formatInterval(90))
        assertEquals("1 h", Prefs.formatInterval(3600))
    }

    @Test
    fun `limiti dell'intervallo coerenti con la descrizione da 10 s a 1 ora`() {
        assertEquals(10, Prefs.MIN_INTERVAL_S)
        assertEquals(3600, Prefs.MAX_INTERVAL_S)
    }

    @Test
    fun `le chiavi della versione 1_0_1 vengono convertite nel pacchetto`() {
        assertEquals("com.spotify.music", MusicApp.normalizeKey("SPOTIFY"))
        assertEquals("com.google.android.apps.youtube.music", MusicApp.normalizeKey("YOUTUBE_MUSIC"))
        assertEquals("org.esempio.player", MusicApp.normalizeKey("org.esempio.player"))
        assertNull(MusicApp.normalizeKey(null))
    }

    @Test
    fun `app note riconosciute dal pacchetto`() {
        assertEquals("Spotify", MusicApp.labelFor("com.spotify.music"))
        assertEquals("Deezer", MusicApp.findKnown("deezer.android.app")?.label)
        assertNull(MusicApp.findKnown("org.esempio.player"))
    }

    @Test
    fun `catalogo senza duplicati e con Spotify come predefinita`() {
        val packages = MusicApp.KNOWN.flatMap { it.packages }
        assertEquals(packages.size, packages.toSet().size)
        assertEquals("Spotify", MusicApp.KNOWN.first().label)
        assertTrue(MusicApp.KNOWN.all { it.key == it.packages.first() })
    }
}
