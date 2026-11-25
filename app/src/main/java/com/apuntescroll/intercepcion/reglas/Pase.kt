package com.apuntescroll.intercepcion.reglas

/**
 * Un pase de redes. Los tiempos son de `elapsedRealtime` (no cambian con la hora del teléfono);
 * [arranque] es el `BOOT_COUNT` de cuando se ganó, así el pase termina al reiniciar.
 */
data class PaseGuardado(val iniciadoEn: Long, val hasta: Long, val arranque: Int, val minutos: Int)

object RelojPase {
    fun nuevo(ahora: Long, arranque: Int, minutos: Int) = PaseGuardado(ahora, ahora + minutos * 60_000L, arranque, minutos)

    /**
     * Lo que queda del pase, en ms. 0 si no hay pase, si se reinició el teléfono (otro arranque, o
     * un reloj menor que el inicio cuando no se pudo leer `BOOT_COUNT`) o si ya venció. Nunca pasa
     * de los minutos otorgados.
     */
    fun restanteMs(pase: PaseGuardado?, ahora: Long, arranqueActual: Int): Long {
        if (pase == null || pase.arranque != arranqueActual || ahora < pase.iniciadoEn) return 0
        return (pase.hasta - ahora).coerceIn(0, pase.minutos * 60_000L)
    }
}

/** Las tarjetas que ya cuentan para el próximo pase (ids, hasta [MAX]). */
data class ProgresoPase(val ids: Set<Long> = emptySet()) {
    val cantidad: Int get() = ids.size

    fun con(id: Long): ProgresoPase = if (id in ids || ids.size >= MAX) this else ProgresoPase(ids + id)

    /** La meta: N, o menos si no quedan tantas tarjetas por estudiar. */
    fun objetivo(tarjetasPase: Int, disponibles: Int): Int = minOf(tarjetasPase, cantidad + disponibles)

    fun listo(tarjetasPase: Int, disponibles: Int): Boolean = cantidad >= objetivo(tarjetasPase, disponibles)

    fun aTexto(): String = ids.joinToString(",")

    companion object {
        const val MAX = 30

        /** Lo contrario de [aTexto]; ignora lo que no es un número. */
        fun desdeTexto(s: String?): ProgresoPase =
            ProgresoPase(s.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.take(MAX).toSet())
    }
}

/**
 * Qué tarjetas suman al pase (PLAN.md, sección 7). Una tarjeta suma una vez si se lee (estuvo en
 * foco su tiempo de lectura) o si se marca "Lo sé" después de contar como vista. Con un pase
 * vigente no suma nada: lo leído no se guarda para el pase siguiente.
 */
class ContadorEstudio {

    private val vistas = HashSet<Long>()

    fun alVer(id: Long) {
        vistas += id
    }

    /** true si suma. */
    fun alLeer(id: Long, progreso: ProgresoPase, paseVigente: Boolean = false): Boolean =
        !paseVigente && id !in progreso.ids && progreso.cantidad < ProgresoPase.MAX

    /** Las que suman: solo las ya vistas en esta pantalla y que todavía no se contaron. */
    fun alMarcarLoSe(ids: List<Long>, progreso: ProgresoPase, paseVigente: Boolean = false): List<Long> =
        if (paseVigente) emptyList() else ids.distinct().filter { it in vistas && it !in progreso.ids }
}
