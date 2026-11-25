package com.apuntescroll.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.apuntescroll.R
import com.apuntescroll.feed.FiltroFeed
import com.apuntescroll.feed.Lectura
import com.apuntescroll.feed.PostFeed
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.ui.componentes.margenesDeLista
import com.apuntescroll.ui.tema.colores
import kotlinx.coroutines.delay

/**
 * Carrusel (estilo Instagram): una sección por publicación, con sus tarjetas como diapositivas.
 * [margenes] no incluye la barra de arriba (la lista empieza debajo de ella). [cabecera] y [pie]
 * van como primer y último elemento de la lista.
 */
@Composable
fun FeedCarrusel(
    estado: EstadoFeed,
    marcas: Map<Long, MarcaTarjeta>,
    margenes: PaddingValues,
    lista: LazyListState,
    acciones: AccionesFeed,
    cabecera: @Composable () -> Unit = {},
    pie: @Composable () -> Unit = {},
) {
    val visibles by remember(lista) { derivedStateOf { clavesVisibles(lista) } }
    val foco by remember(lista) { derivedStateOf { claveEnFoco(lista) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // La diapositiva no pasa del alto visible de la lista (en horizontal, 4:5 ocuparía varias
        // pantallas): así al menos ella entra entera; el encabezado y las acciones se scrollean.
        // Con muy poco alto (horizontal en un teléfono) se permite que pase un poco: más bajita no
        // entraría ni un renglón de texto.
        val altoMaximo = (maxHeight - margenes.calculateBottomPadding() - 16.dp).coerceAtLeast(ALTO_MINIMO)
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize(),
            contentPadding = margenesDeLista(margenes),
        ) {
            item(key = CLAVE_CABECERA, contentType = CLAVE_CABECERA) { cabecera() }
            item(key = "historias", contentType = "historias") {
                FilaHistorias(estado.materias, estado.filtro, estado.hayGuardadas, acciones.alFiltrar)
                HorizontalDivider()
            }
            items(estado.posts, key = { it.clave }, contentType = { "carrusel" }) { post ->
                PostCarrusel(
                    post,
                    marcas,
                    visible = post.clave in visibles,
                    enFoco = post.clave == foco,
                    acciones = acciones,
                    altoMaximo = altoMaximo,
                )
            }
            item(key = "fin", contentType = "fin") {
                EstasAlDia(
                    vacio = estado.posts.isEmpty(),
                    filtro = estado.filtro,
                    alVolverAEmpezar = acciones.alVolverAEmpezar,
                    alVerTodas = { acciones.alFiltrar(FiltroFeed.Todas) },
                )
            }
            item(key = CLAVE_PIE, contentType = CLAVE_PIE) { pie() }
        }
    }
}

@Composable
private fun PostCarrusel(
    post: PostFeed,
    marcas: Map<Long, MarcaTarjeta>,
    visible: Boolean,
    enFoco: Boolean,
    acciones: AccionesFeed,
    altoMaximo: Dp,
) {
    val pager = rememberPagerState { post.tarjetas.size }
    // El pager acomoda su página recién al medirse: si la publicación cambió y tiene menos
    // diapositivas, currentPage puede apuntar afuera durante un cuadro.
    val pagina = pager.currentPage.coerceIn(0, post.tarjetas.lastIndex)
    val actual = post.tarjetas[pagina]
    val marcasPost = post.tarjetas.map { marcas[it.id] ?: MarcaTarjeta(it.sabida, it.guardada) }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        EncabezadoPost(
            emoji = actual.emoji,
            color = actual.color,
            materia = actual.nombreMateria,
            detalle = stringResource(R.string.feed_detalle_carrusel, actual.tituloDocumento, actual.pagina),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
        // 4:5 como Instagram, sin pasar de [altoMaximo].
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val alto = min(maxWidth * 1.25f, altoMaximo)
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(alto),
                key = { post.tarjetas[it].id },
            ) { i ->
                val tarjeta = post.tarjetas[i]
                Diapositiva(
                    tarjeta = tarjeta,
                    titulo = if (i == 0 && post.conTitulo) {
                        tarjeta.tituloSeccion ?: stringResource(R.string.seccion_pagina, tarjeta.paginaInicioSeccion)
                    } else {
                        null
                    },
                    sabida = marcas[tarjeta.id]?.sabida ?: tarjeta.sabida,
                    compacta = alto < ALTO_COMPACTA,
                    alDobleToque = { acciones.alLoSeTarjeta(tarjeta) },
                    alSeguirLeyendo = { acciones.alAbrirHilo(tarjeta) },
                )
            }
        }
        if (post.tarjetas.size > 1) {
            Puntos(total = post.tarjetas.size, actual = pagina, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonLoSe(marcasPost.all { it.sabida }, alTocar = { acciones.alLoSe(post) })
            BotonGuardar(marcasPost.all { it.guardada }, alTocar = { acciones.alGuardar(post) })
            Spacer(Modifier.weight(1f))
            if (post.totalSeccion > 1) {
                BotonHilo(actual.ordenTarjeta + 1, post.totalSeccion) { acciones.alAbrirHilo(actual) }
            }
        }
    }

    // La diapositiva actual cuenta como vista si la publicación está a la vista y el carrusel quieto.
    RegistrarVista(clave = actual.id, activo = visible && !pager.isScrollInProgress) { acciones.alVer(actual) }
    // Para el pase (F3): la diapositiva actual de la publicación en foco, con el carrusel quieto.
    acciones.alLeer?.let { alLeer ->
        RegistrarVista(
            clave = "l${actual.id}",
            activo = enFoco && !pager.isScrollInProgress,
            ms = Lectura.msLectura(actual.texto.length),
        ) { alLeer(actual) }
    }
}

