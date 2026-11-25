package com.apuntescroll.ui.navegacion

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry

/**
 * Ejecuta [accion] solo si esta pantalla es la activa (no la que se está yendo): durante la
 * animación de salida, un segundo toque sacaría otra pantalla o abriría dos veces.
 */
internal inline fun NavBackStackEntry.siEstaActiva(accion: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) accion()
}
