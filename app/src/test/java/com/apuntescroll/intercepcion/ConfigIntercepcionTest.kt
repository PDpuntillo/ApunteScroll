package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.ConfigRed
import com.apuntescroll.intercepcion.reglas.Consentimiento
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.OpcionesPase
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.intercepcion.reglas.VERSION_AVISO
import com.apuntescroll.feed.PielFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigIntercepcionTest {

    private val catalogo = CatalogoRedes(Redes.CONOCIDAS + Redes.DE_PRUEBA)

    private fun desde(valores: Map<String, Any?>, debug: Boolean = false) =
        ConfigIntercepcion.desde(consentida = true, leer = { valores[it] }, catalogo = catalogo, debug = debug)

    @Test
    fun `valores por defecto sin claves`() {
        val c = desde(emptyMap())
        assertEquals(ConfigRed(true, ModoIntercepcion.PASE), c.de(Redes.INSTAGRAM))
        assertEquals(ConfigRed(true, ModoIntercepcion.PASE), c.de(Redes.X))
        assertEquals(15, c.tarjetasPase)
        assertEquals(10, c.minutosPase)
    }

    @Test
    fun `modo desconocido vuelve a pase`() {
        val c = desde(mapOf(ConfigIntercepcion.claveModo(Redes.X.paquete) to "MODO_RARO"))
        assertEquals(ModoIntercepcion.PASE, c.de(Redes.X).modo)
        val r = desde(mapOf(ConfigIntercepcion.claveModo(Redes.X.paquete) to "RECORDATORIO"))
        assertEquals(ModoIntercepcion.RECORDATORIO, r.de(Redes.X).modo)
    }

    @Test
    fun `tarjetas fuera de las opciones vuelve a quince`() {
        assertEquals(15, desde(mapOf(ConfigIntercepcion.CLAVE_TARJETAS to 7)).tarjetasPase)
        assertEquals(30, desde(mapOf(ConfigIntercepcion.CLAVE_TARJETAS to 30)).tarjetasPase)
    }

    @Test
    fun `un minuto solo vale en debug`() {
        assertEquals(10, desde(mapOf(ConfigIntercepcion.CLAVE_MINUTOS to 1), debug = false).minutosPase)
        assertEquals(1, desde(mapOf(ConfigIntercepcion.CLAVE_MINUTOS to 1), debug = true).minutosPase)
        assertEquals(listOf(1, 5, 10, 15, 20, 30), OpcionesPase.opcionesMinutos(debug = true))
        assertEquals(OpcionesPase.MINUTOS, OpcionesPase.opcionesMinutos(debug = false))
    }

    @Test
    fun `una red nueva del catalogo entra activada en pase`() {
        val nueva = RedConocida("com.nueva.red", "Nueva", PielFeed.HILO)
        val c = ConfigIntercepcion.desde(true, { null }, CatalogoRedes(listOf(nueva)), debug = false)
        assertEquals(ConfigRed(true, ModoIntercepcion.PASE), c.de(nueva))
        // Una red que no estaba al armar la configuración también.
        assertEquals(ConfigRed(true, ModoIntercepcion.PASE), desde(emptyMap()).de(nueva))
    }

    @Test
    fun `la red de prueba arranca apagada`() {
        Redes.DE_PRUEBA.forEach { assertFalse(desde(emptyMap(), debug = true).de(it).activada) }
        val prendida = desde(mapOf(ConfigIntercepcion.claveActivada(Redes.DE_PRUEBA.first().paquete) to true), debug = true)
        assertTrue(prendida.de(Redes.DE_PRUEBA.first()).activada)
    }

    @Test
    fun `consentimiento de una version vieja no vale`() {
        assertFalse(Consentimiento(en = 1, version = VERSION_AVISO - 1).vigente())
        assertTrue(Consentimiento(en = 1, version = VERSION_AVISO).vigente())
    }
}

class CatalogoRedesTest {

    private val catalogo = CatalogoRedes(Redes.CONOCIDAS)

    @Test
    fun `de null da null`() {
        assertNull(catalogo.de(null))
    }

    @Test
    fun `de un paquete desconocido da null`() {
        assertNull(catalogo.de("com.whatsapp"))
        assertNull(catalogo.de(""))
    }

    @Test
    fun `de un paquete conocido da su red`() {
        assertEquals(Redes.INSTAGRAM, catalogo.de("com.instagram.android"))
        assertEquals(PielFeed.CARRUSEL, catalogo.de("com.instagram.android")?.piel)
        assertEquals(PielFeed.HILO, catalogo.de("com.twitter.android")?.piel)
    }

    @Test
    fun `paquetes tiene los del catalogo`() {
        assertEquals(setOf("com.instagram.android", "com.twitter.android"), catalogo.paquetes)
    }
}
