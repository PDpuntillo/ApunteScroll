package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.DerivarEstado
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import com.apuntescroll.intercepcion.reglas.ServiciosHabilitados
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivarEstadoTest {

    private val paquete = "com.apuntescroll"
    private val clase = "com.apuntescroll.intercepcion.ServicioIntercepcion"

    private fun estado(
        consentida: Boolean = true,
        habilitado: Boolean = true,
        conectado: Boolean = true,
        ms: Long = 10_000,
        vistoConectado: Boolean = false,
        proteccion: Boolean = false,
        sdk: Int = 36,
    ) = DerivarEstado.estado(consentida, habilitado, conectado, ms, vistoConectado, proteccion, sdk)

    @Test
    fun `ServiciosHabilitados con lista nula vacia o con dos puntos al final`() {
        assertFalse(ServiciosHabilitados.contiene(null, paquete, clase))
        assertFalse(ServiciosHabilitados.contiene("", paquete, clase))
        assertFalse(ServiciosHabilitados.contiene(":", paquete, clase))
        assertTrue(ServiciosHabilitados.contiene("$paquete/$clase:", paquete, clase))
    }

    @Test
    fun `ServiciosHabilitados acepta la forma corta y la larga`() {
        assertTrue(ServiciosHabilitados.contiene("$paquete/.intercepcion.ServicioIntercepcion", paquete, clase))
        assertTrue(ServiciosHabilitados.contiene("com.bloqueador.android/.bloqueo.ServicioAccesibilidad:$paquete/$clase", paquete, clase))
    }

    @Test
    fun `ServiciosHabilitados no confunde otro servicio del mismo paquete`() {
        assertFalse(ServiciosHabilitados.contiene("$paquete/.intercepcion.OtroServicio", paquete, clase))
        assertFalse(ServiciosHabilitados.contiene("com.otra/$clase", paquete, clase))
        assertFalse(ServiciosHabilitados.contiene(paquete, paquete, clase))
    }

    @Test
    fun `aceptar con el servicio conectado y apagarlo da se apago`() {
        // El coordinador marca vistoConectado al aceptar con el servicio ya conectado.
        assertEquals(EstadoServicio.ACTIVADO_SIN_AVISO, estado(consentida = false))
        assertEquals(EstadoServicio.FUNCIONANDO, estado(consentida = true, vistoConectado = true))
        assertEquals(EstadoServicio.SE_APAGO, estado(habilitado = false, conectado = false, vistoConectado = true))
    }

    @Test
    fun `proteccion avanzada gana a sin consentimiento`() {
        assertEquals(
            EstadoServicio.PROTECCION_AVANZADA,
            estado(consentida = false, habilitado = false, conectado = false, proteccion = true, sdk = 37),
        )
    }

    @Test
    fun `proteccion avanzada solo cuenta con sdk 37`() {
        assertEquals(
            EstadoServicio.SIN_CONSENTIMIENTO,
            estado(consentida = false, habilitado = false, conectado = false, proteccion = true, sdk = 36),
        )
        // Con el servicio activado, la protección no está apagándolo.
        assertEquals(EstadoServicio.FUNCIONANDO, estado(proteccion = true, sdk = 37))
    }

    @Test
    fun `activado sin aviso`() {
        assertEquals(EstadoServicio.ACTIVADO_SIN_AVISO, estado(consentida = false, conectado = true))
        assertEquals(EstadoServicio.ACTIVADO_SIN_AVISO, estado(consentida = false, conectado = false))
    }

    @Test
    fun `sin consentimiento`() {
        assertEquals(EstadoServicio.SIN_CONSENTIMIENTO, estado(consentida = false, habilitado = false, conectado = false))
    }

    @Test
    fun `falta activar si nunca se conecto`() {
        assertEquals(EstadoServicio.FALTA_ACTIVAR, estado(habilitado = false, conectado = false))
    }

    @Test
    fun `se apago si alguna vez se conecto`() {
        assertEquals(EstadoServicio.SE_APAGO, estado(habilitado = false, conectado = false, vistoConectado = true))
    }

    @Test
    fun `conectando hasta tres segundos`() {
        assertEquals(EstadoServicio.CONECTANDO, estado(conectado = false, ms = 0))
        assertEquals(EstadoServicio.CONECTANDO, estado(conectado = false, ms = 3_000))
    }

    @Test
    fun `no responde despues de tres segundos`() {
        assertEquals(EstadoServicio.NO_RESPONDE, estado(conectado = false, ms = 3_001))
    }

    @Test
    fun `funcionando`() {
        assertEquals(EstadoServicio.FUNCIONANDO, estado())
    }
}
