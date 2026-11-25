package com.apuntescroll.intercepcion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.R
import com.apuntescroll.intercepcion.reglas.MotivoIntercepcion
import com.apuntescroll.intercepcion.ui.InterceptadoViewModel
import com.apuntescroll.intercepcion.ui.PantallaInterceptada
import com.apuntescroll.ui.Destino
import com.apuntescroll.ui.MainActivity
import com.apuntescroll.ui.feed.FeedViewModel
import com.apuntescroll.ui.feed.OpcionesFeed
import com.apuntescroll.ui.tema.ApunteScrollTema
import java.util.Date

/**
 * El feed de apuntes encima de una red (PLAN.md, sección 7). Va en su propia tarea, fuera de
 * Recientes y con noHistory (manifiesto). Usa el tema de la app (fondo propio también en modo
 * noche), así que no hay destello; en Android 12 o más, la ventana de inicio puede mostrar un
 * instante el ícono de ApunteScroll.
 */
class FeedInterceptadoActivity : ComponentActivity() {

    private val app get() = application as ApunteScrollApp
    private val interceptado: InterceptadoViewModel by viewModels { InterceptadoViewModel.fabrica(app) }
    private val feed: FeedViewModel by viewModels {
        viewModelFactory {
            initializer {
                FeedViewModel(
                    app.contenedor.repositorio,
                    interceptado.piel,
                    app.alcance,
                    app.contenedor.procesador.rearmando,
                    OpcionesFeed(
                        estudio = interceptado,
                        // Inicio recarga al volver: lo leído acá no reaparece como nuevo.
                        alCerrarConCambios = { app.contenedor.cambiosFuera.value = SystemClock.elapsedRealtime() },
                    ),
                )
            }
        }
    }
    private var multiventana by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!leer(intent)) {
            finish()
            return
        }
        // Antes que el NavHost: el del NavHost (registrado después) gana mientras hay un hilo abierto.
        onBackPressedDispatcher.addCallback(this) { irAlInicio() }
        addOnMultiWindowModeChangedListener { multiventana = it.isInMultiWindowMode }
        setContent {
            ApunteScrollTema {
                PantallaInterceptada(
                    app = app,
                    interceptado = interceptado,
                    feed = feed,
                    multiventana = multiventana,
                    alCerrar = ::irAlInicio,
                    alEntrar = ::entrar,
                    alIrIgual = ::irIgual,
                    alAbrirApp = ::abrirApp,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!leer(intent)) return
        // Ya en primer plano, Android puede entregarlo sin volver a pasar por onResume.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) alMostrarse()
    }

    override fun onResume() {
        super.onResume()
        multiventana = isInMultiWindowMode
        alMostrarse()
    }

    override fun onPause() {
        app.contenedor.intercepcion.alOcultarseFeed()
        super.onPause()
    }

    private fun leer(intent: Intent?): Boolean {
        val red = app.contenedor.catalogoRedes.de(intent?.getStringExtra(EXTRA_RED)) ?: return false
        interceptado.mostrar(red, MotivoIntercepcion.desdeNombre(intent?.getStringExtra(EXTRA_MOTIVO)))
        return true
    }

    private fun alMostrarse() {
        val red = interceptado.red.value ?: return
        app.contenedor.intercepcion.alMostrarseFeed(red.paquete)
        interceptado.alMostrarse()
    }

    /** Atrás o cerrar: al inicio del teléfono, nunca de vuelta a la red (un finish() solo volvería a ella). */
    private fun irAlInicio() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            // Sin launcher disponible: igual se cierra.
        }
        finish()
    }

    private fun entrar() {
        val ahora = SystemClock.elapsedRealtime()
        val disponibles = feed.disponiblesSin(interceptado.progreso.value.ids)
        if (!interceptado.entrar(ahora, feed.estado.value, disponibles)) return
        app.contenedor.intercepcion.paseHastaReloj()?.let { hasta ->
            val hora = DateFormat.getTimeFormat(this).format(Date(hasta))
            Toast.makeText(this, getString(R.string.toast_pase_hasta, hora), Toast.LENGTH_SHORT).show()
        }
        // Vuelve la tarea de abajo: la red, en la pantalla en que estaba.
        finish()
    }

    private fun irIgual() {
        if (interceptado.irIgual(SystemClock.elapsedRealtime(), feed.estado.value)) finish()
    }

    private fun abrirApp(destino: Destino) {
        startActivity(MainActivity.intentDestino(this, destino))
        finish()
    }

    companion object {
        private const val EXTRA_RED = "com.apuntescroll.extra.RED"
        private const val EXTRA_MOTIVO = "com.apuntescroll.extra.MOTIVO"

        /** Desde el servicio. NO_USER_ACTION: la red no lo toma como "el usuario se va" y no pasa a PiP. */
        fun crearIntent(context: Context, red: String, motivo: MotivoIntercepcion): Intent =
            Intent(context, FeedInterceptadoActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_RED, red)
                .putExtra(EXTRA_MOTIVO, motivo.name)
    }
}