/**
 * Una tarjeta como diapositiva: texto sobre el color de la materia, que se achica si no entra. Si
 * ni así entra, se corta con puntos suspensivos y aparece "Seguir leyendo", que abre el hilo en
 * esta tarjeta. [compacta]: la diapositiva es bajita (horizontal); márgenes y título más chicos
 * para que entre texto.
 */
@Composable
private fun Diapositiva(
    tarjeta: TarjetaFeed,
    titulo: String?,
    sabida: Boolean,
    compacta: Boolean,
    alDobleToque: () -> Unit,
    alSeguirLeyendo: () -> Unit,
) {
    val colores = tarjeta.color.colores()
    var corazones by remember { mutableIntStateOf(0) }
    var desborda by remember(tarjeta.id) { mutableStateOf(false) }
    // pointerInput no se reinicia cuando cambia la lambda: usar siempre la última.
    val alDobleToqueActual by rememberUpdatedState(alDobleToque)
    val accionLoSe = stringResource(R.string.feed_accion_lo_se)
    val estadoLoSe = stringResource(if (sabida) R.string.feed_estado_sabida else R.string.feed_estado_no_sabida)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            // Con TalkBack el doble toque no llega como gesto: "lo sé" queda como acción.
            .semantics(mergeDescendants = true) {
                stateDescription = estadoLoSe
                customActions = listOf(
                    CustomAccessibilityAction(accionLoSe) {
                        alDobleToqueActual()
                        true
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    alDobleToqueActual()
                    corazones++
                })
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = if (compacta) 14.dp else 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (titulo != null) {
                Text(
                    titulo,
                    style = if (compacta) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = colores.contenido,
                    maxLines = if (compacta) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = if (compacta) 6.dp else 16.dp).semantics { heading() },
                )
            }
            BasicText(
                text = tarjeta.texto,
                style = MaterialTheme.typography.bodyLarge.copy(color = colores.contenido, lineHeight = 1.35.em),
                autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 21.sp, stepSize = 0.5.sp),
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { desborda = it.hasVisualOverflow },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (desborda) {
                TextButton(onClick = alSeguirLeyendo, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.feed_seguir_leyendo), color = colores.contenido)
                }
            }
        }
        if (sabida) {
            // Marca fija en las diapositivas que ya se saben (el corazón del doble toque se va).
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = colores.contenido,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(22.dp),
            )
        }
        CorazonDobleToque(corazones, colores.contenido, Modifier.align(Alignment.Center))
    }
}

/** Corazón que aparece y se desvanece en el centro al hacer doble toque. */
@Composable
private fun CorazonDobleToque(veces: Int, color: Color, modifier: Modifier) {
    var mostrar by remember { mutableIntStateOf(0) }
    LaunchedEffect(veces) {
        if (veces > 0) {
            mostrar = veces
            delay(700)
            mostrar = 0
        }
    }
    AnimatedVisibility(
        visible = mostrar > 0,
        enter = scaleIn(initialScale = 0.4f) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = color, modifier = Modifier.size(96.dp))
    }
}

@Composable
private fun Puntos(total: Int, actual: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == actual) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == actual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

/** Alto mínimo de una diapositiva: con menos no entra texto entre el título y "Seguir leyendo". */
private val ALTO_MINIMO = 200.dp

/** Por debajo de este alto la diapositiva usa márgenes y título más chicos. */
private val ALTO_COMPACTA = 320.dp
