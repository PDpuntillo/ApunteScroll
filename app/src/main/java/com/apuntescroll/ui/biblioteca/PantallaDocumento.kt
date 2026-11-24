package com.apuntescroll.ui.biblioteca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.R
import com.apuntescroll.datos.bd.EstadoDocumento
import com.apuntescroll.datos.bd.OrigenTexto
import com.apuntescroll.datos.bd.Tarjeta
import com.apuntescroll.ui.componentes.PantallaBase
import com.apuntescroll.ui.componentes.margenesDeLista
import com.apuntescroll.ui.pantallas.EstadoVacio

/** Todas las tarjetas de un PDF, agrupadas por sección (para revisar cómo quedó el armado). */
@Composable
fun PantallaDocumento(
    viewModel: DocumentoViewModel,
    alVolver: () -> Unit,
    alAbrirPagina: (Int) -> Unit,
) {
    val documento by viewModel.documento.collectAsStateWithLifecycle()
    val secciones by viewModel.secciones.collectAsStateWithLifecycle()

    PantallaBase(titulo = documento?.titulo ?: "", alVolver = alVolver) { margenes ->
        val lista = secciones
        val doc = documento
        when {
            lista == null || doc == null -> Box(Modifier.fillMaxSize().padding(margenes), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            doc.estado != EstadoDocumento.LISTO -> EstadoVacio(
                titulo = R.string.documento_procesando_titulo,
                texto = R.string.documento_procesando,
                modifier = Modifier.padding(margenes),
            )
            else -> {
                val total = lista.sumOf { it.tarjetas.size }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = margenesDeLista(margenes, lados = 16.dp, arriba = 8.dp, abajo = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "resumen") {
                        Text(
                            stringResource(
                                R.string.resumen_documento,
                                pluralStringResource(R.plurals.cantidad_tarjetas, total, total),
                                pluralStringResource(R.plurals.cantidad_secciones, lista.size, lista.size),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    lista.forEach { seccion ->
                        item(key = "s${seccion.seccion.id}") {
                            Text(
                                text = seccion.seccion.titulo
                                    ?: stringResource(R.string.seccion_pagina, seccion.seccion.paginaInicio),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .semantics { heading() },
                            )
                        }
                        items(seccion.tarjetas, key = { "t${it.id}" }) { tarjeta ->
                            TarjetaVista(tarjeta, alAbrirPagina = { alAbrirPagina(tarjeta.pagina) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaVista(tarjeta: Tarjeta, alAbrirPagina: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(tarjeta.texto, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = alAbrirPagina,
                    label = { Text(stringResource(R.string.tarjeta_pagina, tarjeta.pagina)) },
                )
                if (tarjeta.origen == OrigenTexto.OCR) {
                    Text(
                        stringResource(R.string.tarjeta_ocr),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
