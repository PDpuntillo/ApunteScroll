package com.apuntescroll.pdf

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.apuntescroll.ApunteScrollApp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Procesa un PDF en segundo plano. Es trabajo común de WorkManager, sin servicio en primer plano
 * (cada tipo de servicio en primer plano exige una declaración en Play Console). Como WorkManager
 * corta un trabajo a los 10 minutos, cada pasada usa como mucho [PRESUPUESTO_MS] y, si falta,
 * encola la siguiente (PLAN.md, sección 3.1).
 */
class ProcesarDocumentoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val documentoId = inputData.getLong(CLAVE_DOCUMENTO, -1L)
        if (documentoId < 0) return Result.failure()

        val contenedor = (applicationContext as ApunteScrollApp).contenedor
        val paso = try {
            contenedor.procesador.procesar(documentoId, PRESUPUESTO_MS, runAttemptCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Cualquier cosa que se escape (por ejemplo, un StackOverflowError de PdfBox con un
            // PDF malformado) deja el documento en ERROR, para que no quede "procesando" para
            // siempre y se pueda reintentar o borrar.
            Log.e("ProcesarDocumentoWorker", "Falla inesperada con el documento $documentoId", e)
            runCatching { contenedor.procesador.fallar(documentoId, "Ocurrió un error al procesar el PDF.") }
            return Result.failure()
        }
        return when (paso) {
            Paso.TERMINADO -> Result.success()
            Paso.SEGUIR_DESPUES -> {
                contenedor.programador.continuar(documentoId)
                Result.success()
            }
            Paso.REINTENTAR -> Result.retry()
        }
    }

    companion object {
        const val CLAVE_DOCUMENTO = "documentoId"
        const val PRESUPUESTO_MS = 8 * 60 * 1000L
    }
}

/** Encola y cancela el procesamiento de cada documento (un trabajo único por documento). */
class ProgramadorProcesamiento(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /** Empieza a procesar [documentoId], salvo que ya esté encolado. */
    fun programar(documentoId: Long) {
        workManager.enqueueUniqueWork(nombre(documentoId), ExistingWorkPolicy.KEEP, solicitud(documentoId))
    }

    /** Encola la próxima pasada detrás de la actual. */
    fun continuar(documentoId: Long) {
        workManager.enqueueUniqueWork(nombre(documentoId), ExistingWorkPolicy.APPEND_OR_REPLACE, solicitud(documentoId))
    }

    fun cancelar(documentoId: Long) {
        workManager.cancelUniqueWork(nombre(documentoId))
    }

    private fun solicitud(documentoId: Long) =
        OneTimeWorkRequestBuilder<ProcesarDocumentoWorker>()
            .setInputData(workDataOf(ProcesarDocumentoWorker.CLAVE_DOCUMENTO to documentoId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()

    private fun nombre(documentoId: Long) = "procesar-documento-$documentoId"
}
