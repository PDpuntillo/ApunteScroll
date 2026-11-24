package com.apuntescroll

import android.app.Application
import com.apuntescroll.datos.Ajustes

/**
 * Punto de entrada de la app. Arma el [Contenedor] con las dependencias compartidas
 * (inyección manual, como en el Bloqueador).
 */
class ApunteScrollApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
    }
}

/** Dependencias que viven mientras vive el proceso. Crece fase a fase (base de datos en F1, etc.). */
class Contenedor(app: Application) {
    val ajustes = Ajustes(app)
}
