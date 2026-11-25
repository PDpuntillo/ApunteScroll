package com.apuntescroll.intercepcion

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.apuntescroll.ApunteScrollApp

/**
 * Avisa qué app pasó a primer plano (solo el nombre del paquete) y abre FeedInterceptadoActivity.
 * Nunca lee texto, clases, ventanas ni nodos del evento, y no hace gestos ni acciones globales.
 * onBind es final en AccessibilityService: la vinculación (y con ella el permiso de abrir pantallas
 * desde segundo plano) la maneja Android.
 *
 * El nombre de la clase NO se cambia después de publicar: Android guarda el servicio activado por
 * su nombre, y renombrarlo lo apagaría en todos los teléfonos (PoliticaIntercepcionTest).
 */
class ServicioIntercepcion : AccessibilityService(), LanzadorPantalla {

    private val contenedor get() = (application as ApunteScrollApp).contenedor

    override fun onServiceConnected() {
        super.onServiceConnected()
        contenedor.detectorAccesibilidad.servicioConectado(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Sin el consentimiento de la versión actual del aviso no se usa nada del evento, aunque
        // Android tenga el servicio activado (PoliticaIntercepcionTest mira este orden).
        if (!contenedor.ajustesIntercepcion.consentimientoVigente()) return
        // Del evento se usa SOLO el nombre del paquete. Nada de su texto, clase, fuente ni de su
        // representación como texto: el título de la ventana puede venir adentro.
        val paquete = event.packageName?.toString()
        if (paquete.isNullOrEmpty()) return
        try {
            contenedor.detectorAccesibilidad.servicioVio(paquete)
        } catch (e: RuntimeException) {
            // Un error puntual no debe detener el servicio.
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        contenedor.detectorAccesibilidad.servicioDesconectado(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        contenedor.detectorAccesibilidad.servicioDesconectado(this)
        super.onDestroy()
    }

    /** Los servicios de accesibilidad vinculados pueden abrir pantallas desde segundo plano. */
    override fun abrir(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: RuntimeException) {
        false
    }

    /** Solo a pedido del usuario ("Ahora no" con el servicio activado, o "Apagar y retirar el permiso"). */
    override fun apagar() = disableSelf()
}
