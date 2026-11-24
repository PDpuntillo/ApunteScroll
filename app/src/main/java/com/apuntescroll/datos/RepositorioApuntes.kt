package com.apuntescroll.datos

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.apuntescroll.datos.bd.BaseDeDatos
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.datos.bd.Documento
import com.apuntescroll.datos.bd.EstadoDocumento
import com.apuntescroll.datos.bd.FirmaTarjetas
import com.apuntescroll.datos.bd.Materia
import com.apuntescroll.datos.bd.MateriaConResumen
import com.apuntescroll.datos.bd.SeccionConTarjetas
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.pdf.ArchivosPdf
import com.apuntescroll.pdf.ProgramadorProcesamiento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** PDF ya copiado a la carpeta de la app, todavía sin materia. */
data class PdfPreparado(val titulo: String, val archivo: String)

data class Preparacion(val pdfs: List<PdfPreparado>, val fallidos: Int)

/** Punto único de acceso a materias, documentos y tarjetas para las pantallas. */
class RepositorioApuntes(
    private val bd: BaseDeDatos,
    private val archivos: ArchivosPdf,
    private val programador: ProgramadorProcesamiento,
) {

    fun materias(): Flow<List<MateriaConResumen>> = bd.materias().observarConResumen()

    fun materia(id: Long): Flow<Materia?> = bd.materias().observar(id)

    fun documentos(materiaId: Long): Flow<List<Documento>> = bd.documentos().observarPorMateria(materiaId)

    fun documento(id: Long): Flow<Documento?> = bd.documentos().observar(id)

    /** Secciones en orden, cada una con sus tarjetas en orden. */
    fun secciones(documentoId: Long): Flow<List<SeccionConTarjetas>> =
        bd.tarjetas().observarSecciones(documentoId).map { secciones ->
            secciones.map { it.copy(tarjetas = it.tarjetas.sortedBy { t -> t.orden }) }
        }

    fun totalTarjetas(): Flow<Int> = bd.tarjetas().observarTotal()

    // ---------------------------------------------------------------------- feed (F2)

    suspend fun tarjetasParaFeed(): List<TarjetaFeed> = bd.tarjetas().paraFeed()

    /** Cambia cuando se agregan, se borran o se rearman tarjetas (no cuando se marcan). */
    fun firmaTarjetas(): Flow<FirmaTarjetas> = bd.tarjetas().observarFirma().distinctUntilChanged()

    suspend fun cuantasTarjetasHasta(ultimoId: Long): Int = bd.tarjetas().cuantasHasta(ultimoId)

    fun tarjetasDeSeccion(seccionId: Long): Flow<List<TarjetaFeed>> = bd.tarjetas().observarSeccion(seccionId)

    suspend fun marcarVista(tarjetaId: Long) = bd.tarjetas().marcarVista(tarjetaId, System.currentTimeMillis())

    /**
     * "Lo sé" (o deshacerlo). En F2 alcanza con la caja 1 y el próximo repaso en un día; el
     * repaso espaciado completo llega en F4 (PLAN.md, sección 5).
     */
    suspend fun marcarSabidas(ids: List<Long>, sabidas: Boolean) {
        if (ids.isEmpty()) return
        if (sabidas) {
            bd.tarjetas().fijarCaja(ids, caja = 1, proximoRepaso = System.currentTimeMillis() + UN_DIA_MS)
        } else {
            bd.tarjetas().fijarCaja(ids, caja = 0, proximoRepaso = null)
        }
    }

    suspend fun guardar(ids: List<Long>, guardada: Boolean) {
        if (ids.isNotEmpty()) bd.tarjetas().fijarGuardada(ids, guardada)
    }

    suspend fun materiasParaElegir(): List<Materia> = bd.materias().todas()

    suspend fun crearMateria(nombre: String, color: ColorMateria, emoji: String): Long =
        bd.materias().insertar(
            Materia(nombre = nombre.trim(), color = color, emoji = emoji, creada = System.currentTimeMillis()),
        )

    suspend fun actualizarMateria(materia: Materia) = bd.materias().actualizar(materia.copy(nombre = materia.nombre.trim()))

    /** Borra la materia con todos sus PDFs (archivos, texto y tarjetas). */
    suspend fun borrarMateria(materia: Materia) {
        bd.documentos().porMateria(materia.id).forEach { limpiarArchivo(it) }
        bd.materias().borrar(materia)
    }

    /**
     * Copia cada PDF elegido a la carpeta de la app. Se hace apenas se eligen, porque el permiso
     * para leer lo que devuelve el selector de archivos puede vencer (por ejemplo, si se rota la
     * pantalla mientras se elige la materia).
     */
    suspend fun preparar(uris: List<Uri>): Preparacion {
        val listos = mutableListOf<PdfPreparado>()
        var fallidos = 0
        for (uri in uris) {
            try {
                listos += PdfPreparado(titulo = archivos.nombreVisible(uri), archivo = archivos.copiar(uri))
            } catch (e: CancellationException) {
                // Se canceló la importación: no dejar copias sueltas.
                withContext(NonCancellable) { descartar(listos) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo copiar $uri", e)
                fallidos++
            }
        }
        return Preparacion(listos, fallidos)
    }

    /**
     * Registra los PDFs ya copiados en [materiaId] y empieza a procesarlos. Devuelve false si no
     * se pudo (por ejemplo, porque la materia se borró mientras se copiaban); en ese caso las
     * copias se borran.
     */
    suspend fun registrar(pdfs: List<PdfPreparado>, materiaId: Long): Boolean {
        val ahora = System.currentTimeMillis()
        val ids = try {
            bd.withTransaction {
                if (bd.materias().obtener(materiaId) == null) return@withTransaction null
                pdfs.mapIndexed { i, pdf ->
                    bd.documentos().insertar(
                        // -i para que el orden "más nuevo primero" respete el orden en que se eligieron.
                        Documento(materiaId = materiaId, titulo = pdf.titulo, archivo = pdf.archivo, creado = ahora - i),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron registrar los PDFs en la materia $materiaId", e)
            null
        }
        if (ids == null) {
            descartar(pdfs)
            return false
        }
        // Se encola recién después de confirmar la transacción: el worker tiene que encontrar la fila.
        ids.forEach { programador.programar(it) }
        return true
    }

    /** Borra las copias de PDFs que al final no se importaron. */
    suspend fun descartar(pdfs: List<PdfPreparado>) {
        pdfs.forEach { archivos.borrar(it.archivo) }
    }

    /** [preparar] + [registrar] en un paso. Devuelve cuántos no se pudieron importar. */
    suspend fun importar(uris: List<Uri>, materiaId: Long): Int {
        val preparacion = preparar(uris)
        if (preparacion.pdfs.isEmpty()) return preparacion.fallidos
        return if (registrar(preparacion.pdfs, materiaId)) preparacion.fallidos else uris.size
    }

    suspend fun borrarDocumento(documento: Documento) {
        limpiarArchivo(documento)
        bd.documentos().borrar(documento)
    }

    /** Vuelve a intentar un documento que quedó en ERROR, desde donde había quedado. */
    suspend fun reintentar(documento: Documento) {
        val estado = if (documento.paginas > 0 && documento.paginasProcesadas >= documento.paginas) {
            EstadoDocumento.ARMANDO
        } else {
            EstadoDocumento.PENDIENTE
        }
        bd.documentos().cambiarEstado(documento.id, estado)
        programador.programar(documento.id)
    }

    /**
     * Al abrir la app: vuelve a encolar lo que quedó a medias (por ejemplo, si se cerró a la
     * fuerza) y borra copias de PDFs que quedaron sin documento (importaciones interrumpidas).
     */
    suspend fun mantenimientoAlIniciar() {
        bd.documentos().sinTerminar().forEach { programador.programar(it.id) }
        archivos.borrarHuerfanos(bd.documentos().archivos().toSet(), antiguedadMs = 60 * 60 * 1000L)
    }

    private suspend fun limpiarArchivo(documento: Documento) {
        programador.cancelar(documento.id)
        archivos.borrar(documento.archivo)
    }

    private companion object {
        const val TAG = "RepositorioApuntes"
        const val UN_DIA_MS = 24 * 60 * 60 * 1000L
    }
}
