package com.apuntescroll.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/**
 * Reconocimiento de texto en el teléfono con ML Kit (alfabeto latino). Usa la variante que trae
 * Google Play services: el modelo se descarga una vez (lo pide el manifest al instalar) y después
 * funciona sin internet (PLAN.md, sección 3.1).
 */
class Ocr : Closeable {

    private val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Texto de [imagen]: un renglón por línea y una línea en blanco entre bloques, en el orden de
     * lectura que da ML Kit (respeta columnas). Si el modelo todavía no se descargó, falla con
     * una [com.google.mlkit.common.MlKitException] de código UNAVAILABLE.
     */
    suspend fun reconocer(imagen: Bitmap): String {
        val resultado = reconocedor.process(InputImage.fromBitmap(imagen, 0)).await()
        return resultado.textBlocks.joinToString("\n\n") { bloque ->
            bloque.lines.joinToString("\n") { it.text }
        }
    }

    override fun close() = reconocedor.close()
}
