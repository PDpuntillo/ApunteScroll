package com.apuntescroll.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apuntescroll.R
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.feed.PostFeed
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.ui.componentes.PantallaBase
import com.apuntescroll.ui.componentes.margenesDeLista
import com.apuntescroll.ui.pantallas.EstadoVacio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Las tarjetas de una sección, en orden (para la pantalla del hilo completo). */
class HiloViewModel(repositorio: RepositorioApuntes, seccionId: Long) : ViewModel() {

    /** null mientras carga. */
    val tarjetas: StateFlow<List<TarjetaFeed>?> = repositorio.tarjetasDeSeccion(seccionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * El hilo completo de una sección: todas sus tarjetas seguidas, empezando por [tarjetaId]. Las
 * acciones van al [FeedViewModel] de Inicio, así lo que se marca acá se ve también en el feed.
 */
@Composable
fun PantallaHilo(
    hilo: HiloViewModel,
    feed: FeedViewModel,
    tarjetaId: Long,
    alVolver: () -> Unit,
) {
    val tarjetas by hilo.tarjetas.collectAsStateWithLifecycle()
    val marcas by feed.marcas.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()
    val visibles by remember(lista) { derivedStateOf { clavesVisibles(lista) } }

    val acciones = remember(feed) {
        AccionesFeed(
            alLoSe = feed::alternarSabida,
            alLoSeTarjeta = feed::marcarSabida,
            alGuardar = feed::alternarGuardada,
            alVer = feed::vista,
            alAbrirHilo = {},
            alFiltrar = {},
            alVolverAEmpezar = {},
        )
    }

    val lista0 = tarjetas
    // Llevar la lista a la tarjeta desde la que se abrió, una sola vez (al volver a esta pantalla
    // o al rotar, queda donde estaba).
    var posicionado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lista0 != null) {
        if (posicionado || lista0 == null) return@LaunchedEffect
        val i = lista0.indexOfFirst { it.id == tarjetaId }
        if (i > 0) lista.scrollToItem(i)
        posicionado = true
    }

    val primera = lista0?.firstOrNull()
    PantallaBase(
        titulo = primera?.let { it.tituloSeccion ?: stringResource(R.string.seccion_pagina, it.paginaInicioSeccion) } ?: "",
        alVolver = alVolver,
    ) { margenes ->
        if (lista0 == null) {
            Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (lista0.isEmpty()) {
            // La sección ya no existe: se borró el PDF, o se rearmó y la sección cambió.
            EstadoVacio(R.string.hilo_no_existe_titulo, R.string.hilo_no_existe, Modifier.padding(margenes)) {
                Button(onClick = alVolver, modifier = Modifier.padding(top = 24.dp)) {
                    Text(stringResource(R.string.accion_volver))
                }
            }
        } else {
            LazyColumn(state = lista, modifier = Modifier.fillMaxSize(), contentPadding = margenesDeLista(margenes)) {
                itemsIndexed(lista0, key = { _, t -> t.id }) { i, t ->
                    val post = PostFeed(clave = "t${t.id}", tarjetas = listOf(t), conTitulo = i == 0, totalSeccion = lista0.size)
                    PostHilo(
                        post = post,
                        marca = marcas[t.id] ?: MarcaTarjeta(t.sabida, t.guardada),
                        acciones = acciones,
                        conBotonHilo = false,
                    )
                    RegistrarVista(clave = t.id, activo = t.id in visibles) { feed.vista(t) }
                    HorizontalDivider()
                }
            }
        }
    }
}
