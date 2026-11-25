package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.ClasificadorPaquetes
import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.intercepcion.reglas.TipoPaquete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClasificadorPaquetesTest {

    private val clasificador = ClasificadorPaquetes("com.apuntescroll", CatalogoRedes(Redes.CONOCIDAS))
    private val teclado = "com.samsung.android.honeyboard"

    @Test
    fun `una red del catalogo es Red`() {
        assertEquals(TipoPaquete.Red(Redes.INSTAGRAM), clasificador.tipo("com.instagram.android", teclado))
        assertEquals(TipoPaquete.Red(Redes.X), clasificador.tipo("com.twitter.android", teclado))
    }

    @Test
    fun `el propio paquete es Propio`() {
        assertEquals(TipoPaquete.Propio, clasificador.tipo("com.apuntescroll", teclado))
    }

    @Test
    fun `systemui android intentresolver y permissioncontroller son transitorios`() {
        listOf(
            "com.android.systemui", "android", "com.android.intentresolver",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        ).forEach { assertEquals(it, TipoPaquete.Transitorio, clasificador.tipo(it, teclado)) }
    }

    @Test
    fun `el teclado actual es transitorio y cambia con el parametro`() {
        assertEquals(TipoPaquete.Transitorio, clasificador.tipo(teclado, teclado))
        val gboard = "com.google.android.inputmethod.latin"
        assertEquals(TipoPaquete.Otro, clasificador.tipo(gboard, teclado))
        assertEquals(TipoPaquete.Transitorio, clasificador.tipo(gboard, gboard))
        assertEquals(TipoPaquete.Otro, clasificador.tipo(teclado, gboard))
    }

    @Test
    fun `sin teclado conocido no rompe`() {
        assertEquals(TipoPaquete.Otro, clasificador.tipo(teclado, null))
        assertEquals(TipoPaquete.Red(Redes.INSTAGRAM), clasificador.tipo("com.instagram.android", null))
    }

    @Test
    fun `launcher marcador y whatsapp son Otro`() {
        listOf("com.sec.android.app.launcher", "com.samsung.android.dialer", "com.whatsapp", "com.android.settings")
            .forEach { assertEquals(it, TipoPaquete.Otro, clasificador.tipo(it, teclado)) }
    }

    @Test
    fun `una red fuera del catalogo es Otro`() {
        assertEquals(TipoPaquete.Otro, clasificador.tipo("com.zhiliaoapp.musically", teclado))
        assertEquals(TipoPaquete.Otro, clasificador.tipo("com.instagram.lite", teclado))
        // Las calculadoras de prueba solo cuentan si están en el catálogo (debug).
        assertEquals(TipoPaquete.Otro, clasificador.tipo("com.google.android.calculator", teclado))
    }

    @Test
    fun `paqueteDeTeclado saca el paquete de la forma corta y la larga`() {
        assertEquals(
            "com.samsung.android.honeyboard",
            ClasificadorPaquetes.paqueteDeTeclado("com.samsung.android.honeyboard/.service.HoneyBoardService"),
        )
        assertEquals(
            "com.google.android.inputmethod.latin",
            ClasificadorPaquetes.paqueteDeTeclado("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"),
        )
    }

    @Test
    fun `paqueteDeTeclado con valor nulo vacio o sin barra da null o el valor entero`() {
        assertNull(ClasificadorPaquetes.paqueteDeTeclado(null))
        assertNull(ClasificadorPaquetes.paqueteDeTeclado(""))
        assertNull(ClasificadorPaquetes.paqueteDeTeclado("   "))
        assertNull(ClasificadorPaquetes.paqueteDeTeclado("/.Servicio"))
        assertEquals("com.teclado", ClasificadorPaquetes.paqueteDeTeclado("com.teclado"))
    }
}
