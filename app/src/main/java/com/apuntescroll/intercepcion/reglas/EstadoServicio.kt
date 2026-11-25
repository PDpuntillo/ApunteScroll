package com.apuntescroll.intercepcion.reglas

/** En qué está el servicio de intercepción, tal como se le explica al usuario. */
enum class EstadoServicio {
    PROTECCION_AVANZADA,
    ACTIVADO_SIN_AVISO,
    SIN_CONSENTIMIENTO,
    FALTA_ACTIVAR,
    SE_APAGO,
    CONECTANDO,
    NO_RESPONDE,
    FUNCIONANDO,
}

/**
 * Lee la lista de Android de servicios de accesibilidad activados (ENABLED_ACCESSIBILITY_SERVICES)
 * solo para buscar el propio componente. No devuelve ni guarda la lista, y no la compara con nada
 * más (PoliticaIntercepcionTest).
 */
object ServiciosHabilitados {
    /** [lista] tiene la forma "a/.B:c/c.D". Acepta la forma corta y la larga de cada clase. */
    fun contiene(lista: String?, paquete: String, clase: String): Boolean {
        if (lista.isNullOrBlank()) return false
        return lista.split(':').any { entrada ->
            val p = entrada.substringBefore('/', missingDelimiterValue = "").trim()
            val c = entrada.substringAfter('/', missingDelimiterValue = "").trim()
            p == paquete && (c == clase || (c.startsWith('.') && paquete + c == clase))
        }
    }
}

object DerivarEstado {
    /** Tiempo que se espera a que Android conecte el servicio recién activado. */
    const val MARGEN_CONEXION_MS = 3_000L

    /** [proteccionAvanzada] solo cuenta desde la API 37 (Android 17). */
    fun estado(
        consentida: Boolean,
        habilitado: Boolean,
        conectado: Boolean,
        msDesdeHabilitado: Long,
        vistoConectado: Boolean,
        proteccionAvanzada: Boolean,
        sdk: Int,
    ): EstadoServicio = when {
        !habilitado && sdk >= 37 && proteccionAvanzada -> EstadoServicio.PROTECCION_AVANZADA
        habilitado && !consentida -> EstadoServicio.ACTIVADO_SIN_AVISO
        !habilitado && !consentida -> EstadoServicio.SIN_CONSENTIMIENTO
        !habilitado && vistoConectado -> EstadoServicio.SE_APAGO
        !habilitado -> EstadoServicio.FALTA_ACTIVAR
        !conectado && msDesdeHabilitado <= MARGEN_CONEXION_MS -> EstadoServicio.CONECTANDO
        !conectado -> EstadoServicio.NO_RESPONDE
        else -> EstadoServicio.FUNCIONANDO
    }
}

/** La tarjeta de Inicio sobre la intercepción (se muestra una sola, por prioridad). */
enum class AvisoInicio { ACTIVADO_SIN_AVISO, ESTADO, NO_APARECE, PELEA, BUCLE, PASE_ACTIVO, INVITACION }

/** Los avisos de pelea y de bucle se dejan de mostrar a los 7 días. */
const val DIAS_AVISO_DIAGNOSTICO = 7
private const val MS_DIA = 24 * 60 * 60_000L

/**
 * Qué tarjeta va en Inicio. Las marcas de tiempo son de reloj de pared (solo sirven para ocultar).
 * Invitación: hasta 3 rechazos; el aviso siempre se puede abrir desde Ajustes y desde el acceso
 * mínimo de Inicio.
 */
fun elegirAvisoInicio(
    estado: EstadoServicio,
    consentida: Boolean,
    noAparece: Boolean,
    peleaEn: Long?,
    bucleEn: Long?,
    paseActivo: Boolean,
    rechazos: Int,
    invitacionOcultaHasta: Long,
    estadoOcultoHasta: Long,
    ahoraReloj: Long,
): AvisoInicio? {
    fun reciente(en: Long?) = en != null && ahoraReloj - en in 0 until DIAS_AVISO_DIAGNOSTICO * MS_DIA
    val estadoParaAvisar = when (estado) {
        EstadoServicio.SE_APAGO, EstadoServicio.NO_RESPONDE, EstadoServicio.FALTA_ACTIVAR -> true
        EstadoServicio.PROTECCION_AVANZADA -> consentida
        else -> false
    }
    return when {
        estado == EstadoServicio.ACTIVADO_SIN_AVISO -> AvisoInicio.ACTIVADO_SIN_AVISO
        estadoParaAvisar && estadoOcultoHasta < ahoraReloj -> AvisoInicio.ESTADO
        noAparece -> AvisoInicio.NO_APARECE
        reciente(peleaEn) -> AvisoInicio.PELEA
        reciente(bucleEn) -> AvisoInicio.BUCLE
        paseActivo -> AvisoInicio.PASE_ACTIVO
        estado == EstadoServicio.SIN_CONSENTIMIENTO && rechazos < RECHAZOS_PARA_NO_INVITAR &&
            invitacionOcultaHasta < ahoraReloj -> AvisoInicio.INVITACION
        else -> null
    }
}

/** Con 3 rechazos, Inicio deja de ofrecer la tarjeta (queda el acceso mínimo y Ajustes). */
const val RECHAZOS_PARA_NO_INVITAR = 3
