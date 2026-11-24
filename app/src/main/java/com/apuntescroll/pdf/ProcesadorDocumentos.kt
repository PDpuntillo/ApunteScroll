package com.apuntescroll.pdf

import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.datos.bd.BaseDeDatos
import com.apuntescroll.datos.bd.Documento
import com.apuntescroll.datos.bd.EstadoDocumento
import com.apuntescroll.datos.bd.OrigenTexto
import com.apuntescroll.datos.bd.PaginaTexto
import com.apuntescroll.datos.bd.Seccion
import com.apuntescroll.datos.bd.Tarjeta
import com.apuntescroll.dominio.ArmadorTarjetas
import com.apuntescroll.dominio.DocumentoArmado
import com.apuntescroll.dominio.PaginaExtraida
import com.google.mlkit.common.MlKitException
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** Qué tiene que hacer el worker después de una pasada. */
enum class Paso {
    /** El documento quedó LISTO o en ERROR (o ya no existe). */
    TERMINADO,

    /** Se acabó el tiempo de esta pasada: hay que seguir en otra (PLAN.md, sección 3.1). */
    SEGUIR_DESPUES,

    /** Falló algo pasajero (por ejemplo, el modelo de OCR todavía se descarga). */
    REINTENTAR,
}

/**
 * Convierte un PDF importado en secciones y tarjetas, en dos etapas:
 * 1. EXTRAYENDO: texto de cada página (PdfBox, u OCR si es escaneada), guardado de a una página
 *    para poder seguir donde quedó si el worker se corta.
 * 2. ARMANDO: limpieza y corte en tarjetas con [ArmadorTarjetas], todo junto porque detectar
 *    encabezados y pies repetidos necesita ver todas las páginas.
 */
