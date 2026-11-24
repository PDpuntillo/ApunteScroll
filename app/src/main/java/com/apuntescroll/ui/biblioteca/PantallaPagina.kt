package com.apuntescroll.ui.biblioteca

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.R
import com.apuntescroll.ui.componentes.PantallaBase

/** Página original del PDF, con zoom (pellizcar) y doble toque para volver al tamaño normal. */
@Composable
fun PantallaPagina(viewModel: PaginaViewModel, alVolver: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val lista = estado as? EstadoPagina.Lista

    PantallaBase(
        titulo = lista?.let { stringResource(R.string.pagina_titulo, it.numero, it.total) } ?: "",
        alVolver = alVolver,
        acciones = {
            if (lista != null) {
                IconButton(onClick = { viewModel.irA(lista.numero - 1) }, enabled = lista.numero > 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.pagina_anterior))
                }
                IconButton(onClick = { viewModel.irA(lista.numero + 1) }, enabled = lista.numero < lista.total) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.pagina_siguiente))
                }
            }
        },
    ) { margenes ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .onSizeChanged { viewModel.fijarAncho(it.width) },
            contentAlignment = Alignment.Center,
        ) {
            when (val e = estado) {
                EstadoPagina.Cargando -> CircularProgressIndicator()
                EstadoPagina.Error -> Text(
                    stringResource(R.string.pagina_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(32.dp),
                )
                is EstadoPagina.Lista -> PaginaConZoom(e)
            }
        }
    }
}

@Composable
private fun PaginaConZoom(pagina: EstadoPagina.Lista) {
    var escala by remember(pagina.numero) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(pagina.numero) { mutableStateOf(Offset.Zero) }
    val estadoGesto = rememberTransformableState { cambioZoom, cambioPan, _ ->
        escala = (escala * cambioZoom).coerceIn(1f, 5f)
        desplazamiento = if (escala == 1f) Offset.Zero else desplazamiento + cambioPan
    }
    val imagen = remember(pagina.imagen) { pagina.imagen.asImageBitmap() }

    LaunchedEffect(pagina.numero) {
        escala = 1f
        desplazamiento = Offset.Zero
    }

    Image(
        bitmap = imagen,
        contentDescription = stringResource(R.string.pagina_descripcion, pagina.numero),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pointerInput(pagina.numero) {
                detectTapGestures(onDoubleTap = {
                    escala = 1f
                    desplazamiento = Offset.Zero
                })
            }
            .transformable(estadoGesto)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
                translationX = desplazamiento.x
                translationY = desplazamiento.y
            },
    )
}
