package com.apuntescroll.intercepcion

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.apuntescroll.R
import com.apuntescroll.datos.AjustesIntercepcion
import com.apuntescroll.intercepcion.reglas.Accion
import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.ClasificadorPaquetes
import com.apuntescroll.intercepcion.reglas.ComoPasa
import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.ContextoMotor
import com.apuntescroll.intercepcion.reglas.Entorno
import com.apuntescroll.intercepcion.reglas.MotorIntercepcion
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.intercepcion.reglas.RelojPase

/**
 * Une el detector, el motor (Kotlin puro) y Android: ejecuta lo que el motor decide, programa sus
 * tareas en el hilo principal y guarda lo que hay que guardar. Vive en el Contenedor. Todos los
 * métodos se llaman desde el hilo principal.
 */
@MainThread
class CoordinadorIntercepcion(
    private val app: Application,
    private val ajustes: AjustesIntercepcion,
    catalogo: CatalogoRedes,
    private val entornoAndroid: EntornoAndroid,
) : ContextoMotor, ReceptorPrimerPlano {

    /** Para F5 (conteo de intercepciones por día); F3 no lo usa. Se llama con Lanzar(reintento = false). */
    var alInterceptar: ((RedConocida) -> Unit)? = null

    /** Con el servicio activado sin consentimiento, el aviso se abre solo una vez por proceso (PLAN.md, sección 7). */
    var avisoAutomaticoMostrado = false

    private val motor = MotorIntercepcion(ClasificadorPaquetes(app.packageName, catalogo), Build.VERSION.SDK_INT)
    private val handler = Handler(Looper.getMainLooper())
    private var lanzador: LanzadorPantalla? = null
    private var tecladoActual: String? = null

    private val receptorPantalla = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> ejecutar(motor.alApagarPantalla())
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> ejecutar(motor.alEncenderPantalla(this@CoordinadorIntercepcion))
            }
        }
    }

    private val observadorTeclado = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = leerTeclado()
    }

    // ContextoMotor
    override val config: ConfigIntercepcion get() = ajustes.config.value
    override val teclado: String? get() = tecladoActual

    override fun paseRestanteMs(): Long =
        RelojPase.restanteMs(ajustes.pase.value, SystemClock.elapsedRealtime(), entornoAndroid.arranque())

    override fun hayPaseVencido(): Boolean = ajustes.pase.value != null && paseRestanteMs() == 0L

    override fun entorno(): Entorno = entornoAndroid.leer()

    // ReceptorPrimerPlano
    override fun conectado(lanzador: LanzadorPantalla) {
        if (this.lanzador === lanzador) return
        this.lanzador?.let { desconectado(it) }
        this.lanzador = lanzador
        // SCREEN_ON/OFF los manda system_server y USER_PRESENT, SystemUI (que no es el uid del
        // sistema): con NOT_EXPORTED podría no llegar. Las tres son difusiones protegidas: solo el
        // sistema puede mandarlas.
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(app, receptorPantalla, filtro, ContextCompat.RECEIVER_EXPORTED)
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observadorTeclado,
        )
        leerTeclado()
        if (ajustes.consentimientoVigente()) ajustes.marcarVistoConectado()
        ejecutar(motor.alConectar(this))
    }

    override fun alPrimerPlano(paquete: String) {
        if (!ajustes.consentimientoVigente()) return
        ejecutar(motor.alPrimerPlano(paquete, ahora(), this), paquete)
    }

    /** Idempotente y solo si es el mismo lanzador: quita todo y cancela las tareas. */
    override fun desconectado(lanzador: LanzadorPantalla) {
        if (this.lanzador !== lanzador) return
        this.lanzador = null
        try {
            app.unregisterReceiver(receptorPantalla)
        } catch (e: IllegalArgumentException) {
            // No estaba registrado.
        }
        app.contentResolver.unregisterContentObserver(observadorTeclado)
        handler.removeCallbacksAndMessages(null)
        motor.reiniciar()
    }

    fun alMostrarseFeed(red: String) = ejecutar(motor.alMostrarseFeed(red, ahora()))

    fun alOcultarseFeed() = ejecutar(motor.alOcultarseFeed(ahora()))

    /** "Entrar" (PASE) o "Ir igual" (VISITA), antes del finish() de la pantalla interceptada. */
    fun alDejarPasar(red: String, como: ComoPasa) {
        if (como == ComoPasa.PASE && paseRestanteMs() <= 0) {
            ajustes.iniciarPase(RelojPase.nuevo(ahora(), entornoAndroid.arranque(), config.minutosPase))
        }
        ejecutar(motor.alDejarPasar(red, como, ahora(), this))
    }

    /** Se abrió MainActivity: termina la visita de "Ir igual". */
    fun alAbrirApp() = ejecutar(motor.alAbrirApp())

    fun terminarPase() {
        ajustes.terminarPase()
        ejecutar(motor.alCambiarPase(this))
    }

    /** Hasta cuándo dura el pase, en reloj de pared (para mostrar la hora), o null si no hay. */
    fun paseHastaReloj(): Long? = paseRestanteMs().takeIf { it > 0 }?.let { System.currentTimeMillis() + it }

    fun aceptarAviso() {
        ajustes.aceptar(System.currentTimeMillis())
        // Si el servicio ya estaba conectado (se activó antes de aceptar), conectar() no vuelve a
        // correr: hay que hacer acá lo que hace al conectarse con consentimiento.
        if (lanzador != null) {
            ajustes.marcarVistoConectado()
            ejecutar(motor.alConectar(this))
        }
    }

    /**
     * Apaga el servicio y, si [retirar], también retira el consentimiento. false si el servicio no
     * estaba conectado (hay que apagarlo a mano en Accesibilidad).
     */
    fun apagarServicio(retirar: Boolean): Boolean {
        if (retirar) {
            ajustes.retirar()
            ejecutar(motor.reiniciar())
        }
        val l = lanzador ?: return false
        l.apagar()
        return true
    }

    private fun ahora() = SystemClock.elapsedRealtime()

    private fun leerTeclado() {
        tecladoActual = try {
            ClasificadorPaquetes.paqueteDeTeclado(Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun ejecutar(acciones: List<Accion>, paquete: String? = null) {
        if (acciones.isNotEmpty() || paquete != null) {
            RegistroDepuracion.anotar { "${paquete ?: "-"} → $acciones" }
        }
        var falloLanzar = false
        for (a in acciones) {
            when (a) {
                is Accion.Lanzar -> {
                    val intent = FeedInterceptadoActivity.crearIntent(app, a.red.paquete, a.motivo)
                    if (lanzador?.abrir(intent) != true) falloLanzar = true
                    if (!a.reintento) alInterceptar?.invoke(a.red)
                }
                is Accion.Programar -> {
                    handler.removeCallbacksAndMessages(a.tarea)
                    handler.postAtTime({ ejecutar(motor.alTarea(a.tarea, ahora(), this)) }, a.tarea, SystemClock.uptimeMillis() + a.enMs)
                }
                is Accion.Cancelar -> handler.removeCallbacksAndMessages(a.tarea)
                is Accion.AvisoNoAparece -> ajustes.marcarNoAparece(a.activo)
                Accion.AvisoPelea -> ajustes.marcarPelea(System.currentTimeMillis())
                Accion.AvisoBucle -> ajustes.marcarBucle(System.currentTimeMillis())
                // Los Toast de texto se pueden mostrar desde segundo plano. [verificar en la S23 y en la API 37]
                is Accion.AvisoUltimoMinuto -> Toast.makeText(app, R.string.toast_ultimo_minuto, Toast.LENGTH_LONG).show()
                Accion.OlvidarPaseVencido -> ajustes.terminarPase()
            }
        }
        // Después del resto: si no, el Programar(COMPROBAR_APARICION, 1500) que acompaña al
        // lanzamiento pisaría la comprobación inmediata.
        if (falloLanzar) ejecutar(motor.alFalloLanzar(ahora()))
    }
}
