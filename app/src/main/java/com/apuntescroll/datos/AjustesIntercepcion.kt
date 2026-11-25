package com.apuntescroll.datos

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.Consentimiento
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.OpcionesPase
import com.apuntescroll.intercepcion.reglas.PaseGuardado
import com.apuntescroll.intercepcion.reglas.ProgresoPase
import com.apuntescroll.intercepcion.reglas.VERSION_AVISO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lo que la tarjeta de Inicio y Ajustes necesitan recordar sobre la intercepción. */
data class AvisosIntercepcion(
    val rechazos: Int,
    /** Reloj de pared. */
    val invitacionOcultaHasta: Long,
    val estadoOcultoHasta: Long,
    /** El servicio se conectó alguna vez con consentimiento (para explicar "se apagó"). */
    val vistoConectado: Boolean,
    val noAparece: Boolean,
    val peleaEn: Long?,
    val bucleEn: Long?,
)

/**
 * Ajustes y estado de la intercepción (F3), en SharedPreferences propias ("intercepcion").
 * No se guarda qué apps usa la persona: solo sus elecciones, el progreso y el vencimiento del
 * pase, y avisos de funcionamiento. Todo se llama desde el hilo principal.
 */
class AjustesIntercepcion(context: Context, private val catalogo: CatalogoRedes, private val debug: Boolean) {

    private val prefs: SharedPreferences = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _consentimiento = MutableStateFlow(leerConsentimiento())
    val consentimiento: StateFlow<Consentimiento?> = _consentimiento.asStateFlow()

    fun consentimientoVigente(): Boolean = _consentimiento.value?.vigente() == true

    private val _config = MutableStateFlow(leerConfig())

    /** consentida = hay un consentimiento de la versión actual del aviso. */
    val config: StateFlow<ConfigIntercepcion> = _config.asStateFlow()

    private val _pase = MutableStateFlow(leerPase())

    /** El pase guardado (puede estar vencido: lo olvida el motor). */
    val pase: StateFlow<PaseGuardado?> = _pase.asStateFlow()

    private val _progreso = MutableStateFlow(ProgresoPase.desdeTexto(prefs.getString(PROGRESO, null)))
    val progreso: StateFlow<ProgresoPase> = _progreso.asStateFlow()

    private val _avisos = MutableStateFlow(leerAvisos())
    val avisos: StateFlow<AvisosIntercepcion> = _avisos.asStateFlow()

    fun aceptar(ahoraReloj: Long) {
        prefs.edit {
            putLong(CONSENTIMIENTO_EN, ahoraReloj)
            putInt(CONSENTIMIENTO_VERSION, VERSION_AVISO)
        }
        _consentimiento.value = Consentimiento(ahoraReloj, VERSION_AVISO)
        _config.value = leerConfig()
    }

    /** Borra el consentimiento, el pase, el progreso, vistoConectado y los diagnósticos. Deja las elecciones. */
    fun retirar() {
        prefs.edit {
            remove(CONSENTIMIENTO_EN)
            remove(CONSENTIMIENTO_VERSION)
            borrarPase()
            remove(PROGRESO)
            remove(VISTO_CONECTADO)
            remove(DIAG_NO_APARECE)
            remove(DIAG_PELEA)
            remove(DIAG_BUCLE)
        }
        _consentimiento.value = null
        _config.value = leerConfig()
        _pase.value = null
        _progreso.value = ProgresoPase()
        _avisos.value = leerAvisos()
    }

    fun cambiarRed(paquete: String, activada: Boolean? = null, modo: ModoIntercepcion? = null) {
        prefs.edit {
            activada?.let { putBoolean(ConfigIntercepcion.claveActivada(paquete), it) }
            modo?.let { putString(ConfigIntercepcion.claveModo(paquete), it.name) }
        }
        _config.value = leerConfig()
    }

    fun cambiarTarjetasPase(n: Int) {
        prefs.edit { putInt(ConfigIntercepcion.CLAVE_TARJETAS, OpcionesPase.tarjetas(n)) }
        _config.value = leerConfig()
    }

    fun cambiarMinutosPase(m: Int) {
        prefs.edit { putInt(ConfigIntercepcion.CLAVE_MINUTOS, OpcionesPase.minutos(m, debug)) }
        _config.value = leerConfig()
    }

    /** Empieza un pase y vacía el progreso (lo leído ya se usó). */
    fun iniciarPase(p: PaseGuardado) {
        prefs.edit {
            putLong(PASE_INICIADO, p.iniciadoEn)
            putLong(PASE_HASTA, p.hasta)
            putInt(PASE_ARRANQUE, p.arranque)
            putInt(PASE_MINUTOS, p.minutos)
            remove(PROGRESO)
        }
        _pase.value = p
        _progreso.value = ProgresoPase()
    }

    fun terminarPase() {
        if (_pase.value == null) return
        prefs.edit { borrarPase() }
        _pase.value = null
    }

