package com.apuntescroll.feed

import com.apuntescroll.datos.bd.ColorMateria

/**
 * Una tarjeta con todo lo que el feed necesita para mostrarla (resultado de una consulta de Room
 * que junta tarjeta, sección, documento y materia).
 */
data class TarjetaFeed(
    val id: Long,
    val texto: String,
    val pagina: Int,
    val ordenTarjeta: Int,
    val caja: Int,
    val vecesVista: Int,
    val ultimaVista: Long?,
    val guardada: Boolean,
    val documentoId: Long,
    val tituloDocumento: String,
    val seccionId: Long,
    val ordenSeccion: Int,
    val tituloSeccion: String?,
    val paginaInicioSeccion: Int,
    val materiaId: Long,
    val nombreMateria: String,
    val emoji: String,
    val color: ColorMateria,
) {
    /** "Lo sé": en F2 alcanza con que haya subido de la caja 0 (PLAN.md, sección 5). */
    val sabida: Boolean get() = caja > 0
    val nueva: Boolean get() = vecesVista == 0
}

/** Qué tarjetas muestra el feed. */
sealed interface FiltroFeed {
    data object Todas : FiltroFeed
    data class DeMateria(val materiaId: Long) : FiltroFeed
    data object Guardadas : FiltroFeed
}

/** Estilo del feed (PLAN.md, sección 4). */
enum class PielFeed { HILO, CARRUSEL }

/**
 * Una publicación del feed.
 * - En Hilo: una tarjeta ([tarjetas] tiene una sola).
 * - En Carrusel: un tramo de hasta [ArmadorFeed.MAX_DIAPOSITIVAS] tarjetas de una sección, seguidas
 *   entre las que están en el feed.
 */
data class PostFeed(
    val clave: String,
    val tarjetas: List<TarjetaFeed>,
    /** El feed entra acá en la sección (la publicación anterior es de otra): se muestra su título. */
    val conTitulo: Boolean,
    /** Tarjetas de la sección completa (también las que ya se saben y no están en el feed). */
    val totalSeccion: Int,
) {
    val primera: TarjetaFeed get() = tarjetas.first()
}
