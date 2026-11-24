package com.apuntescroll.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.apuntescroll.R
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.ui.pantallas.PantallaAjustes
import com.apuntescroll.ui.pantallas.PantallaBiblioteca
import com.apuntescroll.ui.pantallas.PantallaInicio
import com.apuntescroll.ui.pantallas.PantallaPerfil

/** Las cuatro secciones de la barra de abajo. */
enum class Seccion(@StringRes val etiqueta: Int, @DrawableRes val icono: Int) {
    INICIO(R.string.nav_inicio, R.drawable.ic_inicio),
    BIBLIOTECA(R.string.nav_biblioteca, R.drawable.ic_biblioteca),
    PERFIL(R.string.nav_perfil, R.drawable.ic_perfil),
    AJUSTES(R.string.nav_ajustes, R.drawable.ic_ajustes),
}

/**
 * Pantalla raíz: barra de navegación abajo y la sección elegida arriba. En F1 se suma la
 * navegación entre pantallas (detalle de materia, documento, tarjetas).
 */
@Composable
fun AppRaiz(ajustes: Ajustes) {
    var seccion by rememberSaveable { mutableStateOf(Seccion.INICIO) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Seccion.entries.forEach { s ->
                    NavigationBarItem(
                        selected = s == seccion,
                        onClick = { seccion = s },
                        icon = { Icon(painterResource(s.icono), contentDescription = null) },
                        label = { Text(stringResource(s.etiqueta)) },
                    )
                }
            }
        },
    ) { margenes ->
        val modifier = Modifier.padding(margenes)
        when (seccion) {
            Seccion.INICIO -> PantallaInicio(modifier)
            Seccion.BIBLIOTECA -> PantallaBiblioteca(modifier)
            Seccion.PERFIL -> PantallaPerfil(modifier)
            Seccion.AJUSTES -> PantallaAjustes(ajustes, modifier)
        }
    }
}
