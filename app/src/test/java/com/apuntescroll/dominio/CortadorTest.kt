package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CortadorTest {

    private fun parrafo(texto: String, pagina: Int = 1) = Bloque.Parrafo(TextoConPaginas.de(texto, pagina))
    private fun lista(vararg items: String, pagina: Int = 1) = Bloque.Lista(items.map { TextoConPaginas.de(it, pagina) })
    private fun textos(bloques: List<Bloque>, max: Int) = Cortador.cortar(bloques, max).map { it.texto }

    /** Una oración de exactamente [largo] caracteres, que termina en punto. */
    private fun oracion(largo: Int, letra: Char = 'a'): String {
        val palabra = letra.toString().repeat(7)
        val sb = StringBuilder("Oración")
        while (sb.length < largo - 1) sb.append(' ').append(palabra)
        return sb.substring(0, largo - 1).trimEnd().padEnd(largo - 1, letra) + "."
    }

    @Test
    fun `la oracion de prueba tiene el largo pedido`() {
        assertEquals(120, oracion(120).length)
        assertEquals(95, oracion(95, 'b').length)
    }

    @Test
    fun `llena la tarjeta con oraciones enteras sin pasarse del maximo`() {
        val o = List(6) { oracion(120, 'a' + it) }
        val tarjetas = textos(listOf(parrafo(o.joinToString(" "))), 450)
        assertEquals(listOf(o.take(3).joinToString(" "), o.drop(3).joinToString(" ")), tarjetas)
    }

    @Test
    fun `un parrafo nuevo empieza tarjeta si la actual esta al menos 60 por ciento llena`() {
        val p1 = List(3) { oracion(100, 'a' + it) }.joinToString(" ") // 302 caracteres: más del 60 % de 450
        val p2 = oracion(100, 'k')
        assertEquals(listOf(p1, p2), textos(listOf(parrafo(p1), parrafo(p2)), 450))
    }

    @Test
    fun `un parrafo nuevo sigue en la misma tarjeta si la actual esta poco llena`() {
        val p1 = oracion(150)
        val p2 = oracion(150, 'b')
        assertEquals(listOf("$p1 $p2"), textos(listOf(parrafo(p1), parrafo(p2)), 450))
    }

    @Test
    fun `una oracion mas larga que el maximo se parte y ninguna parte se pasa`() {
        val larga = (1..90).joinToString(" ") { "dato$it" } + "."
        val tarjetas = textos(listOf(parrafo(larga)), 300)
        assertTrue(tarjetas.size >= 2)
        assertTrue(tarjetas.all { it.length <= 300 })
        assertEquals(larga, tarjetas.joinToString(" "))
    }

    @Test
    fun `una lista que entra va entera con su oracion de presentacion`() {
        val intro = "Los factores que determinan la elasticidad son los siguientes:"
        val tarjetas = textos(listOf(parrafo(intro), lista("• Los sustitutos.", "• La proporción del ingreso.", "• El horizonte temporal.")), 450)
        assertEquals(listOf("$intro\n• Los sustitutos.\n• La proporción del ingreso.\n• El horizonte temporal."), tarjetas)
    }

    @Test
    fun `la oracion de presentacion se pasa a la tarjeta de la lista si hace falta`() {
        val antes = List(3) { oracion(110, 'a' + it) }.joinToString(" ")
        val intro = "Los factores son:"
        val items = arrayOf("• " + oracion(90, 'm'), "• " + oracion(90, 'n'))
        val tarjetas = textos(listOf(parrafo("$antes $intro"), lista(*items)), 450)
        assertEquals(listOf(antes, "$intro\n${items[0]}\n${items[1]}"), tarjetas)
    }

    @Test
    fun `una lista que no entra se reparte entre items sin partir ninguno`() {
        val items = List(6) { "• " + oracion(110, 'a' + it) }
        val tarjetas = textos(listOf(lista(*items.toTypedArray())), 300)
        assertEquals(listOf(items.take(2), items.slice(2..3), items.drop(4)).map { it.joinToString("\n") }, tarjetas)
    }

    @Test
    fun `un item mas largo que el maximo se parte y la continuacion no lleva salto de linea`() {
        val largo = "• " + (1..70).joinToString(" ") { "ítem$it" }
        val tarjetas = textos(listOf(lista(largo)), 200)
        assertTrue(tarjetas.all { it.length <= 200 })
        assertEquals(largo, tarjetas.joinToString(" "))
        assertTrue(tarjetas.none { it.contains('\n') })
    }

    @Test
    fun `un fragmento corto se une con la tarjeta anterior si no se pasa del 15 por ciento`() {
        // Tres oraciones de 140 llenan 422 de 430; la de 20 y la de 13 quedan en una tarjeta de 34,
        // que se une con la anterior porque 457 no pasa de 430 × 1,15 = 494.
        val tres = List(3) { oracion(140, 'a' + it) }.joinToString(" ")
        val tarjetas = textos(listOf(parrafo(tres + " " + oracion(20, 'z')), parrafo("Fin del tema.")), 430)
        assertEquals(1, tarjetas.size)
        assertEquals(457, tarjetas[0].length)
        assertTrue(tarjetas[0].length <= (430 * Cortador.TOLERANCIA_FUSION).toInt())
    }

    @Test
    fun `un fragmento corto al principio se une con la tarjeta siguiente`() {
        val corta = "Muy corta."
        val siguiente = oracion(445)
        assertEquals(listOf("$corta $siguiente"), textos(listOf(parrafo(corta), parrafo(siguiente)), 450))
    }

    @Test
    fun `un fragmento corto que no entra en ningun lado queda solo`() {
        val a = oracion(450)
        val corta = oracion(75, 'c')
        val b = oracion(450, 'b')
        assertEquals(listOf(a, corta, b), textos(listOf(parrafo(a), parrafo(corta), parrafo(b)), 450))
    }

    @Test
    fun `una linea suelta va en su propio renglon dentro de la tarjeta`() {
        val antes = oracion(100)
        val despues = oracion(100, 'b')
        val bloques = listOf(parrafo(antes), Bloque.Parrafo(TextoConPaginas.de("Actividad práctica", 1), esLinea = true), parrafo(despues))
        assertEquals(listOf("$antes\nActividad práctica\n$despues"), textos(bloques, 450))
    }

    @Test
    fun `la pagina de la tarjeta es la de su primer caracter`() {
        val lineas = listOf(Linea(oracion(200), 3), Linea(oracion(200, 'b'), 3), Linea(oracion(200, 'c'), 4))
        val bloque = Bloque.Parrafo(TextoConPaginas.unir(lineas))
        val tarjetas = Cortador.cortar(listOf(bloque), 450)
        assertEquals(listOf(3, 4), tarjetas.map { it.pagina })
    }

    @Test
    fun `sin bloques no hay tarjetas`() {
        assertEquals(emptyList<TarjetaArmada>(), Cortador.cortar(emptyList(), 450))
    }

    // ---- Revisión: ítems largos con varias oraciones (hallazgo 3) ----

    @Test
    fun `un item con varias oraciones mas largo que el maximo se corta entre oraciones`() {
        // 131 y no 130: con 130 la última palabra sería una sola letra (c.), que no cierra oración.
        val oraciones = listOf(oracion(150), oracion(140, 'b'), oracion(131, 'c'), oracion(120, 'd'))
        val item = "1. " + oraciones.joinToString(" ")
        val tarjetas = textos(listOf(lista(item, "2. El producto es homogéneo.")), 450)
        assertEquals(listOf("1. ${oraciones[0]} ${oraciones[1]} ${oraciones[2]}", "${oraciones[3]}\n2. El producto es homogéneo."), tarjetas)
    }

    @Test
    fun `una oracion de un item mas larga que el maximo se parte y sigue sin salto de linea`() {
        val larga = "• " + (1..70).joinToString(" ") { "dato$it" } + ". Otra oración corta del mismo ítem."
        val tarjetas = textos(listOf(lista(larga)), 200)
        assertTrue(tarjetas.all { it.length <= 200 })
        assertTrue(tarjetas.none { it.contains('\n') })
        assertEquals(larga, tarjetas.joinToString(" "))
    }
}
