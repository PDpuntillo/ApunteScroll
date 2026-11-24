package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class EstructuraTest {

    /** Cada bloque como texto: "T: título", "P: párrafo", "S: línea suelta" o "L: ítem | ítem". */
    private fun bloques(vararg paginas: String): List<String> {
        val limpias = paginas.mapIndexed { i, t -> PaginaLimpia(i + 1, Limpieza.normalizarLineas(t).map(Limpieza::limpiarLinea)) }
        return Estructura.clasificar(Parrafos.armar(limpias)).map { b ->
            when (b) {
                is Bloque.Titulo -> "T: ${b.texto}"
                is Bloque.Parrafo -> (if (b.esLinea) "S: " else "P: ") + b.contenido.texto
                is Bloque.Lista -> "L: " + b.items.joinToString(" | ") { it.texto }
            }
        }
    }

    private val CUERPO_1 = "La elasticidad mide la sensibilidad de la cantidad demandada ante cambios en el precio."
    private val CUERPO_2 = "El gasto total de los consumidores depende de la elasticidad y del precio del bien."

    @Test
    fun `un titulo numerado seguido de cuerpo abre una seccion`() {
        assertEquals(listOf("T: 3.1 Elasticidad", "P: $CUERPO_1"), bloques("3.1 Elasticidad\n$CUERPO_1"))
    }

    @Test
    fun `una lista numerada corta no son titulos`() {
        val texto = "Según su valor, la demanda puede clasificarse en:\n1. Elástica\n2. Inelástica\n3. Unitaria\n$CUERPO_2"
        assertEquals(
            listOf("P: Según su valor, la demanda puede clasificarse en:", "L: 1. Elástica | 2. Inelástica | 3. Unitaria", "P: $CUERPO_2"),
            bloques(texto),
        )
    }

    @Test
    fun `un numerado corto seguido de otro numerado largo del mismo nivel es lista`() {
        val texto = "Hay dos supuestos:\n1. Competencia perfecta\n2. Información completa: todos conocen los precios.\n$CUERPO_2"
        assertEquals("L: 1. Competencia perfecta | 2. Información completa: todos conocen los precios.", bloques(texto)[1])
    }

    @Test
    fun `titulos numerados separados por cuerpo son titulos`() {
        val texto = "1. Introducción\n$CUERPO_1\n2. Oferta y demanda\n$CUERPO_2"
        assertEquals(listOf("T: 1. Introducción", "P: $CUERPO_1", "T: 2. Oferta y demanda", "P: $CUERPO_2"), bloques(texto))
    }

    @Test
    fun `titulo y subtitulo seguidos se unen en un solo titulo`() {
        assertEquals(listOf("T: 3. Elasticidad · 3.1 Concepto", "P: $CUERPO_1"), bloques("3. Elasticidad\n3.1 Concepto\n$CUERPO_1"))
        assertEquals(
            listOf("T: Unidad 3: Elasticidad · 3.1 Concepto", "P: $CUERPO_1"),
            bloques("UNIDAD 3: ELASTICIDAD\n3.1 Concepto\n$CUERPO_1"),
        )
    }

    @Test
    fun `varios titulos seguidos dejan los primeros como lineas sueltas`() {
        val texto = "UNIVERSIDAD DE BUENOS AIRES\n\nFACULTAD DE CIENCIAS ECONÓMICAS\n\nECONOMÍA I\n\nUNIDAD 3 – ELASTICIDAD\n\n$CUERPO_1"
        assertEquals(
            listOf(
                "L: UNIVERSIDAD DE BUENOS AIRES | FACULTAD DE CIENCIAS ECONÓMICAS",
                "T: Economía I · Unidad 3 – Elasticidad",
                "P: $CUERPO_1",
            ),
            bloques(texto),
        )
    }

    @Test
    fun `un titulo al final del documento queda como cuerpo`() {
        assertEquals(listOf("P: $CUERPO_1", "P: BIBLIOGRAFÍA"), bloques("$CUERPO_1\n\nBIBLIOGRAFÍA"))
    }

    @Test
    fun `un titulo en mayusculas pasa a tipo oracion`() {
        assertEquals(listOf("P: $CUERPO_1", "T: Elasticidad ingreso", "P: $CUERPO_2"), bloques("$CUERPO_1\n\nELASTICIDAD INGRESO\n$CUERPO_2"))
    }

    @Test
    fun `las vinetas y enumeraciones seguidas forman una sola lista`() {
        val texto = "Ejemplos:\n• Materias primas\n• Mano de obra\n\na) Energía\nb) Transporte de la mercadería hasta el puerto de\nBuenos Aires\n\n$CUERPO_1"
        assertEquals(
            listOf("P: Ejemplos:", "L: • Materias primas | • Mano de obra | a) Energía | b) Transporte de la mercadería hasta el puerto de Buenos Aires", "P: $CUERPO_1"),
            bloques(texto),
        )
    }

    @Test
    fun `un renglon de indice nunca es titulo`() {
        val texto = "ÍNDICE\n1. Introducción ........ 3\n1.1 Concepto ........ 4\n2. Oferta ........ 9\n\n$CUERPO_1"
        assertEquals(
            listOf("T: Índice", "L: • 1. Introducción | • 1.1 Concepto | • 2. Oferta", "P: $CUERPO_1"),
            bloques(texto),
        )
    }

    @Test
    fun `un parrafo de cuerpo largo en mayusculas no es titulo`() {
        val largo = "ESTE PÁRRAFO ESTÁ ESCRITO TODO EN MAYÚSCULAS PORQUE ES UNA ADVERTENCIA MUY IMPORTANTE PARA EL PARCIAL"
        assertEquals(listOf("P: $largo", "P: $CUERPO_1"), bloques("$largo\n\n$CUERPO_1"))
    }

    @Test
    fun `las lineas sueltas seguidas forman un bloque de lineas`() {
        val texto = "$CUERPO_1\nTabla 1. Ejemplo numérico\nPrecio Cantidad Gasto\n10 100 1000\n8 140 1120\n$CUERPO_2"
        assertEquals(
            listOf("P: $CUERPO_1", "L: Tabla 1. Ejemplo numérico | Precio Cantidad Gasto | 10 100 1000 | 8 140 1120", "P: $CUERPO_2"),
            bloques(texto),
        )
    }

    @Test
    fun `una linea suelta justo antes de una lista la encabeza`() {
        val texto = "$CUERPO_1\nPreguntas de repaso\n1) ¿Qué mide la elasticidad?\n2) ¿Cuándo es inelástica?"
        assertEquals(
            listOf("P: $CUERPO_1", "L: Preguntas de repaso | 1) ¿Qué mide la elasticidad? | 2) ¿Cuándo es inelástica?"),
            bloques(texto),
        )
    }

    @Test
    fun `una linea suelta sola queda en su propio renglon`() {
        assertEquals(listOf("P: $CUERPO_1", "S: Actividad práctica", "P: $CUERPO_2"), bloques("$CUERPO_1\nActividad práctica\n$CUERPO_2"))
    }

    @Test
    fun `una linea corta que termina en punto no es linea suelta`() {
        assertEquals(listOf("P: Fin del tema.", "P: $CUERPO_2"), bloques("Fin del tema.\n\n$CUERPO_2"))
    }

    @Test
    fun `la pagina del titulo es la de su linea`() {
        val limpias = listOf(
            PaginaLimpia(4, listOf(CUERPO_1)),
            PaginaLimpia(5, listOf("4.2 Costo marginal", CUERPO_2)),
        )
        val titulo = Estructura.clasificar(Parrafos.armar(limpias)).filterIsInstance<Bloque.Titulo>().single()
        assertEquals(5, titulo.pagina)
    }
}
