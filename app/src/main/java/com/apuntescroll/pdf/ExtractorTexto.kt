package com.apuntescroll.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.Closeable
import java.io.File

/**
 * Lee el texto digital de un PDF con PdfBox, de a una página para no cargar todo el documento en
 * memoria. Requiere que se haya llamado a `PDFBoxResourceLoader.init` (lo hace ApunteScrollApp).
 *
 * Lanza [com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException] si el PDF pide
 * contraseña para abrirse, e [java.io.IOException] si el archivo está dañado.
 */
class ExtractorTexto(archivo: File) : Closeable {

    // Archivos temporales en vez de memoria: los apuntes pueden tener cientos de páginas.
    private val documento: PDDocument = PDDocument.load(archivo, MemoryUsageSetting.setupTempFileOnly())

    private val stripper = PDFTextStripper()

    val paginas: Int get() = documento.numberOfPages

    /** Texto de la página [numero] (empieza en 1). */
    fun texto(numero: Int): String {
        stripper.startPage = numero
        stripper.endPage = numero
        return stripper.getText(documento)
    }

    /**
     * false si la página seguro no tiene imágenes (no usa XObjects de imagen ni de formulario):
     * una portada, una página en blanco o un separador. Esas no pasan por OCR, que es lento y
     * puede fallar. Si no se puede saber, devuelve true.
     */
    fun puedeTenerImagenes(numero: Int): Boolean = try {
        val recursos = documento.getPage(numero - 1).resources
        recursos != null && recursos.xObjectNames.any { nombre ->
            recursos.isImageXObject(nombre) || recursos.getXObject(nombre) is PDFormXObject
        }
    } catch (e: Exception) {
        // IOException, o IllegalStateException / IndexOutOfBounds de un árbol de páginas roto.
        true
    }

    override fun close() = documento.close()

    companion object {
        /**
         * Una página con menos caracteres alfanuméricos que esto se considera escaneada (es una
         * imagen) y pasa por OCR (PLAN.md, sección 3.1).
         */
        const val MINIMO_CARACTERES = 40

        fun pareceEscaneada(texto: String): Boolean = texto.count { it.isLetterOrDigit() } < MINIMO_CARACTERES
    }
}
