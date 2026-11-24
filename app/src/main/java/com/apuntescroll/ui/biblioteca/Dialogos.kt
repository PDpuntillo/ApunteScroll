package com.apuntescroll.ui.biblioteca

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apuntescroll.R
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.datos.bd.Materia
import com.apuntescroll.ui.componentes.AvatarMateria
import com.apuntescroll.ui.tema.colores

/** Emojis para elegir al crear una materia (sin teclado de emojis, que cambia según el teléfono). */
val EMOJIS_MATERIA = listOf(
    "📘", "📗", "📕", "📙", "📓", "🧮", "📐", "📈", "🧪", "🧬",
    "💊", "🧠", "⚖️", "🏛️", "🌎", "💻", "🎨", "🗣️", "🎵", "⚙️",
)

private const val MAX_NOMBRE = 40

/**
 * Crear o editar una materia: nombre, emoji y color. Si [inicial] no es null, se edita esa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoMateria(
    inicial: Materia?,
    alConfirmar: (nombre: String, color: ColorMateria, emoji: String) -> Unit,
    alCancelar: () -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf(inicial?.nombre ?: "") }
    var emoji by rememberSaveable { mutableStateOf(inicial?.emoji ?: EMOJIS_MATERIA.first()) }
    var color by rememberSaveable { mutableStateOf(inicial?.color ?: ColorMateria.INDIGO) }
    var mostrarError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(if (inicial == null) R.string.materia_nueva else R.string.materia_editar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    AvatarMateria(emoji = emoji, color = color, tamano = 64.dp)
                }
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it.take(MAX_NOMBRE)
                        mostrarError = false
                    },
                    label = { Text(stringResource(R.string.materia_nombre)) },
                    placeholder = { Text(stringResource(R.string.materia_nombre_ejemplo)) },
                    singleLine = true,
                    isError = mostrarError,
                    supportingText = if (mostrarError) {
                        { Text(stringResource(R.string.materia_nombre_vacio)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(R.string.materia_emoji),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS_MATERIA.forEach { opcion ->
                        val elegido = opcion == emoji
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (elegido) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .selectable(selected = elegido, onClick = { emoji = opcion }, role = Role.RadioButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(opcion, fontSize = 22.sp)
                        }
                    }
                }

                Text(
                    stringResource(R.string.materia_color),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorMateria.entries.forEach { opcion ->
                        val elegido = opcion == color
                        val colores = opcion.colores()
                        val descripcion = stringResource(nombreColor(opcion))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colores.fondo)
                                .border(
                                    BorderStroke(if (elegido) 3.dp else 1.dp, if (elegido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    CircleShape,
                                )
                                .selectable(selected = elegido, onClick = { color = opcion }, role = Role.RadioButton)
                                .semantics { contentDescription = descripcion },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (elegido) Icon(Icons.Default.Check, contentDescription = null, tint = colores.contenido)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nombre.isBlank()) mostrarError = true else alConfirmar(nombre.trim(), color, emoji)
            }) {
                Text(stringResource(if (inicial == null) R.string.accion_crear else R.string.accion_guardar))
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.accion_cancelar)) } },
    )
}

/** Elegir a qué materia van los PDFs recién elegidos. */
@Composable
fun DialogoElegirMateria(
    materias: List<Materia>,
    alElegir: (Long) -> Unit,
    alPedirNueva: () -> Unit,
    alCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(R.string.importar_elegir_materia)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                materias.forEach { materia ->
                    ListItem(
                        headlineContent = { Text(materia.nombre) },
                        leadingContent = { AvatarMateria(materia.emoji, materia.color, tamano = 40.dp) },
                        modifier = Modifier.clickable { alElegir(materia.id) },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.materia_nueva)) },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(40.dp).padding(8.dp)) },
                    modifier = Modifier.clickable(onClick = alPedirNueva),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.accion_cancelar)) } },
    )
}

/**
 * Mientras se copian los PDFs elegidos. Desde Drive u otras nubes puede tardar (primero se
 * descargan), así que se puede cancelar.
 */
@Composable
fun DialogoCopiando(alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(R.string.importar_copiando)) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.accion_cancelar)) } },
    )
}

/** Confirmación antes de borrar (no se puede deshacer). */
@Composable
fun DialogoBorrar(titulo: String, texto: String, alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(stringResource(R.string.accion_borrar), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.accion_cancelar)) } },
    )
}

private fun nombreColor(color: ColorMateria): Int = when (color) {
    ColorMateria.INDIGO -> R.string.color_indigo
    ColorMateria.AZUL -> R.string.color_azul
    ColorMateria.TURQUESA -> R.string.color_turquesa
    ColorMateria.VERDE -> R.string.color_verde
    ColorMateria.AMBAR -> R.string.color_ambar
    ColorMateria.NARANJA -> R.string.color_naranja
    ColorMateria.ROJO -> R.string.color_rojo
    ColorMateria.ROSA -> R.string.color_rosa
    ColorMateria.VIOLETA -> R.string.color_violeta
    ColorMateria.GRIS -> R.string.color_gris
}
