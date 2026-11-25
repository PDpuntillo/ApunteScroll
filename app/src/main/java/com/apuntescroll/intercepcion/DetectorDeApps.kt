package com.apuntescroll.intercepcion

import android.content.Context
import android.content.Intent
import com.apuntescroll.intercepcion.reglas.EstadoServicio
import kotlinx.coroutines.flow.StateFlow

/**
 * De dónde sale "qué app pasó a primer plano" (PLAN.md, secciones 7 y 10.3). Hoy, el servicio de
 * accesibilidad (DetectorAccesibilidad); el plan B (UsageStats + superposición) sería otra
 * implementación de esta interfaz y de [LanzadorPantalla], sin tocar el coordinador ni la UI.
 */
interface DetectorDeApps {
    val estado: StateFlow<EstadoServicio>

    /** El detector le avisa a [receptor] cada paquete que pasa al frente y le entrega quién puede abrir pantallas. */
    fun alConectar(receptor: ReceptorPrimerPlano)

    /** Vuelve a leer si está activado (al volver de Ajustes de Android: MainActivity.onResume). */
    fun refrescar()

    /** Abre la pantalla de Android donde se activa. false si no pudo abrir ninguna. */
    fun abrirActivacion(context: Context): Boolean

    fun habilitado(): Boolean
}

/** Quien usa lo que detecta el detector (CoordinadorIntercepcion). Todo en el hilo principal. */
interface ReceptorPrimerPlano {
    fun conectado(lanzador: LanzadorPantalla)
    fun alPrimerPlano(paquete: String)
    fun desconectado(lanzador: LanzadorPantalla)
}

/** Quien tiene permiso de abrir pantallas desde segundo plano. Hoy, el servicio de accesibilidad. */
interface LanzadorPantalla {
    fun abrir(intent: Intent): Boolean

    /** Se apaga solo (a pedido del usuario). */
    fun apagar()
}
