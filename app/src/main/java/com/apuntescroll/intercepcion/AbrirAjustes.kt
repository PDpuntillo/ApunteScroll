package com.apuntescroll.intercepcion

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri

// Pantallas de Ajustes de Android que abre la intercepción (adaptado de ui/Componentes.kt del
// Bloqueador). Cada una devuelve false si no pudo abrir nada.

/**
 * Accesibilidad de Android. Ajustes de AOSP y Pixel resaltan el servicio con el extra; One UI
 * puede ignorarlo [verificar en la S23]. No se usa ACTION_ACCESSIBILITY_DETAILS_SETTINGS: es
 * @SystemApi y exige un permiso de firma.
 */
fun abrirAccesibilidad(context: Context): Boolean {
    val clave = ComponentName(context, ServicioIntercepcion::class.java).flattenToString()
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(":settings:fragment_args_key", clave)
        .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", clave) })
    return iniciar(context, intent) || iniciar(context, Intent(Settings.ACTION_SETTINGS))
}

/** La lista de optimización de batería (sin pedir el permiso que Google Play restringe). */
fun abrirBateria(context: Context): Boolean =
    iniciar(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) || abrirAjustesDeApp(context)

/**
 * Xiaomi (MIUI / HyperOS): su editor de permisos, donde está «Mostrar ventanas emergentes en
 * segundo plano». Si este teléfono no lo tiene, la ficha de la app.
 */
fun abrirPermisosXiaomi(context: Context): Boolean {
    val editor = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", context.packageName)
    return iniciar(context, editor) || abrirAjustesDeApp(context)
}

/** La ficha de ApunteScroll en Ajustes > Aplicaciones. Si no, Ajustes generales. */
fun abrirAjustesDeApp(context: Context): Boolean {
    val ficha = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    return iniciar(context, ficha) || iniciar(context, Intent(Settings.ACTION_SETTINGS))
}

fun abrirUrl(context: Context, url: String): Boolean = iniciar(context, Intent(Intent.ACTION_VIEW, url.toUri()))

private fun marca(): String = Build.MANUFACTURER.orEmpty().lowercase()

/** Marcas que suelen cerrar las apps en segundo plano (y con ellas el servicio). */
fun marcaQueCierraApps(): Boolean =
    marca() in setOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "vivo")

fun esXiaomi(): Boolean = marca() in setOf("xiaomi", "redmi", "poco")

/** Si la red está instalada. Funciona solo con las de <queries> (sin QUERY_ALL_PACKAGES). */
fun redInstalada(context: Context, paquete: String): Boolean = try {
    val pm = context.packageManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(paquete, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(paquete, 0)
    }
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/**
 * true si la instaló Google Play. null si no se puede saber (antes de la API 30). Sirve para la
 * pista de "ajustes restringidos", que Android aplica a las apps instaladas a mano.
 */
fun instaladaDesdePlay(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName == "com.android.vending"
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** Abre [intent] desde la actividad (o en una tarea nueva si no hay). Devuelve false si falló. */
private fun iniciar(context: Context, intent: Intent): Boolean {
    val actividad = context.buscarActividad()
    if (actividad == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        (actividad ?: context).startActivity(intent)
        true
    } catch (e: RuntimeException) {
        false
    }
}

private fun Context.buscarActividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
