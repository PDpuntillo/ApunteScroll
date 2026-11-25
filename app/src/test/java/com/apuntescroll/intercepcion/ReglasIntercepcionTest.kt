package com.apuntescroll.intercepcion

import com.apuntescroll.intercepcion.reglas.ConfigIntercepcion
import com.apuntescroll.intercepcion.reglas.ConfigRed
import com.apuntescroll.intercepcion.reglas.Decision
import com.apuntescroll.intercepcion.reglas.Entorno
import com.apuntescroll.intercepcion.reglas.ModoIntercepcion
import com.apuntescroll.intercepcion.reglas.Razon
import com.apuntescroll.intercepcion.reglas.Redes
import com.apuntescroll.intercepcion.reglas.ReglasIntercepcion
import com.apuntescroll.intercepcion.reglas.Visita
import org.junit.Assert.assertEquals
import org.junit.Test

class ReglasIntercepcionTest {

    private val ig = Redes.INSTAGRAM
    private val x = Redes.X
    private val normal = Entorno(pantallaEnUso = true, appFijada = false, enLlamada = false)

    private fun config(
        modo: ModoIntercepcion = ModoIntercepcion.PASE,
        activada: Boolean = true,
        consentida: Boolean = true,
    ) = ConfigIntercepcion(consentida, mapOf(ig.paquete to ConfigRed(activada, modo)), 15, 10)

    private fun decidir(
        config: ConfigIntercepcion = config(),
        paseVigente: Boolean = false,
        visita: Visita? = null,
        redEnFeed: String? = null,
        entorno: Entorno = normal,
    ) = ReglasIntercepcion.decidir(ig, config, paseVigente, visita, redEnFeed) { entorno }

    private fun dejar(razon: Razon) = Decision.Dejar(razon)
    private val interceptar = Decision.Interceptar(ig)

    @Test
    fun `sin consentimiento deja pasar aunque sea Instagram`() {
        assertEquals(dejar(Razon.SIN_CONSENTIMIENTO), decidir(config(consentida = false)))
    }

    @Test
    fun `red apagada deja pasar`() {
        assertEquals(dejar(Razon.RED_APAGADA), decidir(config(activada = false)))
    }

    @Test
    fun `pase sin pase intercepta`() {
        assertEquals(interceptar, decidir())
    }

    @Test
    fun `pase vigente deja pasar`() {
        assertEquals(dejar(Razon.PASE_VIGENTE), decidir(paseVigente = true))
    }

    @Test
    fun `pase vencido intercepta`() {
        assertEquals(interceptar, decidir(paseVigente = false))
    }

    @Test
    fun `el pase no vale para una red en recordatorio`() {
        assertEquals(interceptar, decidir(config(ModoIntercepcion.RECORDATORIO), paseVigente = true))
    }

    @Test
    fun `el pase no vale para una red en reemplazo total`() {
        assertEquals(interceptar, decidir(config(ModoIntercepcion.REEMPLAZO_TOTAL), paseVigente = true))
    }

    @Test
    fun `recordatorio con la visita de esta red deja pasar`() {
        assertEquals(
            dejar(Razon.VISITA_PERMITIDA),
            decidir(config(ModoIntercepcion.RECORDATORIO), visita = Visita(ig.paquete, porLlamada = false)),
        )
    }

    @Test
    fun `recordatorio con la visita de otra red intercepta`() {
        assertEquals(interceptar, decidir(config(ModoIntercepcion.RECORDATORIO), visita = Visita(x.paquete, porLlamada = false)))
    }

    @Test
    fun `reemplazo total ignora ir igual`() {
        assertEquals(interceptar, decidir(config(ModoIntercepcion.REEMPLAZO_TOTAL), visita = Visita(ig.paquete, porLlamada = false)))
    }

    @Test
    fun `una visita por llamada deja pasar en cualquier modo`() {
        ModoIntercepcion.entries.forEach { modo ->
            assertEquals(
                modo.name,
                dejar(Razon.VISITA_LLAMADA),
                decidir(config(modo), visita = Visita(ig.paquete, porLlamada = true)),
            )
        }
    }

    @Test
    fun `feed a la vista con la misma red deja pasar`() {
        assertEquals(dejar(Razon.FEED_A_LA_VISTA), decidir(redEnFeed = ig.paquete))
    }

    @Test
    fun `feed a la vista con otra red intercepta`() {
        assertEquals(interceptar, decidir(redEnFeed = x.paquete))
    }

    @Test
    fun `pantalla bloqueada deja pasar`() {
        assertEquals(dejar(Razon.PANTALLA_NO_EN_USO), decidir(entorno = normal.copy(pantallaEnUso = false)))
    }

    @Test
    fun `app fijada deja pasar`() {
        assertEquals(dejar(Razon.APP_FIJADA), decidir(entorno = normal.copy(appFijada = true)))
    }

    @Test
    fun `en llamada deja pasar`() {
        assertEquals(dejar(Razon.EN_LLAMADA), decidir(entorno = normal.copy(enLlamada = true)))
    }

    @Test
    fun `el entorno no se consulta si una regla anterior ya decidio`() {
        var lecturas = 0
        val leer = { lecturas++; normal }
        ReglasIntercepcion.decidir(ig, config(consentida = false), false, null, null, leer)
        ReglasIntercepcion.decidir(ig, config(activada = false), false, null, null, leer)
        ReglasIntercepcion.decidir(ig, config(), true, null, null, leer)
        ReglasIntercepcion.decidir(ig, config(), false, null, ig.paquete, leer)
        assertEquals(0, lecturas)
        ReglasIntercepcion.decidir(ig, config(), false, null, null, leer)
        assertEquals(1, lecturas)
    }
}
