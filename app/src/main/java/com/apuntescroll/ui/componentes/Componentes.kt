package com.apuntescroll.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import com.apuntescroll.R
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.ui.tema.colores

/**
 * Estructura común de las pantallas: barra de arriba con título (y "atrás" si [alVolver] no es
 * null), botón flotante opcional y avisos (snackbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBase(
    titulo: String,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    botonFlotante: @Composable () -> Unit = {},
    avisos: SnackbarHostState = remember { SnackbarHostState() },
    contenido: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                        }
                    }
                },
                actions = acciones,
            )
        },
        floatingActionButton = botonFlotante,
        snackbarHost = { SnackbarHost(avisos) },
        content = contenido,
    )
}

/**
 * Márgenes para el contenido de una lista dentro de [PantallaBase]: respeta los cuatro lados de
 * [margenes] (también los laterales, que en horizontal incluyen el recorte de la cámara) y suma
 * los extras.
 */
@Composable
fun margenesDeLista(
    margenes: PaddingValues,
    lados: Dp = 0.dp,
    arriba: Dp = 0.dp,
    abajo: Dp = 0.dp,
): PaddingValues {
    val direccion = LocalLayoutDirection.current
    return PaddingValues(
        start = margenes.calculateStartPadding(direccion) + lados,
        end = margenes.calculateEndPadding(direccion) + lados,
        top = margenes.calculateTopPadding() + arriba,
        bottom = margenes.calculateBottomPadding() + abajo,
    )
}

/** Círculo con el emoji de la materia sobre su color. */
@Composable
fun AvatarMateria(emoji: String, color: ColorMateria, modifier: Modifier = Modifier, tamano: Dp = 48.dp) {
    val colores = color.colores()
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(colores.fondo),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = (tamano.value * 0.45f).sp)
    }
}
