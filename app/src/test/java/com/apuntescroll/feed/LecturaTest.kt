package com.apuntescroll.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LecturaTest {

    @Test
    fun `msLectura con cero caracteres da el minimo`() {
        assertEquals(2_500L, Lectura.msLectura(0))
    }

    @Test
    fun `msLectura con 300 450 y 600 caracteres`() {
        assertEquals(3_600L, Lectura.msLectura(300))
        assertEquals(5_400L, Lectura.msLectura(450))
        assertEquals(6_000L, Lectura.msLectura(600))
    }

    @Test
    fun `msLectura con un texto enorme da el maximo`() {
        assertEquals(6_000L, Lectura.msLectura(100_000))
    }

    @Test
    fun `enFoco elige la publicacion mas visible`() {
        val items = listOf(
            ItemEnPantalla("a", inicio = -100, tamano = 400), // 300 a la vista (75 %)
            ItemEnPantalla("b", inicio = 300, tamano = 500), // 500 a la vista
            ItemEnPantalla("c", inicio = 800, tamano = 400), // 200 a la vista (50 %)
        )
        assertEquals("b", Lectura.enFoco(items, inicio = 0, fin = 1_000))
    }

    @Test
    fun `enFoco con empate elige la de arriba`() {
        val items = listOf(ItemEnPantalla("a", 0, 400), ItemEnPantalla("b", 400, 400))
        assertEquals("a", Lectura.enFoco(items, inicio = 0, fin = 1_000))
    }

    @Test
    fun `enFoco ignora las que estan a la vista menos del 60 por ciento`() {
        val items = listOf(ItemEnPantalla("a", -500, 600), ItemEnPantalla("b", 900, 400))
        assertNull(Lectura.enFoco(items, inicio = 0, fin = 1_000))
    }

    @Test
    fun `enFoco acepta una publicacion mas alta que la pantalla`() {
        val items = listOf(ItemEnPantalla("larga", -200, 3_000), ItemEnPantalla("chica", 950, 200))
        assertEquals("larga", Lectura.enFoco(items, inicio = 0, fin = 1_000))
    }

    @Test
    fun `enFoco sin publicaciones da null`() {
        assertNull(Lectura.enFoco(emptyList(), 0, 1_000))
        assertNull(Lectura.enFoco(listOf(ItemEnPantalla("vacia", 0, 0)), 0, 1_000))
    }
}
