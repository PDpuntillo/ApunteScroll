package com.apuntescroll.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.R
import com.apuntescroll.ui.biblioteca.BibliotecaViewModel
import com.apuntescroll.ui.biblioteca.DocumentoViewModel
import com.apuntescroll.ui.biblioteca.MateriaViewModel
import com.apuntescroll.ui.biblioteca.PaginaViewModel
import com.apuntescroll.ui.biblioteca.PantallaBiblioteca
import com.apuntescroll.ui.biblioteca.PantallaDocumento
import com.apuntescroll.ui.biblioteca.PantallaMateria
import com.apuntescroll.ui.biblioteca.PantallaPagina
import com.apuntescroll.ui.feed.FeedViewModel
import com.apuntescroll.ui.feed.HiloViewModel
import com.apuntescroll.ui.feed.PantallaFeed
import com.apuntescroll.ui.feed.PantallaHilo
import com.apuntescroll.ui.navegacion.GrafoBiblioteca
import com.apuntescroll.ui.navegacion.GrafoInicio
import com.apuntescroll.ui.navegacion.RutaHilo
import com.apuntescroll.ui.navegacion.RutaAjustes
import com.apuntescroll.ui.navegacion.RutaBiblioteca
import com.apuntescroll.ui.navegacion.RutaDocumento
import com.apuntescroll.ui.navegacion.RutaInicio
import com.apuntescroll.ui.navegacion.RutaMateria
import com.apuntescroll.ui.navegacion.RutaPagina
import com.apuntescroll.ui.navegacion.RutaPerfil
import com.apuntescroll.ui.pantallas.PantallaAjustes
import com.apuntescroll.ui.pantallas.PantallaPerfil

/** Las cuatro secciones de la barra de abajo, con la ruta (o grafo) de cada una. */
enum class Seccion(@StringRes val etiqueta: Int, @DrawableRes val icono: Int, val ruta: Any) {
    INICIO(R.string.nav_inicio, R.drawable.ic_inicio, GrafoInicio),
    BIBLIOTECA(R.string.nav_biblioteca, R.drawable.ic_biblioteca, GrafoBiblioteca),
    PERFIL(R.string.nav_perfil, R.drawable.ic_perfil, RutaPerfil),
    AJUSTES(R.string.nav_ajustes, R.drawable.ic_ajustes, RutaAjustes),
}

