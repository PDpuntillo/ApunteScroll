package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParrafosTest {

    /** Arma los párrafos de páginas ya sin encabezados (cada texto es una página, desde la 1). */
    private fun armar(vararg paginas: String): List<ParrafoCrudo> =
        Parrafos.armar(paginas.mapIndexed { i, t -> PaginaLimpia(i + 1, Limpieza.normalizarLineas(t).map(Limpieza::limpiarLinea)) })

    private fun textos(vararg paginas: String) = armar(*paginas).map { it.texto }

    @Test
    fun `une las lineas de un parrafo con un espacio`() {
        assertEquals(
            listOf("La economía estudia cómo las sociedades asignan recursos escasos entre usos alternativos."),
            textos("La economía estudia cómo las sociedades asignan\nrecursos escasos entre usos alternativos."),
        )
    }

    @Test
    fun `une las palabras cortadas con guion al final de la linea`() {
        assertEquals(
            listOf("La elasticidad mide la sensibilidad de la cantidad demandada ante cambios en el precio."),
            textos("La elasticidad mide la sensibilidad de la cantidad deman-\ndada ante cambios en el precio."),
        )
    }

    @Test
    fun `conserva el guion si la linea siguiente empieza en mayuscula o con un numero`() {
        assertEquals(
            listOf("El conflicto Norte-Sur marcó la década 1990-2000 en toda la región latinoamericana."),
            textos("El conflicto Norte-\nSur marcó la década 1990-\n2000 en toda la región latinoamericana."),
        )
    }

    @Test
    fun `una linea vacia siempre separa parrafos`() {
        assertEquals(listOf("Primer bloque sin punto", "segundo bloque."), textos("Primer bloque sin punto\n\nsegundo bloque."))
    }

    @Test
    fun `termina el parrafo en una linea corta con punto seguida de mayuscula`() {
        val texto = """
            La demanda de un bien depende de su precio, del ingreso de los consumidores y de
            los precios de los bienes relacionados, entre otros factores importantes.
            Las preferencias también influyen en la cantidad demandada de cada bien.
            El ingreso es la variable que más se estudia en el curso de este año lectivo.
            Por eso lo vemos.
            Sigue otro párrafo con más texto que ocupa una línea completa de la hoja.
        """.trimIndent()
        val parrafos = textos(texto)
        assertEquals(2, parrafos.size)
        assertEquals(true, parrafos[0].startsWith("La demanda de un bien"))
        assertEquals(true, parrafos[0].endsWith("este año lectivo. Por eso lo vemos."))
        assertEquals("Sigue otro párrafo con más texto que ocupa una línea completa de la hoja.", parrafos[1])
    }

    @Test
    fun `no corta en una linea larga que termina en punto si sigue mayuscula`() {
        val texto = """
            La oferta agregada muestra la relación entre el nivel de precios y la producción.
            Keynes sostuvo que en el corto plazo la oferta agregada puede ser casi horizontal
            cuando hay desempleo, porque las empresas pueden producir más sin subir precios.
        """.trimIndent()
        assertEquals(1, textos(texto).size)
    }

    @Test
    fun `una linea corta sin punto seguida de mayuscula queda sola`() {
        val texto = """
            3.1 Elasticidad precio
            La elasticidad precio de la demanda mide cuánto cambia la cantidad demandada
            cuando cambia el precio, manteniendo todo lo demás constante en el análisis.
        """.trimIndent()
        assertEquals("3.1 Elasticidad precio", textos(texto).first())
        assertEquals(2, textos(texto).size)
    }

    @Test
    fun `una linea corta que termina en preposicion no corta el parrafo`() {
        val texto = """
            El modelo fue desarrollado en la década del cincuenta por economistas que seguían
            las ideas de
            Keynes y de sus discípulos de la Universidad de Cambridge en el Reino Unido.
        """.trimIndent()
        assertEquals(1, textos(texto).size)
    }

    @Test
    fun `cada vineta empieza un item y sus lineas de continuacion se le unen`() {
        val texto = """
            Los factores son los siguientes:
            • La existencia de bienes sustitutos cercanos: cuantos más sustitutos tenga un
            bien, más elástica será su demanda.
            • La proporción del ingreso destinada al bien.
        """.trimIndent()
        assertEquals(
            listOf(
                "Los factores son los siguientes:",
                "• La existencia de bienes sustitutos cercanos: cuantos más sustitutos tenga un bien, más elástica será su demanda.",
                "• La proporción del ingreso destinada al bien.",
            ),
            textos(texto),
        )
    }

    @Test
    fun `una enumeracion despues de dos puntos empieza un item`() {
        val texto = "La demanda puede ser:\na) elástica, si reacciona mucho;\nb) inelástica, si reacciona poco."
        assertEquals(listOf("La demanda puede ser:", "a) elástica, si reacciona mucho;", "b) inelástica, si reacciona poco."), textos(texto))
    }

    @Test
    fun `un numero al principio de una linea a mitad de oracion no corta el parrafo`() {
        val texto = """
            Las importaciones crecieron de manera sostenida durante toda la década hasta el año
            2001. Luego cayeron de forma abrupta por la devaluación y la crisis financiera.
        """.trimIndent()
        assertEquals(1, textos(texto).size)
    }

    @Test
    fun `un parrafo que sigue en la pagina siguiente se une y pertenece a la primera`() {
        val pagina1 = """
            La crisis de 1930 afectó profundamente a la economía argentina, que dependía de
            la exportación de productos agropecuarios y del financiamiento externo, con el
        """.trimIndent()
        val pagina2 = """
            objetivo de evitar nuevas crisis.
            Otro párrafo distinto.
        """.trimIndent()
        val parrafos = armar(pagina1, pagina2)
        assertEquals(2, parrafos.size)
        assertEquals(1, parrafos[0].pagina)
        val texto = parrafos[0].texto
        assertEquals(true, texto.endsWith("con el objetivo de evitar nuevas crisis."))
        assertEquals(1, parrafos[0].contenido.paginaEn(0))
        assertEquals(2, parrafos[0].contenido.paginaEn(texto.indexOf("objetivo")))
        assertEquals(2, parrafos[1].pagina)
    }

    @Test
    fun `una palabra cortada entre dos paginas se une`() {
        val parrafos = textos("El proceso de indus-", "trialización avanzó.")
        assertEquals(listOf("El proceso de industrialización avanzó."), parrafos)
    }

    @Test
    fun `no une entre paginas si la primera termina la frase`() {
        val pagina1 = "La industria creció mucho durante la década del treinta en todo el país entero."
        val pagina2 = "Los salarios también subieron en esos años de fuerte crecimiento del sector."
        assertEquals(2, textos(pagina1, pagina2).size)
    }

    @Test
    fun `no une entre paginas si la siguiente empieza con un titulo o una vineta`() {
        val pagina1 = "El texto de la primera página termina sin punto pero es una línea larga de verdad"
        assertEquals(2, textos(pagina1, "3.2 Oferta").size)
        assertEquals(2, textos(pagina1, "• Un ítem").size)
    }

    @Test
    fun `una linea muy corta sin punto despues de una oracion completa queda sola`() {
        val texto = """
            Varian, H. R. (2010). Microeconomía intermedia: un enfoque actual. Barcelona: Bosch.
            Krugman, P. y Wells, R. (2015). Microeconomía, segunda edición. Barcelona: Reverté.
            Preguntas de repaso
            1) ¿Qué mide la elasticidad precio?
        """.trimIndent()
        assertEquals("Preguntas de repaso", textos(texto)[1])
    }

    @Test
    fun `un titulo despues de una linea larga con punto empieza parrafo nuevo`() {
        val texto = """
            La elasticidad precio de la demanda mide cuánto cambia la cantidad demandada al
            variar el precio, y su valor depende de los sustitutos y del horizonte temporal.
            ELASTICIDAD INGRESO
            La elasticidad ingreso mide cómo varía la cantidad demandada cuando cambia el
            ingreso de los consumidores, con todo lo demás constante en el análisis.
        """.trimIndent()
        assertEquals("ELASTICIDAD INGRESO", textos(texto)[1])
        assertEquals(3, textos(texto).size)
    }

    // ---- Revisión: notas al pie y enumeraciones arriba de la página (hallazgos 2 y 8) ----

    private val PAGINA_CON_NOTA = """
        La crisis de 1930 afectó profundamente a la economía argentina, que dependía de
        la exportación de productos agropecuarios¹. La caída de los precios redujo los
        ingresos del Estado y obligó a replantear el modelo, con el proceso de indus-
        ¹ Ver Rapoport, M. (2000), Historia económica, cap. 4.
    """.trimIndent()

    private val PAGINA_SIGUIENTE = """
        trialización por sustitución de importaciones que se consolidó en la década.
        La industria liviana creció sobre todo en el sector textil y en el alimenticio.
    """.trimIndent()

    @Test
    fun `una nota al pie no se mete en el parrafo que sigue en la pagina siguiente`() {
        val parrafos = textos(PAGINA_CON_NOTA, PAGINA_SIGUIENTE)
        assertEquals(true, parrafos.first().contains("con el proceso de industrialización por sustitución de importaciones"))
        assertEquals("¹ Ver Rapoport, M. (2000), Historia económica, cap. 4.", parrafos.last())
        assertEquals(1, parrafos.count { "Rapoport" in it })
    }

    @Test
    fun `una nota con numero comun se aparta si el cuerpo la llama`() {
        val conLlamadaVolada = PAGINA_CON_NOTA.replace("¹ Ver", "1 Ver")
        val conLlamadaComun = conLlamadaVolada.replace("agropecuarios¹", "agropecuarios1")
        for (pagina in listOf(conLlamadaVolada, conLlamadaComun)) {
            val parrafos = textos(pagina, PAGINA_SIGUIENTE)
            assertEquals(true, parrafos.first().contains("con el proceso de industrialización por sustitución"))
            assertEquals("1 Ver Rapoport, M. (2000), Historia económica, cap. 4.", parrafos.last())
        }
    }

    @Test
    fun `una nota al pie despues de una linea abierta sin guion tambien se aparta`() {
        val pagina = PAGINA_CON_NOTA.replace("con el proceso de indus-", "con el proceso de")
        val parrafos = textos(pagina, "industrialización por sustitución de importaciones que se consolidó.")
        assertEquals(true, parrafos.first().endsWith("con el proceso de industrialización por sustitución de importaciones que se consolidó."))
        assertEquals("¹ Ver Rapoport, M. (2000), Historia económica, cap. 4.", parrafos.last())
    }

    @Test
    fun `un numero comun a mitad de frase al pie de la pagina no es una nota`() {
        val pagina1 = """
            La integración regional avanzó durante los años noventa, cuando se sumaron a los
            15 Estados miembros de la unión aduanera varios países asociados de la región
        """.trimIndent()
        val parrafos = textos(pagina1, "que firmaron acuerdos de libre comercio con el bloque.")
        assertEquals(1, parrafos.size)
    }

    @Test
    fun `una linea que empieza con un numero volado empieza parrafo`() {
        val texto = "Los precios cayeron fuertemente durante toda la década del treinta en el país²\n² Ver Rapoport, cap. 4."
        assertEquals(2, textos(texto).size)
    }

    @Test
    fun `una enumeracion en minuscula arriba de la pagina no sigue el parrafo anterior`() {
        val pagina1 = "La elasticidad precio mide la sensibilidad de la cantidad ante cambios del precio.\nSegún su valor, la demanda puede ser:"
        val pagina2 = """
            a) elástica, si la cantidad reacciona más que proporcionalmente;
            b) inelástica, si reacciona menos que proporcionalmente;
            c) unitaria, si reacciona en la misma proporción que el precio.
        """.trimIndent()
        val parrafos = textos(pagina1, pagina2)
        assertEquals(true, parrafos[0].endsWith("la demanda puede ser:"))
        assertEquals("a) elástica, si la cantidad reacciona más que proporcionalmente;", parrafos[1])
        assertEquals(4, parrafos.size)
    }

    // ---- Ronda 3 (hallazgo 6) ----

    @Test
    fun `un titulo largo seguido del texto queda solo`() {
        val texto = """
            curva de demanda se desplaza hacia la derecha o hacia la izquierda según el caso analizado.
            3.1.2 Efectos de los impuestos sobre el equilibrio del mercado
            Un impuesto por unidad vendida introduce una brecha entre el precio que pagan los compradores
            y el precio que reciben los vendedores. La cantidad intercambiada disminuye y se genera una
        """.trimIndent()
        assertEquals("3.1.2 Efectos de los impuestos sobre el equilibrio del mercado", textos(texto)[1])
    }

    @Test
    fun `un parrafo escrito en mayusculas no se parte en sus renglones`() {
        val advertencia = "ESTE PÁRRAFO ESTÁ ESCRITO TODO EN MAYÚSCULAS PORQUE ES UNA ADVERTENCIA\nMUY IMPORTANTE PARA EL PARCIAL Y NO DEBE PARTIRSE EN DOS PEDAZOS."
        val antes = "Texto normal que cierra una oración completa al final de esta línea larga de la prueba."
        val despues = "Otro párrafo distinto que sigue después de la advertencia en mayúsculas."
        val unida = advertencia.replace("\n", " ")
        // Como bloque aparte (entre líneas vacías) queda entero y solo.
        assertEquals(listOf(antes, unida, despues), textos("$antes\n\n$advertencia\n\n$despues"))
        // Pegado al texto, puede unirse a los vecinos, pero nunca se corta entre sus renglones.
        assertTrue(textos("$antes\n$advertencia\n$despues").any { unida in it })
    }

    @Test
    fun `una linea que parece titulo en el medio de una oracion no corta el parrafo`() {
        val texto = """
            La crítica más conocida a este enfoque aparece en el texto de Mankiw, que en su
            Capítulo 5 desarrolla la idea con varios ejemplos de bienes de consumo masivo
            Keynes, en cambio, había puesto el acento en la demanda agregada de la economía.
        """.trimIndent()
        assertEquals(1, textos(texto).size)
    }
}
