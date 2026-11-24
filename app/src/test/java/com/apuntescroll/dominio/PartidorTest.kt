package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartidorTest {

    private fun partir(texto: String, max: Int) = Partidor.partir(texto, max).map { texto.substring(it) }

    /** Las partes, unidas con un espacio, reconstruyen el texto (sin espacios de más). */
    private fun assertReconstruye(texto: String, partes: List<String>) {
        assertEquals(texto.trim().replace(Regex("\\s+"), " "), partes.joinToString(" "))
    }

    @Test
    fun `un texto que entra no se parte`() {
        assertEquals(listOf("Entra entero."), partir("  Entra entero.  ", 100))
    }

    @Test
    fun `prefiere cortar despues de un punto y coma`() {
        val texto = "La demanda es elástica cuando hay muchos sustitutos cercanos en el mercado; " +
            "en cambio, es inelástica cuando el bien es necesario y no tiene reemplazos fáciles."
        val partes = partir(texto, 100)
        assertEquals("La demanda es elástica cuando hay muchos sustitutos cercanos en el mercado;", partes[0])
        assertReconstruye(texto, partes)
    }

    @Test
    fun `sin punto y coma corta despues de una coma`() {
        val texto = "Cuando el costo marginal es menor que el costo medio, el costo medio baja, " +
            "y cuando el costo marginal es mayor que el costo medio el costo medio sube"
        val partes = partir(texto, 100)
        assertTrue(partes.all { it.length <= 100 })
        assertTrue(partes[0], partes[0].endsWith(","))
        assertReconstruye(texto, partes)
    }

    @Test
    fun `sin signos corta antes de una conjuncion`() {
        val texto = "La política monetaria expansiva baja la tasa de interés y aumenta la inversión privada " +
            "que a su vez eleva la demanda agregada en el corto plazo del modelo"
        val partes = partir(texto, 100)
        assertTrue(partes.all { it.length <= 100 })
        assertTrue(partes[1], partes[1].startsWith("y ") || partes[1].startsWith("que "))
        assertReconstruye(texto, partes)
    }

    @Test
    fun `un texto largo sin puntuacion se corta entre palabras en partes parejas`() {
        val texto = (1..120).joinToString(" ") { "palabra$it" }
        val partes = partir(texto, 300)
        assertTrue(partes.all { it.length <= 300 })
        assertReconstruye(texto, partes)
        val largos = partes.map { it.length }
        assertTrue("Partes muy desparejas: $largos", largos.max() - largos.min() < 150)
        // Ninguna palabra quedó partida.
        assertTrue(partes.all { p -> p.split(" ").all { Regex("palabra\\d+").matches(it) } })
    }

    @Test
    fun `una palabra mas larga que el maximo se corta a la fuerza`() {
        val url = "https://www.economia.uba.ar/" + "x".repeat(150)
        val partes = partir(url, 100)
        assertTrue(partes.all { it.length <= 100 })
        assertEquals(url, partes.joinToString(""))
    }
}
