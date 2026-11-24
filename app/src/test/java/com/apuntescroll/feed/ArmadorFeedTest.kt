package com.apuntescroll.feed

import com.apuntescroll.datos.bd.ColorMateria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmadorFeedTest {

    private var siguienteId = 1L

    /** Crea las tarjetas de una sección (en orden) de un documento de una materia. */
    private fun seccion(
        materia: Long,
        documento: Long,
        orden: Int,
        cantidad: Int,
        vistas: Set<Int> = emptySet(),
        sabidas: Set<Int> = emptySet(),
        guardadas: Set<Int> = emptySet(),
        ultimaVista: Long? = null,
    ): List<TarjetaFeed> = (0 until cantidad).map { i ->
        TarjetaFeed(
            id = siguienteId++,
            texto = "m$materia d$documento s$orden t$i",
            pagina = 1,
            ordenTarjeta = i,
            caja = if (i in sabidas) 1 else 0,
            vecesVista = if (i in vistas) 1 else 0,
            ultimaVista = if (i in vistas) ultimaVista else null,
            guardada = i in guardadas,
            documentoId = documento,
            tituloDocumento = "Documento $documento",
            seccionId = documento * 100 + orden,
            ordenSeccion = orden,
            tituloSeccion = "Sección $orden",
            paginaInicioSeccion = 1,
            materiaId = materia,
            nombreMateria = "Materia $materia",
            emoji = "📘",
            color = ColorMateria.INDIGO,
        )
    }

    private fun textos(lista: List<TarjetaFeed>) = lista.map { it.texto }

    @Test
    fun `una sola materia sale en orden de lectura`() {
        val tarjetas = (seccion(1, 1, 1, 2) + seccion(1, 1, 0, 2)).shuffled(java.util.Random(3))
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        assertEquals(listOf("m1 d1 s0 t0", "m1 d1 s0 t1", "m1 d1 s1 t0", "m1 d1 s1 t1"), textos(orden))
    }

    @Test
    fun `con varias materias se alterna de a una seccion`() {
        val tarjetas = seccion(1, 1, 0, 2) + seccion(1, 1, 1, 1) + seccion(2, 2, 0, 1) + seccion(2, 2, 1, 2)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        assertEquals(
            listOf("m1 d1 s0 t0", "m1 d1 s0 t1", "m2 d2 s0 t0", "m1 d1 s1 t0", "m2 d2 s1 t0", "m2 d2 s1 t1"),
            textos(orden),
        )
    }

    @Test
    fun `las vistas no sabidas van despues de las nuevas y las sabidas no aparecen`() {
        val tarjetas = seccion(1, 1, 0, 4, vistas = setOf(0, 1), sabidas = setOf(1), ultimaVista = 50)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        assertEquals(listOf("m1 d1 s0 t2", "m1 d1 s0 t3", "m1 d1 s0 t0"), textos(orden))
    }

    @Test
    fun `las vistas salen de la mas vieja a la mas nueva`() {
        val tarjetas = seccion(1, 1, 0, 1, vistas = setOf(0), ultimaVista = 200) +
            seccion(1, 1, 1, 1, vistas = setOf(0), ultimaVista = 100)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        assertEquals(listOf("m1 d1 s1 t0", "m1 d1 s0 t0"), textos(orden))
    }

    @Test
    fun `el filtro por materia deja solo esa materia`() {
        val tarjetas = seccion(1, 1, 0, 2) + seccion(2, 2, 0, 2)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.DeMateria(2))
        assertTrue(orden.all { it.materiaId == 2L })
        assertEquals(2, orden.size)
    }

    @Test
    fun `el filtro de guardadas muestra todas las guardadas aunque se sepan`() {
        val tarjetas = seccion(1, 1, 0, 3, sabidas = setOf(0), guardadas = setOf(0, 2), vistas = setOf(0))
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Guardadas)
        assertEquals(listOf("m1 d1 s0 t0", "m1 d1 s0 t2"), textos(orden))
    }

    @Test
    fun `el documento importado antes va primero`() {
        // Los ids de documento crecen con cada importación (y en el orden en que se eligieron).
        val tarjetas = seccion(1, 2, 0, 1) + seccion(1, 1, 0, 1) + seccion(1, 3, 0, 1)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        assertEquals(listOf("m1 d1 s0 t0", "m1 d2 s0 t0", "m1 d3 s0 t0"), textos(orden))
    }

    private fun totales(tarjetas: List<TarjetaFeed>) = tarjetas.groupingBy { it.seccionId }.eachCount()

    @Test
    fun `en hilo cada tarjeta es una publicacion y el titulo va en la primera`() {
        val tarjetas = seccion(1, 1, 0, 3)
        val posts = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas), PielFeed.HILO, totales(tarjetas))
        assertEquals(3, posts.size)
        assertEquals(listOf(true, false, false), posts.map { it.conTitulo })
        assertTrue(posts.all { it.totalSeccion == 3 && it.tarjetas.size == 1 })
    }

    @Test
    fun `el total de la seccion cuenta tambien las que ya se saben`() {
        // t0 y t1 ya se saben: el feed empieza en t2, que lleva el título y dice "3/4".
        val tarjetas = seccion(1, 1, 0, 4, sabidas = setOf(0, 1))
        val posts = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas), PielFeed.HILO, totales(tarjetas))
        assertEquals(listOf(2, 3), posts.map { it.primera.ordenTarjeta })
        assertEquals(listOf(true, false), posts.map { it.conTitulo })
        assertTrue(posts.all { it.totalSeccion == 4 })
    }

    @Test
    fun `el titulo va donde el feed entra en la seccion aunque sus primeras ya se hayan visto`() {
        // m1: t0 ya se vio (va al final, con las vistas); el feed entra en la sección por t1.
        val tarjetas = seccion(1, 1, 0, 3, vistas = setOf(0), ultimaVista = 5) + seccion(2, 2, 0, 1)
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas)
        val posts = ArmadorFeed.publicaciones(orden, PielFeed.HILO, totales(tarjetas))
        assertEquals(listOf("m1 d1 s0 t1", "m1 d1 s0 t2", "m2 d2 s0 t0", "m1 d1 s0 t0"), posts.map { it.primera.texto })
        assertEquals(listOf(true, false, true, true), posts.map { it.conTitulo })
    }

    @Test
    fun `en guardadas el total sigue siendo el de la seccion completa`() {
        val tarjetas = seccion(1, 1, 0, 20, guardadas = setOf(14, 17))
        val orden = ArmadorFeed.ordenar(tarjetas, FiltroFeed.Guardadas)
        val posts = ArmadorFeed.publicaciones(orden, PielFeed.HILO, totales(tarjetas))
        assertEquals(listOf(14, 17), posts.map { it.primera.ordenTarjeta })
        assertTrue(posts.all { it.totalSeccion == 20 })
    }

    @Test
    fun `en carrusel una seccion es una publicacion y se parte cada diez tarjetas`() {
        val tarjetas = seccion(1, 1, 0, 23) + seccion(1, 1, 1, 2)
        val posts = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas), PielFeed.CARRUSEL, totales(tarjetas))
        assertEquals(listOf(10, 10, 3, 2), posts.map { it.tarjetas.size })
        assertEquals(listOf(0, 10, 20, 0), posts.map { it.primera.ordenTarjeta })
        assertEquals(listOf(true, false, false, true), posts.map { it.conTitulo })
        assertEquals(listOf(23, 23, 23, 2), posts.map { it.totalSeccion })
    }

    @Test
    fun `en carrusel las tarjetas que quedan de una seccion forman una sola publicacion`() {
        // La t1 ya se sabe y no sale: t0 y t2 quedan juntas como una publicación de dos diapositivas.
        val tarjetas = seccion(1, 1, 0, 3, sabidas = setOf(1))
        val posts = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas), PielFeed.CARRUSEL, totales(tarjetas))
        assertEquals(1, posts.size)
        assertEquals(2, posts.first().tarjetas.size)
        assertEquals(3, posts.first().totalSeccion)
    }

    @Test
    fun `la clave de un carrusel cambia si cambian sus diapositivas`() {
        val tarjetas = seccion(1, 1, 0, 3)
        val antes = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(tarjetas, FiltroFeed.Todas), PielFeed.CARRUSEL, totales(tarjetas))
        val sinLaUltima = tarjetas.mapIndexed { i, t -> if (i == 2) t.copy(caja = 1) else t }
        val despues = ArmadorFeed.publicaciones(ArmadorFeed.ordenar(sinLaUltima, FiltroFeed.Todas), PielFeed.CARRUSEL, totales(tarjetas))
        assertEquals(antes.first().primera.id, despues.first().primera.id)
        assertTrue(antes.first().clave != despues.first().clave)
    }

    @Test
    fun `sin tarjetas el feed queda vacio`() {
        assertEquals(
            emptyList<PostFeed>(),
            ArmadorFeed.publicaciones(ArmadorFeed.ordenar(emptyList(), FiltroFeed.Todas), PielFeed.CARRUSEL, emptyMap()),
        )
    }
}