    fun sumarAlProgreso(id: Long) {
        val nuevo = _progreso.value.con(id)
        if (nuevo == _progreso.value) return
        prefs.edit { putString(PROGRESO, nuevo.aTexto()) }
        _progreso.value = nuevo
    }

    /** "Ahora no": suma un rechazo y oculta la invitación de Inicio por 7 días. */
    fun anotarRechazo(ahoraReloj: Long) {
        val a = _avisos.value
        prefs.edit {
            putInt(RECHAZOS, a.rechazos + 1)
            putLong(INVITACION_OCULTA, ahoraReloj + OCULTAR_MS)
        }
        _avisos.value = leerAvisos()
    }

    fun ocultarEstado(hastaReloj: Long) {
        prefs.edit { putLong(ESTADO_OCULTO, hastaReloj) }
        _avisos.value = leerAvisos()
    }

    fun marcarVistoConectado() {
        if (_avisos.value.vistoConectado) return
        prefs.edit { putBoolean(VISTO_CONECTADO, true) }
        _avisos.value = leerAvisos()
    }

    /** Solo escribe si cambia (se llama cada vez que aparece la pantalla de apuntes). */
    fun marcarNoAparece(v: Boolean) {
        if (_avisos.value.noAparece == v) return
        prefs.edit { putBoolean(DIAG_NO_APARECE, v) }
        _avisos.value = leerAvisos()
    }

    fun marcarPelea(ahoraReloj: Long) {
        prefs.edit { putLong(DIAG_PELEA, ahoraReloj) }
        _avisos.value = leerAvisos()
    }

    fun marcarBucle(ahoraReloj: Long) {
        prefs.edit { putLong(DIAG_BUCLE, ahoraReloj) }
        _avisos.value = leerAvisos()
    }

    fun descartarDiagnostico() {
        prefs.edit {
            remove(DIAG_NO_APARECE)
            remove(DIAG_PELEA)
            remove(DIAG_BUCLE)
        }
        _avisos.value = leerAvisos()
    }

    private fun SharedPreferences.Editor.borrarPase() {
        remove(PASE_INICIADO)
        remove(PASE_HASTA)
        remove(PASE_ARRANQUE)
        remove(PASE_MINUTOS)
    }

    private fun leerConsentimiento(): Consentimiento? =
        if (prefs.contains(CONSENTIMIENTO_EN)) {
            Consentimiento(prefs.getLong(CONSENTIMIENTO_EN, 0), prefs.getInt(CONSENTIMIENTO_VERSION, 0))
        } else {
            null
        }

    private fun leerConfig(): ConfigIntercepcion {
        val consentida = leerConsentimiento()?.vigente() == true
        val guardado = prefs.all
        return ConfigIntercepcion.desde(consentida, { clave -> guardado[clave] }, catalogo, debug)
    }

    private fun leerPase(): PaseGuardado? =
        if (prefs.contains(PASE_HASTA)) {
            PaseGuardado(
                iniciadoEn = prefs.getLong(PASE_INICIADO, 0),
                hasta = prefs.getLong(PASE_HASTA, 0),
                arranque = prefs.getInt(PASE_ARRANQUE, -1),
                minutos = prefs.getInt(PASE_MINUTOS, 0),
            )
        } else {
            null
        }

    private fun leerAvisos() = AvisosIntercepcion(
        rechazos = prefs.getInt(RECHAZOS, 0),
        invitacionOcultaHasta = prefs.getLong(INVITACION_OCULTA, 0),
        estadoOcultoHasta = prefs.getLong(ESTADO_OCULTO, 0),
        vistoConectado = prefs.getBoolean(VISTO_CONECTADO, false),
        noAparece = prefs.getBoolean(DIAG_NO_APARECE, false),
        peleaEn = prefs.getLong(DIAG_PELEA, 0).takeIf { it > 0 },
        bucleEn = prefs.getLong(DIAG_BUCLE, 0).takeIf { it > 0 },
    )

    companion object {
        /** Cuánto se oculta la invitación (o el estado) de Inicio después de "Ahora no". */
        const val OCULTAR_MS = 7 * 24 * 60 * 60_000L

        private const val ARCHIVO = "intercepcion"
        private const val CONSENTIMIENTO_EN = "consentimiento_en"
        private const val CONSENTIMIENTO_VERSION = "consentimiento_version"
        private const val PASE_INICIADO = "pase_iniciado_en"
        private const val PASE_HASTA = "pase_hasta"
        private const val PASE_ARRANQUE = "pase_arranque"
        private const val PASE_MINUTOS = "pase_minutos_otorgados"
        private const val PROGRESO = "progreso_ids"
        private const val RECHAZOS = "aviso_rechazos"
        private const val INVITACION_OCULTA = "aviso_oculto_hasta"
        private const val ESTADO_OCULTO = "estado_oculto_hasta"
        private const val VISTO_CONECTADO = "servicio_visto_conectado"
        private const val DIAG_NO_APARECE = "diag_no_aparece"
        private const val DIAG_PELEA = "diag_pelea_en"
        private const val DIAG_BUCLE = "diag_bucle_en"
    }
}
