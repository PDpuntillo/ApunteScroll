package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.FrenoLanzamientos
import com.apuntescroll.intercepcion.reglas.FrenoLanzamientos.Veredicto
import com.apuntescroll.intercepcion.reglas.VistaFeed
import org.junit.Assert.assertEquals
import org.junit.Test

class FrenoLanzamientosTest {

    private val freno = FrenoLanzamientos()
    private val ig = "com.instagram.android"
    private val x = "com.twitter.android"

    /** Lanza en [en]; la pantalla se ve desde en+100 durante [visibleMs] (null: no se vio). */
    private fun lanzarYVer(en: Long, visibleMs: Long?): VistaFeed? {
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, en, null, huboOtraApp = false).also { freno.lanzado(ig, en) })
        return visibleMs?.let { VistaFeed(ig, en + 100, en + 100 + it) }
    }

    @Test
    fun `la primera vez lanza`() {
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, 1_000, null, huboOtraApp = false))
    }

    @Test
    fun `una rafaga durante la espera de aparicion pide esperar lo que falta`() {
        freno.lanzado(ig, 1_000)
        assertEquals(Veredicto.Esperar(1_300), freno.pedir(ig, 1_200, null, huboOtraApp = true))
        assertEquals(Veredicto.Esperar(100), freno.pedir(ig, 2_400, null, huboOtraApp = false))
    }

    @Test
    fun `pasada la espera sin que se viera vuelve a lanzar`() {
        freno.lanzado(ig, 1_000)
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, 2_500, null, huboOtraApp = false))
    }

    @Test
    fun `si el feed ya se vio no esta en vuelo`() {
        freno.lanzado(ig, 1_000)
        val vista = VistaFeed(ig, 1_200, 2_500)
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, 2_500, vista, huboOtraApp = false))
    }

    @Test
    fun `un lanzamiento que nunca aparecio no cuenta como rebote`() {
        var t = 1_000L
        repeat(5) {
            freno.lanzado(ig, t)
            t += 1_600
            // Nunca se vio: no es rebote (es asunto del vigía), así que nunca pausa.
            assertEquals(Veredicto.Lanzar, freno.pedir(ig, t, null, huboOtraApp = false))
        }
    }

    @Test
    fun `tres rebotes seguidos pausan treinta segundos`() {
        var t = 1_000L
        var vista = lanzarYVer(t, visibleMs = 300)
        t += 1_600
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t, vista, false)); freno.lanzado(ig, t)
        vista = VistaFeed(ig, t + 100, t + 400)
        t += 1_600
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t, vista, false)); freno.lanzado(ig, t)
        vista = VistaFeed(ig, t + 100, t + 400)
        t += 1_600
        assertEquals(Veredicto.PausaNueva, freno.pedir(ig, t, vista, false))
    }

    private fun pausar(): Long {
        var t = 1_000L
        freno.lanzado(ig, t)
        var vista = VistaFeed(ig, t + 100, t + 400)
        var ultimo: Veredicto
        do {
            t += 1_600
            ultimo = freno.pedir(ig, t, vista, false)
            if (ultimo == Veredicto.Lanzar) {
                freno.lanzado(ig, t)
                vista = VistaFeed(ig, t + 100, t + 400)
            }
        } while (ultimo == Veredicto.Lanzar)
        assertEquals(Veredicto.PausaNueva, ultimo)
        return t
    }

    @Test
    fun `durante la pausa no lanza`() {
        val t = pausar()
        assertEquals(Veredicto.Pausado, freno.pedir(ig, t + 2_000, null, false))
        assertEquals(Veredicto.Pausado, freno.pedir(ig, t + 29_000, null, false))
    }

    @Test
    fun `pasar por otra app destraba la pausa`() {
        val t = pausar()
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t + 2_000, null, huboOtraApp = true))
    }

    @Test
    fun `con otra app en el medio no cuenta como rebote`() {
        var t = 1_000L
        freno.lanzado(ig, t)
        repeat(5) {
            val vista = VistaFeed(ig, t + 100, t + 400)
            t += 1_600
            assertEquals(Veredicto.Lanzar, freno.pedir(ig, t, vista, huboOtraApp = true))
            freno.lanzado(ig, t)
        }
    }

    @Test
    fun `un feed visible mas de un segundo no cuenta como rebote`() {
        var t = 1_000L
        freno.lanzado(ig, t)
        repeat(5) {
            val vista = VistaFeed(ig, t + 100, t + 1_300)
            t += 1_900
            assertEquals(Veredicto.Lanzar, freno.pedir(ig, t, vista, huboOtraApp = false))
            freno.lanzado(ig, t)
        }
    }

    @Test
    fun `pasada la pausa vuelve a lanzar`() {
        val t = pausar()
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t + 30_001, null, false))
    }

    @Test
    fun `cada red tiene su propio freno`() {
        val t = pausar()
        assertEquals(Veredicto.Lanzar, freno.pedir(x, t + 100, null, false))
        freno.lanzado(x, t + 100)
        assertEquals(Veredicto.Pausado, freno.pedir(ig, t + 200, null, false))
    }

    @Test
    fun `si el reloj retrocede no se traba`() {
        freno.lanzado(ig, 50_000)
        // Un reloj menor que el último lanzamiento no deja el freno "en vuelo" para siempre.
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, 10_000, null, false))
        val t = pausar()
        // Una pausa que quedó más lejos que su duración (reloj hacia atrás) no traba.
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t - 40_000, null, false))
    }

    @Test
    fun `reiniciar olvida todo`() {
        val t = pausar()
        freno.reiniciar()
        assertEquals(Veredicto.Lanzar, freno.pedir(ig, t + 100, null, false))
    }
}
