package com.alwaysmu.app

import kotlin.math.roundToInt

/**
 * Conversioni tra percentuale scelta dall'utente e indice di volume Android.
 * Senza dipendenze Android, così si può testare sulla JVM.
 */
object VolumeMath {

    /** Indice di volume (0..max) corrispondente a [percent] (limitato a 0-100). */
    fun indexFor(percent: Int, max: Int): Int {
        if (max <= 0) return 0
        return (max * percent.coerceIn(0, 100) / 100.0).roundToInt().coerceIn(0, max)
    }

    /** Percentuale (0-100) corrispondente all'indice [volume] su [max]; 0 se max non è valido. */
    fun percentFor(volume: Int, max: Int): Int {
        if (max <= 0) return 0
        return (volume.coerceIn(0, max) * 100.0 / max).roundToInt()
    }
}
