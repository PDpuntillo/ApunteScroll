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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun PantallaAjustes(ajustes: Ajustes, modifier: Modifier = Modifier) {
    val largo by ajustes.largoTarjeta.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.ajustes_titulo),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.ajustes_largo_tarjeta),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun FilaOpcion(texto: String, elegida: Boolean, alElegir: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El clic lo maneja la fila entera; el botón es solo visual.
        RadioButton(selected = elegida, onClick = null)
        Text(text = texto, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun etiqueta(largo: LargoTarjeta): Int = when (largo) {
    LargoTarjeta.CORTA -> R.string.ajustes_largo_corta
    LargoTarjeta.MEDIA -> R.string.ajustes_largo_media
    LargoTarjeta.LARGA -> R.string.ajustes_largo_larga
}
