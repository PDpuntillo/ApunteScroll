package com.apuntescroll.ui

import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.apuntescroll.intercepcion.abrirAccesibilidad
import com.apuntescroll.intercepcion.abrirUrl
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import com.apuntescroll.intercepcion.ui.AccesoIntercepcion
import com.apuntescroll.intercepcion.ui.AvisoIntercepcion
import com.apuntescroll.intercepcion.ui.PantallaAviso
import com.apuntescroll.intercepcion.ui.SeccionRedes
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
import com.apuntescroll.ui.feed.OpcionesFeed
import com.apuntescroll.ui.feed.PantallaFeed
import com.apuntescroll.ui.feed.PantallaHilo
import com.apuntescroll.ui.navegacion.GrafoBiblioteca
import com.apuntescroll.ui.navegacion.GrafoInicio
import com.apuntescroll.ui.navegacion.RutaHilo
import com.apuntescroll.ui.navegacion.RutaAjustes
import com.apuntescroll.ui.navegacion.RutaAvisoIntercepcion
import com.apuntescroll.ui.navegacion.RutaBiblioteca
import com.apuntescroll.ui.navegacion.RutaDocumento
import com.apuntescroll.ui.navegacion.RutaInicio
import com.apuntescroll.ui.navegacion.RutaMateria
import com.apuntescroll.ui.navegacion.RutaPagina
import com.apuntescroll.ui.navegacion.RutaPerfil
import com.apuntescroll.ui.navegacion.siEstaActiva
import com.apuntescroll.ui.pantallas.PantallaAjustes
import com.apuntescroll.ui.pantallas.PantallaPerfil
import kotlinx.coroutines.flow.StateFlow

/** Las cuatro secciones de la barra de abajo, con la ruta (o grafo) de cada una. */
enum class Seccion(@StringRes val etiqueta: Int, @DrawableRes val icono: Int, val ruta: Any) {
    INICIO(R.string.nav_inicio, R.drawable.ic_inicio, GrafoInicio),
    BIBLIOTECA(R.string.nav_biblioteca, R.drawable.ic_biblioteca, GrafoBiblioteca),
    PERFIL(R.string.nav_perfil, R.drawable.ic_perfil, RutaPerfil),
    AJUSTES(R.string.nav_ajustes, R.drawable.ic_ajustes, RutaAjustes),
}

/**
 * Pantalla raíz: barra de navegación abajo y el NavHost con todas las pantallas arriba.
 * [destinoPedido]: a dónde ir al abrir la app desde afuera (MainActivity); cuando se atiende,
 * se llama a [alAtender].
 */
