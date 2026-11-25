package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.Escenario.Companion.BLOQUEADOR
import com.apuntescroll.intercepcion.Escenario.Companion.COMPARTIR
import com.apuntescroll.intercepcion.Escenario.Companion.IG
import com.apuntescroll.intercepcion.Escenario.Companion.LAUNCHER
import com.apuntescroll.intercepcion.Escenario.Companion.PERMISOS
import com.apuntescroll.intercepcion.Escenario.Companion.PROPIO
import com.apuntescroll.intercepcion.Escenario.Companion.SYSTEMUI
import com.apuntescroll.intercepcion.Escenario.Companion.WHATSAPP
import com.apuntescroll.intercepcion.Escenario.Companion.X
import com.apuntescroll.intercepcion.reglas.Accion
import com.apuntescroll.intercepcion.reglas.ComoPasa
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.MotivoIntercepcion
import com.apuntescroll.intercepcion.reglas.Tarea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorIntercepcionTest {

    private val e = Escenario()
    private val ctx get() = e.ctx
    private val minuto = 60_000L

    private fun List<Accion>.lanzamientos() = filterIsInstance<Accion.Lanzar>()

    /** IG intercepta, aparece la pantalla y el usuario toca "Ir igual" (modo Recordatorio). */
    private fun irIgualEnInstagram() {
        ctx.modo(IG, ModoIntercepcion.RECORDATORIO)
        e.evento(IG.paquete)
        e.aparece(IG)
        e.avanzar(5_000)
        e.dejarPasar(IG, ComoPasa.VISITA)
        e.evento(IG.paquete)
    }

    @Test
    fun `abrir instagram lanza el carrusel y programa la comprobacion`() {
        assertEquals(
            listOf(
                Accion.Lanzar(IG, MotivoIntercepcion.ABRIO_RED),
                Accion.Programar(Tarea.COMPROBAR_APARICION, 1_500),
            ),
            e.evento(IG.paquete),
        )
    }

    @Test
    fun `una rafaga de tres eventos lanza una vez y la revision con el feed visto no hace nada`() {
        e.evento(IG.paquete)
        e.avanzar(80)
        e.evento(IG.paquete)
        e.avanzar(80)
        e.evento(IG.paquete)
        e.aparece(IG, demora = 200)
        e.avanzar(5_000)
        assertEquals(1, e.lanzamientos().size)
    }

    @Test
    fun `evento de la red con su feed a la vista programa una revision de 600 ms`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        assertEquals(listOf(Accion.Programar(Tarea.REVISAR, 600)), e.evento(IG.paquete))
    }

    @Test
    fun `la revision con el feed oculto y la red al frente vuelve a lanzar`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        e.avanzar(3_000)
        e.evento(IG.paquete)
        e.ocultarFeed()
        assertEquals(listOf(IG), e.avanzar(600).lanzamientos().map { it.red })
    }

    @Test
    fun `una revision con el feed a la vista no programa otra`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        e.evento(IG.paquete)
        assertEquals(emptyList<Accion>(), e.avanzar(600))
        assertFalse(Tarea.REVISAR in e.tareas)
    }

    @Test
    fun `con el feed de instagram a la vista un evento de x lanza x`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        assertEquals(listOf(X), e.evento(X.paquete).lanzamientos().map { it.red })
    }

    @Test
    fun `ir igual y despues teclado cortina permisos e instagram no lanza`() {
        irIgualEnInstagram()
        listOf(ContextoFalso.TECLADO, SYSTEMUI, PERMISOS, COMPARTIR, IG.paquete).forEach { e.evento(it) }
        assertEquals(1, e.lanzamientos().size)
    }

    @Test
    fun `ir igual whatsapp diez segundos e instagram no lanza`() {
        irIgualEnInstagram()
        e.evento(WHATSAPP)
        e.avanzar(10_000)
        e.evento(IG.paquete)
        assertEquals(1, e.lanzamientos().size)
    }

    @Test
    fun `ir igual whatsapp cuarenta segundos e instagram lanza`() {
        irIgualEnInstagram()
        e.evento(WHATSAPP)
        e.avanzar(40_000)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
    }

    @Test
    fun `ir igual y apagar la pantalla termina la visita`() {
        irIgualEnInstagram()
        e.apagarPantalla()
        e.avanzar(2_000)
        e.encenderPantalla()
        e.avanzar(500)
        assertEquals(2, e.lanzamientos().size)
    }

    @Test
    fun `ir igual y abrir apuntescroll termina la visita`() {
        irIgualEnInstagram()
        e.aplicar(e.motor.alAbrirApp())
        e.evento(PROPIO)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
    }

    @Test
    fun `ir igual en instagram y pasar a x evalua x por su modo`() {
        irIgualEnInstagram()
        assertEquals(listOf(X), e.evento(X.paquete).lanzamientos().map { it.red })
    }

    @Test
    fun `al dejar pasar la red queda como ultima al frente`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        assertEquals(PROPIO, e.motor.ultimoAlFrente)
        e.entrarConPase(IG)
        assertEquals(IG.paquete, e.motor.ultimoAlFrente)
    }

    @Test
    fun `entrar con pase programa el vencimiento y el aviso del ultimo minuto`() {
        val acciones = e.entrarConPase(IG, minutos = 10)
        assertTrue(Accion.Programar(Tarea.VENCER_PASE, 10 * minuto) in acciones)
        assertTrue(Accion.Programar(Tarea.AVISO_ULTIMO_MINUTO, 9 * minuto) in acciones)
    }

    @Test
    fun `vence el pase con instagram al frente y lanza con pase vencido`() {
        e.entrarConPase(IG, minutos = 10)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        val acciones = e.avanzar(10 * minuto)
        assertEquals(listOf(Accion.Lanzar(IG, MotivoIntercepcion.PASE_VENCIDO)), acciones.lanzamientos())
        assertTrue(Accion.OlvidarPaseVencido in acciones)
        assertNull(ctx.paseHasta)
    }

    @Test
    fun `vence el pase con whatsapp al frente y solo olvida el pase`() {
        e.entrarConPase(IG, minutos = 10)
        e.evento(WHATSAPP)
        val acciones = e.avanzar(10 * minuto)
        assertEquals(emptyList<Accion.Lanzar>(), acciones.lanzamientos())
        assertTrue(Accion.OlvidarPaseVencido in acciones)
        assertEquals(
            listOf(Accion.Lanzar(IG, MotivoIntercepcion.ABRIO_RED)),
            e.evento(IG.paquete).lanzamientos(),
        )
    }

    @Test
    fun `sin vencimiento en vivo el proximo evento de la red lanza con pase vencido`() {
        val e = Escenario(vencerEnVivo = false)
        e.entrarConPase(IG, minutos = 10)
        e.evento(IG.paquete)
        assertEquals(emptyList<Accion.Lanzar>(), e.avanzar(10 * minuto).lanzamientos())
        assertEquals(
            listOf(Accion.Lanzar(IG, MotivoIntercepcion.PASE_VENCIDO)),
            e.evento(IG.paquete).lanzamientos(),
        )
    }

    @Test
    fun `pase compartido deja pasar a x en modo pase pero no en reemplazo`() {
        e.entrarConPase(IG)
        e.evento(LAUNCHER)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(X.paquete).lanzamientos())
        ctx.modo(X, ModoIntercepcion.REEMPLAZO_TOTAL)
        e.evento(LAUNCHER)
        assertEquals(listOf(X), e.evento(X.paquete).lanzamientos().map { it.red })
    }

    @Test
    fun `aviso del ultimo minuto solo con la red al frente y la pantalla en uso`() {
        e.entrarConPase(IG, minutos = 10)
        e.evento(IG.paquete)
        assertTrue(Accion.AvisoUltimoMinuto(IG) in e.avanzar(9 * minuto + 1))

        val conOtra = Escenario()
        conOtra.entrarConPase(IG, minutos = 10)
        conOtra.evento(WHATSAPP)
        assertTrue(conOtra.avanzar(9 * minuto + 1).none { it is Accion.AvisoUltimoMinuto })

        val bloqueado = Escenario()
        bloqueado.entrarConPase(IG, minutos = 10)
        bloqueado.evento(IG.paquete)
        bloqueado.ctx.bloquear()
        assertTrue(bloqueado.avanzar(9 * minuto + 1).none { it is Accion.AvisoUltimoMinuto })
    }

    @Test
    fun `aviso del ultimo minuto no sale si quedan menos de 30 s`() {
        ctx.paseHasta = e.ahora + 20_000
        e.evento(IG.paquete)
        // Un pase de menos de un minuto no programa el aviso.
        assertTrue(Accion.Cancelar(Tarea.AVISO_ULTIMO_MINUTO) in e.aplicar(e.motor.alCambiarPase(ctx)))
        // Y si la tarea llega tarde (teléfono dormido), no se muestra.
        e.aplicar(listOf(Accion.Programar(Tarea.AVISO_ULTIMO_MINUTO, 0)))
        assertTrue(e.avanzar(0).none { it is Accion.AvisoUltimoMinuto })
    }

    @Test
    fun `encender la pantalla reprograma el vencimiento`() {
        ctx.paseHasta = e.ahora + 5 * minuto
        val acciones = e.encenderPantalla()
        assertTrue(Accion.Programar(Tarea.VENCER_PASE, 5 * minuto) in acciones)
        assertTrue(Accion.Programar(Tarea.AVISO_ULTIMO_MINUTO, 4 * minuto) in acciones)
        assertTrue(Accion.Programar(Tarea.REVISAR_DESBLOQUEO, 500) in acciones)
    }

    @Test
    fun `pantalla bloqueada no lanza y el desbloqueo con la red al frente si`() {
        ctx.bloquear()
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        ctx.desbloquear()
        e.encenderPantalla()
        assertEquals(listOf(IG), e.avanzar(500).lanzamientos().map { it.red })
    }

    @Test
    fun `revision de desbloqueo reintenta mientras siga bloqueado y para a los veinte intentos`() {
        ctx.bloquear()
        e.evento(IG.paquete)
        e.encenderPantalla()
        e.avanzar(30_000)
        assertEquals(20, e.registro.count { it == Accion.Programar(Tarea.REVISAR_DESBLOQUEO, 500) })
        assertFalse(Tarea.REVISAR_DESBLOQUEO in e.tareas)
        ctx.desbloquear()
        assertEquals(emptyList<Accion.Lanzar>(), e.avanzar(5_000).lanzamientos())
        assertEquals(0, e.lanzamientos().size)
    }

    @Test
    fun `en llamada no lanza y la visita queda libre hasta salir de la red`() {
        ctx.entornoActual = ctx.entornoActual.copy(enLlamada = true)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        ctx.entornoActual = ctx.entornoActual.copy(enLlamada = false)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        e.evento(WHATSAPP)
        e.avanzar(5_000)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
    }

    @Test
    fun `la visita por llamada termina despues de 30 s en otra app`() {
        ctx.modo(IG, ModoIntercepcion.REEMPLAZO_TOTAL)
        ctx.entornoActual = ctx.entornoActual.copy(enLlamada = true)
        e.evento(IG.paquete)
        ctx.entornoActual = ctx.entornoActual.copy(enLlamada = false)
        e.evento(WHATSAPP)
        e.avanzar(31_000)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
    }

    @Test
    fun `app fijada no lanza`() {
        ctx.entornoActual = ctx.entornoActual.copy(appFijada = true)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
    }

    @Test
    fun `sin consentimiento ningun evento produce acciones ni cambia la ultima app`() {
        ctx.consentida = false
        listOf(IG.paquete, WHATSAPP, X.paquete, PROPIO, LAUNCHER).forEach {
            assertEquals(emptyList<Accion>(), e.evento(it))
        }
        assertNull(e.motor.ultimoAlFrente)
        Tarea.entries.forEach { assertEquals(emptyList<Accion>(), e.motor.alTarea(it, e.ahora, ctx)) }
    }

    @Test
    fun `red apagada no lanza y al encenderla si`() {
        ctx.modo(IG, ModoIntercepcion.PASE, activada = false)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        ctx.modo(IG, ModoIntercepcion.PASE, activada = true)
        e.evento(LAUNCHER)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
    }

    @Test
    fun `eventos propios nunca lanzan ni terminan la visita`() {
        irIgualEnInstagram()
        repeat(3) { assertEquals(emptyList<Accion>(), e.evento(PROPIO)) }
        e.evento(IG.paquete)
        assertEquals(1, e.lanzamientos().size)
    }

    @Test
    fun `un evento propio con pase vencido no lanza`() {
        ctx.paseHasta = e.ahora - 1
        assertEquals(emptyList<Accion>(), e.evento(PROPIO))
    }

    @Test
    fun `un transitorio no cambia la ultima app`() {
        e.evento(LAUNCHER)
        e.evento(SYSTEMUI)
        e.evento(ContextoFalso.TECLADO)
        assertEquals(LAUNCHER, e.motor.ultimoAlFrente)
    }

    @Test
    fun `sin aparecer en 1500 ms reintenta una vez y al segundo fallo avisa`() {
        e.evento(IG.paquete)
        assertEquals(
            listOf(
                Accion.Lanzar(IG, MotivoIntercepcion.ABRIO_RED, reintento = true),
                Accion.Programar(Tarea.COMPROBAR_APARICION, 1_500),
            ),
            e.avanzar(1_500),
        )
        assertEquals(listOf(Accion.AvisoNoAparece(true)), e.avanzar(1_500))
    }

    @Test
    fun `mostrarse el feed borra los fallos y el aviso`() {
        e.evento(IG.paquete)
        e.avanzar(3_000)
        assertTrue(Accion.AvisoNoAparece(true) in e.registro)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
        assertTrue(Accion.AvisoNoAparece(false) in e.aparece(IG))
        // Los fallos volvieron a cero: el próximo fallo solo reintenta.
        e.ocultarFeed()
        e.evento(LAUNCHER)
        e.avanzar(3_000)
        e.evento(IG.paquete)
        val siguiente = e.avanzar(1_500)
        assertTrue(siguiente.lanzamientos().single().reintento)
        assertFalse(Accion.AvisoNoAparece(true) in siguiente)
    }

    @Test
    fun `android 11 con otra app 1 s antes espera 5500 ms sin contar el fallo`() {
        val e = Escenario(sdk = 30)
        e.evento(LAUNCHER)
        e.avanzar(1_000)
        e.evento(IG.paquete)
        assertEquals(listOf(Accion.Programar(Tarea.COMPROBAR_APARICION, 5_500)), e.avanzar(1_500))
        e.aparece(IG, demora = 2_000)
        e.avanzar(5_000)
        assertEquals(1, e.lanzamientos().size)
        assertFalse(Accion.AvisoNoAparece(true) in e.registro)
    }

    @Test
    fun `android 14 con otra app 1 s antes cuenta el fallo`() {
        val e = Escenario(sdk = 34)
        e.evento(LAUNCHER)
        e.avanzar(1_000)
        e.evento(IG.paquete)
        assertTrue(e.avanzar(1_500).lanzamientos().single().reintento)
    }

    @Test
    fun `salir de la red antes de la comprobacion no cuenta fallo`() {
        e.evento(IG.paquete)
        e.avanzar(500)
        e.evento(LAUNCHER)
        assertEquals(emptyList<Accion>(), e.avanzar(1_500))
        e.evento(IG.paquete)
        // Si el anterior hubiera contado, este primer fallo ya avisaría.
        assertTrue(e.avanzar(1_500).lanzamientos().single().reintento)
        assertEquals(listOf(Accion.AvisoNoAparece(true)), e.avanzar(1_500))
    }

    @Test
    fun `revisar con un lanzamiento sin comprobar no relanza`() {
        e.evento(IG.paquete)
        e.avanzar(500)
        assertEquals(emptyList<Accion>(), e.evento(IG.paquete))
        e.aplicar(listOf(Accion.Programar(Tarea.REVISAR, 1_000)))
        // REVISAR y la comprobación vencen juntos: solo el vigía reintenta.
        val acciones = e.avanzar(1_000)
        assertEquals(1, acciones.lanzamientos().size)
        assertTrue(acciones.lanzamientos().single().reintento)
        assertEquals(2, e.lanzamientos().size)
    }

    @Test
    fun `en xiaomi sin aparicion avisa no aparece y no bucle`() {
        e.evento(IG.paquete)
        repeat(25) {
            e.avanzar(400)
            e.evento(IG.paquete)
        }
        assertTrue(Accion.AvisoNoAparece(true) in e.registro)
        assertFalse(Accion.AvisoBucle in e.registro)
    }

    @Test
    fun `apagar la pantalla cancela la comprobacion pendiente`() {
        e.evento(IG.paquete)
        e.apagarPantalla()
        assertFalse(Tarea.COMPROBAR_APARICION in e.tareas)
        e.avanzar(5_000)
        assertEquals(1, e.lanzamientos().size)
    }

    @Test
    fun `fallo de startActivity comprueba enseguida`() {
        e.evento(IG.paquete)
        assertEquals(listOf(Accion.Programar(Tarea.COMPROBAR_APARICION, 0)), e.aplicar(e.motor.alFalloLanzar(e.ahora)))
        assertTrue(e.avanzar(0).lanzamientos().single().reintento)
    }

    @Test
    fun `el bloqueador al frente 1 s despues de lanzar avisa la pelea`() {
        e.evento(IG.paquete)
        e.avanzar(1_000)
        assertTrue(Accion.AvisoPelea in e.evento(BLOQUEADOR))
    }

    @Test
    fun `el bloqueador al frente 10 s despues no avisa`() {
        e.evento(IG.paquete)
        e.aparece(IG)
        e.avanzar(10_000)
        assertFalse(Accion.AvisoPelea in e.evento(BLOQUEADOR))
    }

    /** IG vuelve encima de la pantalla de apuntes una y otra vez, sin pasar por otra app. */
    private fun rebotar(veces: Int) {
        e.evento(IG.paquete)
        repeat(veces) {
            e.aparece(IG, demora = 300)
            e.avanzar(200)
            e.ocultarFeed()
            e.evento(IG.paquete)
        }
    }

    @Test
    fun `tres rebotes avisan el bucle`() {
        rebotar(3)
        assertTrue(Accion.AvisoBucle in e.registro)
        assertEquals(3, e.lanzamientos().size)
    }

    @Test
    fun `un evento propio no destraba la pausa`() {
        rebotar(3)
        e.evento(PROPIO)
        assertEquals(emptyList<Accion.Lanzar>(), e.evento(IG.paquete).lanzamientos())
        e.evento(LAUNCHER)
        assertEquals(1, e.evento(IG.paquete).lanzamientos().size)
    }

    @Test
    fun `al conectar con pase vigente reprograma el vencimiento`() {
        ctx.paseHasta = e.ahora + 5 * minuto
        assertEquals(
            listOf(
                Accion.Programar(Tarea.VENCER_PASE, 5 * minuto),
                Accion.Programar(Tarea.AVISO_ULTIMO_MINUTO, 4 * minuto),
            ),
            e.motor.alConectar(ctx),
        )
    }

    @Test
    fun `lanzar con un pase vencido guardado lo olvida`() {
        ctx.paseHasta = e.ahora - 1
        val acciones = e.evento(IG.paquete)
        assertTrue(Accion.OlvidarPaseVencido in acciones)
        assertNull(ctx.paseHasta)
    }

    @Test
    fun `reiniciar el motor cancela todas las tareas`() {
        e.evento(IG.paquete)
        e.entrarConPase(IG)
        assertEquals(Tarea.entries.map { Accion.Cancelar(it) }, e.aplicar(e.motor.reiniciar()))
        assertTrue(e.tareas.isEmpty())
        assertNull(e.motor.ultimoAlFrente)
    }
}
