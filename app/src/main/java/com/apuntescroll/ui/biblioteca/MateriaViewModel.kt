package com.apuntescroll.ui.biblioteca

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.datos.bd.Documento
import com.apuntescroll.datos.bd.Materia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la materia: cargando, existe, o ya no existe (se borró). */
sealed interface EstadoMateria {
    data object Cargando : EstadoMateria
    data class Lista(val materia: Materia) : EstadoMateria
    data object Borrada : EstadoMateria
}

class MateriaViewModel(
    private val repositorio: RepositorioApuntes,
    private val alcanceApp: CoroutineScope,
    materiaId: Long,
) : ViewModel() {

    val materia: StateFlow<EstadoMateria> = repositorio.materia(materiaId)
        .map { if (it == null) EstadoMateria.Borrada else EstadoMateria.Lista(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMateria.Cargando)

    /** null mientras carga. */
    val documentos: StateFlow<List<Documento>?> = repositorio.documentos(materiaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importando = MutableStateFlow(false)
    val importando: StateFlow<Boolean> = _importando.asStateFlow()

    private val _resultados = Channel<ResultadoImportacion>(Channel.BUFFERED)
    val resultados: Flow<ResultadoImportacion> = _resultados.receiveAsFlow()

    private val id = materiaId

    fun importar(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _importando.value = true
        // En el alcance de la app: si se cierra la pantalla en medio, la importación termina igual.
        alcanceApp.launch {
            val fallidos = try {
                repositorio.importar(uris, id)
            } finally {
                _importando.value = false
            }
            _resultados.send(ResultadoImportacion(uris.size - fallidos, fallidos))
        }
    }

    fun editar(nombre: String, color: ColorMateria, emoji: String) {
        val actual = (materia.value as? EstadoMateria.Lista)?.materia ?: return
        viewModelScope.launch { repositorio.actualizarMateria(actual.copy(nombre = nombre, color = color, emoji = emoji)) }
    }

    fun borrarMateria() {
        val actual = (materia.value as? EstadoMateria.Lista)?.materia ?: return
        alcanceApp.launch { repositorio.borrarMateria(actual) }
    }

    fun borrarDocumento(documento: Documento) {
        alcanceApp.launch { repositorio.borrarDocumento(documento) }
    }

    fun reintentar(documento: Documento) {
        viewModelScope.launch { repositorio.reintentar(documento) }
    }
}
