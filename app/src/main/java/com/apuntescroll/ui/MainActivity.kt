package com.apuntescroll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.ui.tema.ApunteScrollTema
import kotlinx.coroutines.flow.MutableStateFlow

/** A dónde ir al abrir la app desde afuera (la pantalla interceptada o Accesibilidad de Android). */
enum class Destino { AJUSTES_REDES, AVISO, BIBLIOTECA }

class MainActivity : ComponentActivity() {

    /** El destino pedido que AppRaiz todavía no atendió. */
    private val destino = MutableStateFlow<Destino?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dibuja de borde a borde en todas las versiones (Android lo fuerza solo desde la 15) y
        // ajusta el color de los íconos de las barras del sistema según el tema. No sacarlo.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Solo la primera vez: al rotar, el intent es el mismo y no hay que volver a navegar.
        if (savedInstanceState == null) destino.value = leerDestino(intent)

        val app = application as ApunteScrollApp
        setContent {
            ApunteScrollTema {
                AppRaiz(app, destino, alAtender = { destino.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerDestino(intent)?.let { destino.value = it }
    }

    override fun onResume() {
        super.onResume()
        val c = (application as ApunteScrollApp).contenedor
        // Abrir ApunteScroll termina la visita de "Ir igual".
        c.intercepcion.alAbrirApp()
        // Por si vuelve de Ajustes de Android (activar o apagar el servicio).
        c.detector.refrescar()
    }

    companion object {
        private const val EXTRA_DESTINO = "com.apuntescroll.extra.DESTINO"

        fun intentDestino(context: Context, destino: Destino): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DESTINO, destino.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun leerDestino(intent: Intent?): Destino? = when {
            // El botón «Ajustes» del servicio en Accesibilidad de Android (activity-alias).
            intent?.component?.className?.endsWith(".ui.AbrirAjustesRedes") == true -> Destino.AJUSTES_REDES
            else -> Destino.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_DESTINO) }
        }
    }
}
