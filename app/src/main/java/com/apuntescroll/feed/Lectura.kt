package com.apuntescroll.feed

import kotlin.math.max
import kotlin.math.min

/** Una publicación a la vista: dónde empieza y cuánto mide, en píxeles de la lista. */
data class ItemEnPantalla(val clave: Any, val inicio: Int, val tamano: Int)

/**
 * Qué cuenta como "leer" una tarjeta en la pantalla interceptada (PLAN.md, sección 7): estar en
 * foco el tiempo de lectura de su texto.
 */
object Lectura {
    const val MS_POR_CARACTER = 12L
    const val MS_MIN = 2_500L
    const val MS_MAX = 6_000L

    fun msLectura(caracteres: Int): Long = (caracteres * MS_POR_CARACTER).coerceIn(MS_MIN, MS_MAX)

    /**
     * La publicación en foco: de las que están al menos al 60 % a la vista (o cubren el 60 % de la
     * pantalla, si son más altas que ella), la más visible; si empatan, la de arriba. [inicio] y
     * [fin] son los bordes de lo visible.
     */
    fun enFoco(items: List<ItemEnPantalla>, inicio: Int, fin: Int): Any? {
        val alto = (fin - inicio).coerceAtLeast(1)
        return items
            .filter { it.tamano > 0 }
            .map { it to (min(it.inicio + it.tamano, fin) - max(it.inicio, inicio)) }
            .filter { (item, visible) -> visible >= item.tamano * 0.6f || visible >= alto * 0.6f }
            .sortedWith(compareByDescending<Pair<ItemEnPantalla, Int>> { it.second }.thenBy { it.first.inicio })
            .firstOrNull()?.first?.clave
    }
}
