package com.apuntescroll.intercepcion.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.datos.AjustesIntercepcion
import com.apuntescroll.feed.PielFeed
import com.apuntescroll.intercepcion.CoordinadorIntercepcion
import com.apuntescroll.intercepcion.reglas.CalculoPuerta
import com.apuntescroll.intercepcion.reglas.ComoPasa
import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.ContadorEstudio
import com.apuntescroll.intercepcion.reglas.EstadoPuerta
import com.apuntescroll.intercepcion.reglas.MotivoIntercepcion
import com.apuntescroll.intercepcion.reglas.ProgresoPase
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.ui.feed.EstadoFeed
import com.apuntescroll.ui.feed.ObservadorEstudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de la pantalla interceptada: qué red se tapó, la puerta (barra de abajo) y lo que se
 * estudia para el pase. Sobrevive a la rotación (la cuenta de 5 s y lo visto siguen).
 */
class InterceptadoViewModel(
    private val coordinador: CoordinadorIntercepcion,
    private val ajustes: AjustesIntercepcion,
    private val reloj: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel(), ObservadorEstudio {

    private val _red = MutableStateFlow<RedConocida?>(null)
    val red: StateFlow<RedConocida?> = _red.asStateFlow()

    private val _motivo = MutableStateFlow(MotivoIntercepcion.ABRIO_RED)
    val motivo: StateFlow<MotivoIntercepcion> = _motivo.asStateFlow()

    // Cambia junto con [red] (no derivada): así el feed no se arma primero con otra piel.
    private val _piel = MutableStateFlow(PielFeed.HILO)
    val piel: StateFlow<PielFeed> = _piel.asStateFlow()

    val progreso: StateFlow<ProgresoPase> = ajustes.progreso
    val config: StateFlow<ConfigIntercepcion> = ajustes.config

    private val contador = ContadorEstudio()

    /** Desde cuándo se ve la pantalla con esta red (para "Ir igual" a los 5 s). */
    private var mostradoDesde: Long? = null

    fun mostrar(red: RedConocida, motivo: MotivoIntercepcion) {
        if (red != _red.value) mostradoDesde = null
        _piel.value = red.piel
        _red.value = red
        _motivo.value = motivo
    }

    /** La pantalla pasó a primer plano: la cuenta de "Ir igual" empieza la primera vez para esta red. */
    fun alMostrarse() {
        if (mostradoDesde == null) mostradoDesde = reloj()
    }

    /** [disponibles]: FeedViewModel.disponiblesSin (null si el feed no cargó). */
    fun puerta(feed: EstadoFeed, disponibles: Int?, ahora: Long): EstadoPuerta {
        val r = _red.value ?: return EstadoPuerta.Cargando
        val c = config.value
        return CalculoPuerta.calcular(
            modo = c.de(r).modo,
            cargando = feed.cargando || feed.rearmando || disponibles == null,
            hayTarjetas = feed.hayTarjetas,
            disponibles = disponibles ?: 0,
            progreso = progreso.value,
            tarjetasPase = c.tarjetasPase,
            minutosPase = c.minutosPase,
            paseRestanteMs = coordinador.paseRestanteMs(),
            msDesdeQueSeMostro = ahora - (mostradoDesde ?: ahora),
        )
    }

    /** "Entrar": vuelve a calcular la puerta en el momento. true si deja pasar. */
    fun entrar(ahora: Long, feed: EstadoFeed, disponibles: Int?): Boolean {
        val r = _red.value ?: return false
        val p = puerta(feed, disponibles, ahora)
        val puede = (p is EstadoPuerta.Pase && p.listo) || p is EstadoPuerta.PaseActivo
        if (puede) coordinador.alDejarPasar(r.paquete, ComoPasa.PASE)
        return puede
    }

    /** "Ir igual": solo en Solo recordatorio y pasados los 5 s. true si deja pasar. */
    fun irIgual(ahora: Long, feed: EstadoFeed): Boolean {
        val r = _red.value ?: return false
        val p = puerta(feed, 0, ahora)
        val puede = p is EstadoPuerta.Recordatorio && p.segundosRestantes == 0
        if (puede) coordinador.alDejarPasar(r.paquete, ComoPasa.VISITA)
        return puede
    }

    private fun paseVigente() = coordinador.paseRestanteMs() > 0

    override fun alVer(tarjetaId: Long) = contador.alVer(tarjetaId)

    override fun alLeer(tarjetaId: Long) {
        if (contador.alLeer(tarjetaId, progreso.value, paseVigente())) ajustes.sumarAlProgreso(tarjetaId)
    }

    override fun alMarcarSabidas(tarjetaIds: List<Long>) {
        contador.alMarcarLoSe(tarjetaIds, progreso.value, paseVigente()).forEach(ajustes::sumarAlProgreso)
    }

    companion object {
        fun fabrica(app: ApunteScrollApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { InterceptadoViewModel(app.contenedor.intercepcion, app.contenedor.ajustesIntercepcion) }
        }
    }
}