class ProcesadorDocumentos(
    private val bd: BaseDeDatos,
    private val archivos: ArchivosPdf,
    private val ajustes: Ajustes,
) {

    suspend fun procesar(documentoId: Long, presupuestoMs: Long, intento: Int): Paso {
        val documento = bd.documentos().obtener(documentoId) ?: return Paso.TERMINADO
        if (documento.estado == EstadoDocumento.LISTO || documento.estado == EstadoDocumento.ERROR) {
            return Paso.TERMINADO
        }
        val archivo = archivos.archivo(documento.archivo)
        if (!archivo.exists()) return fallar(documentoId, "No se encontró el archivo del PDF.")

        return try {
            if (documento.estado != EstadoDocumento.ARMANDO) {
                bd.documentos().cambiarEstado(documentoId, EstadoDocumento.EXTRAYENDO)
                val completo = extraer(documento, archivo, SystemClock.elapsedRealtime() + presupuestoMs)
                if (!completo) return Paso.SEGUIR_DESPUES
                bd.documentos().cambiarEstado(documentoId, EstadoDocumento.ARMANDO)
            }
            armar(documentoId)
        } catch (e: CancellationException) {
            // WorkManager detuvo el worker; lo guardado hasta acá se retoma en la próxima pasada.
            throw e
        } catch (e: InvalidPasswordException) {
            fallar(documentoId, "El PDF está protegido con contraseña.")
        } catch (e: MlKitException) {
            // Solo llega acá UNAVAILABLE (ver leerConOcr): el modelo de OCR todavía se descarga.
            // La página que lo necesitaba no se guardó, así que Reintentar sigue desde ahí.
            Log.w(TAG, "El modelo de OCR todavía no está disponible", e)
            if (intento < MAX_INTENTOS_OCR) {
                Paso.REINTENTAR
            } else {
                fallar(
                    documentoId,
                    "El reconocimiento de texto todavía no está disponible. Conectá el teléfono a internet y tocá Reintentar.",
                )
            }
        } catch (e: IOException) {
            if (esCifradoConCertificado(e)) {
                fallar(documentoId, "El PDF usa un tipo de protección que la app no puede abrir.")
            } else {
                Log.w(TAG, "No se pudo leer el PDF $documentoId", e)
                if (intento < MAX_INTENTOS) Paso.REINTENTAR else fallar(documentoId, "No se pudo leer el PDF. Puede estar dañado.")
            }
        } catch (e: LinkageError) {
            // Respaldo: falta una clase de BouncyCastle excluida a propósito (app/build.gradle.kts).
            Log.w(TAG, "PDF con un cifrado no soportado: $documentoId", e)
            fallar(documentoId, "El PDF usa un tipo de protección que la app no puede abrir.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar el PDF $documentoId", e)
            if (intento < MAX_INTENTOS) Paso.REINTENTAR else fallar(documentoId, "Ocurrió un error al procesar el PDF.")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Sin memoria al procesar el PDF $documentoId", e)
            fallar(documentoId, "El PDF es demasiado pesado para procesarlo en este teléfono.")
        }
    }

    /** Devuelve true si se extrajeron todas las páginas, false si se acabó el tiempo. */
    private suspend fun extraer(documento: Documento, archivo: File, limite: Long): Boolean {
        val id = documento.id
        ExtractorTexto(archivo).use { extractor ->
            val total = extractor.paginas
            if (documento.paginas != total) bd.documentos().fijarPaginas(id, total)

            var procesadas = documento.paginasProcesadas
            var conOcr = documento.paginasOcr
            var renderizador: RenderizadorPdf? = null
            var ocr: Ocr? = null
            try {
                while (procesadas < total) {
                    if (SystemClock.elapsedRealtime() > limite) return false
                    val numero = procesadas + 1
                    var texto = extractor.texto(numero)
                    var origen = OrigenTexto.TEXTO

                    if (ExtractorTexto.pareceEscaneada(texto) && extractor.puedeTenerImagenes(numero)) {
                        val leido = leerConOcr(numero) {
                            val r = renderizador ?: RenderizadorPdf(archivo).also { renderizador = it }
                            val o = ocr ?: Ocr().also { ocr = it }
                            val imagen = r.dibujar(numero, RenderizadorPdf.ANCHO_OCR)
                            try {
                                o.reconocer(imagen)
                            } finally {
                                imagen.recycle()
                            }
                        }
                        if (leido != null && leido.count { it.isLetterOrDigit() } > texto.count { it.isLetterOrDigit() }) {
                            texto = leido
                            origen = OrigenTexto.OCR
                            conOcr++
                        }
                    }

                    bd.paginas().guardar(PaginaTexto(id, numero, texto, origen))
                    procesadas = numero
                    bd.documentos().fijarProgreso(id, procesadas, conOcr)
                }
            } finally {
                renderizador?.close()
                ocr?.close()
            }
        }
        return true
    }

    /**
     * OCR de una página, sin que un fallo tire abajo todo el documento: si no se puede leer, se
     * queda el texto de PdfBox y se sigue con la próxima. La única excepción que sube es
     * UNAVAILABLE (el modelo todavía se descarga): esa página no se guarda, y el documento se
     * reintenta más tarde desde ella (ver procesar).
     */
    private suspend fun leerConOcr(numero: Int, leer: suspend () -> String): String? = try {
        leer()
    } catch (e: CancellationException) {
        throw e
    } catch (e: MlKitException) {
        if (e.errorCode == MlKitException.UNAVAILABLE) throw e
        Log.w(TAG, "OCR no disponible para la página $numero (código ${e.errorCode})", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer con OCR la página $numero", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Sin memoria para el OCR de la página $numero", e)
        null
    }

    private suspend fun armar(documentoId: Long): Paso {
        val documento = bd.documentos().obtener(documentoId) ?: return Paso.TERMINADO
        val armado = armarTarjetas(documento)
            ?: return fallar(documentoId, "No se encontró texto en el PDF. Si es escaneado, no se pudo reconocer.")
        bd.withTransaction {
            guardarArmado(documentoId, armado)
            bd.documentos().cambiarEstado(documentoId, EstadoDocumento.LISTO)
        }
        return Paso.TERMINADO
    }

    private val _rearmando = MutableStateFlow(true)

    /**
     * true desde que arranca la app hasta que termina [rearmarDesactualizados]. El feed espera a
     * que sea false para leer las tarjetas: si no, mostraría tarjetas que están por reemplazarse
     * (y lo que se marcara en ellas se perdería).
     */
    val rearmando: StateFlow<Boolean> = _rearmando.asStateFlow()

    /**
     * Rearma los PDFs listos cuyas tarjetas salieron de una versión anterior de [ArmadorTarjetas],
     * con el texto de las páginas ya guardado (no se vuelve a leer el PDF). Las tarjetas cuyo texto
     * no cambió conservan su id y su progreso. Se llama una vez al abrir la app; si se corta, se
     * retoma la próxima.
     */
    suspend fun rearmarDesactualizados() {
        try {
            val ids = bd.documentos().armadosAntesDe(ArmadorTarjetas.VERSION)
            if (ids.isNotEmpty()) Log.i(TAG, "Rearmando ${ids.size} PDF con el armado ${ArmadorTarjetas.VERSION}")
            ids.forEach { rearmar(it) }
        } finally {
            _rearmando.value = false
        }
    }

    private suspend fun rearmar(id: Long) {
        try {
            val documento = bd.documentos().obtener(id) ?: return
            val inicio = SystemClock.elapsedRealtime()
            // Si el armado nuevo no da tarjetas, quedan las de antes.
            val armado = armarTarjetas(documento) ?: return
            bd.withTransaction {
                // Se pudo haber borrado (o empezado a reprocesar) mientras se armaba.
                if (bd.documentos().obtener(id)?.estado != EstadoDocumento.LISTO) return@withTransaction
                guardarArmado(id, armado)
            }
            Log.i(TAG, "PDF $id rearmado en ${SystemClock.elapsedRealtime() - inicio} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo rearmar el PDF $id", e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Sin memoria para rearmar el PDF $id", e)
        }
    }

    /** Armado del documento con lo guardado de sus páginas; null si no sale ninguna tarjeta. */
    private suspend fun armarTarjetas(documento: Documento): Armado? {
        val paginas = bd.paginas().deDocumento(documento.id)
        // Se rearma con el largo con que se armó: cambiar el ajuste solo vale para los PDFs nuevos.
        // largoArmado = 0 solo queda en PDFs armados antes de la base v2 (instalaciones de
        // desarrollo anteriores al 24/11/2026, ya rearmadas): ahí se usa el ajuste actual.
        val largo = documento.largoArmado.takeIf { it > 0 } ?: ajustes.largoTarjeta.value.maxCaracteres
        val armado = ArmadorTarjetas.armar(paginas.map { PaginaExtraida(it.numero, it.texto) }, largo)
        if (armado.secciones.none { it.tarjetas.isNotEmpty() }) return null
        return Armado(armado, largo, paginas.associate { it.numero to it.origen })
    }

    /**
     * Reemplaza las secciones y tarjetas del documento. Se llama dentro de una transacción. Las
     * secciones y tarjetas que no cambiaron conservan su id (y las tarjetas, su progreso).
     */
    private suspend fun guardarArmado(documentoId: Long, armado: Armado) {
        val secciones = bd.tarjetas().seccionesDe(documentoId)
        val progreso = ProgresoPrevio(bd.tarjetas().deDocumento(documentoId), secciones.associate { it.id to it.orden })
        val seccionesPrevias = SeccionesPrevias(secciones)
        bd.tarjetas().borrarArmado(documentoId)
        armado.documento.secciones.forEachIndexed { i, seccion ->
            val seccionId = bd.tarjetas().insertarSeccion(
                Seccion(
                    id = seccionesPrevias.idPara(seccion.titulo, seccion.paginaInicio),
                    documentoId = documentoId,
                    orden = i,
                    titulo = seccion.titulo,
                    paginaInicio = seccion.paginaInicio,
                ),
            )
            bd.tarjetas().insertarTarjetas(
                seccion.tarjetas.mapIndexed { j, tarjeta ->
                    progreso.pasarA(
                        Tarjeta(
                            documentoId = documentoId,
                            seccionId = seccionId,
                            orden = j,
                            texto = tarjeta.texto,
                            pagina = tarjeta.pagina,
                            origen = armado.origenes[tarjeta.pagina] ?: OrigenTexto.TEXTO,
                        ),
                    )
                },
            )
        }
        bd.documentos().fijarArmado(documentoId, ArmadorTarjetas.VERSION, armado.largo)
    }

    private class Armado(val documento: DocumentoArmado, val largo: Int, val origenes: Map<Int, OrigenTexto>)

    suspend fun fallar(documentoId: Long, mensaje: String): Paso {
        bd.documentos().cambiarEstado(documentoId, EstadoDocumento.ERROR, mensaje)
        return Paso.TERMINADO
    }

    /**
     * PDF cifrado con certificado (PublicKeySecurityHandler): PdfBox lo rechaza con este mensaje
     * antes de usar BouncyCastle. El texto es el de PdfBox-Android 2.0.27.0; revisarlo si se
     * actualiza la librería.
     */
    private fun esCifradoConCertificado(e: IOException): Boolean =
        e.message?.startsWith("Provided decryption material is not compatible") == true ||
            e.message?.startsWith("No security handler for filter") == true

    private companion object {
        const val TAG = "ProcesadorDocumentos"
        const val MAX_INTENTOS = 2

        /** La primera descarga del modelo de OCR puede tardar: se reintenta más veces. */
        const val MAX_INTENTOS_OCR = 8
    }
}

/**
 * Progreso de las tarjetas de un armado anterior (lo sé, vistas, guardadas). Al rearmar, cada
 * tarjeta nueva toma el id y el progreso de una tarjeta anterior con el mismo texto; las que
 * cambiaron empiezan de cero, con un id nuevo. Con textos repetidos se emparejan en orden.
 *
 * Conservar el id hace que el feed y el hilo sigan encontrando la tarjeta. Ojo para F4: las
 * tarjetas viejas se borran antes de insertar las nuevas, así que lo que dependa de ellas con
 * borrado en cascada (por ejemplo, preguntas) se borra igual; habrá que pasarlo aparte.
 */
internal class ProgresoPrevio(
    previas: List<Tarjeta>,
    /** Orden de cada sección anterior (id → orden): las tarjetas se emparejan en orden de lectura. */
    ordenDeSeccion: Map<Long, Int> = emptyMap(),
) {

    // Por el orden de la sección, no por su id: después de un rearmado los ids de sección ya no
    // siguen el orden del documento (las que no cambiaron conservan el suyo).
    private val porTexto: Map<String, ArrayDeque<Tarjeta>> =
        previas.sortedWith(compareBy({ ordenDeSeccion[it.seccionId] ?: Int.MAX_VALUE }, { it.seccionId }, { it.orden }))
            .groupBy { it.texto }
            .mapValues { (_, lista) -> ArrayDeque(lista) }

    fun pasarA(nueva: Tarjeta): Tarjeta {
        val previa = porTexto[nueva.texto]?.removeFirstOrNull() ?: return nueva
        return nueva.copy(
            id = previa.id,
            caja = previa.caja,
            proximoRepaso = previa.proximoRepaso,
            vecesVista = previa.vecesVista,
            ultimaVista = previa.ultimaVista,
            guardada = previa.guardada,
        )
    }
}

/**
 * Ids de las secciones de un armado anterior. Una sección nueva con el mismo título y la misma
 * página de inicio que una anterior conserva su id (así un hilo abierto la sigue encontrando);
 * con repetidas se emparejan en orden. 0 = id nuevo.
 */
internal class SeccionesPrevias(previas: List<Seccion>) {

    private val porClave: Map<Pair<String?, Int>, ArrayDeque<Long>> =
        previas.sortedBy { it.orden }
            .groupBy { it.titulo to it.paginaInicio }
            .mapValues { (_, lista) -> ArrayDeque(lista.map { it.id }) }

    fun idPara(titulo: String?, paginaInicio: Int): Long = porClave[titulo to paginaInicio]?.removeFirstOrNull() ?: 0L
}
