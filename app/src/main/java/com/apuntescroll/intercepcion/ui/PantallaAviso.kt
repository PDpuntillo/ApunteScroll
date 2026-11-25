package com.apuntescroll.intercepcion.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apuntescroll.R

/**
 * El aviso y consentimiento de la intercepción (PLAN.md, sección 7): pantalla propia, separada de
 * cualquier otro aviso, que dice con exactitud qué lee y qué guarda el servicio. Los botones quedan
 * fijos abajo, del mismo tamaño; no hay temporizadores ni casillas. Atrás cuenta como "Ahora no".
 *
 * [yaActivado]: el servicio ya está activado en Android (el botón dice "Aceptar"). [yaAceptado]:
 * solo se está releyendo (un único botón, "Entendido"). [versionVieja]: se aceptó un aviso anterior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAviso(
    yaActivado: Boolean,
    yaAceptado: Boolean,
    versionVieja: Boolean,
    conPrivacidad: Boolean,
    alAceptar: () -> Unit,
    alRechazar: () -> Unit,
    alCerrar: () -> Unit,
    alAbrirPrivacidad: () -> Unit,
) {
    val alSalir = if (yaAceptado) alCerrar else alRechazar
    BackHandler(onBack = alSalir)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = alSalir) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accion_volver))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (yaAceptado) {
                        Button(onClick = alCerrar, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.aviso_entendido)) }
                    } else {
                        OutlinedButton(onClick = alRechazar, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.aviso_ahora_no))
                        }
                        Button(onClick = alAceptar, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (yaActivado) R.string.aviso_aceptar else R.string.aviso_aceptar_y_activar))
                        }
                    }
                }
            }
        },
    ) { margenes ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(margenes)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.aviso_titulo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            when {
                versionVieja && !yaAceptado -> Destacado(R.string.aviso_nueva_version)
                yaActivado && !yaAceptado -> Destacado(R.string.aviso_ya_activado)
            }
            Parrafo(R.string.aviso_intro)
            Seccion(R.string.aviso_lee_titulo, R.string.aviso_lee)
            Seccion(R.string.aviso_para_titulo, R.string.aviso_para)
            Seccion(R.string.aviso_guarda_titulo, R.string.aviso_guarda)
            Seccion(R.string.aviso_no_hace_titulo, R.string.aviso_no_hace)
            Seccion(R.string.aviso_android_titulo, R.string.aviso_android)
            Seccion(R.string.aviso_pasos_titulo, R.string.aviso_pasos)
            Seccion(R.string.aviso_apagar_titulo, R.string.aviso_apagar)
            if (conPrivacidad) {
                TextButton(onClick = alAbrirPrivacidad, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.aviso_privacidad))
                }
            }
        }
    }
}

@Composable
private fun Destacado(@StringRes texto: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
    ) {
        Text(
            stringResource(texto),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Seccion(@StringRes titulo: Int, @StringRes texto: Int) {
    Text(
        stringResource(titulo),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp).semantics { heading() },
    )
    Parrafo(texto, arriba = 4)
}

@Composable
private fun Parrafo(@StringRes texto: Int, arriba: Int = 16) {
    Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = arriba.dp))
}
