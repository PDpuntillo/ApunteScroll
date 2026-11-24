package com.apuntescroll.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apuntescroll.datos.Ajustes
import com.apuntescroll.datos.RepositorioApuntes
import com.apuntescroll.datos.bd.ColorMateria
import com.apuntescroll.datos.bd.FirmaTarjetas
import com.apuntescroll.feed.ArmadorFeed
import com.apuntescroll.feed.FiltroFeed
import com.apuntescroll.feed.PielFeed
import com.apuntescroll.feed.PostFeed
import com.apuntescroll.feed.TarjetaFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Una materia en la fila de filtros (historias del Carrusel o chips del Hilo). */
data class MateriaFiltro(
    val id: Long,
    val nombre: String,
    val emoji: String,
    val color: ColorMateria,
    /** Tarjetas nuevas (nunca vistas) que le quedan: el anillo de la historia se pinta si hay. */
    val nuevas: Int,
)

data class EstadoFeed(
    val cargando: Boolean = true,
    val filtro: FiltroFeed = FiltroFeed.Todas,
    val materias: List<MateriaFiltro> = emptyList(),
    val posts: List<PostFeed> = emptyList(),
    /** false si todavía no hay ninguna tarjeta en la app (ningún PDF procesado). */
    val hayTarjetas: Boolean = false,
    val hayGuardadas: Boolean = false,
    /** Se terminaron de procesar PDFs después de armar el feed: hay tarjetas que no están. */
    val hayNuevas: Boolean = false,
    val actualizando: Boolean = false,
    /** PDFs que se están procesando (para Inicio vacío: "Procesando 1 PDF…"). */
    val procesando: Int = 0,
    /** Se están rearmando PDFs al abrir la app: el feed espera (ver ProcesadorDocumentos). */
    val rearmando: Boolean = false,
    /**
     * Cambia cada vez que se lee la base y el orden se arma de nuevo (0 = todavía no). La lista
     * vuelve arriba cuando cambia: las vistas pasan al final, así que arriba está la próxima
     * tarjeta sin ver (y una posición guardada de antes apuntaría a otra tarjeta).
     */
    val idFoto: Long = 0L,
)

/** Lo que el usuario cambió en esta sesión y todavía no está en la foto del feed. */
data class MarcaTarjeta(val sabida: Boolean, val guardada: Boolean)

/**
 * Feed de tarjetas (F2). El orden se arma una vez (al abrir, al cambiar de filtro o de piel, o al
 * actualizar) y no se reacomoda mientras se scrollea: "lo sé" y "guardar" se ven al instante con
 * [marcas], y se guardan en la base.
 *
 * La pantalla del hilo usa esta misma instancia (AppRaiz), así lo que se marca o se ve en el hilo
 * queda también en el feed.
 */
