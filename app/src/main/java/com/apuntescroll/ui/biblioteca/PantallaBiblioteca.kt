package com.apuntescroll.ui.biblioteca

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.R
import com.apuntescroll.datos.bd.MateriaConResumen
import com.apuntescroll.ui.componentes.AvatarMateria
import com.apuntescroll.ui.componentes.PantallaBase
import com.apuntescroll.ui.componentes.margenesDeLista
import com.apuntescroll.ui.pantallas.EstadoVacio

/** Tipos de archivo que se pueden importar. */
val TIPOS_PDF = arrayOf("application/pdf")

@Composable
fun PantallaBiblioteca(viewModel: BibliotecaViewModel, alAbrirMateria: (Long) -> Unit) {
    val materias by viewModel.materias.collectAsStateWithLifecycle()
    val paso by viewModel.paso.collectAsStateWithLifecycle()
    var creandoMateria by rememberSaveable { mutableStateOf(false) }
    val avisos = remember { SnackbarHostState() }
    val recursos = LocalResources.current

    val elegirPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.pdfsElegidos(uris)
    }

    LaunchedEffect(viewModel) {
        viewModel.resultados.collect { r ->
            val texto = if (r.fallidos == 0) {
                recursos.getQuantityString(R.plurals.importar_listo, r.importados, r.importados)
            } else {
                recursos.getQuantityString(R.plurals.importar_fallidos, r.fallidos, r.fallidos)
            }
            avisos.showSnackbar(texto)
        }
    }

    PantallaBase(
        titulo = stringResource(R.string.biblioteca_titulo),
        avisos = avisos,
        botonFlotante = {
            if (!materias.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { elegirPdfs.launch(TIPOS_PDF) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.importar_pdf)) },
                )
            }
        },
    ) { margenes ->
        val lista = materias
        when {
            lista == null -> Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lista.isEmpty() -> EstadoVacio(
                titulo = R.string.biblioteca_vacia_titulo,
                texto = R.string.biblioteca_vacia,
                modifier = Modifier.padding(margenes),
            ) {
                Button(onClick = { elegirPdfs.launch(TIPOS_PDF) }, modifier = Modifier.padding(top = 24.dp)) {
                    Text(stringResource(R.string.importar_pdf))
                }
                TextButton(onClick = { creandoMateria = true }) {
                    Text(stringResource(R.string.materia_crear_sin_pdf))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Lugar abajo para que el botón flotante no tape la última materia.
                contentPadding = margenesDeLista(margenes, abajo = 88.dp),
            ) {
                items(lista, key = { it.materia.id }) { item ->
                    FilaMateria(item, alTocar = { alAbrirMateria(item.materia.id) })
                }
            }
        }
    }

    when (val p = paso) {
        is PasoImportacion.ElegirMateria -> DialogoElegirMateria(
            materias = p.materias,
            alElegir = viewModel::materiaElegida,
            alPedirNueva = viewModel::pedirMateriaNueva,
            alCancelar = viewModel::cancelar,
        )
        is PasoImportacion.CrearMateria -> DialogoMateria(
            inicial = null,
            alConfirmar = viewModel::crearMateria,
            alCancelar = viewModel::cancelar,
        )
        PasoImportacion.Copiando -> DialogoCopiando(alCancelar = viewModel::cancelar)
        PasoImportacion.Ninguno -> Unit
    }

    if (creandoMateria) {
        DialogoMateria(
            inicial = null,
            alConfirmar = { nombre, color, emoji ->
                creandoMateria = false
                viewModel.crearMateria(nombre, color, emoji)
            },
            alCancelar = { creandoMateria = false },
        )
    }
}

@Composable
private fun FilaMateria(item: MateriaConResumen, alTocar: () -> Unit) {
    val pdfs = pluralStringResource(R.plurals.cantidad_pdfs, item.documentos, item.documentos)
    val tarjetas = pluralStringResource(R.plurals.cantidad_tarjetas, item.tarjetas, item.tarjetas)
    ListItem(
        headlineContent = { Text(item.materia.nombre, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            if (item.procesando > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(pluralStringResource(R.plurals.procesando_pdfs, item.procesando, item.procesando))
                }
            } else {
                Text(stringResource(R.string.resumen_materia, pdfs, tarjetas))
            }
        },
        leadingContent = { AvatarMateria(item.materia.emoji, item.materia.color) },
        modifier = Modifier.clickable(onClick = alTocar),
    )
}
