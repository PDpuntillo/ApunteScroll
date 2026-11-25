package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.CalculoPuerta
import com.apuntescroll.intercepcion.reglas.EstadoPuerta
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.ProgresoPase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoPuertaTest {

    private fun calcular(
        modo: ModoIntercepcion = ModoIntercepcion.PASE,
        cargando: Boolean = false,
        hayTarjetas: Boolean = true,
        disponibles: Int = 100,
        leidas: Int = 0,
        paseRestanteMs: Long = 0,
        ms: Long = 0,
    ) = CalculoPuerta.calcular(
        modo, cargando, hayTarjetas, disponibles, ProgresoPase((1L..leidas.toLong()).toSet()),
        tarjetasPase = 15, minutosPase = 10, paseRestanteMs = paseRestanteMs, msDesdeQueSeMostro = ms,
    )

    @Test
    fun `pase cargando`() {
        assertEquals(EstadoPuerta.Cargando, calcular(cargando = true))
    }

    @Test
    fun `pase con 14 de 15 falta`() {
        val p = calcular(leidas = 14) as EstadoPuerta.Pase
        assertEquals(EstadoPuerta.Pase(14, 15, 10, true), p)
        assertFalse(p.listo)
    }

    @Test
    fun `pase con 15 de 15 listo`() {
        assertTrue((calcular(leidas = 15) as EstadoPuerta.Pase).listo)
    }

    @Test
    fun `pase sin tarjetas listo con objetivo cero`() {
        val p = calcular(hayTarjetas = false, disponibles = 0) as EstadoPuerta.Pase
        assertEquals(EstadoPuerta.Pase(0, 0, 10, false), p)
        assertTrue(p.listo)
    }

    @Test
    fun `pase al dia listo con objetivo cero`() {
        val p = calcular(hayTarjetas = true, disponibles = 0) as EstadoPuerta.Pase
        assertEquals(0, p.objetivo)
        assertTrue(p.hayTarjetas)
        assertTrue(p.listo)
    }

    @Test
    fun `pase vigente muestra los minutos redondeados hacia arriba`() {
        assertEquals(EstadoPuerta.PaseActivo(6), calcular(paseRestanteMs = 5 * 60_000L + 1))
        assertEquals(EstadoPuerta.PaseActivo(1), calcular(paseRestanteMs = 1))
        // El pase vigente gana a "cargando".
        assertEquals(EstadoPuerta.PaseActivo(5), calcular(cargando = true, paseRestanteMs = 5 * 60_000L))
    }

    @Test
    fun `recordatorio a los 0 ms faltan 5 segundos`() {
        assertEquals(EstadoPuerta.Recordatorio(5), calcular(ModoIntercepcion.RECORDATORIO, ms = 0))
    }

    @Test
    fun `recordatorio a los 4001 ms falta 1 segundo`() {
        assertEquals(EstadoPuerta.Recordatorio(1), calcular(ModoIntercepcion.RECORDATORIO, ms = 4_001))
    }

    @Test
    fun `recordatorio a los 5000 ms esta activo`() {
        assertEquals(EstadoPuerta.Recordatorio(0), calcular(ModoIntercepcion.RECORDATORIO, ms = 5_000))
        assertEquals(EstadoPuerta.Recordatorio(0), calcular(ModoIntercepcion.RECORDATORIO, ms = 60_000))
    }

    @Test
    fun `recordatorio no espera a que cargue el feed`() {
        assertEquals(EstadoPuerta.Recordatorio(0), calcular(ModoIntercepcion.RECORDATORIO, cargando = true, ms = 5_000))
    }

    @Test
    fun `reemplazo con y sin tarjetas`() {
        assertEquals(EstadoPuerta.Reemplazo(true), calcular(ModoIntercepcion.REEMPLAZO_TOTAL))
        assertEquals(EstadoPuerta.Reemplazo(false), calcular(ModoIntercepcion.REEMPLAZO_TOTAL, hayTarjetas = false))
        // Un pase vigente no vale en Reemplazo total.
        assertEquals(EstadoPuerta.Reemplazo(true), calcular(ModoIntercepcion.REEMPLAZO_TOTAL, paseRestanteMs = 60_000))
    }

    @Test
    fun `el modo se lee en el momento`() {
        // La misma pantalla, con el modo cambiado en Ajustes mientras estaba abierta.
        assertTrue(calcular(ModoIntercepcion.PASE, ms = 9_000) is EstadoPuerta.Pase)
        assertEquals(EstadoPuerta.Recordatorio(0), calcular(ModoIntercepcion.RECORDATORIO, ms = 9_000))
    }
}
