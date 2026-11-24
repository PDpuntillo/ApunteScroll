package com.apuntescroll.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copia de cada PDF importado, en la carpeta privada de la app. Así el apunte sigue disponible
 * aunque el usuario borre o mueva el original (PLAN.md, sección 9).
 */
class ArchivosPdf(private val context: Context) {

    private val carpeta: File
        get() = File(context.filesDir, CARPETA).apply { mkdirs() }

    fun archivo(nombre: String): File = File(carpeta, nombre)

    /** Copia el PDF de [uri] y devuelve el nombre del archivo nuevo. */
    suspend fun copiar(uri: Uri): String = withContext(Dispatchers.IO) {
        val nombre = "${UUID.randomUUID()}.pdf"
        val destino = archivo(nombre)
        try {
            val entrada = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo elegido")
            entrada.use { origen ->
                destino.outputStream().use { salida ->
                    // Copia por bloques, revisando si se canceló (desde una nube puede tardar).
                    val bloque = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val leidos = origen.read(bloque)
                        if (leidos < 0) break
                        salida.write(bloque, 0, leidos)
                    }
                }
            }
        } catch (e: Exception) {
            destino.delete()
            throw e
        }
        nombre
    }

    /** Nombre que muestra el selector de archivos, sin ".pdf". */
    suspend fun nombreVisible(uri: Uri): String = withContext(Dispatchers.IO) {
        val nombre = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        nombre?.removeSuffix(".pdf")?.removeSuffix(".PDF")?.trim()?.takeIf { it.isNotEmpty() } ?: "Apunte sin nombre"
    }

    suspend fun borrar(nombre: String) {
        withContext(Dispatchers.IO) { archivo(nombre).delete() }
    }

    /** Borra las copias que no figuran en [enUso] y tienen más de [antiguedadMs] (huérfanas). */
    suspend fun borrarHuerfanos(enUso: Set<String>, antiguedadMs: Long) = withContext(Dispatchers.IO) {
        val limite = System.currentTimeMillis() - antiguedadMs
        carpeta.listFiles()
            ?.filter { it.isFile && it.name !in enUso && it.lastModified() < limite }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val CARPETA = "pdfs"
    }
}
