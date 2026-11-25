package com.apuntescroll.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.apuntescroll.R
import com.apuntescroll.feed.FiltroFeed
import com.apuntescroll.feed.TarjetaFeed
import com.apuntescroll.ui.componentes.AvatarMateria
import com.apuntescroll.ui.tema.colores
import kotlinx.coroutines.delay

/** Tiempo que una tarjeta tiene que estar a la vista para contar como vista (PLAN.md, sección 5). */
const val MS_PARA_VISTA = 1_500L

/**
 * Color del corazón de "lo sé" (propio de ApunteScroll, no el de ninguna red). En el tema claro es
 * más oscuro para que el texto "Lo sé" tenga contraste suficiente.
 */
@Composable
private fun colorLoSe(): Color = if (isSystemInDarkTheme()) Color(0xFFE5484D) else Color(0xFFC2272D)

/** Márgenes de [margenes] sin el de arriba (cuando la barra de arriba ya se dejó afuera). */
@Composable
fun sinMargenArriba(margenes: PaddingValues): PaddingValues {
    val direccion = LocalLayoutDirection.current
    return PaddingValues(
        start = margenes.calculateStartPadding(direccion),
        end = margenes.calculateEndPadding(direccion),
        bottom = margenes.calculateBottomPadding(),
    )
}

/**
 * Llama a [alVer] cuando [activo] lleva [ms] seguido en true y la app está en primer plano (con la
 * pantalla apagada o la app en segundo plano no cuenta). Sirve para contar una tarjeta como vista
 * ([MS_PARA_VISTA]) y como leída para el pase (Lectura.msLectura, F3).
 */
@Composable
fun RegistrarVista(clave: Any, activo: Boolean, ms: Long = MS_PARA_VISTA, alVer: () -> Unit) {
    val estadoCiclo by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val alVerActual by rememberUpdatedState(alVer)
    val enPantalla = activo && estadoCiclo.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(clave, enPantalla) {
        if (enPantalla) {
            delay(ms)
            alVerActual()
        }
    }
}

