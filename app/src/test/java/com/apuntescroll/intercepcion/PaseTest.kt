package com.apuntescroll.intercepcion

import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.feed.ArmadorFeed
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.intercepcion.reglas.ContadorEstudio
import com.apuntescroll.intercepcion.reglas.PaseGuardado
import com.apuntescroll.intercepcion.reglas.ProgresoPase
import com.apuntescroll.intercepcion.reglas.RelojPase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelojPaseTest {

    private val minuto = 60_000L
    private val pase = PaseGuardado(iniciadoEn = 100_000, hasta = 100_000 + 10 * minuto, arranque = 7, minutos = 10)

    @Test
    fun `sin pase queda cero`() {
        assertEquals(0L, RelojPase.restanteMs(null, 200_000, 7))
    }

    @Test
    fun `pase vigente da hasta menos ahora`() {
        assertEquals(10 * minuto - 50_000, RelojPase.restanteMs(pase, 150_000, 7))
    }

    @Test
    fun `justo al llegar a hasta da cero`() {
        assertEquals(0L, RelojPase.restanteMs(pase, pase.hasta, 7))
        assertEquals(0L, RelojPase.restanteMs(pase, pase.hasta + 1, 7))
    }

    @Test
    fun `con otro arranque el pase termino`() {
        assertEquals(0L, RelojPase.restanteMs(pase, 150_000, 8))
    }

    @Test
    fun `sin boot count y ahora menor que el inicio el pase termino`() {
        val sinArranque = pase.copy(arranque = -1)
        assertEquals(0L, RelojPase.restanteMs(sinArranque, 50_000, -1))
    }

    @Test
    fun `arranque -1 en los dos lados no da un pase eterno`() {
        val sinArranque = pase.copy(arranque = -1)
        assertEquals(10 * minuto - 1, RelojPase.restanteMs(sinArranque, 100_001, -1))
        assertEquals(0L, RelojPase.restanteMs(sinArranque, pase.hasta + 1_000_000, -1))
    }

    @Test
    fun `lo que queda no pasa de los minutos otorgados`() {
        val raro = pase.copy(hasta = pase.iniciadoEn + 500 * minuto)
        assertEquals(10 * minuto, RelojPase.restanteMs(raro, pase.iniciadoEn, 7))
    }

    @Test
    fun `nuevo calcula hasta con los minutos`() {
        assertEquals(PaseGuardado(1_000, 1_000 + 15 * minuto, 3, 15), RelojPase.nuevo(1_000, 3, 15))
    }
}

class ProgresoPaseTest {

    @Test
    fun `no cuenta dos veces la misma tarjeta`() {
        val p = ProgresoPase().con(1).con(2).con(1)
        assertEquals(2, p.cantidad)
    }

    @Test
    fun `no pasa de treinta`() {
        var p = ProgresoPase()
        (1L..40L).forEach { p = p.con(it) }
        assertEquals(30, p.cantidad)
    }

    @Test
    fun `objetivo es el menor entre N y progreso mas disponibles`() {
        val p = ProgresoPase(setOf(1, 2, 3))
        assertEquals(15, p.objetivo(15, 100))
        assertEquals(8, p.objetivo(15, 5))
        assertEquals(3, p.objetivo(15, 0))
    }

    @Test
    fun `sin tarjetas disponibles esta listo`() {
        assertTrue(ProgresoPase().listo(15, 0))
        assertFalse(ProgresoPase().listo(15, 1))
    }

    @Test
    fun `bajar N con el progreso alto deja listo`() {
        val p = ProgresoPase((1L..12L).toSet())
        assertFalse(p.listo(15, 50))
        assertTrue(p.listo(10, 50))
    }

    @Test
    fun `desdeTexto ignora lo que no es numero`() {
        assertEquals(ProgresoPase(setOf(12, 57, 90)), ProgresoPase.desdeTexto("12, 57,x,,90,1.5"))
        assertEquals(ProgresoPase(), ProgresoPase.desdeTexto(null))
        assertEquals(ProgresoPase(), ProgresoPase.desdeTexto(""))
    }

    @Test
    fun `aTexto y desdeTexto ida y vuelta`() {
        val p = ProgresoPase(setOf(5, 900, 12_345_678_901))
        assertEquals(p, ProgresoPase.desdeTexto(p.aTexto()))
    }

    private fun tarjeta(id: Long, sabida: Boolean) = TarjetaFeed(
        id = id, texto = "t$id", pagina = 1, ordenTarjeta = 0, caja = if (sabida) 1 else 0, vecesVista = 0,
        ultimaVista = null, guardada = false, documentoId = 1, tituloDocumento = "D", seccionId = id,
        ordenSeccion = 0, tituloSeccion = null, paginaInicioSeccion = 1, materiaId = 1, nombreMateria = "M",
        emoji = "📘", color = ColorMateria.entries.first(),
    )

    @Test
    fun `marcar lo se sin ver no baja el objetivo`() {
        // Quedan 5 por estudiar al cargar. Marcar "lo sé" en 4 (sin leerlas) no las saca de la meta.
        val alCargar = (1L..5L).map { tarjeta(it, sabida = false) } + tarjeta(6, sabida = true)
        val progreso = ProgresoPase(setOf(1))
        val antes = progreso.objetivo(15, ArmadorFeed.disponibles(alCargar, emptySet(), progreso.ids))
        val trasMarcar = alCargar.map { if (it.id in 2L..5L) it.copy(caja = 1) else it }
        val despues = progreso.objetivo(15, ArmadorFeed.disponibles(trasMarcar, setOf(2, 3, 4, 5), progreso.ids))
        assertEquals(5, antes)
        assertEquals(antes, despues)
    }
}

class ContadorEstudioTest {

    private val contador = ContadorEstudio()

    @Test
    fun `una lectura suma`() {
        assertTrue(contador.alLeer(1, ProgresoPase()))
    }

    @Test
    fun `una lectura de una tarjeta ya contada no suma`() {
        assertFalse(contador.alLeer(1, ProgresoPase(setOf(1))))
    }

    @Test
    fun `lo se despues de verla suma`() {
        contador.alVer(1)
        assertEquals(listOf(1L), contador.alMarcarLoSe(listOf(1), ProgresoPase()))
    }

    @Test
    fun `lo se sin haberla visto no suma`() {
        assertEquals(emptyList<Long>(), contador.alMarcarLoSe(listOf(1), ProgresoPase()))
    }

    @Test
    fun `lo se de un carrusel suma solo las diapositivas vistas`() {
        contador.alVer(1)
        contador.alVer(3)
        assertEquals(listOf(1L, 3L), contador.alMarcarLoSe(listOf(1, 2, 3, 4), ProgresoPase(setOf(9))))
        assertEquals(listOf(3L), contador.alMarcarLoSe(listOf(1, 2, 3, 4), ProgresoPase(setOf(1))))
    }

    @Test
    fun `con un pase vigente no suma nada`() {
        contador.alVer(1)
        assertFalse(contador.alLeer(2, ProgresoPase(), paseVigente = true))
        assertEquals(emptyList<Long>(), contador.alMarcarLoSe(listOf(1), ProgresoPase(), paseVigente = true))
    }
}
