package com.apuntescroll.intercepcion.reglas

/**
 * Freno contra ráfagas y rebotes, por red. No descarta eventos: lo que frena lo vuelve a revisar
 * el motor más tarde (Esperar). Aparte corta los rebotes (la red vuelve encima de la pantalla de
 * apuntes una y otra vez) con una pausa corta. Pasar por otra app la destraba al instante.
 *
 * Un lanzamiento que todavía no se vio nunca cuenta como rebote: si la pantalla no aparece, es
 * asunto del vigía del motor (COMPROBAR_APARICION).
 */
class FrenoLanzamientos(
    private val esperaAparicionMs: Long = 1_500,
    private val ventanaReboteMs: Long = 2_000,
    private val feedBreveMs: Long = 1_000,
    private val rebotesParaPausa: Int = 3,
    private val pausaMs: Long = 30_000,
) {
    sealed interface Veredicto {
        data object Lanzar : Veredicto
        data class Esperar(val ms: Long) : Veredicto
        data object Pausado : Veredicto
        data object PausaNueva : Veredicto
    }

    private val ultimoLanzamiento = HashMap<String, Long>()
    private val rebotes = HashMap<String, Int>()
    private val pausa = HashMap<String, Long>()

    /**
     * [vista]: la última vez que se vio la pantalla de apuntes (de cualquier red). [huboOtraApp]:
     * desde el último lanzamiento pasó otra app al frente.
     */
    fun pedir(red: String, ahora: Long, vista: VistaFeed?, huboOtraApp: Boolean): Veredicto {
        val u = ultimoLanzamiento[red]
        // La vista de ese lanzamiento: de esta red y empezada después de lanzar.
        val vistaDeU = vista?.takeIf { u != null && it.red == red && it.desde >= u }
        // 1. En vuelo: se lanzó hace poco y todavía no se vio.
        if (u != null && vistaDeU == null && ahora - u in 0 until esperaAparicionMs) {
            return Veredicto.Esperar(u + esperaAparicionMs - ahora)
        }
        // 2. Con otra app en el medio no es un rebote: se destraba todo.
        if (huboOtraApp) {
            rebotes[red] = 0
            pausa[red] = 0
            return Veredicto.Lanzar
        }
        // 3. Pausa por rebotes (si el reloj volviera atrás, no queda trabada).
        val hasta = pausa[red] ?: 0
        if (hasta - ahora in 1..pausaMs) return Veredicto.Pausado
        // 4. Rebote: la pantalla de ese lanzamiento se vio, pero menos de [feedBreveMs].
        val breve = vistaDeU != null && vistaDeU.hasta != null && vistaDeU.hasta - vistaDeU.desde < feedBreveMs
        val rebote = u != null && ahora - u in 0 until ventanaReboteMs && breve
        if (rebote) {
            val n = (rebotes[red] ?: 0) + 1
            if (n >= rebotesParaPausa) {
                rebotes[red] = 0
                pausa[red] = ahora + pausaMs
                return Veredicto.PausaNueva
            }
            rebotes[red] = n
        } else {
            rebotes[red] = 0
        }
        return Veredicto.Lanzar
    }

    fun lanzado(red: String, ahora: Long) {
        ultimoLanzamiento[red] = ahora
    }

    fun reiniciar() {
        ultimoLanzamiento.clear()
        rebotes.clear()
        pausa.clear()
    }
}
