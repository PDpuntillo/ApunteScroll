package com.apuntescroll.intercepcion.reglas

/**
 * Clasifica el paquete que pasó a primer plano (PLAN.md, sección 7). Un error de clasificación
 * cuesta poco: un transitorio tomado como Otro agrega a lo sumo un recordatorio de más, y una app
 * tomada como transitoria hace que la visita siga.
 */
class ClasificadorPaquetes(private val propio: String, val catalogo: CatalogoRedes) {

    fun tipo(paquete: String, teclado: String?): TipoPaquete {
        catalogo.de(paquete)?.let { return TipoPaquete.Red(it) }
        return when {
            paquete == propio -> TipoPaquete.Propio
            paquete in TRANSITORIOS || paquete == teclado -> TipoPaquete.Transitorio
            else -> TipoPaquete.Otro
        }
    }

    companion object {
        /**
         * Ventanas que aparecen encima de una app sin que el usuario la deje: cortina, diálogos del
         * sistema, hoja de compartir y permisos. Los paquetes de One UI (paneles Edge, captura,
         * hoja de compartir de Samsung) se agregan después de calibrar en la S23.
         */
        val TRANSITORIOS = setOf(
            "com.android.systemui",
            "android",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        /** "pkg/.Servicio" o "pkg/pkg.Servicio" → "pkg"; null si viene vacío; sin barra, el valor entero. */
        fun paqueteDeTeclado(valor: String?): String? {
            val limpio = valor?.trim().orEmpty()
            if (limpio.isEmpty()) return null
            return limpio.substringBefore('/').ifEmpty { null }
        }
    }
}
