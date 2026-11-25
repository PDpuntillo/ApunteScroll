package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.AvisoInicio
import com.apuntescroll.intercepcion.reglas.Consentimiento
import com.apuntescroll.intercepcion.reglas.DerivarEstado
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import com.apuntescroll.intercepcion.reglas.elegirAvisoInicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElegirAvisoTest {

    private val ahora = 1_800_000_000_000L
    private val dia = 24 * 60 * 60_000L

    private fun elegir(
        estado: EstadoServicio = EstadoServicio.FUNCIONANDO,
        consentida: Boolean = true,
        noAparece: Boolean = false,
        peleaEn: Long? = null,
        bucleEn: Long? = null,
        paseActivo: Boolean = false,
        rechazos: Int = 0,
        invitacionOcultaHasta: Long = 0,
        estadoOcultoHasta: Long = 0,
    ) = elegirAvisoInicio(
        estado, consentida, noAparece, peleaEn, bucleEn, paseActivo, rechazos,
        invitacionOcultaHasta, estadoOcultoHasta, ahora,
    )

    @Test
    fun `activado sin aviso gana a todo`() {
        assertEquals(
            AvisoInicio.ACTIVADO_SIN_AVISO,
            elegir(EstadoServicio.ACTIVADO_SIN_AVISO, consentida = false, noAparece = true, peleaEn = ahora, paseActivo = true),
        )
    }

    @Test
    fun `aviso de version vieja`() {
        // Un consentimiento de una versión anterior no vale: con el servicio activado, el aviso vuelve.
        val consentida = Consentimiento(en = ahora - dia, version = 0).vigente()
        val estado = DerivarEstado.estado(consentida, habilitado = true, conectado = true, msDesdeHabilitado = 60_000,
            vistoConectado = true, proteccionAvanzada = false, sdk = 36)
        assertEquals(AvisoInicio.ACTIVADO_SIN_AVISO, elegir(estado, consentida = consentida))
    }

    @Test
    fun `estado apagado antes que los diagnosticos`() {
        assertEquals(AvisoInicio.ESTADO, elegir(EstadoServicio.SE_APAGO, noAparece = true, peleaEn = ahora))
        assertEquals(AvisoInicio.ESTADO, elegir(EstadoServicio.NO_RESPONDE))
        assertEquals(AvisoInicio.ESTADO, elegir(EstadoServicio.FALTA_ACTIVAR))
        assertEquals(AvisoInicio.ESTADO, elegir(EstadoServicio.PROTECCION_AVANZADA, consentida = true))
        assertNull(elegir(EstadoServicio.PROTECCION_AVANZADA, consentida = false, rechazos = 5))
    }

    @Test
    fun `estado oculto no se muestra hasta la fecha`() {
        assertEquals(AvisoInicio.NO_APARECE, elegir(EstadoServicio.SE_APAGO, noAparece = true, estadoOcultoHasta = ahora + dia))
        assertEquals(AvisoInicio.ESTADO, elegir(EstadoServicio.SE_APAGO, estadoOcultoHasta = ahora - 1))
    }

    @Test
    fun `no aparece antes que la pelea`() {
        assertEquals(AvisoInicio.NO_APARECE, elegir(noAparece = true, peleaEn = ahora, bucleEn = ahora))
        assertEquals(AvisoInicio.PELEA, elegir(peleaEn = ahora, bucleEn = ahora))
        assertEquals(AvisoInicio.BUCLE, elegir(bucleEn = ahora - dia))
    }

    @Test
    fun `la pelea vence a los siete dias`() {
        assertEquals(AvisoInicio.PELEA, elegir(peleaEn = ahora - 6 * dia))
        assertNull(elegir(peleaEn = ahora - 7 * dia))
        assertNull(elegir(bucleEn = ahora - 8 * dia))
    }

    @Test
    fun `pase activo antes que la invitacion`() {
        assertEquals(AvisoInicio.PASE_ACTIVO, elegir(EstadoServicio.SIN_CONSENTIMIENTO, consentida = false, paseActivo = true))
    }

    @Test
    fun `tres rechazos quitan la invitacion`() {
        assertEquals(AvisoInicio.INVITACION, elegir(EstadoServicio.SIN_CONSENTIMIENTO, consentida = false, rechazos = 2))
        assertNull(elegir(EstadoServicio.SIN_CONSENTIMIENTO, consentida = false, rechazos = 3))
    }

    @Test
    fun `invitacion oculta hasta la fecha`() {
        assertNull(elegir(EstadoServicio.SIN_CONSENTIMIENTO, consentida = false, invitacionOcultaHasta = ahora + dia))
        assertEquals(
            AvisoInicio.INVITACION,
            elegir(EstadoServicio.SIN_CONSENTIMIENTO, consentida = false, rechazos = 1, invitacionOcultaHasta = ahora - 1),
        )
    }

    @Test
    fun `funcionando y sin nada pendiente no muestra nada`() {
        assertNull(elegir())
        assertNull(elegir(EstadoServicio.CONECTANDO))
    }
}
