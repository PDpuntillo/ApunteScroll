package com.apuntescroll.intercepcion.ui

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apuntescroll.Contenedor
import com.apuntescroll.R
import com.apuntescroll.datos.AjustesIntercepcion
import com.apuntescroll.intercepcion.abrirBateria
import com.apuntescroll.intercepcion.abrirPermisosXiaomi
import com.apuntescroll.intercepcion.esXiaomi
import com.apuntescroll.intercepcion.instaladaDesdePlay
import com.apuntescroll.intercepcion.marcaQueCierraApps
import com.apuntescroll.intercepcion.reglas.AvisoInicio
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import com.apuntescroll.intercepcion.reglas.elegirAvisoInicio
import java.util.Date

/**
 * La tarjeta de la intercepción en Inicio (una sola, por prioridad: elegirAvisoInicio). Va arriba
 * del feed y se ve también con Inicio vacío: es donde el revisor de Google Play encuentra el aviso.
 */
@Composable
fun AvisoIntercepcion(contenedor: Contenedor, alAbrirAviso: () -> Unit) {
    val ajustes = contenedor.ajustesIntercepcion
    val estado by contenedor.detector.estado.collectAsStateWithLifecycle()
    val consentimiento by ajustes.consentimiento.collectAsStateWithLifecycle()
    val avisos by ajustes.avisos.collectAsStateWithLifecycle()
    val pase by ajustes.pase.collectAsStateWithLifecycle()
    val hastaReloj = remember(pase) { contenedor.intercepcion.paseHastaReloj() }
    val aviso = elegirAvisoInicio(
        estado = estado,
        consentida = consentimiento?.vigente() == true,
        noAparece = avisos.noAparece,
        peleaEn = avisos.peleaEn,
        bucleEn = avisos.bucleEn,
        paseActivo = hastaReloj != null,
        rechazos = avisos.rechazos,
        invitacionOcultaHasta = avisos.invitacionOcultaHasta,
        estadoOcultoHasta = avisos.estadoOcultoHasta,
        ahoraReloj = System.currentTimeMillis(),
    ) ?: return
    val versionVieja = consentimiento?.vigente() == false
    val ahora = { System.currentTimeMillis() }

    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            when (aviso) {
                AvisoInicio.ACTIVADO_SIN_AVISO -> {
                    Titulo(stringResource(R.string.inicio_aviso_titulo))
                    ContenidoEstado(contenedor, estado, versionVieja, alAbrirAviso)
                }
                AvisoInicio.ESTADO -> {
                    Titulo(stringResource(R.string.inicio_aviso_titulo_estado))
                    ContenidoEstado(contenedor, estado, versionVieja, alAbrirAviso) {
                        TextButton(onClick = { ajustes.ocultarEstado(ahora() + AjustesIntercepcion.OCULTAR_MS) }) {
                            Text(stringResource(R.string.inicio_aviso_ahora_no))
                        }
                    }
                }
                AvisoInicio.NO_APARECE, AvisoInicio.PELEA, AvisoInicio.BUCLE -> {
                    Titulo(stringResource(R.string.inicio_aviso_titulo_diagnostico))
                    Diagnostico(aviso, ajustes)
                }
                AvisoInicio.PASE_ACTIVO -> {
                    val context = LocalContext.current
                    val hora = hastaReloj?.let { DateFormat.getTimeFormat(context).format(Date(it)) }.orEmpty()
                    Texto(stringResource(R.string.inicio_aviso_pase, hora), arriba = 0)
                    Botones {
                        TextButton(onClick = { contenedor.intercepcion.terminarPase() }) {
                            Text(stringResource(R.string.ajustes_pase_terminar))
                        }
                    }
                }
                AvisoInicio.INVITACION -> {
                    Titulo(stringResource(R.string.inicio_aviso_titulo))
                    Texto(stringResource(R.string.inicio_aviso_invitacion))
                    Botones {
                        TextButton(onClick = { ajustes.anotarRechazo(ahora()) }) {
                            Text(stringResource(R.string.inicio_aviso_ahora_no))
                        }
                        FilledTonalButton(onClick = alAbrirAviso) { Text(stringResource(R.string.servicio_ver_como)) }
                    }
                }
            }
        }
    }
}

/**
 * Acceso mínimo y permanente al aviso en Inicio (en el estado vacío y al pie de la lista), para
 * cuando la tarjeta ya no se ofrece. Solo sin consentimiento y si la tarjeta no está a la vista.
 */
@Composable
fun AccesoIntercepcion(contenedor: Contenedor, alAbrirAviso: () -> Unit) {
    val estado by contenedor.detector.estado.collectAsStateWithLifecycle()
    val avisos by contenedor.ajustesIntercepcion.avisos.collectAsStateWithLifecycle()
    if (estado != EstadoServicio.SIN_CONSENTIMIENTO) return
    val conInvitacion = elegirAvisoInicio(
        estado, consentida = false, noAparece = avisos.noAparece, peleaEn = avisos.peleaEn, bucleEn = avisos.bucleEn,
        paseActivo = false, rechazos = avisos.rechazos, invitacionOcultaHasta = avisos.invitacionOcultaHasta,
        estadoOcultoHasta = avisos.estadoOcultoHasta, ahoraReloj = System.currentTimeMillis(),
    ) == AvisoInicio.INVITACION
    if (conInvitacion) return
    TextButton(onClick = alAbrirAviso, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Text(stringResource(R.string.inicio_acceso_intercepcion))
    }
}