class FeedViewModel(
    private val repositorio: RepositorioApuntes,
    private val ajustes: Ajustes,
    private val alcanceApp: CoroutineScope,
    /** true mientras se rearman PDFs al abrir la app (ProcesadorDocumentos.rearmando). */
    private val rearmando: StateFlow<Boolean>,
) : ViewModel() {

    val piel: StateFlow<PielFeed> = ajustes.piel

    private val _estado = MutableStateFlow(EstadoFeed())
    val estado: StateFlow<EstadoFeed> = _estado.asStateFlow()

    /** Marcas de esta sesión que todavía no se aplicaron a [todas] (se aplican al rearmar). */
    private val _marcas = MutableStateFlow<Map<Long, MarcaTarjeta>>(emptyMap())
    val marcas: StateFlow<Map<Long, MarcaTarjeta>> = _marcas.asStateFlow()

    /** Todas las tarjetas de documentos listos, tal como se leyeron de la base. */
    private var todas: List<TarjetaFeed> = emptyList()

    /** Tarjetas ya contadas como vistas mientras vive el feed (cada una se cuenta una vez). */
    private val contadas = HashSet<Long>()

    /** Vistas que todavía no se aplicaron a [todas] (se aplican al rearmar). */
    private val vistasPendientes = HashSet<Long>()

    /** false hasta la primera lectura: antes no se arma nada (evita mostrar "no hay tarjetas"). */
    private var cargado = false

    /** Escrituras a la base lanzadas en esta sesión (se esperan antes de recargar). */
    private val escrituras = mutableListOf<Job>()

    private var recarga: Job? = null
    private var ultimaFirma: FirmaTarjetas? = null

    private fun escribir(bloque: suspend () -> Unit) {
        escrituras.removeAll { it.isCompleted }
        escrituras += alcanceApp.launch { bloque() }
    }

    init {
        viewModelScope.launch {
            rearmando.collect { r -> _estado.update { it.copy(rearmando = r) } }
        }
        viewModelScope.launch {
            // Si al abrir la app se están rearmando PDFs, esperar: si no, el feed mostraría
            // tarjetas que están por reemplazarse.
            rearmando.first { !it }
            recargar()
        }
        viewModelScope.launch {
            // Si cambia la piel se rearman las publicaciones con el mismo orden.
            piel.collect { rearmar() }
        }
        viewModelScope.launch {
            repositorio.firmaTarjetas().collect { firma ->
                ultimaFirma = firma
                revisarCambios(firma)
            }
        }
        viewModelScope.launch {
            repositorio.materias().collect { materias ->
                _estado.update { it.copy(procesando = materias.sumOf { m -> m.procesando }) }
            }
        }
    }

    /** Vuelve a leer las tarjetas (por ejemplo, después de importar un PDF) y rearma el feed. */
    fun recargar() = recargar(automatica = false)

    /** [automatica]: la pidió la app (no el usuario), sin el indicador de "actualizando". */
    private fun recargar(automatica: Boolean) {
        recarga?.cancel()
        if (!automatica) _estado.update { it.copy(actualizando = cargado) }
        recarga = viewModelScope.launch {
            // Antes de leer, esperar a que se escriba lo marcado en esta sesión. Las marcas no se
            // borran: rearmar() las vuelve a aplicar sobre la lectura nueva (dan lo mismo si ya
            // estaban), y así no se pierde lo marcado mientras se leía.
            escrituras.toList().joinAll()
            val firmaAntes = ultimaFirma
            todas = repositorio.tarjetasParaFeed()
            cargado = true
            rearmar(recargado = true)
            // Si la base cambió mientras se leía, revisar de nuevo.
            ultimaFirma?.takeIf { it != firmaAntes }?.let { revisarCambios(it) }
        }
    }

    /**
     * Se agregaron o se borraron tarjetas. Si falta alguna de las del feed (se borró su PDF), se
     * recarga: "lo sé" sobre una tarjeta que ya no existe no haría nada. Si hay nuevas, se avisa,
     * para no mover lo que se está leyendo (o se recarga si el feed está vacío); si las nuevas ya
     * no están (se borraron antes de actualizar), el aviso se va.
     */
    private suspend fun revisarCambios(firma: FirmaTarjetas) {
        if (!cargado) return
        val cargadas = todas
        val siguen = repositorio.cuantasTarjetasHasta(cargadas.maxOfOrNull { it.id } ?: 0L)
        when {
            siguen < cargadas.size -> recargar(automatica = true)
            cargadas.isEmpty() && firma.total > 0 -> recargar(automatica = true)
            else -> _estado.update { it.copy(hayNuevas = firma.total > siguen) }
        }
    }

    fun elegirFiltro(filtro: FiltroFeed) {
        if (filtro == _estado.value.filtro) return
        _estado.update { it.copy(filtro = filtro) }
        rearmar()
    }

    fun cambiarPiel(piel: PielFeed) = ajustes.cambiarPiel(piel)

    /** ♥ de una publicación: si todas sus tarjetas ya se saben, lo deshace; si no, las marca. */
    fun alternarSabida(post: PostFeed) {
        val sabidas = post.tarjetas.all { marca(it).sabida }
        marcarSabidas(post.tarjetas, !sabidas)
    }

    /** Doble toque sobre una diapositiva: marca esa tarjeta como sabida (no la desmarca). */
    fun marcarSabida(tarjeta: TarjetaFeed) {
        if (!marca(tarjeta).sabida) marcarSabidas(listOf(tarjeta), true)
    }

    /** 🔖 de una publicación: guarda o deja de guardar todas sus tarjetas. */
    fun alternarGuardada(post: PostFeed) {
        val guardadas = post.tarjetas.all { marca(it).guardada }
        val nuevo = !guardadas
        _marcas.update { actuales ->
            actuales + post.tarjetas.associate { it.id to marca(it).copy(guardada = nuevo) }
        }
        _estado.update { it.copy(hayGuardadas = nuevo || hayGuardadas()) }
        escribir { repositorio.guardar(post.tarjetas.map { it.id }, nuevo) }
    }

    /** La tarjeta estuvo a la vista el tiempo suficiente (ver RegistrarVista). */
    fun vista(tarjeta: TarjetaFeed) {
        if (!contadas.add(tarjeta.id)) return
        vistasPendientes += tarjeta.id
        escribir { repositorio.marcarVista(tarjeta.id) }
    }

    fun marca(tarjeta: TarjetaFeed): MarcaTarjeta =
        _marcas.value[tarjeta.id] ?: MarcaTarjeta(sabida = tarjeta.sabida, guardada = tarjeta.guardada)

    private fun marcarSabidas(tarjetas: List<TarjetaFeed>, sabidas: Boolean) {
        _marcas.update { actuales -> actuales + tarjetas.associate { it.id to marca(it).copy(sabida = sabidas) } }
        escribir { repositorio.marcarSabidas(tarjetas.map { it.id }, sabidas) }
    }

    private fun hayGuardadas(): Boolean = todas.any { marca(it).guardada }

    /**
     * Rearma el feed con el filtro y la piel actuales, aplicando lo marcado en esta sesión.
     * [recargado] es true si se acaba de leer la base (entonces ya no faltan tarjetas nuevas).
     */
    private fun rearmar(recargado: Boolean = false) {
        if (!cargado) return
        val marcas = _marcas.value
        val ahora = System.currentTimeMillis()
        val actuales = todas.map { t ->
            val m = marcas[t.id]
            val vista = t.id in vistasPendientes
            if (m == null && !vista) {
                t
            } else {
                t.copy(
                    caja = when {
                        m == null -> t.caja
                        m.sabida -> maxOf(t.caja, 1)
                        else -> 0
                    },
                    guardada = m?.guardada ?: t.guardada,
                    vecesVista = if (vista) t.vecesVista + 1 else t.vecesVista,
                    ultimaVista = if (vista) ahora else t.ultimaVista,
                )
            }
        }
        todas = actuales
        // Con una lectura en curso, las marcas quedan para aplicarlas también sobre ella (esa
        // lectura puede ser de antes de que se escribieran). Aplicarlas dos veces da lo mismo.
        if (recargado || recarga?.isActive != true) {
            _marcas.value = emptyMap()
            vistasPendientes.clear()
        }

        val filtro = _estado.value.filtro.let { f ->
            when {
                // La materia del filtro ya no tiene tarjetas (se borró), o ya no queda ninguna
                // guardada: volver a "Todas".
                f is FiltroFeed.DeMateria && actuales.none { it.materiaId == f.materiaId } -> FiltroFeed.Todas
                f == FiltroFeed.Guardadas && actuales.none { it.guardada } -> FiltroFeed.Todas
                else -> f
            }
        }
        val orden = ArmadorFeed.ordenar(actuales, filtro)
        val totales = actuales.groupingBy { it.seccionId }.eachCount()
        val posts = ArmadorFeed.publicaciones(orden, piel.value, totales)
        _estado.update {
            it.copy(
                cargando = false,
                filtro = filtro,
                materias = materiasDe(actuales),
                posts = posts,
                hayTarjetas = actuales.isNotEmpty(),
                hayGuardadas = actuales.any { t -> t.guardada },
                hayNuevas = if (recargado) false else it.hayNuevas,
                actualizando = if (recargado) false else it.actualizando,
                idFoto = if (recargado) maxOf(it.idFoto + 1, System.nanoTime()) else it.idFoto,
            )
        }
    }

    private fun materiasDe(tarjetas: List<TarjetaFeed>): List<MateriaFiltro> =
        tarjetas.groupBy { it.materiaId }.map { (id, lista) ->
            val primera = lista.first()
            MateriaFiltro(
                id = id,
                nombre = primera.nombreMateria,
                emoji = primera.emoji,
                color = primera.color,
                nuevas = lista.count { it.nueva && !it.sabida },
            )
        }.sortedBy { it.nombre.lowercase() }
}
