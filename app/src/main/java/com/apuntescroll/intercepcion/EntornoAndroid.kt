package com.apuntescroll.intercepcion

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import com.apuntescroll.intercepcion.reglas.Entorno

/** Lo que el motor necesita saber del teléfono, leído en el momento (ReglasIntercepcion, regla 7). */
class EntornoAndroid(context: Context) {

    private val app = context.applicationContext
    private val power = app.getSystemService(PowerManager::class.java)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private val actividades = app.getSystemService(ActivityManager::class.java)
    private val audio = app.getSystemService(AudioManager::class.java)

    /** Si algo falla, devuelve lo permisivo: pantalla en uso, sin fijar, sin llamada. */
    fun leer(): Entorno = Entorno(
        pantallaEnUso = intentar(true) { power.isInteractive && !keyguard.isKeyguardLocked },
        appFijada = intentar(false) { actividades.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE },
        // 1 a 6: RINGTONE, IN_CALL, IN_COMMUNICATION, CALL_SCREENING, CALL_REDIRECT y
        // COMMUNICATION_REDIRECT. [verificar en la S23 con una llamada y una videollamada de IG]
        enLlamada = intentar(false) { audio.mode in 1..6 },
    )

    private val arranqueLeido: Int by lazy {
        try {
            Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: RuntimeException) {
            -1
        }
    }

    /** Settings.Global.BOOT_COUNT, leído una vez por proceso; -1 si no se pudo. */
    fun arranque(): Int = arranqueLeido

    private inline fun intentar(siFalla: Boolean, leer: () -> Boolean): Boolean = try {
        leer()
    } catch (e: RuntimeException) {
        siFalla
    }
}
