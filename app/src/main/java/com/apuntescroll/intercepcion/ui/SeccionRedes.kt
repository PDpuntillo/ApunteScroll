package com.apuntescroll.intercepcion.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.BuildConfig
import com.apuntescroll.Contenedor
import com.apuntescroll.R
import com.apuntescroll.feed.PielFeed
import com.apuntescroll.intercepcion.abrirUrl
import com.apuntescroll.intercepcion.redInstalada
import com.apuntescroll.intercepcion.reglas.AvisoInicio
import com.apuntescroll.intercepcion.reglas.DIAS_AVISO_DIAGNOSTICO
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.OpcionesPase
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.ui.pantallas.FilaOpcion
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Ajustes > Redes (F3): el estado del servicio, qué pasa con cada red, el pase y el acceso al
 * aviso. Se puede cambiar todo aunque no haya consentimiento; los cambios valen al instante (el
 * motor lee la configuración en cada evento).
 */
@Composable
fun SeccionRedes(contenedor: Contenedor, avisos: SnackbarHostState, alAbrirAviso: () -> Unit) {
    val ajustes = contenedor.ajustesIntercepcion
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    val estado by contenedor.detector.estado.collectAsStateWithLifecycle()
    val consentimiento by ajustes.consentimiento.collectAsStateWithLifecycle()
    val config by ajustes.config.collectAsStateWithLifecycle()
    val diag by ajustes.avisos.collectAsStateWithLifecycle()
    val pase by ajustes.pase.collectAsStateWithLifecycle()
    val hastaReloj = remember(pase) { contenedor.intercepcion.paseHastaReloj() }
    var confirmarApagado by rememberSaveable { mutableStateOf(false) }
    val urlPrivacidad = stringResource(R.string.url_privacidad)

    Titulo(stringResource(R.string.ajustes_redes_titulo))
    Explicacion(stringResource(R.string.ajustes_redes_explicacion))

    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            ContenidoEstado(contenedor, estado, versionVieja = consentimiento?.vigente() == false, alAbrirAviso = alAbrirAviso)
            val ahora = System.currentTimeMillis()
            val reciente = { en: Long? -> en != null && ahora - en in 0 until DIAS_AVISO_DIAGNOSTICO * 24 * 60 * 60_000L }
            val activos = buildList {
                if (diag.noAparece) add(AvisoInicio.NO_APARECE)
                if (reciente(diag.peleaEn)) add(AvisoInicio.PELEA)
                if (reciente(diag.bucleEn)) add(AvisoInicio.BUCLE)
            }
            activos.forEach { aviso ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Diagnostico(aviso, ajustes)
            }
        }
    }

    contenedor.catalogoRedes.redes.forEach { red ->
        FilaRed(
            red = red,
            activada = config.de(red).activada,
            modo = config.de(red).modo,
            tarjetas = config.tarjetasPase,
            minutos = config.minutosPase,
            instalada = remember(red) { redInstalada(context, red.paquete) },
            alActivar = { ajustes.cambiarRed(red.paquete, activada = it) },
            alElegirModo = { ajustes.cambiarRed(red.paquete, modo = it) },
        )
    }

    Subtitulo(stringResource(R.string.ajustes_pase_titulo))
    Etiqueta(stringResource(R.string.ajustes_pase_tarjetas))
    Chips(OpcionesPase.TARJETAS, config.tarjetasPase, { n -> pluralStringResource(R.plurals.cantidad_tarjetas, n, n) }) {
        ajustes.cambiarTarjetasPase(it)
    }
    Etiqueta(stringResource(R.string.ajustes_pase_minutos))
    Chips(OpcionesPase.opcionesMinutos(BuildConfig.DEBUG), config.minutosPase, { m -> pluralStringResource(R.plurals.cantidad_minutos, m, m) }) {
        ajustes.cambiarMinutosPase(it)
    }
    Explicacion(stringResource(R.string.ajustes_pase_explicacion))
    if (hastaReloj != null) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ajustes_pase_activo, DateFormat.getTimeFormat(context).format(Date(hastaReloj))),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { contenedor.intercepcion.terminarPase() }) { Text(stringResource(R.string.ajustes_pase_terminar)) }
        }
    }

    Explicacion(stringResource(R.string.ajustes_redes_otra_app), arriba = 16)
    Explicacion(stringResource(R.string.ajustes_redes_limites))

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = alAbrirAviso) { Text(stringResource(R.string.ajustes_redes_ver_aviso)) }
        if (consentimiento != null) {
            OutlinedButton(onClick = { confirmarApagado = true }) { Text(stringResource(R.string.ajustes_redes_apagar)) }
        }
        if (urlPrivacidad.isNotEmpty()) {
            TextButton(onClick = { abrirUrl(context, urlPrivacidad) }) { Text(stringResource(R.string.ajustes_privacidad)) }
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))

    if (confirmarApagado) {
        val apagado = stringResource(R.string.aviso_toast_apagado)
        val aMano = stringResource(R.string.ajustes_redes_apagar_a_mano)
        AlertDialog(
            onDismissRequest = { confirmarApagado = false },
            title = { Text(stringResource(R.string.ajustes_redes_apagar_titulo)) },
            text = { Text(stringResource(R.string.ajustes_redes_apagar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarApagado = false
                    val habilitado = contenedor.detector.habilitado()
                    val listo = contenedor.intercepcion.apagarServicio(retirar = true) || !habilitado
                    alcance.launch { avisos.showSnackbar(if (listo) apagado else aMano) }
                }) { Text(stringResource(R.string.ajustes_redes_apagar_confirmar)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarApagado = false }) { Text(stringResource(R.string.accion_cancelar)) }
            },
        )
    }
}

