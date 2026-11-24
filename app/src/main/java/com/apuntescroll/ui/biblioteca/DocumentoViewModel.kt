package com.apuntescroll.ui.biblioteca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.Documento
import com.apuntescroll.datos.bd.SeccionConTarjetas
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DocumentoViewModel(repositorio: RepositorioApuntes, documentoId: Long) : ViewModel() {

    val documento: StateFlow<Documento?> = repositorio.documento(documentoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null mientras carga. */
    val secciones: StateFlow<List<SeccionConTarjetas>?> = repositorio.secciones(documentoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
