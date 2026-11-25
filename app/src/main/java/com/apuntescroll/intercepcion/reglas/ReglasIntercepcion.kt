package com.apuntescroll.intercepcion.reglas

/** Si hay que tapar una red que pasó a primer plano. Las reglas van en este orden. */
object ReglasIntercepcion {

    /**
     * [redEnFeed]: la red de la pantalla de apuntes que se está viendo ahora (null si no se ve).
     * [entorno] se lee solo si ninguna regla anterior decidió (regla 7).
     */
    fun decidir(
        red: RedConocida,
        config: ConfigIntercepcion,
        paseVigente: Boolean,
        visita: Visita?,
        redEnFeed: String?,
        entorno: () -> Entorno,
    ): Decision {
        if (!config.consentida) return Decision.Dejar(Razon.SIN_CONSENTIMIENTO)
        val elegido = config.de(red)
        if (!elegido.activada) return Decision.Dejar(Razon.RED_APAGADA)
        val visitaDeEsta = visita?.takeIf { it.red == red.paquete }
        if (visitaDeEsta?.porLlamada == true) return Decision.Dejar(Razon.VISITA_LLAMADA)
        if (elegido.modo == ModoIntercepcion.RECORDATORIO && visitaDeEsta != null) {
            return Decision.Dejar(Razon.VISITA_PERMITIDA)
        }
        if (elegido.modo == ModoIntercepcion.PASE && paseVigente) return Decision.Dejar(Razon.PASE_VIGENTE)
        if (redEnFeed == red.paquete) return Decision.Dejar(Razon.FEED_A_LA_VISTA)
        val e = entorno()
        return when {
            !e.pantallaEnUso -> Decision.Dejar(Razon.PANTALLA_NO_EN_USO)
            e.appFijada -> Decision.Dejar(Razon.APP_FIJADA)
            e.enLlamada -> Decision.Dejar(Razon.EN_LLAMADA)
            else -> Decision.Interceptar(red)
        }
    }
}