/** Pantalla raíz: barra de navegación abajo y el NavHost con todas las pantallas arriba. */
@Composable
fun AppRaiz(app: ApunteScrollApp) {
    val contenedor = app.contenedor
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val destino = entrada?.destination

    Scaffold(
        // Cada pantalla tiene su propia barra de arriba, que maneja la barra de estado.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Seccion.entries.forEach { s ->
                    val elegida = destino?.hierarchy?.any { it.hasRoute(s.ruta::class) } == true
                    NavigationBarItem(
                        selected = elegida,
                        onClick = { irASeccion(nav, s, yaElegida = elegida) },
                        icon = { Icon(painterResource(s.icono), contentDescription = null) },
                        label = { Text(stringResource(s.etiqueta)) },
                    )
                }
            }
        },
    ) { margenes ->
        NavHost(
            navController = nav,
            startDestination = GrafoInicio,
            modifier = Modifier
                .padding(margenes)
                .consumeWindowInsets(margenes),
        ) {
            navigation<GrafoInicio>(startDestination = RutaInicio) {
                composable<RutaInicio> { e ->
                    PantallaFeed(
                        viewModel = viewModel { feedViewModel(app) },
                        alAbrirHilo = { seccionId, tarjetaId -> e.siEstaActiva { nav.navigate(RutaHilo(seccionId, tarjetaId)) } },
                        alIrABiblioteca = { irASeccion(nav, Seccion.BIBLIOTECA, yaElegida = false) },
                    )
                }
                composable<RutaHilo> { e ->
                    val ruta = e.toRoute<RutaHilo>()
                    // El hilo usa el FeedViewModel de Inicio (que sigue abajo en la pila): lo que se
                    // marca o se ve en el hilo queda también en el feed.
                    val inicio = remember(e) { nav.getBackStackEntry<RutaInicio>() }
                    PantallaHilo(
                        hilo = viewModel { HiloViewModel(contenedor.repositorio, ruta.seccionId) },
                        feed = viewModel(viewModelStoreOwner = inicio) { feedViewModel(app) },
                        tarjetaId = ruta.tarjetaId,
                        alVolver = dropUnlessResumed { nav.popBackStack() },
                    )
                }
            }

            // Los "volver" y "abrir" solo actúan si su pantalla está activa: durante la animación
            // de salida (700 ms) un segundo toque sacaría otra pantalla o abriría dos veces.
            navigation<GrafoBiblioteca>(startDestination = RutaBiblioteca) {
                composable<RutaBiblioteca> { e ->
                    PantallaBiblioteca(
                        viewModel = viewModel { BibliotecaViewModel(contenedor.repositorio, app.alcance) },
                        alAbrirMateria = { id -> e.siEstaActiva { nav.navigate(RutaMateria(id)) } },
                    )
                }
                composable<RutaMateria> { e ->
                    val ruta = e.toRoute<RutaMateria>()
                    PantallaMateria(
                        viewModel = viewModel { MateriaViewModel(contenedor.repositorio, app.alcance, ruta.materiaId) },
                        alVolver = dropUnlessResumed { nav.popBackStack() },
                        alBorrada = { nav.popBackStack<RutaMateria>(inclusive = true) },
                        alAbrirDocumento = { id -> e.siEstaActiva { nav.navigate(RutaDocumento(id)) } },
                    )
                }
                composable<RutaDocumento> { e ->
                    val ruta = e.toRoute<RutaDocumento>()
                    PantallaDocumento(
                        viewModel = viewModel { DocumentoViewModel(contenedor.repositorio, ruta.documentoId) },
                        alVolver = dropUnlessResumed { nav.popBackStack() },
                        alAbrirPagina = { pagina -> e.siEstaActiva { nav.navigate(RutaPagina(ruta.documentoId, pagina)) } },
                    )
                }
                composable<RutaPagina> { e ->
                    val ruta = e.toRoute<RutaPagina>()
                    PantallaPagina(
                        viewModel = viewModel { paginaViewModel(app, ruta) },
                        alVolver = dropUnlessResumed { nav.popBackStack() },
                    )
                }
            }

            composable<RutaPerfil> { PantallaPerfil() }
            composable<RutaAjustes> { PantallaAjustes(ajustes = contenedor.ajustes) }
        }
    }
}

private fun feedViewModel(app: ApunteScrollApp) =
    FeedViewModel(app.contenedor.repositorio, app.contenedor.ajustes, app.alcance, app.contenedor.procesador.rearmando)

private fun paginaViewModel(app: ApunteScrollApp, ruta: RutaPagina) =
    PaginaViewModel(app.contenedor.repositorio, app.contenedor.archivos, app.alcance, ruta.documentoId, ruta.pagina)

/** Ejecuta [accion] solo si esta pantalla es la activa (no la que se está yendo). */
private inline fun NavBackStackEntry.siEstaActiva(accion: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) accion()
}

/**
 * Cambia de sección guardando el estado de la anterior (volver a una sección la deja como
 * estaba). Tocar la sección en la que ya se está vuelve a su pantalla principal.
 */
private fun irASeccion(nav: NavHostController, seccion: Seccion, yaElegida: Boolean) {
    if (yaElegida) {
        when (seccion) {
            Seccion.BIBLIOTECA -> nav.popBackStack(RutaBiblioteca, inclusive = false)
            Seccion.INICIO -> nav.popBackStack(RutaInicio, inclusive = false)
            else -> Unit
        }
        return
    }
    // El hilo abierto en Inicio no se guarda: Inicio es la pantalla de partida y se puede volver a
    // ella con "atrás" sin pasar por la pestaña, y entonces un hilo guardado reaparecería más
    // tarde, fuera de lugar. Al volver, Inicio muestra el feed donde estaba.
    if (nav.currentDestination?.hasRoute(RutaHilo::class) == true) {
        nav.popBackStack(RutaInicio, inclusive = false)
    }
    nav.navigate(seccion.ruta) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
