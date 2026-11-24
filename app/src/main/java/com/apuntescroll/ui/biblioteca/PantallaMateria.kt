package com.apuntescroll.ui.biblioteca

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.apuntescroll.datos.bd.Documento
import com.apuntescroll.datos.bd.EstadoDocumento
import com.apuntescroll.ui.componentes.PantallaBase
import com.apuntescroll.ui.componentes.margenesDeLista
import com.apuntescroll.ui.pantallas.EstadoVacio

/**
 * [alBorrada] cierra esta pantalla cuando la materia deja de existir (se borró desde acá o desde
 * otro lado); es distinto de [alVolver] para no sacar otra pantalla por error.
 */
@Composable
fun PantallaMateria(
    viewModel: MateriaViewModel,
    alVolver: () -> Unit,
    alBorrada: () -> Unit,
    alAbrirDocumento: (Long) -> Unit,
) {
    val estado by viewModel.materia.collectAsStateWithLifecycle()
    val documentos by viewModel.documentos.collectAsStateWithLifecycle()
    val importando by viewModel.importando.collectAsStateWithLifecycle()
    var editando by rememberSaveable { mutableStateOf(false) }
    var borrandoMateria by rememberSaveable { mutableStateOf(false) }
    var documentoABorrar by remember { mutableStateOf<Documento?>(null) }
    val avisos = remember { SnackbarHostState() }
    val recursos = LocalResources.current

    val elegirPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importar(uris)
    }

    // Si la materia se borró (desde acá o desde otro lado), volver a la Biblioteca.
    LaunchedEffect(estado) {
        if (estado == EstadoMateria.Borrada) alBorrada()
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

    val materia = (estado as? EstadoMateria.Lista)?.materia
    PantallaBase(
        titulo = materia?.let { "${it.emoji} ${it.nombre}" } ?: "",
        alVolver = alVolver,
        avisos = avisos,
        acciones = {
            if (materia != null) {
                IconButton(onClick = { editando = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.materia_editar))
                }
                // Mientras se importa no se puede borrar: los PDFs quedarían sin materia.
                IconButton(onClick = { borrandoMateria = true }, enabled = !importando) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.materia_borrar))
                }
            }
        },
        botonFlotante = {
            if (!documentos.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { if (!importando) elegirPdfs.launch(TIPOS_PDF) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.importar_agregar)) },
                )
            }
        },
    ) { margenes ->
        val lista = documentos
        Box(Modifier.fillMaxSize()) {
            when {
                lista == null || materia == null -> Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                lista.isEmpty() -> EstadoVacio(
                    titulo = R.string.materia_vacia_titulo,
                    texto = R.string.materia_vacia,
                    modifier = Modifier.padding(margenes),
                ) {
                    Button(
                        onClick = { elegirPdfs.launch(TIPOS_PDF) },
                        enabled = !importando,
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Text(stringResource(R.string.importar_pdf))
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = margenesDeLista(margenes, abajo = 88.dp),
                ) {
                    items(lista, key = { it.id }) { documento ->
                        FilaDocumento(
                            documento = documento,
                            alTocar = { alAbrirDocumento(documento.id) },
                            alReintentar = { viewModel.reintentar(documento) },
                            alBorrar = { documentoABorrar = documento },
                        )
                    }
                }
            }
            // Mientras se copian los PDFs agregados (desde una nube puede tardar).
            if (importando) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = margenes.calculateTopPadding()))
            }
        }
    }

    if (editando && materia != null) {
        DialogoMateria(
            inicial = materia,
            alConfirmar = { nombre, color, emoji ->
                editando = false
                viewModel.editar(nombre, color, emoji)
            },
            alCancelar = { editando = false },
        )
    }
    if (borrandoMateria && materia != null) {
        DialogoBorrar(
            titulo = stringResource(R.string.materia_borrar_titulo, materia.nombre),
            texto = stringResource(R.string.materia_borrar_texto),
            alConfirmar = {
                borrandoMateria = false
                viewModel.borrarMateria()
            },
            alCancelar = { borrandoMateria = false },
        )
    }
    documentoABorrar?.let { documento ->
        DialogoBorrar(
            titulo = stringResource(R.string.documento_borrar_titulo, documento.titulo),
            texto = stringResource(R.string.documento_borrar_texto),
            alConfirmar = {
                documentoABorrar = null
                viewModel.borrarDocumento(documento)
            },
            alCancelar = { documentoABorrar = null },
        )
    }
}

@Composable
private fun FilaDocumento(
    documento: Documento,
    alTocar: () -> Unit,
    alReintentar: () -> Unit,
    alBorrar: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val listo = documento.estado == EstadoDocumento.LISTO

    ListItem(
        headlineContent = { Text(documento.titulo, maxLines = 2) },
        supportingContent = {
            Column {
                Text(
                    textoEstado(documento),
                    color = if (documento.estado == EstadoDocumento.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (documento.estado) {
                    EstadoDocumento.PENDIENTE, EstadoDocumento.ARMANDO ->
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    EstadoDocumento.EXTRAYENDO ->
                        if (documento.paginas > 0) {
                            LinearProgressIndicator(
                                progress = { documento.paginasProcesadas.toFloat() / documento.paginas },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    else -> Unit
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.accion_mas_opciones))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (documento.estado == EstadoDocumento.ERROR) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accion_reintentar)) },
                            onClick = {
                                menu = false
                                alReintentar()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accion_borrar)) },
                        onClick = {
                            menu = false
                            alBorrar()
                        },
                    )
                }
            }
        },
        modifier = if (listo) Modifier.clickable(onClick = alTocar) else Modifier,
    )
}

@Composable
private fun textoEstado(documento: Documento): String = when (documento.estado) {
    EstadoDocumento.PENDIENTE -> stringResource(R.string.estado_pendiente)
    EstadoDocumento.EXTRAYENDO -> {
        val base = stringResource(R.string.estado_extrayendo, documento.paginasProcesadas, documento.paginas)
        if (documento.paginasOcr > 0) {
            base + " · " + pluralStringResource(R.plurals.estado_con_ocr, documento.paginasOcr, documento.paginasOcr)
        } else {
            base
        }
    }
    EstadoDocumento.ARMANDO -> stringResource(R.string.estado_armando)
    EstadoDocumento.LISTO -> {
        val paginas = pluralStringResource(R.plurals.cantidad_paginas, documento.paginas, documento.paginas)
        if (documento.paginasOcr > 0) {
            paginas + " · " + pluralStringResource(R.plurals.estado_con_ocr, documento.paginasOcr, documento.paginasOcr)
        } else {
            paginas
        }
    }
    EstadoDocumento.ERROR -> documento.error ?: stringResource(R.string.estado_error)
}
