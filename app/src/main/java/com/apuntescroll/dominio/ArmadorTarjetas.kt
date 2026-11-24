package com.apuntescroll.dominio

/**
 * Convierte el texto de las páginas de un PDF en secciones con tarjetas (PLAN.md, secciones 3.2 y
 * 3.3). Es Kotlin puro y determinista: el mismo texto da siempre las mismas tarjetas.
 *
 * Pasos:
 * 1. [Limpieza]: normaliza caracteres (letras espaciadas y errores del OCR, con
 *    [Correcciones]), quita encabezados, pies y números de página; [Indices] quita las tablas de
 *    contenido; y se limpian viñetas, renglones de índice y líneas de puro símbolo.
 * 2. [Parrafos]: une las líneas en párrafos, también a través de los cambios de página, y une
 *    las palabras cortadas con guion.
 * 3. [Estructura]: decide qué párrafos son títulos, cuáles son ítems de lista y cuáles cuerpo.
 * 4. Secciones: cada título abre una sección; lo que está antes del primer título va en una
 *    sección sin título. Si el mismo título aparece dos veces seguidas, la sección sigue. Si el
 *    documento no tiene ningún título, hay una sección por página (cada párrafo va en la página
 *    donde empieza). Nunca hay secciones vacías.
 * 5. [Cortador]: corta cada sección en tarjetas de hasta `maxCaracteres`.
 */
object ArmadorTarjetas {

    const val MAX_PREDETERMINADO = 450

    /**
     * Versión del armado. Hay que subirla con cada cambio en dominio/ que cambie las tarjetas que
     * salen del mismo texto: al abrir la app, los PDFs armados con una versión anterior se rearman
     * (ProcesadorDocumentos.rearmarDesactualizados).
     */
    const val VERSION = 1

    /** Un máximo más chico que este se sube a este valor. */
    const val MAX_MINIMO = 100

    fun armar(paginas: List<PaginaExtraida>, maxCaracteres: Int = MAX_PREDETERMINADO): DocumentoArmado {
        val max = maxOf(MAX_MINIMO, maxCaracteres)
        val ordenadas = paginas.sortedBy { it.numero }
        val normalizadas = ordenadas.map { Limpieza.normalizarLineas(it.texto) }
        val sinBordes = Limpieza.quitarEncabezadosYPies(normalizadas, ordenadas.map { it.numero })
        val sinIndices = Indices.quitar(sinBordes)
        val limpias = ordenadas.zip(sinIndices) { pagina, lineas -> PaginaLimpia(pagina.numero, lineas.map(Limpieza::limpiarLinea)) }
        val bloques = Estructura.clasificar(Parrafos.armar(limpias))

        val secciones = agrupar(bloques).mapNotNull { borrador ->
            val tarjetas = Cortador.cortar(borrador.bloques, max)
            if (tarjetas.isEmpty()) null else SeccionArmada(borrador.titulo, borrador.pagina, tarjetas)
        }
        return DocumentoArmado(secciones)
    }

    private class BorradorSeccion(val titulo: String?, val pagina: Int) {
        val bloques = mutableListOf<Bloque>()
    }

    private fun agrupar(bloques: List<Bloque>): List<BorradorSeccion> {
        val secciones = mutableListOf<BorradorSeccion>()
        if (bloques.none { it is Bloque.Titulo }) {
            for (bloque in bloques) {
                val ultima = secciones.lastOrNull()
                if (ultima == null || ultima.pagina != bloque.pagina) secciones.add(BorradorSeccion(null, bloque.pagina))
                secciones.last().bloques.add(bloque)
            }
            return secciones
        }
        for (bloque in bloques) {
            if (bloque is Bloque.Titulo) {
                // El mismo título otra vez (un encabezado que no se detectó como tal): sigue la sección.
                if (secciones.lastOrNull()?.titulo != bloque.texto) secciones.add(BorradorSeccion(bloque.texto, bloque.pagina))
            } else {
                if (secciones.isEmpty()) secciones.add(BorradorSeccion(null, bloque.pagina))
                secciones.last().bloques.add(bloque)
            }
        }
        return secciones
    }
}