/** ♥ "Lo sé". */
@Composable
fun BotonLoSe(sabida: Boolean, alTocar: () -> Unit, conTexto: Boolean = true) {
    val descripcion = stringResource(R.string.feed_lo_se)
    val estado = stringResource(if (sabida) R.string.feed_estado_sabida else R.string.feed_estado_no_sabida)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = alTocar)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                stateDescription = estado
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (sabida) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (sabida) colorLoSe() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (conTexto) {
            Text(
                stringResource(R.string.feed_lo_se),
                style = MaterialTheme.typography.labelLarge,
                color = if (sabida) colorLoSe() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 🔖 Guardar. */
@Composable
fun BotonGuardar(guardada: Boolean, alTocar: () -> Unit) {
    val descripcion = stringResource(if (guardada) R.string.feed_quitar_guardada else R.string.feed_guardar)
    IconButton(onClick = alTocar) {
        Icon(
            painterResource(if (guardada) R.drawable.ic_guardado else R.drawable.ic_guardar),
            contentDescription = descripcion,
            tint = if (guardada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Chips de filtro del Hilo: Todas, cada materia y Guardadas. */
@Composable
fun FilaFiltros(
    materias: List<MateriaFiltro>,
    filtro: FiltroFeed,
    hayGuardadas: Boolean,
    alElegir: (FiltroFeed) -> Unit,
) {
    val fila = rememberLazyListState()
    MostrarElegido(fila, materias, filtro)
    LazyRow(
        state = fila,
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "todas") {
            FilterChip(
                selected = filtro == FiltroFeed.Todas,
                onClick = { alElegir(FiltroFeed.Todas) },
                label = { Text(stringResource(R.string.feed_filtro_todas)) },
            )
        }
        items(materias, key = { "m${it.id}" }) { m ->
            FilterChip(
                selected = filtro == FiltroFeed.DeMateria(m.id),
                onClick = { alElegir(FiltroFeed.DeMateria(m.id)) },
                label = { Text("${m.emoji} ${m.nombre}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        // Mientras está elegido, el chip queda aunque ya no haya guardadas (se ve qué filtro es).
        if (hayGuardadas || filtro == FiltroFeed.Guardadas) {
            item(key = "guardadas") {
                FilterChip(
                    selected = filtro == FiltroFeed.Guardadas,
                    onClick = { alElegir(FiltroFeed.Guardadas) },
                    label = { Text(stringResource(R.string.feed_filtro_guardadas)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_guardado), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/**
 * Si el filtro elegido no se ve entero en la fila (por ejemplo, Guardadas al final con muchas
 * materias, o al cambiar de piel), la fila se desplaza hasta él. El orden es el de las filas:
 * Todas, las materias y Guardadas.
 */
@Composable
private fun MostrarElegido(fila: LazyListState, materias: List<MateriaFiltro>, filtro: FiltroFeed) {
    val indice = when (filtro) {
        FiltroFeed.Todas -> 0
        is FiltroFeed.DeMateria -> 1 + materias.indexOfFirst { it.id == filtro.materiaId }.coerceAtLeast(0)
        FiltroFeed.Guardadas -> 1 + materias.size
    }
    LaunchedEffect(indice) {
        val info = fila.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == indice }
        val entero = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!entero) fila.animateScrollToItem(indice)
    }
}

/**
 * Historias del Carrusel: un círculo por materia (con anillo de color si le quedan tarjetas
 * nuevas), más "Todas" y "Guardadas". Tocar una filtra el feed.
 */
@Composable
fun FilaHistorias(
    materias: List<MateriaFiltro>,
    filtro: FiltroFeed,
    hayGuardadas: Boolean,
    alElegir: (FiltroFeed) -> Unit,
) {
    val fila = rememberLazyListState()
    MostrarElegido(fila, materias, filtro)
    LazyRow(
        state = fila,
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "todas") {
            Historia(
                etiqueta = stringResource(R.string.feed_filtro_todas),
                elegida = filtro == FiltroFeed.Todas,
                conAnillo = materias.any { it.nuevas > 0 },
                alTocar = { alElegir(FiltroFeed.Todas) },
            ) {
                Box(Modifier.size(60.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_inicio), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(materias, key = { "m${it.id}" }) { m ->
            Historia(
                etiqueta = m.nombre,
                elegida = filtro == FiltroFeed.DeMateria(m.id),
                conAnillo = m.nuevas > 0,
                // El tono del otro tema: contrasta con el círculo de la materia y con el fondo.
                colorAnillo = m.color.colores(oscuro = !isSystemInDarkTheme()).fondo,
                alTocar = { alElegir(FiltroFeed.DeMateria(m.id)) },
            ) {
                AvatarMateria(m.emoji, m.color, tamano = 60.dp)
            }
        }
        if (hayGuardadas || filtro == FiltroFeed.Guardadas) {
            item(key = "guardadas") {
                Historia(
                    etiqueta = stringResource(R.string.feed_filtro_guardadas),
                    elegida = filtro == FiltroFeed.Guardadas,
                    conAnillo = false,
                    alTocar = { alElegir(FiltroFeed.Guardadas) },
                ) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_guardado), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Historia(
    etiqueta: String,
    elegida: Boolean,
    conAnillo: Boolean,
    alTocar: () -> Unit,
    colorAnillo: Color = MaterialTheme.colorScheme.tertiary,
    contenido: @Composable () -> Unit,
) {
    // Dos cosas distintas, que se pueden dar juntas: el anillo de color dice que hay tarjetas
    // nuevas; la elegida lleva una marca con un tilde y la etiqueta en negrita.
    val anillo = if (conAnillo) {
        BorderStroke(3.dp, colorAnillo)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val descripcion = if (conAnillo) stringResource(R.string.feed_historia_con_nuevas, etiqueta) else etiqueta
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = elegida, role = Role.Tab, onClick = alTocar),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(70.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(anillo, CircleShape)
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                contenido()
            }
            if (elegida) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (elegida) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { contentDescription = descripcion },
            color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Fin del feed. [vacio] es true si para este filtro no quedaba nada (todo se sabe), y false si se
 * llegó al final después de scrollear.
 */
@Composable
fun EstasAlDia(
    vacio: Boolean,
    filtro: FiltroFeed,
    alVolverAEmpezar: () -> Unit,
    alVerTodas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        val guardadas = filtro == FiltroFeed.Guardadas
        Text(
            stringResource(if (guardadas) R.string.feed_fin_guardadas_titulo else R.string.feed_al_dia_titulo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(
                when {
                    guardadas -> R.string.feed_fin_guardadas
                    vacio -> R.string.feed_al_dia_sin_pendientes
                    else -> R.string.feed_al_dia_fin
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        when {
            // "Empezar de nuevo" vuelve a mostrar las vistas que todavía no se saben: si no queda
            // ninguna no hace nada. Con un filtro, se ofrece ver todas las materias.
            !vacio && !guardadas -> FilledTonalButton(onClick = alVolverAEmpezar, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.feed_volver_a_empezar))
            }
            filtro != FiltroFeed.Todas -> FilledTonalButton(onClick = alVerTodas, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.feed_ver_todas))
            }
        }
    }
}

/** Avatar + materia + documento · página, arriba de cada publicación. */
@Composable
fun EncabezadoPost(
    emoji: String,
    color: com.apuntescroll.datos.bd.ColorMateria,
    materia: String,
    detalle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AvatarMateria(emoji, color, tamano = 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(materia, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Botón de texto chico "3/12": abre el hilo completo de la sección en la tarjeta [indice]. */
@Composable
fun BotonHilo(indice: Int, total: Int, alTocar: () -> Unit) {
    val descripcion = stringResource(R.string.feed_hilo_descripcion, indice, total)
    TextButton(
        onClick = alTocar,
        modifier = Modifier.semantics { contentDescription = descripcion },
    ) {
        Icon(painterResource(R.drawable.ic_hilo), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.feed_hilo_posicion, indice, total),
            style = MaterialTheme.typography.labelLarge,
            // TalkBack lee la descripción del botón, no "3/12" (que se leería como fracción).
            modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics {},
        )
    }
}
