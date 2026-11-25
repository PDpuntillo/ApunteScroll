package com.apuntescroll.intercepcion.reglas

/** Lo que muestra la barra de abajo de la pantalla interceptada. */
sealed interface EstadoPuerta {
    /** Pase: el feed carga o se rearma, todavía no se sabe la meta. */
    data object Cargando : EstadoPuerta

    data class Pase(val leidas: Int, val objetivo: Int, val minutos: Int, val hayTarjetas: Boolean) : EstadoPuerta {
        val listo: Boolean get() = leidas >= objetivo
    }

    /** Ya hay un pase vigente. [restanteMin] va redondeado hacia arriba. */
    data class PaseActivo(val restanteMin: Int) : EstadoPuerta

    /** [segundosRestantes] = 0: "Ir igual" activo. */
    data class Recordatorio(val segundosRestantes: Int) : EstadoPuerta

    data class Reemplazo(val hayTarjetas: Boolean) : EstadoPuerta
}

object CalculoPuerta {
    const val MS_RECORDATORIO = 5_000L

    /**
     * [disponibles]: tarjetas no sabidas que todavía no están en [progreso]. [modo] y
     * [paseRestanteMs] se leen en el momento (al tocar, no al crear la pantalla).
     */
    fun calcular(
        modo: ModoIntercepcion,
        cargando: Boolean,
        hayTarjetas: Boolean,
        disponibles: Int,
        progreso: ProgresoPase,
        tarjetasPase: Int,
        minutosPase: Int,
        paseRestanteMs: Long,
        msDesdeQueSeMostro: Long,
    ): EstadoPuerta = when (modo) {
        ModoIntercepcion.RECORDATORIO -> {
            val falta = (MS_RECORDATORIO - msDesdeQueSeMostro).coerceAtLeast(0)
            EstadoPuerta.Recordatorio(((falta + 999) / 1000).toInt())
        }
        ModoIntercepcion.REEMPLAZO_TOTAL -> EstadoPuerta.Reemplazo(hayTarjetas)
        ModoIntercepcion.PASE -> when {
            paseRestanteMs > 0 -> EstadoPuerta.PaseActivo(((paseRestanteMs + 59_999) / 60_000).toInt())
            cargando -> EstadoPuerta.Cargando
            else -> EstadoPuerta.Pase(
                leidas = progreso.cantidad,
                objetivo = progreso.objetivo(tarjetasPase, disponibles),
                minutos = minutosPase,
                hayTarjetas = hayTarjetas,
            )
        }
    }
}
