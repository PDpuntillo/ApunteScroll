package com.apuntescroll.intercepcion

import android.util.Log
import com.apuntescroll.BuildConfig

/**
 * Único lugar con Log en intercepcion/: solo en debug, para calibrar en la S23 (PoliticaIntercepcionTest).
 * Nunca el evento de accesibilidad: a lo sumo el nombre del paquete y lo que decidió el motor. En
 * release la lambda no se llega a evaluar.
 */
internal object RegistroDepuracion {
    inline fun anotar(mensaje: () -> String) {
        if (BuildConfig.DEBUG) Log.d("Intercepcion", mensaje())
    }
}