@Composable
fun AppRaiz(app: ApunteScrollApp, destinoPedido: StateFlow<Destino?>, alAtender: () -> Unit) {
    val contenedor = app.contenedor
    val context = LocalContext.current
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val destino = entrada?.destination
    val abrirAviso = { nav.navigate(RutaAvisoIntercepcion) { launchSingleTop = true } }

    // El NavHost está dentro del Scaffold y arma su grafo recién al medirse: hasta que haya una
    // entrada en la pila, navegar fallaría.
    val pedido by destinoPedido.collectAsStateWithLifecycle()
    LaunchedEffect(pedido, entrada != null) {
        val d = pedido ?: return@LaunchedEffect
        if (entrada == null) return@LaunchedEffect
        when (d) {
            Destino.AJUSTES_REDES -> irASeccion(nav, Seccion.AJUSTES, yaElegida = false)
            Destino.BIBLIOTECA -> irASeccion(nav, Seccion.BIBLIOTECA, yaElegida = false)
            Destino.AVISO -> abrirAviso()
        }
        alAtender()
    }
    // Si el servicio está activado en Android sin que se haya aceptado el aviso, el aviso se abre
    // solo, una vez por proceso.
    val estadoServicio by contenedor.detector.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estadoServicio, entrada != null) {
        if (entrada == null) return@LaunchedEffect
        if (estadoServicio == EstadoServicio.ACTIVADO_SIN_AVISO && !contenedor.intercepcion.avisoAutomaticoMostrado) {
            contenedor.intercepcion.avisoAutomaticoMostrado = true
            abrirAviso()
        }
    }
    val enAviso = destino?.hasRoute(RutaAvisoIntercepcion::class) == true

    Scaffold(
        // Cada pantalla tiene su propia barra de arriba, que maneja la barra de estado.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!enAviso) NavigationBar {
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
                        cabecera = { AvisoIntercepcion(contenedor, alAbrirAviso = { e.siEstaActiva { abrirAviso() } }) },
                        pie = { AccesoIntercepcion(contenedor, alAbrirAviso = { e.siEstaActiva { abrirAviso() } }) },
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
            composable<RutaAjustes> { e ->
                PantallaAjustes(
                    ajustes = contenedor.ajustes,
                    seccionRedes = { avisos ->
                        SeccionRedes(contenedor, avisos, alAbrirAviso = { e.siEstaActiva { abrirAviso() } })
                    },
                )
            }

            // El aviso de la intercepción (F3): pantalla propia, sin la barra de abajo.
            composable<RutaAvisoIntercepcion> {
                val consentimiento by contenedor.ajustesIntercepcion.consentimiento.collectAsStateWithLifecycle()
                val urlPrivacidad = stringResource(R.string.url_privacidad)
                val cerrar = dropUnlessResumed { nav.popBackStack() }
                PantallaAviso(
                    yaActivado = estadoServicio == EstadoServicio.ACTIVADO_SIN_AVISO || contenedor.detector.habilitado(),
                    yaAceptado = consentimiento?.vigente() == true,
                    versionVieja = consentimiento?.vigente() == false,
                    conPrivacidad = urlPrivacidad.isNotEmpty(),
                    alAceptar = dropUnlessResumed {
                        aceptarAviso(app, context)
                        nav.popBackStack()
                    },
                    alRechazar = dropUnlessResumed {
                        rechazarAviso(app, context)
                        nav.popBackStack()
                    },
                    alCerrar = cerrar,
                    alAbrirPrivacidad = { abrirUrl(context, urlPrivacidad) },
                )
            }
        }
    }
}

private fun feedViewModel(app: ApunteScrollApp): FeedViewModel {
    val c = app.contenedor
    return FeedViewModel(
        c.repositorio,
        c.ajustes.piel,
        app.alcance,
        c.procesador.rearmando,
        OpcionesFeed(alCambiarPiel = c.ajustes::cambiarPiel, cambiosFuera = c.cambiosFuera),
    )
}

/** "Aceptar y activar": guarda el consentimiento y, si falta, lleva a Accesibilidad de Android. */
private fun aceptarAviso(app: ApunteScrollApp, context: Context) {
    val c = app.contenedor
    c.intercepcion.aceptarAviso()
    if (c.detector.habilitado()) {
        Toast.makeText(context, R.string.aviso_toast_listo, Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(context, R.string.aviso_toast_buscar, Toast.LENGTH_LONG).show()
        if (!c.detector.abrirActivacion(context)) {
            Toast.makeText(context, R.string.abrir_ajustes_fallo, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * "Ahora no". Si el servicio ya estaba activado sin consentimiento, se apaga: nunca queda un
 * servicio corriendo sin que se haya aceptado el aviso.
 */
private fun rechazarAviso(app: ApunteScrollApp, context: Context) {
    val c = app.contenedor
    c.ajustesIntercepcion.anotarRechazo(System.currentTimeMillis())
    if (c.ajustesIntercepcion.consentimientoVigente() || !c.detector.habilitado()) return
    if (c.intercepcion.apagarServicio(retirar = true)) {
        Toast.makeText(context, R.string.aviso_toast_apagado, Toast.LENGTH_LONG).show()
    } else if (!abrirAccesibilidad(context)) {
        Toast.makeText(context, R.string.abrir_ajustes_fallo, Toast.LENGTH_LONG).show()
    }
}

private fun paginaViewModel(app: ApunteScrollApp, ruta: RutaPagina) =
    PaginaViewModel(app.contenedor.repositorio, app.contenedor.archivos, app.alcance, ruta.documentoId, ruta.pagina)

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
