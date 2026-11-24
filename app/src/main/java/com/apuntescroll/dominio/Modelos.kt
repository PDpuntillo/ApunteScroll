package com.apuntescroll.dominio

/** Texto crudo de una página, tal como sale de PdfBox o del OCR. [numero] empieza en 1. */
data class PaginaExtraida(val numero: Int, val texto: String)

/** Una tarjeta del feed: su texto (ya limpio) y la página donde empieza. */
data class TarjetaArmada(val texto: String, val pagina: Int)

/** [titulo] es null cuando el documento no tiene títulos detectables; la UI muestra "Página N". */
data class SeccionArmada(val titulo: String?, val paginaInicio: Int, val tarjetas: List<TarjetaArmada>)

/** Resultado de armar un documento: sus secciones, en el orden del PDF. */
data class DocumentoArmado(val secciones: List<SeccionArmada>)
