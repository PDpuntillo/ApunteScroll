package com.apuntescroll.ui.biblioteca

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.pdf.ArchivosPdf
import com.apuntescroll.pdf.RenderizadorPdf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EstadoPagina {
    data object Cargando : EstadoPagina
    data class Lista(val imagen: Bitmap, val numero: Int, val total: Int) : EstadoPagina
    data object Error : EstadoPagina
}

/** Muestra la página original del PDF (para tablas, fórmulas o gráficos que el texto no captura). */
class PaginaViewModel(
    private val repositorio: RepositorioApuntes,
    private val archivos: ArchivosPdf,
    /** Alcance de la app: el renderizador se cierra ahí, después de que termine lo que esté dibujando. */
    private val alcanceApp: CoroutineScope,
    private val documentoId: Long,
    paginaInicial: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoPagina>(EstadoPagina.Cargando)
    val estado: StateFlow<EstadoPagina> = _estado.asStateFlow()

    // Todo lo que toca el renderizador va dentro de [mutex]: PdfRenderer no admite dos hilos a la
    // vez, y cerrarlo con una página abierta lanza una excepción.
    private val mutex = Mutex()
    private var renderizador: RenderizadorPdf? = null
    private var cerrado = false

    private var trabajo: Job? = null
    private var ancho = 0
    private var numero = paginaInicial

    /** La pantalla avisa su ancho en píxeles; se dibuja al doble para poder hacer zoom. */
    fun fijarAncho(px: Int) {
        val nuevo = (px * 2).coerceIn(800, 3000)
        if (nuevo == ancho) return
        ancho = nuevo
        cargar(numero)
    }

    fun irA(pagina: Int) {
        val total = (estado.value as? EstadoPagina.Lista)?.total ?: return
        if (pagina in 1..total && pagina != numero) cargar(pagina)
    }

    private fun cargar(pagina: Int) {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            try {
                val (imagen, total) = mutex.withLock {
                    withContext(Dispatchers.IO) {
                        if (cerrado) throw CancellationException("La pantalla ya se cerró")
                        val r = renderizador ?: abrir().also { renderizador = it }
                        val n = pagina.coerceIn(1, r.paginas)
                        numero = n
                        r.dibujar(n, ancho) to r.paginas
                    }
                }
                _estado.value = EstadoPagina.Lista(imagen, numero, total)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo dibujar la página $pagina", e)
                _estado.value = EstadoPagina.Error
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Sin memoria para dibujar la página $pagina", e)
                _estado.value = EstadoPagina.Error
            }
        }
    }

    private suspend fun abrir(): RenderizadorPdf {
        val documento = repositorio.documento(documentoId).first() ?: error("El documento ya no existe")
        return RenderizadorPdf(archivos.archivo(documento.archivo))
    }

    override fun onCleared() {
        trabajo?.cancel()
        // El dibujo en curso no se puede interrumpir: se cierra cuando suelte el mutex.
        alcanceApp.launch(Dispatchers.IO) {
            mutex.withLock {
                cerrado = true
                renderizador?.close()
                renderizador = null
            }
        }
    }

    private companion object {
        const val TAG = "PaginaViewModel"
    }
}
