package com.apuntescroll.intercepcion.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.R
import com.apuntescroll.intercepcion.reglas.EstadoPuerta
import com.apuntescroll.intercepcion.reglas.MotivoIntercepcion
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.ui.Destino
import com.apuntescroll.ui.feed.ContenidoFeed
import com.apuntescroll.ui.feed.ContenidoHilo
import com.apuntescroll.ui.feed.FeedViewModel
import com.apuntescroll.ui.feed.HiloViewModel
import com.apuntescroll.ui.feed.rememberAccionesFeed
import com.apuntescroll.ui.navegacion.RutaFeedInterceptado
import com.apuntescroll.ui.navegacion.RutaHilo
import com.apuntescroll.ui.navegacion.siEstaActiva
import com.apuntescroll.ui.pantallas.EstadoVacio
import kotlinx.coroutines.delay

/**
 * La pantalla de apuntes encima de una red: el feed con la piel de esa red (y su hilo completo),
 * con la puerta abajo. No usa logos, colores ni íconos de Instagram o X.
 */
@Composable
fun PantallaInterceptada(
    app: ApunteScrollApp,
    interceptado: InterceptadoViewModel,
    feed: FeedViewModel,
    multiventana: Boolean,
    alCerrar: () -> Unit,
    alEntrar: () -> Unit,
    alIrIgual: () -> Unit,
    alAbrirApp: (Destino) -> Unit,
) {
    val red by interceptado.red.collectAsStateWithLifecycle()
    val motivo by interceptado.motivo.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val enHilo = entrada?.destination?.hasRoute(RutaHilo::class) == true

    // Otra red por onNewIntent: volver a la raíz. Solo si cambió (al rotar, la pila se restaura
    // y el hilo abierto sigue) y con el grafo ya armado (si no, popBackStack falla).
    var redVista by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(red?.paquete, entrada != null) {
        val p = red?.paquete ?: return@LaunchedEffect
        if (entrada == null) return@LaunchedEffect
        if (redVista != null && redVista != p) nav.popBackStack(RutaFeedInterceptado, inclusive = false)
        redVista = p
    }

    val r = red ?: return
    val panel = stringResource(R.string.intercepcion_panel, r.nombre)
    Scaffold(
        modifier = Modifier.semantics { paneTitle = panel },
        topBar = {
            BarraIntercepcion(
                red = r,
                motivo = motivo,
                enHilo = enHilo,
                alVolver = { nav.popBackStack() },
                alCambiarModo = { alAbrirApp(Destino.AJUSTES_REDES) },
                alCerrar = alCerrar,
            )
        },
        bottomBar = {
            PuertaConEstado(
                interceptado = interceptado,
                feed = feed,
                red = r,
                alEntrar = alEntrar,
                alIrIgual = alIrIgual,
                alCerrar = alCerrar,
                alImportar = { alAbrirApp(Destino.BIBLIOTECA) },
            )
        },
    ) { margenes ->
        NavHost(nav, startDestination = RutaFeedInterceptado) {
            composable<RutaFeedInterceptado> { e ->
                val lista = rememberLazyListState()
                val acciones = rememberAccionesFeed(feed, lista) { s, t -> e.siEstaActiva { nav.navigate(RutaHilo(s, t)) } }
                ContenidoFeed(
                    viewModel = feed,
                    margenes = margenes,
                    lista = lista,
                    acciones = acciones,
                    vacio = { m ->
                        EstadoVacio(
                            R.string.intercepcion_vacio_titulo,
                            texto = null,
                            modifier = m,
                            textoLibre = stringResource(R.string.intercepcion_vacio, r.nombre),
                        ) {
                            Button(onClick = { alAbrirApp(Destino.BIBLIOTECA) }, modifier = Modifier.padding(top = 24.dp)) {
                                Text(stringResource(R.string.intercepcion_importar))
                            }
                        }
                    },
                    cabecera = { if (multiventana) FranjaDividida(r) },
                )
            }
            composable<RutaHilo> { e ->
                val ruta = e.toRoute<RutaHilo>()
                ContenidoHilo(
                    hilo = viewModel { HiloViewModel(app.contenedor.repositorio, ruta.seccionId) },
                    feed = feed,
                    tarjetaId = ruta.tarjetaId,
                    margenes = margenes,
                    alVolver = dropUnlessResumed { nav.popBackStack() },
                )
            }
        }
    }
}