/**
 * El estado del servicio con su explicación y su botón (lo comparten Inicio y Ajustes > Redes).
 * [extra] agrega botones al final de la fila.
 */
@Composable
fun ContenidoEstado(
    contenedor: Contenedor,
    estado: EstadoServicio,
    versionVieja: Boolean,
    alAbrirAviso: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val activar = { if (!contenedor.detector.abrirActivacion(context)) avisarFallo(context) }
    val texto = when (estado) {
        EstadoServicio.PROTECCION_AVANZADA -> R.string.servicio_estado_proteccion
        EstadoServicio.ACTIVADO_SIN_AVISO, EstadoServicio.SIN_CONSENTIMIENTO ->
            when {
                versionVieja -> R.string.servicio_estado_aviso_nuevo
                estado == EstadoServicio.ACTIVADO_SIN_AVISO -> R.string.servicio_estado_activado_sin_aviso
                else -> R.string.servicio_estado_sin_consentimiento
            }
        EstadoServicio.FALTA_ACTIVAR -> R.string.servicio_estado_falta_activar
        EstadoServicio.SE_APAGO -> R.string.servicio_estado_se_apago
        EstadoServicio.CONECTANDO -> R.string.servicio_estado_conectando
        EstadoServicio.NO_RESPONDE -> R.string.servicio_estado_no_responde
        EstadoServicio.FUNCIONANDO -> R.string.servicio_estado_funcionando
    }
    Texto(stringResource(texto), arriba = 0)
    if (estado == EstadoServicio.SE_APAGO && Build.VERSION.SDK_INT >= 37) {
        Texto(stringResource(R.string.servicio_estado_se_apago_proteccion))
    }
    val apagado = estado == EstadoServicio.SE_APAGO || estado == EstadoServicio.FALTA_ACTIVAR
    val dePlay = remember { instaladaDesdePlay(context) }
    // Android 13 o más bloquea la accesibilidad de las apps instaladas a mano ("ajustes restringidos").
    if (apagado && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && dePlay != true) {
        Texto(stringResource(R.string.servicio_estado_restringidos))
    }
    if (estado == EstadoServicio.SE_APAGO && marcaQueCierraApps()) {
        Texto(stringResource(R.string.servicio_estado_bateria))
    }
    Botones {
        extra()
        when (estado) {
            EstadoServicio.SIN_CONSENTIMIENTO -> FilledTonalButton(onClick = alAbrirAviso) { Text(stringResource(R.string.servicio_ver_como)) }
            EstadoServicio.ACTIVADO_SIN_AVISO -> FilledTonalButton(onClick = alAbrirAviso) { Text(stringResource(R.string.servicio_ver_aviso)) }
            EstadoServicio.FALTA_ACTIVAR -> FilledTonalButton(onClick = activar) { Text(stringResource(R.string.servicio_activar)) }
            EstadoServicio.SE_APAGO -> {
                if (marcaQueCierraApps()) {
                    TextButton(onClick = { if (!abrirBateria(context)) avisarFallo(context) }) {
                        Text(stringResource(R.string.servicio_abrir_bateria))
                    }
                }
                FilledTonalButton(onClick = activar) { Text(stringResource(R.string.servicio_activar_de_nuevo)) }
            }
            EstadoServicio.NO_RESPONDE -> FilledTonalButton(onClick = activar) { Text(stringResource(R.string.servicio_abrir_accesibilidad)) }
            EstadoServicio.PROTECCION_AVANZADA, EstadoServicio.CONECTANDO, EstadoServicio.FUNCIONANDO -> Unit
        }
    }
}

/** Los avisos de funcionamiento (no apareció, otra app la tapó, bucle), con su botón. */
@Composable
fun Diagnostico(aviso: AvisoInicio, ajustes: AjustesIntercepcion) {
    val context = LocalContext.current
    when (aviso) {
        AvisoInicio.NO_APARECE -> {
            Texto(stringResource(R.string.diag_no_aparece))
            if (esXiaomi()) Texto(stringResource(R.string.diag_no_aparece_xiaomi))
        }
        AvisoInicio.PELEA -> Texto(stringResource(R.string.diag_pelea))
        AvisoInicio.BUCLE -> Texto(stringResource(R.string.diag_bucle))
        else -> return
    }
    Botones {
        if (aviso == AvisoInicio.NO_APARECE && esXiaomi()) {
            TextButton(onClick = { if (!abrirPermisosXiaomi(context)) avisarFallo(context) }) {
                Text(stringResource(R.string.diag_abrir_permisos))
            }
        }
        FilledTonalButton(onClick = ajustes::descartarDiagnostico) { Text(stringResource(R.string.accion_entendido)) }
    }
}

private fun avisarFallo(context: Context) {
    Toast.makeText(context, R.string.abrir_ajustes_fallo, Toast.LENGTH_LONG).show()
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Texto(texto: String, arriba: Int = 8) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = arriba.dp),
    )
}

@Composable
private fun Botones(contenido: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { contenido() }
}
