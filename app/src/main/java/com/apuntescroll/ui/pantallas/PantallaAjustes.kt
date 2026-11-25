package com.apuntescroll.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.BuildConfig
import com.apuntescroll.R
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.datos.LargoTarjeta
import com.apuntescroll.ui.componentes.PantallaBase

/** Ajustes. [seccionRedes] (F3) va arriba de "Largo de las tarjetas" y avisa con [SnackbarHostState]. */
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    modifier: Modifier = Modifier,
    seccionRedes: @Composable (SnackbarHostState) -> Unit = {},
) {
    val largo by ajustes.largoTarjeta.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    PantallaBase(titulo = stringResource(R.string.ajustes_titulo), modifier = modifier, avisos = avisos) { margenes ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margenes)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            seccionRedes(avisos)

            Text(
                text = stringResource(R.string.ajustes_largo_tarjeta),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.ajustes_largo_explicacion),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                LargoTarjeta.entries.forEach { opcion ->
                    FilaOpcion(
                        texto = stringResource(etiqueta(opcion)),
                        elegida = opcion == largo,
                        alElegir = { ajustes.cambiarLargoTarjeta(opcion) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.ajustes_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Una opción de una lista de elección única, con un [detalle] opcional debajo. */
@Composable
internal fun FilaOpcion(texto: String, elegida: Boolean, alElegir: () -> Unit, detalle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El clic lo maneja la fila entera; el botón es solo visual.
        RadioButton(selected = elegida, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(text = texto, style = MaterialTheme.typography.bodyLarge)
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun etiqueta(largo: LargoTarjeta): Int = when (largo) {
    LargoTarjeta.CORTA -> R.string.ajustes_largo_corta
    LargoTarjeta.MEDIA -> R.string.ajustes_largo_media
    LargoTarjeta.LARGA -> R.string.ajustes_largo_larga
}