/** Barra de arriba: ApunteScroll (no la red), el motivo y las salidas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraIntercepcion(
    red: RedConocida,
    motivo: MotivoIntercepcion,
    enHilo: Boolean,
    alVolver: () -> Unit,
    alCambiarModo: () -> Unit,
    alCerrar: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoApp()
                        Text(
                            stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp).semantics { heading() },
                        )
                    }
                },
                navigationIcon = {
                    if (enHilo) {
                        IconButton(onClick = alVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.intercepcion_volver_feed))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = alCambiarModo) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.intercepcion_cambiar_modo))
                    }
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.intercepcion_cerrar))
                    }
                },
            )
            Text(
                text = when (motivo) {
                    MotivoIntercepcion.ABRIO_RED -> stringResource(R.string.intercepcion_motivo_abrio, red.nombre)
                    MotivoIntercepcion.PASE_VENCIDO -> stringResource(R.string.intercepcion_motivo_vencido)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** El ícono de ApunteScroll: el primer plano del ícono adaptable sobre su fondo, en un círculo. */
@Composable
private fun IconoApp() {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.icono_fondo)),
        contentAlignment = Alignment.Center,
    ) {
        // El primer plano mide 108 dp con 72 de zona segura: 32 × 108 / 72 = 48.
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(48.dp))
    }
}

/** Arriba del feed en pantalla dividida o ventana flotante: la red sigue a la vista. */
@Composable
private fun FranjaDividida(red: RedConocida) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.intercepcion_dividida, red.nombre),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * La puerta con su estado: junta el feed, el progreso y la configuración, y avanza el reloj solo
 * mientras hace falta (la cuenta de "Ir igual", y cada 30 s con un pase activo).
 */
@Composable
private fun PuertaConEstado(
    interceptado: InterceptadoViewModel,
    feed: FeedViewModel,
    red: RedConocida,
    alEntrar: () -> Unit,
    alIrIgual: () -> Unit,
    alCerrar: () -> Unit,
    alImportar: () -> Unit,
) {
    val estado by feed.estado.collectAsStateWithLifecycle()
    val marcas by feed.marcas.collectAsStateWithLifecycle()
    val progreso by interceptado.progreso.collectAsStateWithLifecycle()
    val config by interceptado.config.collectAsStateWithLifecycle()
    var ahora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val disponibles = remember(estado, marcas, progreso) { feed.disponiblesSin(progreso.ids) }
    val puerta = remember(estado, disponibles, progreso, config, ahora) { interceptado.puerta(estado, disponibles, ahora) }

    val intervalo = when {
        puerta is EstadoPuerta.Recordatorio && puerta.segundosRestantes > 0 -> 250L
        puerta is EstadoPuerta.PaseActivo -> 30_000L
        else -> 0L
    }
    LaunchedEffect(intervalo) {
        // También al empezar: la cuenta se fija en onResume, después de la primera composición.
        ahora = SystemClock.elapsedRealtime()
        if (intervalo == 0L) return@LaunchedEffect
        while (true) {
            delay(intervalo)
            ahora = SystemClock.elapsedRealtime()
        }
    }
    Puerta(puerta, red, alEntrar, alIrIgual, alCerrar, alImportar)
}

