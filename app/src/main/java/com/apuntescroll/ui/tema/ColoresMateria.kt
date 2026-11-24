package com.apuntescroll.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.apuntescroll.datos.bd.ColorMateria

/** Fondo y contenido (texto, emoji) de una materia, con contraste suficiente en los dos temas. */
data class ParColores(val fondo: Color, val contenido: Color)

private val claros = mapOf(
    ColorMateria.INDIGO to ParColores(Color(0xFFE2DFFF), Color(0xFF1E1B4B)),
    ColorMateria.AZUL to ParColores(Color(0xFFD6E3FF), Color(0xFF001B3E)),
    ColorMateria.TURQUESA to ParColores(Color(0xFFB8F0E8), Color(0xFF00201D)),
    ColorMateria.VERDE to ParColores(Color(0xFFC8F0C0), Color(0xFF002204)),
    ColorMateria.AMBAR to ParColores(Color(0xFFFFDDB4), Color(0xFF2A1800)),
    ColorMateria.NARANJA to ParColores(Color(0xFFFFDBCA), Color(0xFF331100)),
    ColorMateria.ROJO to ParColores(Color(0xFFFFDAD6), Color(0xFF410002)),
    ColorMateria.ROSA to ParColores(Color(0xFFFFD8E8), Color(0xFF3B0725)),
    ColorMateria.VIOLETA to ParColores(Color(0xFFF2DAFF), Color(0xFF2C0A3D)),
    ColorMateria.GRIS to ParColores(Color(0xFFE2E2E9), Color(0xFF1A1B20)),
)

private val oscuros = mapOf(
    ColorMateria.INDIGO to ParColores(Color(0xFF3730A3), Color(0xFFE2DFFF)),
    ColorMateria.AZUL to ParColores(Color(0xFF284777), Color(0xFFD6E3FF)),
    ColorMateria.TURQUESA to ParColores(Color(0xFF00504A), Color(0xFFB8F0E8)),
    ColorMateria.VERDE to ParColores(Color(0xFF1E5221), Color(0xFFC8F0C0)),
    ColorMateria.AMBAR to ParColores(Color(0xFF7A4A00), Color(0xFFFFDDB4)),
    ColorMateria.NARANJA to ParColores(Color(0xFF7A3000), Color(0xFFFFDBCA)),
    ColorMateria.ROJO to ParColores(Color(0xFF93000A), Color(0xFFFFDAD6)),
    ColorMateria.ROSA to ParColores(Color(0xFF7B2950), Color(0xFFFFD8E8)),
    ColorMateria.VIOLETA to ParColores(Color(0xFF5E3A73), Color(0xFFF2DAFF)),
    ColorMateria.GRIS to ParColores(Color(0xFF45464F), Color(0xFFE2E2E9)),
)

@Composable
fun ColorMateria.colores(oscuro: Boolean = isSystemInDarkTheme()): ParColores =
    (if (oscuro) oscuros else claros).getValue(this)
