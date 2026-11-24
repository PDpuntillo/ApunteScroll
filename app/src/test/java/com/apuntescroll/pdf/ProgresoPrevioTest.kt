package com.apuntescroll.pdf

import com.apuntescroll.datos.bd.OrigenTexto
import com.apuntescroll.datos.bd.Seccion
import com.apuntescroll.datos.bd.Tarjeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgresoPrevioTest {

    private fun tarjeta(texto: String, seccion: Long = 1, orden: Int = 0, caja: Int = 0, vista: Int = 0, guardada: Boolean = false) =
        Tarjeta(
            documentoId = 1,
            seccionId = seccion,
            orden = orden,
            texto = texto,
            pagina = 1,
            origen = OrigenTexto.TEXTO,
            caja = caja,
            proximoRepaso = if (caja > 0) 99L else null,
            vecesVista = vista,
            ultimaVista = if (vista > 0) 42L else null,
            guardada = guardada,
        )

    @Test
    fun `una tarjeta con el mismo texto conserva el id y el progreso`() {
        val progreso = ProgresoPrevio(listOf(tarjeta("A", caja = 1, vista = 2, guardada = true).copy(id = 55)))
        val nueva = progreso.pasarA(tarjeta("A", seccion = 7, orden = 3))
        assertEquals(55L, nueva.id)
        assertEquals(1, nueva.caja)
        assertEquals(99L, nueva.proximoRepaso)
        assertEquals(2, nueva.vecesVista)
        assertEquals(42L, nueva.ultimaVista)
        assertTrue(nueva.guardada)
        // Lo demás es lo del armado nuevo.
        assertEquals(7L, nueva.seccionId)
        assertEquals(3, nueva.orden)
    }

    @Test
    fun `una tarjeta con texto nuevo empieza de cero con un id nuevo`() {
        val progreso = ProgresoPrevio(listOf(tarjeta("A", caja = 1, vista = 2, guardada = true).copy(id = 55)))
        val nueva = progreso.pasarA(tarjeta("B"))
        assertEquals(0L, nueva.id)
        assertEquals(0, nueva.caja)
        assertEquals(0, nueva.vecesVista)
        assertNull(nueva.ultimaVista)
        assertFalse(nueva.guardada)
    }

    @Test
    fun `con textos repetidos se emparejan en orden y cada previa se usa una vez`() {
        val progreso = ProgresoPrevio(
            listOf(
                tarjeta("R", seccion = 2, orden = 0, vista = 2),
                tarjeta("R", seccion = 1, orden = 0, vista = 1),
            ),
        )
        assertEquals(1, progreso.pasarA(tarjeta("R")).vecesVista)
        assertEquals(2, progreso.pasarA(tarjeta("R")).vecesVista)
        assertEquals(0, progreso.pasarA(tarjeta("R")).vecesVista)
    }

    @Test
    fun `con textos repetidos manda el orden de las secciones y no su id`() {
        // Después de un rearmado, la sección 9 (nueva) va antes que la 2 (que conservó su id).
        val progreso = ProgresoPrevio(
            listOf(
                tarjeta("R", seccion = 2, vista = 0, guardada = true).copy(id = 20),
                tarjeta("R", seccion = 9).copy(id = 90),
            ),
            ordenDeSeccion = mapOf(2L to 2, 9L to 1),
        )
        val primera = progreso.pasarA(tarjeta("R", seccion = 9))
        val segunda = progreso.pasarA(tarjeta("R", seccion = 2))
        assertEquals(90L, primera.id)
        assertFalse(primera.guardada)
        assertEquals(20L, segunda.id)
        assertTrue(segunda.guardada)
    }

    @Test
    fun `una seccion con el mismo titulo y pagina conserva el id`() {
        val previas = SeccionesPrevias(
            listOf(
                Seccion(id = 10, documentoId = 1, orden = 0, titulo = null, paginaInicio = 1),
                Seccion(id = 11, documentoId = 1, orden = 1, titulo = "Tema", paginaInicio = 3),
                Seccion(id = 12, documentoId = 1, orden = 2, titulo = "Tema", paginaInicio = 3),
            ),
        )
        assertEquals(11L, previas.idPara("Tema", 3))
        assertEquals(12L, previas.idPara("Tema", 3))
        // Ya se usaron las dos: una tercera es nueva.
        assertEquals(0L, previas.idPara("Tema", 3))
        // Otro título o la misma sección en otra página: nueva.
        assertEquals(0L, previas.idPara("Otro", 1))
        assertEquals(10L, previas.idPara(null, 1))
    }
}
