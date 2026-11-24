package com.apuntescroll.ui.pantallas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apuntescroll.R
import com.apuntescroll.ui.componentes.PantallaBase

@Composable
fun PantallaPerfil() {
    PantallaBase(titulo = stringResource(R.string.perfil_titulo)) { margenes ->
        EstadoVacio(R.string.perfil_titulo, R.string.perfil_vacio, Modifier.padding(margenes))
    }
}

/** Título, una línea de explicación y, opcionalmente, botones debajo; todo centrado. */
@Composable
fun EstadoVacio(
    @StringRes titulo: Int,
    @StringRes texto: Int?,
    modifier: Modifier = Modifier,
    textoLibre: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titulo),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        val explicacion = textoLibre ?: texto?.let { stringResource(it) }
        if (explicacion != null) {
            Text(
                text = explicacion,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        extra()
    }
}
