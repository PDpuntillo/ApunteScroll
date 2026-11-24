package com.apuntescroll.datos

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Largo máximo de cada tarjeta, en caracteres (PLAN.md, sección 3.3). */
enum class LargoTarjeta(val maxCaracteres: Int) {
    CORTA(300),
    MEDIA(450),
    LARGA(600);

    companion object {
        val PREDETERMINADO = MEDIA

        fun desdeNombre(nombre: String?): LargoTarjeta =
            entries.firstOrNull { it.name == nombre } ?: PREDETERMINADO
    }
}

/** Ajustes del usuario, guardados en SharedPreferences y expuestos como flujos para Compose. */
class Ajustes(context: Context) {

    private val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _largoTarjeta = MutableStateFlow(LargoTarjeta.desdeNombre(prefs.getString(CLAVE_LARGO, null)))
    val largoTarjeta: StateFlow<LargoTarjeta> = _largoTarjeta.asStateFlow()

    fun cambiarLargoTarjeta(largo: LargoTarjeta) {
        prefs.edit { putString(CLAVE_LARGO, largo.name) }
        _largoTarjeta.value = largo
    }

    private companion object {
        const val ARCHIVO = "ajustes"
        const val CLAVE_LARGO = "largo_tarjeta"
    }
}
