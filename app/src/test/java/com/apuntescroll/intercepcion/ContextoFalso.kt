package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.ConfigRed
import com.apuntescroll.intercepcion.reglas.ContextoMotor
import com.apuntescroll.intercepcion.reglas.Entorno
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.RedConocida

/** Un ContextoMotor que se puede cambiar desde la prueba. El pase vence según [reloj]. */
class ContextoFalso(
    var consentida: Boolean = true,
    var redes: Map<String, ConfigRed> = emptyMap(),
    var tarjetasPase: Int = 15,
    var minutosPase: Int = 10,
    override var teclado: String? = TECLADO,
    var entornoActual: Entorno = Entorno(pantallaEnUso = true, appFijada = false, enLlamada = false),
) : ContextoMotor {

    var reloj: () -> Long = { 0L }

    /** Hasta cuándo dura el pase guardado (elapsedRealtime), o null si no hay. */
    var paseHasta: Long? = null

    /** Cuántas veces se leyó el entorno. */
    var llamadasEntorno = 0
        private set

    override val config: ConfigIntercepcion
        get() = ConfigIntercepcion(consentida, redes, tarjetasPase, minutosPase)

    override fun paseRestanteMs(): Long = paseHasta?.let { (it - reloj()).coerceAtLeast(0) } ?: 0L

    override fun hayPaseVencido(): Boolean = paseHasta != null && paseRestanteMs() == 0L

    override fun entorno(): Entorno {
        llamadasEntorno++
        return entornoActual
    }

    fun modo(red: RedConocida, modo: ModoIntercepcion, activada: Boolean = true) {
        redes = redes + (red.paquete to ConfigRed(activada, modo))
    }

    fun bloquear() {
        entornoActual = entornoActual.copy(pantallaEnUso = false)
    }

    fun desbloquear() {
        entornoActual = entornoActual.copy(pantallaEnUso = true)
    }

    companion object {
        const val TECLADO = "com.samsung.android.honeyboard"
    }
}
