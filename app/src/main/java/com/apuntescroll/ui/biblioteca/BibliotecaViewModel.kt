package com.apuntescroll.ui.biblioteca

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apuntescroll.datos.PdfPreparado
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.datos.bd.Materia
import com.apuntescroll.datos.bd.MateriaConResumen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de una importación, para mostrar en un aviso. */
data class ResultadoImportacion(val importados: Int, val fallidos: Int)

/** Paso del flujo "Importar PDF" desde la Biblioteca. */
sealed interface PasoImportacion {
    data object Ninguno : PasoImportacion

    /** Copiando los PDFs elegidos a la carpeta de la app. */
    data object Copiando : PasoImportacion

    /** Ya están copiados: falta decir a qué materia van. */
    data class ElegirMateria(val pdfs: List<PdfPreparado>, val fallidos: Int, val materias: List<Materia>) : PasoImportacion

    /** Se eligió "Materia nueva" (o no hay ninguna): falta crearla. */
    data class CrearMateria(val pdfs: List<PdfPreparado>, val fallidos: Int) : PasoImportacion
}

class BibliotecaViewModel(
    private val repositorio: RepositorioApuntes,
    /** Alcance de la app, para terminar tareas aunque se cierre la pantalla. */
    private val alcanceApp: CoroutineScope,
) : ViewModel() {

    /** null mientras carga. */
    val materias: StateFlow<List<MateriaConResumen>?> = repositorio.materias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _paso = MutableStateFlow<PasoImportacion>(PasoImportacion.Ninguno)
    val paso: StateFlow<PasoImportacion> = _paso.asStateFlow()

    private val _resultados = Channel<ResultadoImportacion>(Channel.BUFFERED)
    val resultados: Flow<ResultadoImportacion> = _resultados.receiveAsFlow()

    private var copia: Job? = null

    fun pdfsElegidos(uris: List<Uri>) {
        if (uris.isEmpty() || _paso.value != PasoImportacion.Ninguno) return
        _paso.value = PasoImportacion.Copiando
        copia = viewModelScope.launch {
            val preparacion = repositorio.preparar(uris)
            if (preparacion.pdfs.isEmpty()) {
                _paso.value = PasoImportacion.Ninguno
                _resultados.send(ResultadoImportacion(0, preparacion.fallidos))
                return@launch
            }
            val materias = repositorio.materiasParaElegir()
            _paso.value = if (materias.isEmpty()) {
                PasoImportacion.CrearMateria(preparacion.pdfs, preparacion.fallidos)
            } else {
                PasoImportacion.ElegirMateria(preparacion.pdfs, preparacion.fallidos, materias)
            }
        }
    }

    fun materiaElegida(materiaId: Long) {
        val actual = _paso.value as? PasoImportacion.ElegirMateria ?: return
        _paso.value = PasoImportacion.Ninguno
        registrar(actual.pdfs, actual.fallidos, materiaId)
    }

    fun pedirMateriaNueva() {
        val actual = _paso.value as? PasoImportacion.ElegirMateria ?: return
        _paso.value = PasoImportacion.CrearMateria(actual.pdfs, actual.fallidos)
    }

    /** Crea una materia; si venía del flujo de importación, registra los PDFs en ella. */
    fun crearMateria(nombre: String, color: ColorMateria, emoji: String) {
        val pendiente = _paso.value as? PasoImportacion.CrearMateria
        _paso.value = PasoImportacion.Ninguno
        viewModelScope.launch {
            val id = repositorio.crearMateria(nombre, color, emoji)
            if (pendiente != null) registrar(pendiente.pdfs, pendiente.fallidos, id)
        }
    }

    /** Cancela la importación en curso y borra las copias. */
    fun cancelar() {
        if (_paso.value == PasoImportacion.Copiando) {
            // preparar() borra lo que ya había copiado al cancelarse.
            copia?.cancel()
            _paso.value = PasoImportacion.Ninguno
            return
        }
        val pdfs = when (val p = _paso.value) {
            is PasoImportacion.ElegirMateria -> p.pdfs
            is PasoImportacion.CrearMateria -> p.pdfs
            else -> emptyList()
        }
        _paso.value = PasoImportacion.Ninguno
        if (pdfs.isNotEmpty()) viewModelScope.launch { repositorio.descartar(pdfs) }
    }

    override fun onCleared() {
        // Si la pantalla se cierra del todo con PDFs copiados sin materia, no dejarlos huérfanos.
        val pdfs = when (val p = _paso.value) {
            is PasoImportacion.ElegirMateria -> p.pdfs
            is PasoImportacion.CrearMateria -> p.pdfs
            else -> emptyList()
        }
        if (pdfs.isNotEmpty()) alcanceApp.launch { repositorio.descartar(pdfs) }
    }

    private fun registrar(pdfs: List<PdfPreparado>, fallidos: Int, materiaId: Long) {
        viewModelScope.launch {
            // Registrar no se corta aunque se cierre la pantalla: si no, quedarían copias sueltas.
            val ok = withContext(NonCancellable) { repositorio.registrar(pdfs, materiaId) }
            _resultados.send(
                if (ok) ResultadoImportacion(pdfs.size, fallidos) else ResultadoImportacion(0, pdfs.size + fallidos),
            )
        }
    }
}
