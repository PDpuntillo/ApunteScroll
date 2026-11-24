package com.apuntescroll.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apuntescroll.ApunteScrollApp
import com.apuntescroll.ui.tema.ApunteScrollTema

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dibuja de borde a borde en todas las versiones (Android lo fuerza solo desde la 15) y
        // ajusta el color de los íconos de las barras del sistema según el tema. No sacarlo.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val contenedor = (application as ApunteScrollApp).contenedor
        setContent {
            ApunteScrollTema {
                AppRaiz(ajustes = contenedor.ajustes)
            }
        }
    }
}