/** La barra de abajo: qué falta para entrar y los botones. Los botones bajan si no entran. */
@Composable
fun Puerta(
    estado: EstadoPuerta,
    red: RedConocida,
    alEntrar: () -> Unit,
    alIrIgual: () -> Unit,
    alCerrar: () -> Unit,
    alImportar: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .semantics { isTraversalGroup = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val botones = Modifier.padding(top = 8.dp)
            when (estado) {
                EstadoPuerta.Cargando -> TextoPuerta(stringResource(R.string.puerta_cargando))
                is EstadoPuerta.PaseActivo -> {
                    TextoPuerta(stringResource(R.string.puerta_pase_activo, estado.restanteMin))
                    FilaBotones(botones) {
                        Button(onClick = alEntrar) { Text(stringResource(R.string.puerta_entrar_con_pase, red.nombre)) }
                    }
                }
                is EstadoPuerta.Pase -> PuertaPase(estado, red, alEntrar, alImportar)
                is EstadoPuerta.Recordatorio -> {
                    TextoPuerta(stringResource(R.string.puerta_recordatorio))
                    FilaBotones(botones) {
                        val espera = stringResource(R.string.puerta_ir_igual_espera)
                        if (estado.segundosRestantes > 0) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.semantics { contentDescription = espera },
                            ) { Text(stringResource(R.string.puerta_ir_igual_en, estado.segundosRestantes)) }
                        } else {
                            OutlinedButton(onClick = alIrIgual) { Text(stringResource(R.string.puerta_ir_igual)) }
                        }
                    }
                }
                is EstadoPuerta.Reemplazo -> {
                    TextoPuerta(stringResource(R.string.puerta_reemplazo, red.nombre))
                    FilaBotones(botones) {
                        Button(onClick = alCerrar) { Text(stringResource(R.string.puerta_volver_inicio)) }
                        if (!estado.hayTarjetas) {
                            OutlinedButton(onClick = alImportar) { Text(stringResource(R.string.intercepcion_importar)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuertaPase(estado: EstadoPuerta.Pase, red: RedConocida, alEntrar: () -> Unit, alImportar: () -> Unit) {
    val entrar = stringResource(R.string.puerta_entrar, red.nombre, estado.minutos)
    when {
        estado.objetivo == 0 -> {
            TextoPuerta(stringResource(if (estado.hayTarjetas) R.string.puerta_pase_al_dia else R.string.puerta_pase_sin_tarjetas))
            FilaBotones(Modifier.padding(top = 8.dp)) {
                Button(onClick = alEntrar) { Text(entrar) }
                if (!estado.hayTarjetas) OutlinedButton(onClick = alImportar) { Text(stringResource(R.string.intercepcion_importar)) }
            }
        }
        estado.listo -> {
            // Se anuncia una vez, cuando aparece.
            Text(
                stringResource(R.string.puerta_pase_listo, estado.minutos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            FilaBotones(Modifier.padding(top = 8.dp)) { Button(onClick = alEntrar) { Text(entrar) } }
        }
        else -> {
            val faltan = estado.objetivo - estado.leidas
            TextoPuerta(pluralStringResource(R.plurals.puerta_pase_faltan, faltan, faltan, estado.minutos))
            val descripcion = pluralStringResource(R.plurals.puerta_pase_progreso_descripcion, estado.objetivo, estado.leidas, estado.objetivo)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { estado.leidas.toFloat() / estado.objetivo },
                    // El riel del tema es dorado y parece más lleno que el avance: gris neutro.
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).semantics { contentDescription = descripcion },
                )
                Text(
                    stringResource(R.string.puerta_pase_progreso, estado.leidas, estado.objetivo),
                    style = MaterialTheme.typography.labelLarge,
                    // Ya lo dice la barra: TalkBack no lo repite.
                    modifier = Modifier.padding(start = 12.dp).clearAndSetSemantics {},
                )
            }
            Text(
                stringResource(R.string.puerta_pase_como_cuenta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            FilaBotones(Modifier.padding(top = 8.dp)) { Button(onClick = {}, enabled = false) { Text(entrar) } }
        }
    }
}

@Composable
private fun TextoPuerta(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun FilaBotones(modifier: Modifier, botones: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { botones() }
}
