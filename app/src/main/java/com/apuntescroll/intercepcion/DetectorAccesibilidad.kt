package com.apuntescroll.intercepcion

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.security.advancedprotection.AdvancedProtectionManager
import androidx.annotation.MainThread
import com.apuntescroll.datos.AjustesIntercepcion
import com.apuntescroll.intercepcion.reglas.DerivarEstado
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import com.apuntescroll.intercepcion.reglas.ServiciosHabilitados
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * [DetectorDeApps] con el servicio de accesibilidad (ServicioIntercepcion). Sabe si Android lo
 * tiene activado (ContentObserver sobre ENABLED_ACCESSIBILITY_SERVICES, de la API 26 a la 37) y si
 * está conectado, y le pasa al receptor cada paquete que llega. Todo en el hilo principal.
 */
class DetectorAccesibilidad(
    private val app: Application,
    private val ajustes: AjustesIntercepcion,
) : DetectorDeApps {

    private val principal = Handler(Looper.getMainLooper())
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var receptor: ReceptorPrimerPlano? = null
    private var servicio: ServicioIntercepcion? = null
    private var habilitadoAhora = habilitado()
    private var habilitadoDesde = SystemClock.elapsedRealtime()
    private var proteccion = proteccionAvanzada()

    private val recalcularLuego = Runnable { recalcular() }

    private val _estado = MutableStateFlow(calcular())
    override val estado: StateFlow<EstadoServicio> = _estado.asStateFlow()

    private val observador = object : ContentObserver(principal) {
        override fun onChange(selfChange: Boolean) = refrescar()
    }

    init {
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observador,
        )
        alcance.launch {
            combine(ajustes.consentimiento, ajustes.avisos) { _, _ -> }.collect { recalcular() }
        }
    }

    /**
     * Busca el propio servicio en la lista de Android. La lista no se guarda, no se registra y no
     * se compara con nada más.
     */
    override fun habilitado(): Boolean = ServiciosHabilitados.contiene(
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        paquete = app.packageName,
        clase = ServicioIntercepcion::class.java.name,
    )

    /** Android 17: con la Protección avanzada, Android apaga la accesibilidad de las apps que no son herramientas. */
    private fun proteccionAvanzada(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return try {
            app.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
        } catch (e: RuntimeException) {
            // SecurityException si faltara el permiso.
            false
        }
    }

    override fun alConectar(receptor: ReceptorPrimerPlano) {
        this.receptor = receptor
        servicio?.let { receptor.conectado(it) }
    }

    @MainThread
    override fun refrescar() {
        val nuevo = habilitado()
        if (nuevo && !habilitadoAhora) habilitadoDesde = SystemClock.elapsedRealtime()
        habilitadoAhora = nuevo
        proteccion = proteccionAvanzada()
        recalcular()
        // CONECTANDO → NO_RESPONDE: recalcular pasado el margen.
        principal.removeCallbacks(recalcularLuego)
        principal.postDelayed(recalcularLuego, DerivarEstado.MARGEN_CONEXION_MS + 100)
    }

    override fun abrirActivacion(context: Context): Boolean = abrirAccesibilidad(context)

    /** Lo llama ServicioIntercepcion.onServiceConnected. */
    fun servicioConectado(s: ServicioIntercepcion) {
        servicio = s
        habilitadoAhora = true
        receptor?.conectado(s)
        recalcular()
    }

    /** Un paquete pasó a primer plano (ya se comprobó el consentimiento). */
    fun servicioVio(paquete: String) {
        receptor?.alPrimerPlano(paquete)
    }

    /** Lo llama ServicioIntercepcion al desvincularse. Idempotente. */
    fun servicioDesconectado(s: ServicioIntercepcion) {
        if (servicio !== s) return
        servicio = null
        receptor?.desconectado(s)
        habilitadoAhora = habilitado()
        recalcular()
    }

    private fun recalcular() {
        _estado.value = calcular()
    }

    private fun calcular(): EstadoServicio = DerivarEstado.estado(
        consentida = ajustes.consentimientoVigente(),
        habilitado = habilitadoAhora,
        conectado = servicio != null,
        msDesdeHabilitado = SystemClock.elapsedRealtime() - habilitadoDesde,
        vistoConectado = ajustes.avisos.value.vistoConectado,
        proteccionAvanzada = proteccion,
        sdk = Build.VERSION.SDK_INT,
    )
}
