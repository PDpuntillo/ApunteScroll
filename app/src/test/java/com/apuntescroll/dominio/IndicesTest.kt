package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicesTest {

    private fun vacias(lineas: List<String>) = lineas.all { it.isEmpty() }

    @Test
    fun `quita un indice digital con numeros de pagina y su continuacion`() {
        val portada = listOf("Manual de prácticas", "Edición de prueba")
        val indice1 = listOf("Índice", "Mapa general del curso 5", "Cómo usar este manual 5", "Parte I · 1 · Conceptos y convenciones 7",
            "Primeras definiciones 7", "Medidas y unidades 8", "Parte I · 2 · Requisitos previos 11", "Materiales necesarios 11")
        val indice2 = listOf("Parte II · 1 · Procedimientos 15", "Preparación 15", "Ejecución paso a paso 17", "Controles 19",
            "Parte II · 2 · Errores frecuentes 22", "Qué no hacer 23")
        val contenido = listOf("Mapa general del curso", "Este manual reúne los procedimientos que se ven en las clases prácticas.")
        val resultado = Indices.quitar(listOf(portada, indice1, indice2, contenido))
        assertEquals(portada, resultado[0])
        assertTrue(vacias(resultado[1]))
        assertTrue(vacias(resultado[2]))
        assertEquals(contenido, resultado[3])
    }

    @Test
    fun `quita un indice de OCR sin numeros hasta donde vuelve a empezar el primer capitulo`() {
        val pagina = listOf(
            "Prólogo breve de la obra.", "", "ÍNDICE", "", "Capitulo1", "", "Las fuentes del derecho", "", "Capitulo 2", "",
            "Las personas", "", "Capitulo 3", "", "Los contratos en general", "",
            "Capitulo1", "", "Las fuentes del derecho", "", "El derecho se apoya en varias fuentes que conviene distinguir.",
        )
        val resultado = Indices.quitar(listOf(pagina)).single()
        assertEquals("Prólogo breve de la obra.", resultado[0])
        assertTrue(resultado.subList(2, 16).all { it.isEmpty() })
        assertEquals(pagina.subList(16, pagina.size), resultado.subList(16, resultado.size))
    }

    @Test
    fun `una tabla de datos que termina en numeros no es un indice`() {
        val tabla = listOf("Exportaciones por rubro", "Cereales 45", "Carnes 30", "Combustibles 12", "Manufacturas 9", "Servicios 4")
        assertEquals(listOf(tabla), Indices.quitar(listOf(tabla)))
    }

    @Test
    fun `en un mazo solo se quita la diapositiva de contenidos`() {
        val agenda = listOf("CONTENIDOS", "• La oferta", "• La demanda", "• El equilibrio")
        val diapositivas = listOf(listOf("LA OFERTA", "• Primera idea."), listOf("LA DEMANDA", "• Segunda idea."))
        val resultado = Indices.quitar(listOf(agenda) + diapositivas)
        assertTrue(vacias(resultado[0]))
        assertEquals(diapositivas, resultado.drop(1))
    }

    @Test
    fun `un indice que no vuelve a aparecer se corta en la primera pagina que no parece indice`() {
        val indice = listOf("Tabla de contenidos", "Introducción general 3", "Primera parte 9", "Segunda parte 21", "Tercera parte 40", "Anexos 55")
        val texto = (1..20).map { "Renglón $it de una página de texto corrido que no tiene números de página al final." }
        val resultado = Indices.quitar(listOf(indice, texto, texto))
        assertTrue(vacias(resultado[0]))
        assertEquals(listOf(texto, texto), resultado.drop(1))
    }
}
