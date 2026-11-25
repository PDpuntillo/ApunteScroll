package com.apuntescroll.intercepcion.reglas

import com.apuntescroll.feed.PielFeed

/**
 * Una red que se puede interceptar. [nombre] es el nombre propio de la app (no se traduce).
 * [dePrueba]: solo existe en debug y arranca apagada (no se intercepta sin pedirlo).
 */
data class RedConocida(
    val paquete: String,
    val nombre: String,
    val piel: PielFeed,
    val dePrueba: Boolean = false,
)

object Redes {
    val INSTAGRAM = RedConocida("com.instagram.android", "Instagram", PielFeed.CARRUSEL)
    val X = RedConocida("com.twitter.android", "X", PielFeed.HILO)

    /**
     * Lo que se declara en <queries> y en Play. Ampliar = entrada nueva + <queries> + aviso +
     * declaración de Play Console (la lista va en el código a propósito).
     */
    val CONOCIDAS = listOf(INSTAGRAM, X)

    /** Solo debug: probar sin sesión en IG y en el emulador. [verificar en la S23 el paquete de Samsung] */
    val DE_PRUEBA = listOf(
        RedConocida("com.sec.android.app.popupcalculator", "Calculadora de Samsung (prueba)", PielFeed.HILO, dePrueba = true),
        RedConocida("com.google.android.calculator", "Calculadora de Google (prueba)", PielFeed.HILO, dePrueba = true),
    )

    /** Apps de bloqueo que se pelean por la pantalla (solo para el aviso de funcionamiento). */
    val APPS_DE_BLOQUEO = setOf("com.bloqueador.android")
}

/** Las redes que esta versión intercepta (en debug, también las de prueba). */
class CatalogoRedes(val redes: List<RedConocida>) {

    private val porPaquete = redes.associateBy { it.paquete }

    val paquetes: Set<String> = porPaquete.keys

    fun de(paquete: String?): RedConocida? = paquete?.let { porPaquete[it] }
}
