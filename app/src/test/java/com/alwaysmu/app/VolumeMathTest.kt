package com.alwaysmu.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMathTest {

    @Test
    fun `percentuale convertita nell'indice piu vicino`() {
        assertEquals(8, VolumeMath.indexFor(50, 15))   // 7.5 -> 8
        assertEquals(0, VolumeMath.indexFor(0, 15))
        assertEquals(15, VolumeMath.indexFor(100, 15))
        assertEquals(5, VolumeMath.indexFor(33, 15))   // 4.95 -> 5
    }

    @Test
    fun `percentuale fuori scala limitata a 0-100`() {
        assertEquals(15, VolumeMath.indexFor(150, 15))
        assertEquals(0, VolumeMath.indexFor(-20, 15))
    }

    @Test
    fun `volume massimo non valido non causa divisioni per zero`() {
        assertEquals(0, VolumeMath.indexFor(50, 0))
        assertEquals(0, VolumeMath.percentFor(3, 0))
    }

    @Test
    fun `indice convertito in percentuale`() {
        assertEquals(53, VolumeMath.percentFor(8, 15))
        assertEquals(100, VolumeMath.percentFor(15, 15))
        assertEquals(0, VolumeMath.percentFor(0, 15))
    }

    @Test
    fun `andata e ritorno stabile per ogni percentuale`() {
        // Riapplicare la percentuale letta non deve spostare il volume (niente "deriva").
        for (max in listOf(7, 15, 25, 30)) {
            for (index in 0..max) {
                assertEquals(index, VolumeMath.indexFor(VolumeMath.percentFor(index, max), max))
            }
        }
    }
}
