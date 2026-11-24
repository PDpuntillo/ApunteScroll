package com.apuntescroll.ui.pantallas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

// F0: las secciones todavía están vacías. Cada una se completa en su fase (PLAN.md, sección 11).

@Composable
fun PantallaInicio(modifier: Modifier = Modifier) =
    EstadoVacio(R.string.inicio_titulo, R.string.inicio_vacio, modifier)

@Composable
fun PantallaBiblioteca(modifier: Modifier = Modifier) =
    EstadoVacio(R.string.biblioteca_titulo, R.string.biblioteca_vacia, modifier)

@Composable
fun PantallaPerfil(modifier: Modifier = Modifier) =
    EstadoVacio(R.string.perfil_titulo, R.string.perfil_vacio, modifier)

/** Título y una línea de explicación, centrados. */
@Composable
fun EstadoVacio(@StringRes titulo: Int, @StringRes texto: Int, modifier: Modifier = Modifier) {
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
        Text(
            text = stringResource(texto),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
