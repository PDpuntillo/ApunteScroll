package com.apuntescroll.intercepcion.reglas

/**
 * Decide qué hacer con cada app que pasa a primer plano y con cada tarea programada (PLAN.md,
 * sección 7). Kotlin puro y sin hilos: todo se llama desde el hilo principal, y el tiempo ([ahora])
 * es el de `elapsedRealtime`. No hace nada por su cuenta: devuelve [Accion]es que ejecuta el
 * coordinador.
 *
 * Recuerda solo la última app al frente ([ultimoAlFrente]); nunca arma una lista de apps.
 */
class MotorIntercepcion(
    private val clasificador: ClasificadorPaquetes,
    private val sdk: Int,
    private val freno: FrenoLanzamientos = FrenoLanzamientos(),
    private val vencerEnVivo: Boolean = VENCER_EN_VIVO,
) {
    /** Un lanzamiento que todavía no se vio aparecer (lo sigue el vigía, COMPROBAR_APARICION). */
    private class Lanzamiento(
        val red: RedConocida,
        val motivo: MotivoIntercepcion,
        var en: Long,
        var reintentos: Int = 0,
        var esperoInicio: Boolean = false,
    )

    /** La última app al frente (sin contar las transitorias). */
    var ultimoAlFrente: String? = null
        private set

    private var ultimaOtraEn: Long? = null
    private var huboOtraApp = true
    private var visita: Visita? = null
    private var vista: VistaFeed? = null
    private var lanzamiento: Lanzamiento? = null
    private var ultimoLanzamientoEn: Long? = null
    private var fallos = 0
    private var ultimoFalloEn: Long? = null
    private var intentosDesbloqueo = 0

    private val catalogo get() = clasificador.catalogo

    /** La red cuya pantalla de apuntes se está viendo ahora. */
    private fun redEnFeed(): String? = vista?.takeIf { it.hasta == null }?.red

    fun alPrimerPlano(paquete: String, ahora: Long, ctx: ContextoMotor): List<Accion> {
        if (!ctx.config.consentida) return emptyList()
        val tipo = clasificador.tipo(paquete, ctx.teclado)
        if (tipo == TipoPaquete.Transitorio) return emptyList()
        val acciones = mutableListOf<Accion>()
        val lanzadoEn = ultimoLanzamientoEn
        if (paquete in Redes.APPS_DE_BLOQUEO && lanzadoEn != null && ahora - lanzadoEn in 0 until PELEA_MS) {
            acciones += Accion.AvisoPelea
        }
        // Variable local a propósito: solo se recuerda la última app (el aviso lo dice así).
        val anterior = ultimoAlFrente
        ultimoAlFrente = paquete
        when (tipo) {
            TipoPaquete.Propio, TipoPaquete.Transitorio -> Unit
            TipoPaquete.Otro -> {
                ultimaOtraEn = ahora
                huboOtraApp = true
                visita = visita?.let { if (it.salioEn == null) it.copy(salioEn = ahora) else it }
            }
            is TipoPaquete.Red -> {
                val r = tipo.red
                actualizarVisita(r.paquete, ahora)
                val motivo = if (anterior == r.paquete) motivoPara(r, ctx) else MotivoIntercepcion.ABRIO_RED
                acciones += evaluar(r, motivo, desdeEvento = true, ahora, ctx)
            }
        }
        return acciones
    }

    private fun actualizarVisita(red: String, ahora: Long) {
        val v = visita ?: return
        visita = if (v.red != red) {
            // Salió de la red de la visita hacia otra red.
            val salioEn = v.salioEn ?: ahora
            if (ahora - salioEn > GRACIA_VISITA_MS) null else v.copy(salioEn = salioEn)
        } else {
            val salioEn = v.salioEn
            if (salioEn != null && ahora - salioEn > GRACIA_VISITA_MS) null else v.copy(salioEn = null)
        }
    }

    /** PASE_VENCIDO si la red está en modo Pase y el pase guardado ya venció. */
    private fun motivoPara(red: RedConocida, ctx: ContextoMotor): MotivoIntercepcion =
        if (ctx.config.de(red).modo == ModoIntercepcion.PASE && ctx.hayPaseVencido()) {
            MotivoIntercepcion.PASE_VENCIDO
        } else {
            MotivoIntercepcion.ABRIO_RED
        }

    private fun evaluar(r: RedConocida, motivo: MotivoIntercepcion, desdeEvento: Boolean, ahora: Long, ctx: ContextoMotor): List<Accion> {
        val decision = ReglasIntercepcion.decidir(r, ctx.config, ctx.paseRestanteMs() > 0, visita, redEnFeed(), ctx::entorno)
        when (decision) {
            is Decision.Dejar -> return when (decision.razon) {
                Razon.EN_LLAMADA -> {
                    visita = Visita(r.paquete, porLlamada = true)
                    emptyList()
                }
                Razon.FEED_A_LA_VISTA ->
                    if (desdeEvento) listOf(Accion.Programar(Tarea.REVISAR, REVISION_TRAS_FEED_MS)) else emptyList()
                else -> emptyList()
            }
            is Decision.Interceptar -> Unit
        }
        // Con un lanzamiento de esta red que todavía no se vio, decide el vigía (si no, el
        // reintento del vigía y un evento de la red lanzarían dos veces).
        if (lanzamiento?.red == r) return emptyList()
        return when (val v = freno.pedir(r.paquete, ahora, vista, huboOtraApp)) {
            FrenoLanzamientos.Veredicto.Lanzar -> {
                freno.lanzado(r.paquete, ahora)
                huboOtraApp = false
                lanzamiento = Lanzamiento(r, motivo, ahora)
                ultimoLanzamientoEn = ahora
                buildList {
                    add(Accion.Lanzar(r, motivo))
                    add(Accion.Programar(Tarea.COMPROBAR_APARICION, ESPERA_APARICION_MS))
                    if (ctx.hayPaseVencido()) add(Accion.OlvidarPaseVencido)
                }
            }
            is FrenoLanzamientos.Veredicto.Esperar -> listOf(Accion.Programar(Tarea.REVISAR, v.ms))
            FrenoLanzamientos.Veredicto.Pausado -> emptyList()
            FrenoLanzamientos.Veredicto.PausaNueva -> listOf(Accion.AvisoBucle)
        }
    }

    fun alTarea(tarea: Tarea, ahora: Long, ctx: ContextoMotor): List<Accion> {
        if (!ctx.config.consentida) return emptyList()
        return when (tarea) {
            Tarea.REVISAR -> revisar(ahora, ctx)
            Tarea.REVISAR_DESBLOQUEO -> {
                if (!ctx.entorno().pantallaEnUso) {
                    intentosDesbloqueo++
                    if (intentosDesbloqueo < DESBLOQUEO_INTENTOS) {
                        listOf(Accion.Programar(Tarea.REVISAR_DESBLOQUEO, DESBLOQUEO_CADA_MS))
                    } else {
                        emptyList()
                    }
                } else {
                    intentosDesbloqueo = 0
                    revisar(ahora, ctx)
                }
            }
            Tarea.COMPROBAR_APARICION -> comprobarAparicion(ahora, ctx)
            Tarea.VENCER_PASE -> {
                val r = catalogo.de(ultimoAlFrente)
                when {
                    r == null -> if (ctx.hayPaseVencido()) listOf(Accion.OlvidarPaseVencido) else emptyList()
                    !vencerEnVivo || ctx.paseRestanteMs() > 0 -> emptyList()
                    ctx.config.de(r).modo != ModoIntercepcion.PASE -> emptyList()
                    redEnFeed() == r.paquete -> emptyList()
                    else -> evaluar(r, MotivoIntercepcion.PASE_VENCIDO, desdeEvento = false, ahora, ctx)
                }
            }
            Tarea.AVISO_ULTIMO_MINUTO -> {
                val r = catalogo.de(ultimoAlFrente)
                val restante = ctx.paseRestanteMs()
                // El reloj de las tareas se frena con el teléfono dormido: si quedan menos de 30 s
                // (o ya venció), el aviso llega tarde y no se muestra.
                if (r != null && ctx.config.de(r).modo == ModoIntercepcion.PASE &&
                    restante in AVISO_MIN_MS..AVISO_MAX_MS && ctx.entorno().pantallaEnUso
                ) {
                    listOf(Accion.AvisoUltimoMinuto(r))
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun revisar(ahora: Long, ctx: ContextoMotor): List<Accion> {
        val r = catalogo.de(ultimoAlFrente) ?: return emptyList()
        if (redEnFeed() == r.paquete) return emptyList()
        return evaluar(r, motivoPara(r, ctx), desdeEvento = false, ahora, ctx)
    }

    /** El vigía: comprueba que la pantalla de apuntes llegó a aparecer (sin pulsar inicio). */
    private fun comprobarAparicion(ahora: Long, ctx: ContextoMotor): List<Accion> {
        val l = lanzamiento ?: return emptyList()
        val v = vista
        if (v != null && v.red == l.red.paquete && v.desde >= l.en) {
            lanzamiento = null
            return emptyList()
        }
        val e = ctx.entorno()
        // Con la pantalla apagada, una app fijada o si el usuario ya salió de la red, no se
        // cuenta como fallo (en Android 8 a 11, apretar Inicio demora la pantalla unos 5 s).
        if (!e.pantallaEnUso || e.appFijada || ultimoAlFrente != l.red.paquete) {
            lanzamiento = null
            return emptyList()
        }
        val otra = ultimaOtraEn
        if (sdk <= 30 && !l.esperoInicio && otra != null && otra in (l.en - ESPERA_TRAS_INICIO_MS)..l.en) {
            l.esperoInicio = true
            return listOf(Accion.Programar(Tarea.COMPROBAR_APARICION, ESPERA_TRAS_INICIO_MS))
        }
        val falloAnterior = ultimoFalloEn
        if (falloAnterior != null && ahora - falloAnterior !in 0..OLVIDO_FALLOS_MS) fallos = 0
        fallos++
        ultimoFalloEn = ahora
        if (fallos >= FALLOS_PARA_AVISAR) {
            lanzamiento = null
            return listOf(Accion.AvisoNoAparece(true))
        }
        val sigue = ReglasIntercepcion.decidir(
            l.red, ctx.config, ctx.paseRestanteMs() > 0, visita, redEnFeed(), ctx::entorno,
        ) is Decision.Interceptar
        if (l.reintentos == 0 && sigue) {
            l.reintentos = 1
            l.en = ahora
            freno.lanzado(l.red.paquete, ahora)
            ultimoLanzamientoEn = ahora
            return listOf(
                Accion.Lanzar(l.red, l.motivo, reintento = true),
                Accion.Programar(Tarea.COMPROBAR_APARICION, ESPERA_APARICION_MS),
            )
        }
        lanzamiento = null
        return emptyList()
    }

    /** startActivity tiró una excepción: comprobar enseguida. */
    fun alFalloLanzar(ahora: Long): List<Accion> = listOf(Accion.Programar(Tarea.COMPROBAR_APARICION, 0))

    /** La pantalla de apuntes pasó a primer plano. Repetirlo (rotar) no tiene efectos. */
    fun alMostrarseFeed(red: String, ahora: Long): List<Accion> {
        val v = vista
        if (v != null && v.red == red && v.hasta == null) return emptyList()
        vista = VistaFeed(red, ahora)
        if (lanzamiento?.red?.paquete == red) lanzamiento = null
        fallos = 0
        ultimoFalloEn = null
        return listOf(Accion.AvisoNoAparece(false))
    }

    fun alOcultarseFeed(ahora: Long): List<Accion> {
        vista = vista?.let { if (it.hasta == null) it.copy(hasta = ahora) else it }
        return emptyList()
    }

    /** Antes de que la pantalla haga finish(): la red queda debajo, así que pasa a ser la última al frente. */
    fun alDejarPasar(red: String, como: ComoPasa, ahora: Long, ctx: ContextoMotor): List<Accion> {
        ultimoAlFrente = red
        lanzamiento = null
        if (como == ComoPasa.VISITA) visita = Visita(red, porLlamada = false)
        return alCambiarPase(ctx)
    }

    /** Se abrió ApunteScroll: termina la visita y cuenta como "otra app" para el freno. */
    fun alAbrirApp(): List<Accion> {
        visita = null
        huboOtraApp = true
        return emptyList()
    }

    /** Termina la visita y cancela lo que dependía de la pantalla encendida. */
    fun alApagarPantalla(): List<Accion> {
        visita = null
        lanzamiento = null
        intentosDesbloqueo = 0
        return listOf(Accion.Cancelar(Tarea.COMPROBAR_APARICION), Accion.Cancelar(Tarea.REVISAR_DESBLOQUEO))
    }

    /**
     * La pantalla se encendió o se desbloqueó. Además reprograma el pase: el reloj de las tareas
     * (uptimeMillis) se frena mientras el teléfono duerme.
     */
    fun alEncenderPantalla(ctx: ContextoMotor): List<Accion> {
        intentosDesbloqueo = 0
        return listOf(Accion.Programar(Tarea.REVISAR_DESBLOQUEO, DESBLOQUEO_CADA_MS)) + alCambiarPase(ctx)
    }

    /** Programa o cancela VENCER_PASE y AVISO_ULTIMO_MINUTO según lo que queda del pase. */
    fun alCambiarPase(ctx: ContextoMotor): List<Accion> {
        val restante = ctx.paseRestanteMs()
        if (restante <= 0) return listOf(Accion.Cancelar(Tarea.VENCER_PASE), Accion.Cancelar(Tarea.AVISO_ULTIMO_MINUTO))
        val aviso = if (restante > UN_MINUTO_MS) {
            Accion.Programar(Tarea.AVISO_ULTIMO_MINUTO, restante - UN_MINUTO_MS)
        } else {
            Accion.Cancelar(Tarea.AVISO_ULTIMO_MINUTO)
        }
        return listOf(Accion.Programar(Tarea.VENCER_PASE, restante), aviso)
    }

    fun alConectar(ctx: ContextoMotor): List<Accion> = alCambiarPase(ctx)

    /** Estado de cero y Cancelar de todas las tareas. */
    fun reiniciar(): List<Accion> {
        ultimoAlFrente = null
        ultimaOtraEn = null
        huboOtraApp = true
        visita = null
        vista = null
        lanzamiento = null
        ultimoLanzamientoEn = null
        fallos = 0
        ultimoFalloEn = null
        intentosDesbloqueo = 0
        freno.reiniciar()
        return Tarea.entries.map { Accion.Cancelar(it) }
    }

    companion object {
        const val GRACIA_VISITA_MS = 30_000L
        const val REVISION_TRAS_FEED_MS = 600L
        const val ESPERA_APARICION_MS = 1_500L
        const val ESPERA_TRAS_INICIO_MS = 5_500L
        const val FALLOS_PARA_AVISAR = 2

        /** Dos fallos separados por más de un día no avisan. */
        const val OLVIDO_FALLOS_MS = 24 * 60 * 60_000L
        const val PELEA_MS = 3_000L
        const val DESBLOQUEO_CADA_MS = 500L
        const val DESBLOQUEO_INTENTOS = 20
        const val UN_MINUTO_MS = 60_000L
        const val AVISO_MIN_MS = 30_000L
        const val AVISO_MAX_MS = 75_000L

        /** Apagar si la calibración (prueba manual, escenario 26) muestra falsos positivos. */
        const val VENCER_EN_VIVO = true
    }
}
