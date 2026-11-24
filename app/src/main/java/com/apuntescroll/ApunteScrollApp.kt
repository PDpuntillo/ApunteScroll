package com.apuntescroll

import android.app.Application
import android.util.Log
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.BaseDeDatos
import com.apuntescroll.pdf.ArchivosPdf
import com.apuntescroll.pdf.ProcesadorDocumentos
import com.apuntescroll.pdf.ProgramadorProcesamiento
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        // PdfBox necesita el contexto para cargar sus fuentes y recursos.
        PDFBoxResourceLoader.init(applicationContext)
        contenedor = Contenedor(this)
        alcance.launch { contenedor.repositorio.mantenimientoAlIniciar() }
        // Si cambió el armado (dominio/), los PDFs ya listos se rearman con el texto guardado. El
        // feed espera a que termine (ProcesadorDocumentos.rearmando).
        alcance.launch { contenedor.procesador.rearmarDesactualizados() }
    }
}

/** Dependencias que viven mientras vive el proceso. */
class Contenedor(app: Application) {
    val ajustes = Ajustes(app)
    val bd: BaseDeDatos by lazy { BaseDeDatos.crear(app) }
    val archivos = ArchivosPdf(app)
    val programador = ProgramadorProcesamiento(app)
    val procesador by lazy { ProcesadorDocumentos(bd, archivos, ajustes) }
    val repositorio by lazy { RepositorioApuntes(bd, archivos, programador) }
}
