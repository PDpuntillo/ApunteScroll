package com.apuntescroll.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.R
import com.apuntescroll.feed.FiltroFeed
import com.apuntescroll.feed.PielFeed
import com.apuntescroll.ui.componentes.PantallaBase
import com.apuntescroll.ui.pantallas.EstadoVacio
import kotlinx.coroutines.launch

/** Inicio: el feed de tarjetas con la piel elegida (F2). */
@Composable
fun PantallaFeed(
    viewModel: FeedViewModel,
    alAbrirHilo: (seccionId: Long, tarjetaId: Long) -> Unit,
    alIrABiblioteca: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val marcas by viewModel.marcas.collectAsStateWithLifecycle()
    val piel by viewModel.piel.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()
    val alcance = rememberCoroutineScope()

    // Al cambiar de filtro o de piel, empezar desde arriba. Solo cuando cambian: al volver del
    // hilo, de otra pestaña o al rotar, la lista queda donde estaba.
    val vista = "${claveFiltro(estado.filtro)}|$piel"
    var vistaAnterior by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vista) {
        if (vistaAnterior != null && vistaAnterior != vista) lista.scrollToItem(0)
        vistaAnterior = vista
    }
    // Cada vez que se lee la base el orden es otro (las vistas pasan al final): volver arriba,
    // donde está la próxima tarjeta sin ver. También cubre una posición que quedó guardada de
    // antes (por ejemplo, si Android cerró la app y la volvió a abrir).
    var fotoAnterior by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(estado.idFoto) {
        if (estado.idFoto == 0L) return@LaunchedEffect
        if (fotoAnterior != null && fotoAnterior != estado.idFoto) lista.scrollToItem(0)
        fotoAnterior = estado.idFoto
    }

    val acciones = remember(viewModel) {
        AccionesFeed(
            alLoSe = viewModel::alternarSabida,
            alLoSeTarjeta = viewModel::marcarSabida,
            alGuardar = viewModel::alternarGuardada,
            alVer = viewModel::vista,
            alAbrirHilo = { t -> alAbrirHilo(t.seccionId, t.id) },
            alFiltrar = viewModel::elegirFiltro,
            alVolverAEmpezar = {
                viewModel.recargar()
                alcance.launch { lista.scrollToItem(0) }
            },
        )
    }

    PantallaBase(
        titulo = stringResource(R.string.app_name),
        acciones = {
            if (estado.hayTarjetas) {
                val otra = if (piel == PielFeed.HILO) PielFeed.CARRUSEL else PielFeed.HILO
                IconButton(onClick = { viewModel.cambiarPiel(otra) }) {
                    Icon(
                        painterResource(if (otra == PielFeed.CARRUSEL) R.drawable.ic_piel_carrusel else R.drawable.ic_piel_hilo),
                        contentDescription = stringResource(
                            if (otra == PielFeed.CARRUSEL) R.string.feed_cambiar_a_carrusel else R.string.feed_cambiar_a_hilo,
                        ),
                    )
                }
            }
        },
        botonFlotante = {
            if (estado.hayNuevas) {
                ExtendedFloatingActionButton(
                    onClick = acciones.alVolverAEmpezar,
                    text = { Text(stringResource(R.string.feed_hay_nuevas)) },
                    icon = { Icon(painterResource(R.drawable.ic_inicio), contentDescription = null) },
                )
            }
        },
    ) { margenes ->
        when {
            estado.cargando && estado.rearmando -> Esperando(
                titulo = stringResource(R.string.feed_rearmando),
                texto = stringResource(R.string.feed_rearmando_detalle),
                modifier = Modifier.padding(margenes),
            )
            estado.cargando -> Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !estado.hayTarjetas && estado.procesando > 0 -> Esperando(
                titulo = pluralStringResource(R.plurals.procesando_pdfs, estado.procesando, estado.procesando),
                texto = stringResource(R.string.documento_procesando),
                modifier = Modifier.padding(margenes),
            )
            !estado.hayTarjetas -> EstadoVacio(R.string.inicio_titulo, R.string.inicio_vacio, Modifier.padding(margenes)) {
                Button(onClick = alIrABiblioteca, modifier = Modifier.padding(top = 24.dp)) {
                    Text(stringResource(R.string.inicio_ir_biblioteca))
                }
            }
            // La lista empieza debajo de la barra de arriba (que es opaca): así el indicador de
            // "deslizar para actualizar" no queda tapado.
            else -> PullToRefreshBox(
                isRefreshing = estado.actualizando,
                onRefresh = acciones.alVolverAEmpezar,
                modifier = Modifier.fillMaxSize().padding(top = margenes.calculateTopPadding()),
            ) {
                val resto = sinMargenArriba(margenes)
                when (piel) {
                    PielFeed.HILO -> FeedHilo(estado, marcas, resto, lista, acciones)
                    PielFeed.CARRUSEL -> FeedCarrusel(estado, marcas, resto, lista, acciones)
                }
            }
        }
    }
}

/**
 * Inicio mientras espera algo que tarda: los primeros PDFs que se procesan, o el rearmado de las
 * tarjetas al abrir la app después de una actualización.
 */
@Composable
private fun Esperando(titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Texto estable del filtro, para recordarlo entre recreaciones de la pantalla. */
private fun claveFiltro(filtro: FiltroFeed): String = when (filtro) {
    FiltroFeed.Todas -> "todas"
    FiltroFeed.Guardadas -> "guardadas"
    is FiltroFeed.DeMateria -> "m${filtro.materiaId}"
}
