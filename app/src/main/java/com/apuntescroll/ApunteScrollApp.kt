package com.apuntescroll

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.datos.AjustesIntercepcion
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.BaseDeDatos
import com.apuntescroll.intercepcion.CoordinadorIntercepcion
import com.apuntescroll.intercepcion.DetectorAccesibilidad
import com.apuntescroll.intercepcion.DetectorDeApps
import com.apuntescroll.intercepcion.EntornoAndroid
import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.pdf.ArchivosPdf
import com.apuntescroll.pdf.ProcesadorDocumentos
import com.apuntescroll.pdf.ProgramadorProcesamiento
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app. Arma el [Contenedor] con las dependencias compartidas
 * (inyección manual, como en el Bloqueador).
 */
class ApunteScrollApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    /**
     * Para tareas que no dependen de ninguna pantalla. Un error en una de ellas queda en el log en
     * vez de cerrar la app.
     */
    val alcance = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e("ApunteScrollApp", "Error en una tarea de fondo", e) },
    )

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Deja en el log cada pantalla que Android no deja abrir desde segundo plano (F3).
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectBlockedBackgroundActivityLaunch().penaltyLog().build())
        }
        // PdfBox necesita el contexto para cargar sus fuentes y recursos.
        PDFBoxResourceLoader.init(applicationContext)
        contenedor = Contenedor(this)
        // Si el proceso lo levanta el servicio de intercepción (al arrancar el teléfono, después de
        // una caída o de una actualización), esto también corre. Es a propósito: sin rearmado es una
        // consulta, y con rearmado el trabajo hay que hacerlo igual (la pantalla interceptada muestra
        // "Preparando tus tarjetas…"). Se mide en la S23.
        alcance.launch { contenedor.repositorio.mantenimientoAlIniciar() }
        // Si cambió el armado (dominio/), los PDFs ya listos se rearman con el texto guardado. El
        // feed espera a que termine (ProcesadorDocumentos.rearmando).
        alcance.launch { contenedor.procesador.rearmarDesactualizados() }
    }
}

/** Dependencias que viven mientras vive el proceso. */
class Contenedor(app: ApunteScrollApp) {
    val ajustes = Ajustes(app)
    val bd: BaseDeDatos by lazy { BaseDeDatos.crear(app) }
    val archivos = ArchivosPdf(app)
    val programador = ProgramadorProcesamiento(app)
    val procesador by lazy { ProcesadorDocumentos(bd, archivos, ajustes) }
    val repositorio by lazy { RepositorioApuntes(bd, archivos, programador) }

    // Intercepción (F3). La red de prueba (calculadora) existe solo en debug.
    val catalogoRedes = CatalogoRedes(Redes.CONOCIDAS + if (BuildConfig.DEBUG) Redes.DE_PRUEBA else emptyList())
    val ajustesIntercepcion = AjustesIntercepcion(app, catalogoRedes, BuildConfig.DEBUG)
    val intercepcion by lazy { CoordinadorIntercepcion(app, ajustesIntercepcion, catalogoRedes, EntornoAndroid(app)) }

    /** El detector concreto: solo ServicioIntercepcion lo usa con este tipo. */
    val detectorAccesibilidad: DetectorAccesibilidad by lazy {
        DetectorAccesibilidad(app, ajustesIntercepcion).also { it.alConectar(intercepcion) }
    }
    val detector: DetectorDeApps get() = detectorAccesibilidad

    /** Cambia cuando el feed interceptado escribió en la base: Inicio recarga al volver (FeedViewModel). */
    val cambiosFuera = MutableStateFlow(0L)
}
