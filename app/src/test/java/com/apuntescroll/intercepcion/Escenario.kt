package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.Accion
import com.apuntescroll.intercepcion.reglas.CatalogoRedes
import com.apuntescroll.intercepcion.reglas.ClasificadorPaquetes
import com.apuntescroll.intercepcion.reglas.ComoPasa
import com.apuntescroll.intercepcion.reglas.MotorIntercepcion
import com.apuntescroll.intercepcion.reglas.RedConocida
import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.intercepcion.reglas.Tarea

/**
 * Corre el motor como lo haría el coordinador: con un reloj, las tareas programadas (una por
 * tipo, como el Handler con su token) y el pase de [ctx].
 */
class Escenario(
    sdk: Int = 36,
    vencerEnVivo: Boolean = true,
    val ctx: ContextoFalso = ContextoFalso(),
) {
    var ahora = 1_000_000L
        private set

    val motor = MotorIntercepcion(
        ClasificadorPaquetes(PROPIO, CatalogoRedes(Redes.CONOCIDAS)),
        sdk,
        vencerEnVivo = vencerEnVivo,
    )

    /** Tarea → cuándo vence. */
    val tareas = mutableMapOf<Tarea, Long>()

    /** Todo lo que devolvió el motor, en orden. */
    val registro = mutableListOf<Accion>()

    init {
        ctx.reloj = { ahora }
    }

    fun aplicar(acciones: List<Accion>): List<Accion> {
        for (a in acciones) {
            when (a) {
                is Accion.Programar -> tareas[a.tarea] = ahora + a.enMs
                is Accion.Cancelar -> tareas.remove(a.tarea)
                Accion.OlvidarPaseVencido -> ctx.paseHasta = null
                else -> Unit
            }
        }
        registro += acciones
        return acciones
    }

    fun evento(paquete: String): List<Accion> = aplicar(motor.alPrimerPlano(paquete, ahora, ctx))

    /** Pasa el tiempo y corre las tareas que vencen, en orden. Devuelve lo que hicieron. */
    fun avanzar(ms: Long): List<Accion> {
        val fin = ahora + ms
        val hechas = mutableListOf<Accion>()
        while (true) {
            val (tarea, en) = tareas.entries.filter { it.value <= fin }.minByOrNull { it.value }?.toPair() ?: break
            tareas.remove(tarea)
            ahora = maxOf(ahora, en)
            hechas += aplicar(motor.alTarea(tarea, ahora, ctx))
        }
        ahora = fin
        return hechas
    }

    fun mostrarFeed(red: RedConocida): List<Accion> = aplicar(motor.alMostrarseFeed(red.paquete, ahora))

    fun ocultarFeed(): List<Accion> = aplicar(motor.alOcultarseFeed(ahora))

    /** La pantalla de apuntes aparece [demora] ms después y manda su propio evento. */
    fun aparece(red: RedConocida, demora: Long = 300): List<Accion> {
        val hechas = avanzar(demora).toMutableList()
        hechas += mostrarFeed(red)
        hechas += evento(PROPIO)
        return hechas
    }

    fun dejarPasar(red: RedConocida, como: ComoPasa): List<Accion> {
        val hechas = aplicar(motor.alDejarPasar(red.paquete, como, ahora, ctx)).toMutableList()
        hechas += ocultarFeed()
        return hechas
    }

    /** Gana un pase de [minutos] y deja pasar a [red] (como "Entrar"). */
    fun entrarConPase(red: RedConocida, minutos: Int = 10): List<Accion> {
        ctx.paseHasta = ahora + minutos * 60_000L
        return dejarPasar(red, ComoPasa.PASE)
    }

    fun apagarPantalla(): List<Accion> = aplicar(motor.alApagarPantalla())

    fun encenderPantalla(): List<Accion> = aplicar(motor.alEncenderPantalla(ctx))

    fun lanzamientos(): List<Accion.Lanzar> = registro.filterIsInstance<Accion.Lanzar>()

    fun limpiarRegistro() = registro.clear()

    companion object {
        const val PROPIO = "com.apuntescroll"
        const val LAUNCHER = "com.sec.android.app.launcher"
        const val WHATSAPP = "com.whatsapp"
        const val SYSTEMUI = "com.android.systemui"
        const val PERMISOS = "com.google.android.permissioncontroller"
        const val COMPARTIR = "com.android.intentresolver"
        const val BLOQUEADOR = "com.bloqueador.android"
        val IG = Redes.INSTAGRAM
        val X = Redes.X
    }
}
