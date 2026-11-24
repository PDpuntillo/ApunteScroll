package com.apuntescroll.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dibuja páginas de un PDF como imagen con el [PdfRenderer] de Android. Se usa para el OCR de las
 * páginas escaneadas y para mostrar la página original de una tarjeta.
 *
 * No es seguro para varios hilos: se usa desde una sola corrutina a la vez.
 */
class RenderizadorPdf(archivo: File) : Closeable {

    private val descriptor = ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }

    val paginas: Int get() = renderer.pageCount

    /**
     * Imagen de la página [numero] (empieza en 1), de hasta [anchoMax] píxeles de ancho y con
     * fondo blanco (PdfRenderer deja transparente lo que no tiene dibujo, y el OCR lo leería como
     * negro). Una página muy alta se achica para no pasar de [MAX_PIXELES]: más allá, Android no
     * puede dibujar la imagen en pantalla y la memoria se dispara.
     */
    fun dibujar(numero: Int, anchoMax: Int): Bitmap {
        renderer.openPage(numero - 1).use { pagina ->
            val porAncho = anchoMax.toFloat() / pagina.width
            val porPixeles = sqrt(MAX_PIXELES.toDouble() / (pagina.width.toDouble() * pagina.height)).toFloat()
            val escala = min(porAncho, porPixeles)
            val ancho = (pagina.width * escala).roundToInt().coerceAtLeast(1)
            val alto = (pagina.height * escala).roundToInt().coerceAtLeast(1)
            val imagen = createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
            imagen.eraseColor(Color.WHITE)
            pagina.render(imagen, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return imagen
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        /** Ancho para el OCR: suficiente para letra chica sin gastar demasiada memoria. */
        const val ANCHO_OCR = 2000

        /** 16 millones de píxeles = 64 MB en ARGB_8888, lejos del límite de 100 MB al dibujar. */
        const val MAX_PIXELES = 16_000_000
    }
}
