package com.apuntescroll.feed

/**
 * Arma el orden del feed (F2; el repaso espaciado completo llega en F4, PLAN.md sección 5).
 *
 * 1. **Nuevas** (nunca vistas, no sabidas), en orden de lectura: documento (en el orden en que se
 *    importaron; los de una misma importación, en el orden en que se eligieron), sección y tarjeta. Con varias materias se alterna de a una sección por materia, para que el
 *    feed no sea una sola materia seguida.
 * 2. **Vistas que todavía no se saben**, la que se vio hace más tiempo primero.
 * 3. Las **sabidas** ("lo sé") no aparecen, salvo en el filtro de guardadas.
 *
 * El filtro de guardadas muestra todas las guardadas (sabidas o no) en orden de lectura.
 */
object ArmadorFeed {

    /**
     * Tarjetas que quedan por estudiar para la meta del pase (F3): las no sabidas, sin contar las
     * de [excluir] (las que ya están en el progreso). Las que se marcaron "lo sé" en esta sesión
     * ([marcadasEnSesion]) siguen contando: si no, marcar sin leer bajaría la meta.
     */
    fun disponibles(tarjetas: List<TarjetaFeed>, marcadasEnSesion: Set<Long>, excluir: Set<Long>): Int =
        tarjetas.count { (!it.sabida || it.id in marcadasEnSesion) && it.id !in excluir }

    /** Máximo de diapositivas por publicación del Carrusel (como Instagram). */
    const val MAX_DIAPOSITIVAS = 10

    fun ordenar(tarjetas: List<TarjetaFeed>, filtro: FiltroFeed): List<TarjetaFeed> {
        val candidatas = when (filtro) {
            FiltroFeed.Todas -> tarjetas
            is FiltroFeed.DeMateria -> tarjetas.filter { it.materiaId == filtro.materiaId }
            FiltroFeed.Guardadas -> return tarjetas.filter { it.guardada }.sortedWith(ORDEN_LECTURA)
        }
        val nuevas = candidatas.filter { it.nueva && !it.sabida }
        val vistas = candidatas.filter { !it.nueva && !it.sabida }
            .sortedWith(compareBy<TarjetaFeed> { it.ultimaVista ?: 0L }.then(ORDEN_LECTURA))
        return alternarMaterias(nuevas) + vistas
    }

    /**
     * Agrupa el orden en publicaciones según la piel. [totalPorSeccion] es la cantidad de tarjetas
     * de cada sección completa (con las que ya se saben), para el "3/12" que abre el hilo.
     */
    fun publicaciones(orden: List<TarjetaFeed>, piel: PielFeed, totalPorSeccion: Map<Long, Int>): List<PostFeed> {
        // Posición (desde 1) de cada tarjeta entre las de su sección que están en el feed, en orden
        // de lectura: decide qué tarjetas van juntas en un carrusel.
        val posicion = HashMap<Long, Int>()
        orden.sortedWith(ORDEN_LECTURA).groupBy { it.seccionId }.values.forEach { lista ->
            lista.forEachIndexed { i, t -> posicion[t.id] = i + 1 }
        }
        // El título de la sección va donde el feed entra en ella (la tarjeta anterior es de otra).
        val entraEnSeccion = HashSet<Long>()
        orden.forEachIndexed { i, t -> if (i == 0 || orden[i - 1].seccionId != t.seccionId) entraEnSeccion += t.id }
        fun total(t: TarjetaFeed) = maxOf(totalPorSeccion[t.seccionId] ?: 0, t.ordenTarjeta + 1)

        return when (piel) {
            PielFeed.HILO -> orden.map { t ->
                PostFeed(
                    clave = "t${t.id}",
                    tarjetas = listOf(t),
                    conTitulo = t.id in entraEnSeccion,
                    totalSeccion = total(t),
                )
            }
            PielFeed.CARRUSEL -> {
                val posts = mutableListOf<PostFeed>()
                var actual = mutableListOf<TarjetaFeed>()
                fun cerrar() {
                    if (actual.isEmpty()) return
                    val primera = actual.first()
                    posts += PostFeed(
                        // La clave cambia si cambian las diapositivas: así el carrusel no conserva
                        // una página que ya no existe (ver PielCarrusel).
                        clave = "c${primera.id}-${actual.size}-${actual.last().id}",
                        tarjetas = actual,
                        conTitulo = primera.id in entraEnSeccion,
                        totalSeccion = total(primera),
                    )
                    actual = mutableListOf()
                }
                for (t in orden) {
                    val anterior = actual.lastOrNull()
                    val sigue = anterior != null && anterior.seccionId == t.seccionId &&
                        posicion.getValue(t.id) == posicion.getValue(anterior.id) + 1
                    if (!sigue || actual.size == MAX_DIAPOSITIVAS) cerrar()
                    actual += t
                }
                cerrar()
                posts
            }
        }
    }

    /**
     * Intercala materias de a una sección: toma la próxima sección (completa, en orden) de cada
     * materia por turnos. Las materias se recorren en el orden en que aparece su primer documento.
     */
    private fun alternarMaterias(nuevas: List<TarjetaFeed>): List<TarjetaFeed> {
        val porMateria = nuevas.sortedWith(ORDEN_LECTURA)
            .groupBy { it.materiaId }
            .mapValues { (_, lista) -> ArrayDeque(lista.groupBy { it.seccionId }.values.toList()) }
        if (porMateria.size <= 1) return porMateria.values.flatMap { cola -> cola.flatten() }

        val resultado = ArrayList<TarjetaFeed>(nuevas.size)
        val colas = porMateria.values.toMutableList()
        while (colas.isNotEmpty()) {
            val iterador = colas.iterator()
            while (iterador.hasNext()) {
                val cola = iterador.next()
                resultado += cola.removeFirst()
                if (cola.isEmpty()) iterador.remove()
            }
        }
        return resultado
    }

    /**
     * Orden de lectura. Los ids de documento crecen en el orden de importación (y, dentro de una
     * importación, en el orden en que se eligieron los PDFs).
     */
    private val ORDEN_LECTURA: Comparator<TarjetaFeed> =
        compareBy<TarjetaFeed>({ it.documentoId }, { it.ordenSeccion }, { it.ordenTarjeta })
}
