package com.apuntescroll.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Ambar40,
    onSecondary = Color.White,
    secondaryContainer = Ambar90,
    onSecondaryContainer = Ambar30,
    background = FondoClaro,
    surface = FondoClaro,
)

private val EsquemaOscuro = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Ambar80,
    onSecondary = Ambar20,
    secondaryContainer = Ambar30,
    onSecondaryContainer = Ambar90,
    background = FondoOscuro,
    surface = FondoOscuro,
    surfaceContainer = SuperficieOscura,
)

/**
 * Tema de ApunteScroll. No usa los colores dinámicos de Android 12+ a propósito: la app tiene
 * que verse siempre como ApunteScroll, sobre todo cuando aparece encima de otra red.
 */
@Composable
fun ApunteScrollTema(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        content = contenido,
    )
}
