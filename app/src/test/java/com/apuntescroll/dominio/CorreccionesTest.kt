package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class CorreccionesTest {

    private fun unir(linea: String) = Correcciones.unirLetrasEspaciadas(linea)
    private fun ocr(linea: String) = Correcciones.corregirOcr(linea)

    // ---- Letras espaciadas (ronda 4, hallazgo 2) ----

    @Test
    fun `une las letras espaciadas de un titulo`() {
        assertEquals("MANUAL PRÁCTICO", unir("M A N U A L  P R Á C T I CO"))
        assertEquals("INICIO", unir("I N I C I O"))
    }

    @Test
    fun `une las letras espaciadas de una pestana con romanos y numeros`() {
        assertEquals("PARTE I · 1", unir("PA RT E  I  ·  1"))
        assertEquals("PARTE II · 3", unir("PA RT E  I I  ·  3"))
    }

    @Test
    fun `no toca el texto normal ni las palabras cortas comunes`() {
        for (l in listOf("La demanda cae cuando sube el precio.", "y la de  esa", "Precio  10  20", "a b", "El PBI y el IVA")) {
            assertEquals(l, unir(l))
        }
    }

    @Test
    fun `la normalizacion une las letras espaciadas antes de colapsar los espacios`() {
        assertEquals(listOf("GUÍA DE EJERCICIOS"), Limpieza.normalizarLineas("G U Í A  D E  E J E R C I C I O S"))
    }

    // ---- Errores del OCR (ronda 4, hallazgo 5) ----

    @Test
    fun `EI suelto es El y en una linea en mayusculas es EL`() {
        assertEquals("El contrato es un acuerdo de partes.", ocr("EI contrato es un acuerdo de partes."))
        assertEquals("EL RÉGIMEN DE LOS CONTRATOS", ocr("EI RÉGIMEN DE LOS CONTRATOS"))
        assertEquals("REINO y EIDER", ocr("REINO y EIDER"))
    }

    @Test
    fun `gue suelto es que`() {
        assertEquals("Lo que dice la norma es que el plazo vence. Que se cumpla.", ocr("Lo gue dice la norma es gue el plazo vence. Gue se cumpla."))
        assertEquals("guerra y sangre", ocr("guerra y sangre"))
    }

    @Test
    fun `un cero pegado a una letra es una o`() {
        assertEquals("Asimismo, no se aplica a los sujetoS", ocr("Asimism0, n0 se aplica a los sujet0S"))
    }

    @Test
    fun `no toca variables siglas ni numeros con cero`() {
        for (l in listOf("El precio P0 y la cantidad Q0", "el valor x0", "la fase F0", "el G20 se reunió", "un auto 0km", "en 2020 y 1990", "CO2 y H2O")) {
            assertEquals(l, ocr(l))
        }
    }

    @Test
    fun `quita las llamadas a notas vacias pegadas a una palabra`() {
        assertEquals("la norma general.", ocr("la norma general (\")."))
        assertEquals("una regla que se aplica", ocr("una regla () que se aplica"))
        assertEquals("obligaciones solidarias o conjuntas", ocr("obligaciones solidarias ()o conjuntas"))
        assertEquals("los contratos, los delitos y las leyes", ocr("los contratos (\"), los delitos ( ) y las leyes"))
    }

    @Test
    fun `no quita una casilla para marcar al principio`() {
        assertEquals("( ) Verdadero", ocr("( ) Verdadero"))
    }
}
