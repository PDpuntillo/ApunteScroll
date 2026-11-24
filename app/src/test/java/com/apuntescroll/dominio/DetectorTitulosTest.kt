package com.apuntescroll.dominio

import com.apuntescroll.dominio.DetectorTitulos.TipoNumeracion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTitulosTest {

    @Test
    fun `reconoce titulos numerados con arabigos romanos y letras`() {
        for (t in listOf("1. Introducción", "2.3 Elasticidad precio", "1.2.3 Casos especiales", "3.1. Concepto", "I. Mercados", "IV - Costos", "A. Demanda", "4 - Oferta")) {
            assertTrue("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    @Test
    fun `cuenta los niveles y el tipo de la numeracion`() {
        val n = DetectorTitulos.numeracion("1.2.3 Casos especiales")!!
        assertEquals(TipoNumeracion.ARABIGA, n.tipo)
        assertEquals(3, n.niveles)
        assertEquals("1.2.3 ", n.prefijo)
        assertEquals(TipoNumeracion.ROMANA, DetectorTitulos.numeracion("IV - Costos")!!.tipo)
        assertEquals(TipoNumeracion.LETRA, DetectorTitulos.numeracion("B. Oferta")!!.tipo)
    }

    @Test
    fun `un numero seguido de minuscula o un decimal no es numeracion`() {
        assertNull(DetectorTitulos.numeracion("2.5 millones de personas"))
        assertNull(DetectorTitulos.numeracion("3 empresas compiten"))
        assertNull(DetectorTitulos.numeracion("2001 Odisea"))
    }

    @Test
    fun `las iniciales de un nombre no son una numeracion con letra`() {
        assertNull(DetectorTitulos.numeracion("J. M. Keynes"))
        assertFalse(DetectorTitulos.pareceTitulo("J. M. Keynes"))
    }

    @Test
    fun `reconoce titulos que empiezan con palabras clave`() {
        for (t in listOf("Unidad 3", "UNIDAD III: Elasticidad", "Capítulo 2 - Mercados", "Módulo 1", "Tema 4: Costos", "Parte II", "Sección A", "Bloque 2", "capitulo uno")) {
            assertTrue("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    @Test
    fun `las palabras clave comunes necesitan un numero o una letra despues`() {
        assertFalse(DetectorTitulos.pareceTitulo("Parte de la demanda"))
        assertFalse(DetectorTitulos.pareceTitulo("Tema central del curso"))
        assertFalse(DetectorTitulos.pareceTitulo("Temas"))
        assertFalse(DetectorTitulos.pareceTitulo("Unidad"))
    }

    @Test
    fun `reconoce titulos en mayusculas`() {
        assertTrue(DetectorTitulos.pareceTitulo("ELASTICIDAD INGRESO"))
        assertTrue(DetectorTitulos.pareceTitulo("OFERTA Y DEMANDA"))
        assertTrue(DetectorTitulos.pareceTitulo("¿QUÉ ES LA ELASTICIDAD?"))
    }

    @Test
    fun `siglas cortas y formulas no son titulos en mayusculas`() {
        assertFalse(DetectorTitulos.pareceTitulo("PBI = C + I + G"))
        assertFalse(DetectorTitulos.pareceTitulo("UBA – FCE"))
        assertFalse(DetectorTitulos.esMayusculas("IVA"))
    }

    @Test
    fun `no son titulos las lineas largas las que terminan en puntuacion ni las vinetas`() {
        assertFalse(DetectorTitulos.pareceTitulo("1. Introducción."))
        assertFalse(DetectorTitulos.pareceTitulo("UNIDAD 3:"))
        assertFalse(DetectorTitulos.pareceTitulo("ELASTICIDAD, CONCEPTO,"))
        assertFalse(DetectorTitulos.pareceTitulo("• OFERTA"))
        assertFalse(DetectorTitulos.pareceTitulo("La elasticidad precio de la demanda"))
        assertFalse(DetectorTitulos.pareceTitulo("1. " + "Muy largo ".repeat(9)))
    }

    @Test
    fun `numeraciones hermanas son del mismo tipo y nivel`() {
        fun n(t: String) = DetectorTitulos.numeracion(t)!!
        assertTrue(DetectorTitulos.sonHermanas(n("1. Elástica"), n("2. Inelástica")))
        assertTrue(DetectorTitulos.sonHermanas(n("A. Demanda"), n("C. Costos")))
        assertFalse(DetectorTitulos.sonHermanas(n("3. Elasticidad"), n("3.1 Concepto")))
        assertFalse(DetectorTitulos.sonHermanas(n("1. Uno"), n("A. Otro")))
    }

    @Test
    fun `pasa los titulos en mayusculas a tipo oracion conservando la numeracion`() {
        assertEquals("3.1 Elasticidad precio de la demanda", DetectorTitulos.normalizarTitulo("3.1 ELASTICIDAD PRECIO DE LA DEMANDA"))
        assertEquals("Oferta y demanda", DetectorTitulos.normalizarTitulo("OFERTA Y DEMANDA"))
        assertEquals("¿Qué es la elasticidad?", DetectorTitulos.normalizarTitulo("¿QUÉ ES LA ELASTICIDAD?"))
    }

    @Test
    fun `al pasar a tipo oracion respeta romanos y siglas y pone mayuscula despues de dos puntos y guiones`() {
        assertEquals("Unidad III: Elasticidad", DetectorTitulos.normalizarTitulo("UNIDAD III: ELASTICIDAD"))
        assertEquals("Economía I – Unidad 3", DetectorTitulos.normalizarTitulo("ECONOMÍA I – UNIDAD 3"))
        assertEquals("El PBI y sus componentes", DetectorTitulos.normalizarTitulo("EL PBI Y SUS COMPONENTES"))
        assertEquals("Política del BCRA", DetectorTitulos.normalizarTitulo("POLÍTICA DEL BCRA"))
        assertEquals("IV - Costos de producción", DetectorTitulos.normalizarTitulo("IV - COSTOS DE PRODUCCIÓN"))
    }

    @Test
    fun `los titulos que no estan en mayusculas quedan como estan`() {
        assertEquals("3.2 Clasificación según el PBI", DetectorTitulos.normalizarTitulo("  3.2   Clasificación según el PBI "))
        assertEquals("Unidad 3 – ELASTICIDAD", DetectorTitulos.normalizarTitulo("Unidad 3 – ELASTICIDAD"))
    }

    @Test
    fun `una sola letra romana es ambigua entre romano y letra`() {
        val c = DetectorTitulos.numeracion("C. Costos")
        assertNotNull(c)
        assertTrue(c!!.ambigua)
    }

    // ---- Revisión: palabras clave sin número (hallazgo 7) ----

    @Test
    fun `una palabra clave seguida de una frase comun no es titulo`() {
        for (t in listOf("Unidad de cuenta", "Unidad de medida", "Módulo de elasticidad", "Capítulo aparte", "Parte civil", "Tema a desarrollar", "Parte y contraparte")) {
            assertFalse("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    @Test
    fun `una palabra clave con numero romano letra mayuscula o numero en letras es titulo`() {
        for (t in listOf("Unidad 3", "Capítulo iv", "Módulo uno", "Sección B", "Parte segunda", "Tema XII: Costos")) {
            assertTrue("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    @Test
    fun `una palabra clave en mayusculas sigue siendo titulo por ir en mayusculas`() {
        assertTrue(DetectorTitulos.pareceTitulo("UNIDAD DE CUENTA"))
    }

    // ---- Ronda 2 (hallazgo 6) ----

    @Test
    fun `una unidad con marca de numero o calificativo es titulo`() {
        for (t in listOf(
            "Unidad Nº 3: Elasticidad", "Unidad N° 3", "Unidad N.º 3", "Unidad Nro. 3 – Elasticidad", "Unidad Temática 2: Oferta y demanda",
            "Unidad temática III", "Unidad didáctica 4: Costos", "Capítulo Nº 2: La oferta", "Módulo Nº 1", "Módulo Introductorio",
            "Capítulo uno: Introducción", "Tema único",
        )) {
            assertTrue("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    @Test
    fun `un numero en letras seguido de mas texto no hace titulo`() {
        for (t in listOf("Parte una de las causas", "Tema uno de los ejes del curso", "Parte final del contrato")) {
            assertFalse("«$t»", DetectorTitulos.pareceTitulo(t))
        }
    }

    // ---- Ronda 4 ----

    @Test
    fun `el final de un sumario con otro item numerado adentro no es titulo`() {
        // Hallazgo 6 de la ronda 4.
        assertFalse(DetectorTitulos.pareceTitulo("7.2. Efectos de las obligaciones. 8. Extinción de las obligaciones"))
        assertTrue(DetectorTitulos.pareceTitulo("Capítulo 4. La oferta y la demanda"))
    }

    @Test
    fun `separa la palabra clave pegada a su numero`() {
        // Hallazgo 7 de la ronda 4: el OCR deja "Capitulo1".
        assertEquals("Capitulo 1", DetectorTitulos.normalizarTitulo("Capitulo1"))
        assertEquals("Capítulo 2", DetectorTitulos.normalizarTitulo("CAPÍTULO2"))
        assertTrue(DetectorTitulos.esSoloPalabraClave("Capitulo1"))
        assertTrue(DetectorTitulos.esSoloPalabraClave("Unidad III"))
        assertFalse(DetectorTitulos.esSoloPalabraClave("Capítulo 2: Las personas"))
    }
}