/** Una red: el interruptor, su estilo y los tres modos (con su detalle en vivo). */
@Composable
private fun FilaRed(
    red: RedConocida,
    activada: Boolean,
    modo: ModoIntercepcion,
    tarjetas: Int,
    minutos: Int,
    instalada: Boolean,
    alActivar: (Boolean) -> Unit,
    alElegirModo: (ModoIntercepcion) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = activada, role = Role.Switch, onValueChange = alActivar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(red.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when {
                        !instalada -> R.string.ajustes_redes_no_instalada
                        red.piel == PielFeed.CARRUSEL -> R.string.ajustes_redes_estilo_carrusel
                        else -> R.string.ajustes_redes_estilo_hilo
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // El clic lo maneja la fila entera; el interruptor es solo visual.
        Switch(checked = activada, onCheckedChange = null)
    }
    if (activada) {
        Column(Modifier.selectableGroup()) {
            ModoIntercepcion.entries.forEach { m ->
                val (texto, detalle) = when (m) {
                    ModoIntercepcion.PASE ->
                        stringResource(R.string.modo_pase) to pluralStringResource(R.plurals.modo_pase_detalle, tarjetas, tarjetas, minutos)
                    ModoIntercepcion.RECORDATORIO ->
                        stringResource(R.string.modo_recordatorio) to stringResource(R.string.modo_recordatorio_detalle)
                    ModoIntercepcion.REEMPLAZO_TOTAL ->
                        stringResource(R.string.modo_reemplazo) to stringResource(R.string.modo_reemplazo_detalle)
                }
                FilaOpcion(texto = texto, elegida = m == modo, alElegir = { alElegirModo(m) }, detalle = detalle)
            }
        }
    }
}

@Composable
private fun Chips(opciones: List<Int>, elegida: Int, descripcion: @Composable (Int) -> String, alElegir: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEach { n ->
            val texto = descripcion(n)
            FilterChip(
                selected = n == elegida,
                onClick = { alElegir(n) },
                label = { Text(n.toString()) },
                modifier = Modifier.semantics { contentDescription = texto },
            )
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        text = texto,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        text = texto,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun Etiqueta(texto: String) {
    Text(
        text = texto,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Explicacion(texto: String, arriba: Int = 4) {
    Text(
        text = texto,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = arriba.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
