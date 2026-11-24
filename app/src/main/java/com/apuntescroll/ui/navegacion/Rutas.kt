package com.apuntescroll.ui.navegacion

import kotlinx.serialization.Serializable

// Rutas de Navigation Compose con tipos. Cada sección de la barra de abajo es una ruta o un
// grafo anidado; así la barra marca la sección correcta también en las pantallas de detalle.

@Serializable
data object GrafoInicio

@Serializable
data object RutaInicio

/** El hilo completo de una sección, abierto en la tarjeta [tarjetaId]. */
@Serializable
data class RutaHilo(val seccionId: Long, val tarjetaId: Long)

@Serializable
data object GrafoBiblioteca

@Serializable
data object RutaBiblioteca

@Serializable
data class RutaMateria(val materiaId: Long)

@Serializable
data class RutaDocumento(val documentoId: Long)

/** [pagina] empieza en 1. */
@Serializable
data class RutaPagina(val documentoId: Long, val pagina: Int)

@Serializable
data object RutaPerfil

@Serializable
data object RutaAjustes
