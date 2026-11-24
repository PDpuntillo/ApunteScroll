package com.apuntescroll.dominio

import com.apuntescroll.dominio.Comprobaciones.assertConservaPalabras
import com.apuntescroll.dominio.Comprobaciones.assertTarjetasBienFormadas
import com.apuntescroll.dominio.Comprobaciones.describir
import com.apuntescroll.dominio.Comprobaciones.palabrasEsperadas
import com.apuntescroll.dominio.Comprobaciones.todasLasTarjetas
import com.apuntescroll.dominio.EjemplosApuntes.DERECHO
import com.apuntescroll.dominio.EjemplosApuntes.ECONOMIA
import com.apuntescroll.dominio.EjemplosApuntes.EJERCICIOS
import com.apuntescroll.dominio.EjemplosApuntes.ENCABEZADO_1
import com.apuntescroll.dominio.EjemplosApuntes.ENCABEZADO_2
import com.apuntescroll.dominio.EjemplosApuntes.ENCABEZADO_HISTORIA
import com.apuntescroll.dominio.EjemplosApuntes.HISTORIA_SIN_TITULOS
import com.apuntescroll.dominio.EjemplosApuntes.OCR_COSTOS
import com.apuntescroll.dominio.EjemplosApuntes.PORTADA_E_INDICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmadorTarjetasTest {

    private val LARGOS = listOf(300, 450, 600)

    private fun armar(paginas: List<PaginaExtraida>, max: Int = ArmadorTarjetas.MAX_PREDETERMINADO) = ArmadorTarjetas.armar(paginas, max)

    private fun tarjetaCon(doc: DocumentoArmado, fragmento: String): TarjetaArmada =
        todasLasTarjetas(doc).firstOrNull { fragmento in it.texto }
            ?: throw AssertionError("Ninguna tarjeta contiene «$fragmento»:\n${describir(doc)}")

    private fun esEncabezadoEconomia(linea: String) = linea == ENCABEZADO_1 || linea == ENCABEZADO_2 || Limpieza.esNumeroPagina(linea)

    // ---- Casos borde ----

    @Test
    fun `sin paginas da un documento vacio`() {
        assertEquals(DocumentoArmado(emptyList()), armar(emptyList()))
    }

    @Test
    fun `las paginas en blanco o solo con simbolos se ignoran`() {
        val doc = armar(listOf(PaginaExtraida(1, "  \n\t "), PaginaExtraida(2, ""), PaginaExtraida(3, "....\n* * *\n3")))
        assertEquals(DocumentoArmado(emptyList()), doc)
    }

    @Test
    fun `una sola oracion da una seccion con una tarjeta`() {
        val doc = armar(listOf(PaginaExtraida(5, "La economía estudia la asignación de recursos escasos.")))
        assertEquals(
            DocumentoArmado(listOf(SeccionArmada(null, 5, listOf(TarjetaArmada("La economía estudia la asignación de recursos escasos.", 5))))),
            doc,
        )
    }

    @Test
    fun `un maximo menor que 100 se sube a 100`() {
        val texto = (1..60).joinToString(" ") { "palabra$it" } + "."
        val conCincuenta = armar(listOf(PaginaExtraida(1, texto)), 50)
        assertEquals(armar(listOf(PaginaExtraida(1, texto)), 100), conCincuenta)
        assertTrue(todasLasTarjetas(conCincuenta).all { it.texto.length <= 100 })
        assertTrue(todasLasTarjetas(conCincuenta).any { it.texto.length > 50 })
    }

    @Test
    fun `el resultado es estable para la misma entrada`() {
        for (max in LARGOS) assertEquals(armar(ECONOMIA, max), armar(ECONOMIA.map { it.copy() }, max))
    }

    @Test
    fun `ordena las paginas por numero`() {
        assertEquals(armar(ECONOMIA), armar(ECONOMIA.reversed()))
    }

    @Test
    fun `acepta finales de linea de Windows`() {
        val windows = ECONOMIA.map { it.copy(texto = it.texto.replace("\n", "\r\n")) }
        assertEquals(armar(ECONOMIA), armar(windows))
    }

    @Test
    fun `un texto largo sin ninguna puntuacion se corta igual sin pasarse del maximo`() {
        val texto = (1..400).joinToString(" ") { "palabra$it" }
        val lineas = (1..400).chunked(8).joinToString("\n") { grupo -> grupo.joinToString(" ") { "palabra$it" } }
        for (max in LARGOS + 100) {
            val doc = armar(listOf(PaginaExtraida(1, lineas)), max)
            assertTrue(todasLasTarjetas(doc).all { it.texto.length <= max })
            assertConservaPalabras(palabrasEsperadas(listOf(PaginaExtraida(1, texto))), doc)
        }
    }

    // ---- Apunte digital de Economía (PdfBox) ----

    @Test
    fun `economia arma una seccion por titulo con su pagina`() {
        val doc = armar(ECONOMIA)
        assertEquals(
            listOf(
                "Unidad 3: Elasticidad · 3.1 Elasticidad precio de la demanda" to 1,
                "3.2 Clasificación" to 2,
                "Elasticidad ingreso" to 3,
                "3.3 Elasticidad cruzada" to 4,
            ),
            doc.secciones.map { it.titulo to it.paginaInicio },
        )
    }

    @Test
    fun `economia quita encabezados pies y numeros de pagina`() {
        val texto = describir(armar(ECONOMIA))
        assertFalse(texto, texto.contains("Facultad de Ciencias Económicas"))
        assertFalse(texto, texto.contains("Economía I – Unidad 3"))
        assertFalse(texto, texto.contains("Página 3"))
        assertFalse(texto, texto.contains("Pág. 4"))
    }

    @Test
    fun `economia une las palabras cortadas con guion`() {
        val texto = todasLasTarjetas(armar(ECONOMIA)).joinToString(" ") { it.texto }
        for (palabra in listOf("cantidad demandada", "la variación porcentual", "los ingresos ante", "positiva", "advertía", "alimentos")) {
            assertTrue("Falta «$palabra»", texto.contains(palabra))
        }
        assertFalse(texto.contains("-\n"))
        assertFalse(texto.contains("deman- dada"))
    }

    @Test
    fun `economia une el parrafo que cruza de pagina y lo deja en la pagina donde empieza`() {
        val doc = armar(ECONOMIA)
        val tarjeta = tarjetaCon(doc, "reaccionan de manera inmediata ante los cambios de precios.")
        assertEquals(2, tarjeta.pagina)
        assertEquals("3.2 Clasificación", doc.secciones.single { tarjeta in it.tarjetas }.titulo)
    }

    @Test
    fun `economia mantiene la lista numerada junto a su presentacion`() {
        val tarjeta = tarjetaCon(armar(ECONOMIA), "1. Elástica")
        assertTrue(tarjeta.texto, tarjeta.texto.startsWith("Según su valor, la demanda puede clasificarse en:\n1. Elástica\n2. Inelástica\n3. Unitaria"))
    }

    @Test
    fun `economia mantiene la lista con vinetas entera con sus items unidos`() {
        val tarjeta = tarjetaCon(armar(ECONOMIA, 450), "• La existencia")
        assertEquals(
            "Según el Dr. Mankiw (cap. 5, pág. 98), los factores que determinan la elasticidad son los siguientes:\n" +
                "• La existencia de bienes sustitutos cercanos: cuantos más sustitutos tenga un bien, más elástica será su demanda.\n" +
                "• La proporción del ingreso destinada al bien.\n" +
                "• El horizonte temporal considerado, ya que en el largo plazo los consumidores pueden adaptar sus hábitos.",
            tarjeta.texto,
        )
    }

    @Test
    fun `economia no corta en abreviaturas iniciales ni decimales`() {
        val doc = armar(ECONOMIA, 300)
        tarjetaCon(doc, "Según el Dr. Mankiw (cap. 5, pág. 98), los factores")
        tarjetaCon(doc, "J. M. Keynes ya advertía")
        tarjetaCon(doc, "de 0.5 en valor absoluto")
        tarjetaCon(doc, "(por ej. el café y el té)")
    }

    @Test
    fun `economia empieza cada tarjeta en una oracion`() {
        for (max in LARGOS) {
            for (t in todasLasTarjetas(armar(ECONOMIA, max))) {
                val primera = t.texto.first()
                assertTrue("[$max] «${t.texto}»", primera.isUpperCase() || primera == '•' || primera.isDigit() || t.texto.startsWith("en un contexto"))
            }
        }
    }

    @Test
    fun `economia conserva todas las palabras del cuerpo en orden`() {
        val esperadas = palabrasEsperadas(ECONOMIA, ::esEncabezadoEconomia)
        for (max in LARGOS) assertConservaPalabras(esperadas, armar(ECONOMIA, max))
    }

    @Test
    fun `economia arma tarjetas bien formadas con cada largo`() {
        for (max in LARGOS) assertTarjetasBienFormadas(armar(ECONOMIA, max), max)
    }

    @Test
    fun `economia con tarjetas largas usa menos tarjetas que con cortas`() {
        val cortas = todasLasTarjetas(armar(ECONOMIA, 300)).size
        val largas = todasLasTarjetas(armar(ECONOMIA, 600)).size
        assertTrue("$cortas cortas y $largas largas", largas < cortas)
    }

    // ---- Páginas escaneadas (ML Kit) ----

    @Test
    fun `ocr detecta los titulos y quita el ruido`() {
        val doc = armar(OCR_COSTOS)
        assertEquals(
            listOf("Economia I - Unidad 4 · 4.1 Costos de produccion" to 7, "4.2 Costo marginal" to 8),
            doc.secciones.map { it.titulo to it.paginaInicio },
        )
        for (t in todasLasTarjetas(doc)) {
            assertFalse(t.texto, t.texto.contains("|") || t.texto.contains("~"))
            assertFalse(t.texto, Regex("\\b1[23]\\b").containsMatchIn(t.texto))
        }
    }

    @Test
    fun `ocr une los renglones de cada bloque y arma la lista con su presentacion`() {
        val doc = armar(OCR_COSTOS)
        tarjetaCon(doc, "Los costos de produccion son los gastos en que incurre una empresa para producir bienes y servicios.")
        val lista = tarjetaCon(doc, "Ejemplos de costos variables:")
        assertTrue(lista.texto, lista.texto.endsWith("Ejemplos de costos variables:\n• Materias primas\n• Mano de obra directa\n• Energia utilizada en la planta"))
    }

    @Test
    fun `ocr conserva las palabras y arma tarjetas bien formadas`() {
        val esperadas = palabrasEsperadas(OCR_COSTOS, Limpieza::esNumeroPagina)
        for (max in LARGOS) {
            val doc = armar(OCR_COSTOS, max)
            assertConservaPalabras(esperadas, doc)
            assertTarjetasBienFormadas(doc, max)
        }
    }

    // ---- Documento sin títulos ----

    @Test
    fun `sin titulos hay una seccion por pagina sin titulo`() {
        val doc = armar(HISTORIA_SIN_TITULOS)
        assertEquals(listOf(1, 2, 3), doc.secciones.map { it.paginaInicio })
        doc.secciones.forEach { assertNull(it.titulo) }
    }

    @Test
    fun `sin titulos el parrafo que cruza de pagina queda en la seccion donde empieza`() {
        val doc = armar(HISTORIA_SIN_TITULOS, 300)
        val pagina1 = doc.secciones.first()
        assertTrue(describir(doc), pagina1.tarjetas.any { "evitar nuevas crisis financieras" in it.texto })
        // La tarjeta que empieza con texto de la página 2 lo dice, aunque esté en la sección de la página 1.
        val alMismoTiempo = pagina1.tarjetas.single { it.texto.startsWith("Al mismo tiempo") }
        assertEquals(2, alMismoTiempo.pagina)
        assertTrue(doc.secciones[1].tarjetas.first().texto.startsWith("La industria liviana"))
    }

    @Test
    fun `sin titulos conserva las palabras y arma tarjetas bien formadas`() {
        val esperadas = palabrasEsperadas(HISTORIA_SIN_TITULOS) { it == ENCABEZADO_HISTORIA || Limpieza.esNumeroPagina(it) }
        for (max in LARGOS) {
            val doc = armar(HISTORIA_SIN_TITULOS, max)
            assertConservaPalabras(esperadas, doc)
            assertTarjetasBienFormadas(doc, max)
        }
    }

    // ---- Oraciones muy largas ----

    @Test
    fun `una oracion muy larga se corta en signos y nunca dentro de una palabra`() {
        for (max in LARGOS) {
            val doc = armar(DERECHO, max)
            val tarjetas = todasLasTarjetas(doc)
            assertTrue(tarjetas.size >= 2)
            assertTrue(describir(doc), tarjetas.all { it.texto.length <= max })
            // Cada corte dentro de la oración larga cae después de un signo, no entre palabras sueltas.
            for (t in tarjetas) assertTrue("[$max] «${t.texto}»", t.texto.last() in ".;,")
        }
    }

    @Test
    fun `derecho conserva las palabras y arma tarjetas bien formadas`() {
        val esperadas = palabrasEsperadas(DERECHO)
        for (max in LARGOS) {
            val doc = armar(DERECHO, max)
            assertConservaPalabras(esperadas, doc)
            assertTarjetasBienFormadas(doc, max)
            assertEquals("Capítulo 2 - Derechos y garantías", doc.secciones.single().titulo)
        }
    }

    // ---- Tablas, notas al pie, bibliografía y subtítulos sin numerar ----

    private fun esEncabezadoEjercicios(linea: String) = linea.startsWith("Economía I – Unidad 3")

    @Test
    fun `quita el encabezado que trae el numero de pagina en la misma linea`() {
        val doc = armar(EJERCICIOS)
        assertTrue(todasLasTarjetas(doc).none { "Unidad 3" in it.texto })
        assertEquals(listOf("2.4 Gasto total y elasticidad", "Bibliografía"), doc.secciones.map { it.titulo })
    }

    @Test
    fun `una tabla queda con una fila por renglon`() {
        val tarjeta = tarjetaCon(armar(EJERCICIOS, 450), "Tabla 1.")
        assertTrue(tarjeta.texto, tarjeta.texto.contains("\nTabla 1. Ejemplo numérico\nPrecio Cantidad Gasto\n10 100 1000\n8 140 1120\n6 170 1020"))
    }

    @Test
    fun `un subtitulo sin numerar encabeza la lista que sigue y otro queda en su renglon`() {
        val doc = armar(EJERCICIOS, 450)
        tarjetaCon(doc, "Preguntas de repaso\n1) ¿Qué mide la elasticidad precio?\n2) ¿Cuándo una demanda es inelástica?")
        val actividad = tarjetaCon(doc, "Actividad práctica")
        assertTrue(actividad.texto, actividad.texto.startsWith("Actividad práctica\n\"La elasticidad no es constante"))
        assertEquals(3, actividad.pagina)
    }

    @Test
    fun `la bibliografia no se corta en las iniciales de los autores`() {
        val doc = armar(EJERCICIOS, 300)
        tarjetaCon(doc, "Mankiw, N. G. (2012). Principios de economía.")
        tarjetaCon(doc, "Varian, H. R. (2010).")
    }

    @Test
    fun `ejercicios conserva las palabras y arma tarjetas bien formadas`() {
        val esperadas = palabrasEsperadas(EJERCICIOS, ::esEncabezadoEjercicios)
        for (max in LARGOS) {
            val doc = armar(EJERCICIOS, max)
            assertConservaPalabras(esperadas, doc)
            assertTarjetasBienFormadas(doc, max)
        }
    }

    // ---- Portada e índice ----

    @Test
    fun `la portada y el indice no rompen el armado`() {
        val doc = armar(PORTADA_E_INDICE)
        val titulos = doc.secciones.map { it.titulo }
        // Los nombres propios en mayúsculas quedan en minúscula al pasar a tipo oración (limitación conocida).
        // El índice no genera sección ni tarjetas (ronda 4, hallazgo 1).
        assertEquals(listOf(null, "Facultad de ciencias económicas · Microeconomía", "1. Introducción", "2. El mercado", "2.1 La demanda"), titulos)
        assertEquals("UNIVERSIDAD DE BUENOS AIRES", doc.secciones.first().tarjetas.single().texto)
        assertTrue(todasLasTarjetas(doc).none { it.texto.contains("....") || it.texto.contains("• 1. Introducción") })
        assertTarjetasBienFormadas(doc, 450)
    }

    @Test
    fun `el texto antes del primer titulo va en una seccion sin titulo`() {
        val paginas = listOf(
            PaginaExtraida(
                1,
                "Este apunte resume los temas del primer parcial de la materia y conviene leerlo entero.\n\n" +
                    "1. Introducción\nLa microeconomía estudia las decisiones de consumidores y empresas.",
            ),
        )
        val doc = armar(paginas)
        assertEquals(listOf(null, "1. Introducción"), doc.secciones.map { it.titulo })
        assertTrue(doc.secciones[0].tarjetas.single().texto.startsWith("Este apunte resume"))
    }

    @Test
    fun `el mismo titulo dos veces seguidas no abre otra seccion`() {
        val paginas = listOf(
            PaginaExtraida(1, "UNIDAD 3 – ELASTICIDAD\nLa elasticidad mide la sensibilidad de la cantidad ante el precio."),
            PaginaExtraida(2, "UNIDAD 3 – ELASTICIDAD\nEl gasto total depende de la elasticidad de la demanda del bien."),
        )
        val doc = armar(paginas)
        assertEquals(listOf("Unidad 3 – Elasticidad"), doc.secciones.map { it.titulo })
        assertEquals(1, doc.secciones.single().paginaInicio)
    }

    @Test
    fun `una lista larga se reparte entre tarjetas sin partir items y la primera lleva la presentacion`() {
        val items = (1..8).map { "• Ítem $it: un factor que influye en la demanda del bien y que el apunte explica con un ejemplo." }
        val texto = "Los factores que influyen en la demanda son los siguientes:\n" + items.joinToString("\n")
        val doc = armar(listOf(PaginaExtraida(1, texto)), 300)
        val tarjetas = todasLasTarjetas(doc)
        assertTrue(describir(doc), tarjetas.size >= 3)
        assertTrue(tarjetas.first().texto.startsWith("Los factores que influyen en la demanda son los siguientes:\n• Ítem 1:"))
        assertEquals(items, tarjetas.flatMap { t -> t.texto.lines().filter { it.startsWith("•") } })
        assertTarjetasBienFormadas(doc, 300)
    }

    @Test
    fun `con dos paginas no quita lineas repetidas que no son numeros de pagina`() {
        val paginas = listOf(
            PaginaExtraida(1, "Apunte de Economía\nPrimer tema: la demanda depende del precio.\n1"),
            PaginaExtraida(2, "Apunte de Economía\nSegundo tema: la oferta también depende del precio.\n2"),
        )
        val doc = armar(paginas)
        assertEquals(2, Regex("Apunte de Economía").findAll(describir(doc)).count())
        assertTrue(describir(doc), todasLasTarjetas(doc).none { it.texto.endsWith("1") || it.texto.endsWith("2") })
    }

    @Test
    fun `ligaduras espacios raros y vinetas de simbolo se limpian de punta a punta`() {
        val doc = armar(listOf(PaginaExtraida(1, "La eﬁciencia del ﬂujo:\n\uF0B7 primer\u00A0punto\n\uF0B7 segundo pun-\nto")))
        assertEquals("La eficiencia del flujo:\n• primer punto\n• segundo punto", todasLasTarjetas(doc).single().texto)
    }

    // ---- Documentos generados al azar (con semilla fija) ----

    @Test
    fun `documentos generados conservan las palabras y respetan el largo con cualquier maximo`() {
        for (semilla in 1..25) {
            val doc = DocumentoGenerado(semilla)
            val esperadas = palabrasEsperadas(doc.paginas, doc::esBorde)
            for (max in listOf(100, 300, 450, 600, 1000)) {
                val armado = armar(doc.paginas, max)
                try {
                    assertConservaPalabras(esperadas, armado)
                    assertTarjetasBienFormadas(armado, max)
                } catch (e: AssertionError) {
                    throw AssertionError("Semilla $semilla, máximo $max: ${e.message}\n${describir(armado)}", e)
                }
            }
        }
    }

    // ---- Revisión: regresiones de punta a punta ----

    /** Envuelve un texto en renglones de hasta [ancho] caracteres, como los corta el PDF. */
    private fun renglones(texto: String, ancho: Int = 80): List<String> {
        val lineas = mutableListOf<String>()
        var actual = StringBuilder()
        for (palabra in texto.split(" ")) {
            if (actual.isNotEmpty() && actual.length + 1 + palabra.length > ancho) {
                lineas.add(actual.toString())
                actual = StringBuilder()
            }
            if (actual.isNotEmpty()) actual.append(' ')
            actual.append(palabra)
        }
        if (actual.isNotEmpty()) lineas.add(actual.toString())
        return lineas
    }

    private val vocabulario = listOf(
        "demanda", "oferta", "precio", "mercado", "consumidores", "empresas", "ingreso", "elasticidad",
        "costos", "producción", "equilibrio", "bienes", "servicios", "sustitutos", "inflación", "salario",
        "empleo", "inversión", "consumo", "ahorro", "impuestos", "política", "de", "la", "el", "los", "las",
        "y", "en", "que", "con", "por", "para", "un", "una", "se", "su", "más", "cuando", "como", "entre", "sobre",
    )

    /**
     * Un texto corrido al azar (siempre igual para el mismo [tema]), con oraciones que cruzan los
     * renglones. Las palabras al azar hacen que ningún renglón se repita en otra página.
     */
    private fun textoCorrido(tema: String, oraciones: Int): String {
        val azar = kotlin.random.Random(tema.hashCode())
        return (1..oraciones).joinToString(" ") {
            "En $tema, " + (1..azar.nextInt(12, 22)).joinToString(" ") { vocabulario[azar.nextInt(vocabulario.size)] } + " en el mercado."
        }
    }

    @Test
    fun `una guia de ejercicios conserva cada Ejercicio N y no queda vacia`() {
        // Hallazgo 1 (s02): un ejercicio por página, con el número igual al de la página.
        val paginas = (1..4).map { n ->
            PaginaExtraida(
                n,
                "Ejercicio $n\nDada la función de demanda Q = 100 - ${n}P, calcule la elasticidad precio en el\n" +
                    "punto donde el precio es igual a ${n * 5} pesos y explique si la demanda es elástica.\n" +
                    "Grafique la curva y marque el punto de elasticidad unitaria en el gráfico.",
            )
        }
        val doc = armar(paginas)
        assertTrue(doc.secciones.isNotEmpty())
        val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
        for (n in 1..4) assertTrue(describir(doc), texto.contains("Ejercicio $n"))
        // "Grafique la curva..." es la misma línea al pie de todas las páginas: por la regla de 3.2 es un pie.
        assertConservaPalabras(palabrasEsperadas(paginas) { it.startsWith("Grafique") }, doc)
    }

    @Test
    fun `Unidad N en las paginas impares queda como titulo de seccion`() {
        // Hallazgo 1 (r03): "Unidad N" sola en su renglón, en las páginas impares de un resumen.
        val nombres = listOf("Introducción a la economía", "Oferta y demanda", "Elasticidad", "Costos de producción", "Mercados")
        val paginas = (1..10).map { p ->
            val cuerpo = renglones(textoCorrido("la página $p", 3)).joinToString("\n")
            val texto = if (p % 2 == 1) "Unidad ${(p + 1) / 2}\n${nombres[p / 2]}\n$cuerpo" else cuerpo
            PaginaExtraida(p, texto)
        }
        val doc = armar(paginas)
        // Cada "Unidad N" se une con su nombre, que va en la línea de abajo (ronda 4, hallazgo 7).
        assertEquals((1..5).map { "Unidad $it · ${nombres[it - 1]}" }, doc.secciones.mapNotNull { it.titulo })
        assertConservaPalabras(palabrasEsperadas(paginas), doc)
    }

    @Test
    fun `una nota al pie queda despues del parrafo que cruza de pagina`() {
        // Hallazgo 2 (s01, s01b y sin guion).
        val pagina1 = "La crisis de 1930 afectó profundamente a la economía argentina, que dependía de\n" +
            "la exportación de productos agropecuarios¹. La caída de los precios redujo los\n" +
            "ingresos del Estado y obligó a replantear el modelo, con el proceso de indus-\n" +
            "¹ Ver Rapoport, M. (2000), Historia económica, cap. 4."
        val pagina2 = "trialización por sustitución de importaciones que se consolidó en la década.\n" +
            "La industria liviana creció sobre todo en el sector textil y en el alimenticio."
        val variantes = listOf(
            pagina1 to "¹ Ver Rapoport",
            pagina1.replace("¹ Ver", "1 Ver") to "1 Ver Rapoport",
            pagina1.replace("agropecuarios¹", "agropecuarios1").replace("¹ Ver", "1 Ver") to "1 Ver Rapoport",
        )
        for ((pagina, nota) in variantes) for (max in LARGOS) {
            val doc = armar(listOf(PaginaExtraida(1, pagina), PaginaExtraida(2, pagina2)), max)
            val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
            assertTrue(texto, texto.contains("con el proceso de industrialización por sustitución de importaciones"))
            assertFalse(texto, texto.contains("indus-") || texto.contains("indus "))
            assertTrue(texto, texto.indexOf(nota) > texto.indexOf("industrialización"))
            assertTarjetasBienFormadas(doc, max)
        }
        val sinGuion = armar(listOf(PaginaExtraida(1, pagina1.replace("de indus-", "de")), PaginaExtraida(2, "industrialización y" + pagina2.removePrefix("trialización"))))
        assertTrue(describir(sinGuion), todasLasTarjetas(sinGuion).any { "con el proceso de industrialización y por sustitución" in it.texto })
    }

    @Test
    fun `un parrafo numerado largo se corta entre oraciones`() {
        // Hallazgo 3 (q01).
        val texto = """
            Los supuestos del modelo de competencia perfecta son los siguientes:
            1. Hay muchos compradores y muchos vendedores, de modo que ninguno puede influir por sí solo en el
            precio de mercado, que todos toman como dado. Esto implica que cada empresa enfrenta una demanda
            horizontal. Por ejemplo, un productor de trigo de la provincia de Buenos Aires, que vende una
            fracción ínfima de la cosecha, no puede subir el precio sin perder a todos sus clientes. En el corto
            plazo, además, la cantidad de empresas está fija, y solo en el largo plazo pueden entrar o salir.
            2. El producto es homogéneo.
        """.trimIndent()
        for (max in listOf(300, 450)) {
            val doc = armar(listOf(PaginaExtraida(1, texto)), max)
            for (t in todasLasTarjetas(doc)) assertTrue("[$max] «${t.texto}»", t.texto.endsWith("."))
            tarjetaCon(doc, "Por ejemplo, un productor de trigo de la provincia de Buenos Aires, que vende una fracción ínfima de la cosecha, no puede subir el precio sin perder a todos sus clientes.")
            assertTarjetasBienFormadas(doc, max)
        }
    }

    @Test
    fun `una vineta larga con varias oraciones se corta entre oraciones`() {
        // Hallazgo 3 (s05): la misma idea con una viñeta.
        val vineta = """
            • Elasticidad precio: mide cuánto cambia la cantidad demandada cuando cambia el precio, con
            todo lo demás constante. Se calcula como el cociente entre variaciones porcentuales, y su
            valor absoluto indica si la demanda es elástica o inelástica. Por ejemplo, si el precio de
            la nafta sube un 10 % y la cantidad cae un 2 %, la elasticidad es 0,2, de modo que la
            demanda es inelástica. Esto explica, en parte, por qué los impuestos a los combustibles
            recaudan tanto.
        """.trimIndent()
        for (max in listOf(300, 450)) {
            val doc = armar(listOf(PaginaExtraida(1, "Conceptos centrales:\n$vineta")), max)
            for (t in todasLasTarjetas(doc)) assertTrue("[$max] «${t.texto}»", t.texto.endsWith(".") || t.texto.endsWith(":"))
        }
    }

    @Test
    fun `las diapositivas con el mismo titulo quedan en una sola seccion con ese titulo`() {
        // Hallazgo 5 (s12, y un mazo de 12 diapositivas con pie y número de página).
        val temas = listOf("LA OFERTA", "ELASTICIDAD PRECIO", "ELASTICIDAD INGRESO", "COSTOS")
        val paginas = (1..12).map { p ->
            val tema = temas[(p - 1) / 3]
            PaginaExtraida(p, "$tema\n• Primera idea de la diapositiva $p sobre el tema.\n• Segunda idea de la diapositiva $p.\nMicroeconomía – Clase 3\n$p")
        }
        val doc = armar(paginas)
        assertEquals(listOf("La oferta", "Elasticidad precio", "Elasticidad ingreso", "Costos"), doc.secciones.map { it.titulo })
        assertEquals(listOf(1, 4, 7, 10), doc.secciones.map { it.paginaInicio })
        val precio = doc.secciones[1].tarjetas.joinToString("\n") { it.texto }
        for (p in 4..6) assertTrue(precio, precio.contains("diapositiva $p sobre el tema"))
        assertTrue(todasLasTarjetas(doc).none { "Clase 3" in it.texto })
    }

    @Test
    fun `un encabezado de unidad que aparece recien en la segunda pagina no abre una seccion`() {
        // Hallazgo 5 (guarda): libro con el encabezado de la unidad desde su segunda página.
        val unidad3 = renglones(textoCorrido("la elasticidad", 55))
        val unidad4 = renglones(textoCorrido("los costos", 45))
        // Cada unidad ocupa al menos 4 páginas, así el encabezado aparece en 3 o más.
        assertTrue(unidad3.chunked(20).size >= 4 && unidad4.chunked(20).size >= 4)
        val paginas = mutableListOf<PaginaExtraida>()
        unidad3.chunked(20).forEachIndexed { i, lineas ->
            val arriba = if (i == 0) "UNIDAD 3: ELASTICIDAD" else "Unidad 3 – Elasticidad"
            paginas.add(PaginaExtraida(paginas.size + 1, (listOf(arriba) + lineas).joinToString("\n")))
        }
        unidad4.chunked(20).forEachIndexed { i, lineas ->
            val arriba = if (i == 0) "UNIDAD 4: COSTOS" else "Unidad 4 – Costos"
            paginas.add(PaginaExtraida(paginas.size + 1, (listOf(arriba) + lineas).joinToString("\n")))
        }
        val doc = armar(paginas)
        assertEquals(listOf("Unidad 3: Elasticidad", "Unidad 4: Costos"), doc.secciones.map { it.titulo })
        assertConservaPalabras(palabrasEsperadas(paginas) { it == "Unidad 3 – Elasticidad" || it == "Unidad 4 – Costos" }, doc)
    }

    @Test
    fun `los encabezados alternados de un libro no abren secciones ni cortan los parrafos`() {
        // Hallazgo 6 (r10): páginas 80 a 97, parte en las pares y capítulo con número en las impares.
        val capitulo = { n: Int -> if (n < 86) "CAPÍTULO 4 • LA OFERTA Y LA DEMANDA" else if (n < 92) "CAPÍTULO 5 • LA ELASTICIDAD" else "CAPÍTULO 6 • LA POLÍTICA ECONÓMICA" }
        val cuerpo = renglones(textoCorrido("los mercados", 230)).chunked(20)
        assertTrue(cuerpo.size >= 18)
        val paginas = cuerpo.indices.map { i ->
            val n = 80 + i
            val encabezado = if (n % 2 == 0) "$n PARTE II • CÓMO FUNCIONAN LOS MERCADOS" else "${capitulo(n)} $n"
            PaginaExtraida(n, (listOf(encabezado) + cuerpo[i]).joinToString("\n"))
        }
        val doc = armar(paginas)
        assertTrue(describir(doc), doc.secciones.all { it.titulo == null })
        assertTrue(todasLasTarjetas(doc).none { "CAPÍTULO" in it.texto || "PARTE II" in it.texto })
        val esperadas = palabrasEsperadas(paginas) { it.contains("PARTE II") || it.startsWith("CAPÍTULO") }
        assertConservaPalabras(esperadas, doc)
        // Ninguna oración quedó cortada en un cambio de página: todas las tarjetas terminan en punto.
        for (t in todasLasTarjetas(doc)) assertTrue(t.texto, t.texto.endsWith("."))
    }

    @Test
    fun `una lista de lineas con Unidad de cuenta no abre una seccion`() {
        // Hallazgo 7 (s04).
        val texto = "3.2 Funciones del dinero\n" +
            "El dinero cumple tres funciones básicas en una economía moderna, según la mayoría\n" +
            "de los manuales de introducción a la economía que se usan en la cátedra.\n" +
            "Las funciones del dinero son:\nMedio de cambio\nUnidad de cuenta\nReserva de valor\n" +
            "Como medio de cambio, el dinero facilita las transacciones y evita el trueque,\n" +
            "que exige una doble coincidencia de necesidades entre los que intercambian."
        val doc = armar(listOf(PaginaExtraida(1, texto)))
        assertEquals(listOf("3.2 Funciones del dinero"), doc.secciones.map { it.titulo })
        tarjetaCon(doc, "Las funciones del dinero son:\nMedio de cambio\nUnidad de cuenta\nReserva de valor")
    }

    @Test
    fun `una enumeracion arriba de la pagina queda junto a su presentacion`() {
        // Hallazgo 8 (s06).
        val pagina1 = "2.1 Clasificación\nLa elasticidad precio mide la sensibilidad de la cantidad ante cambios del precio.\n" +
            "Según su valor, la demanda puede ser:"
        val pagina2 = "a) elástica, si la cantidad reacciona más que proporcionalmente;\n" +
            "b) inelástica, si reacciona menos que proporcionalmente;\n" +
            "c) unitaria, si reacciona en la misma proporción que el precio."
        val doc = armar(listOf(PaginaExtraida(1, pagina1), PaginaExtraida(2, pagina2)))
        tarjetaCon(
            doc,
            "la demanda puede ser:\na) elástica, si la cantidad reacciona más que proporcionalmente;\n" +
                "b) inelástica, si reacciona menos que proporcionalmente;\nc) unitaria, si reacciona en la misma proporción que el precio.",
        )
    }

    @Test
    fun `una cita del Codigo Civil y Comercial no se corta entre tarjetas`() {
        // Hallazgo 9 (s08b).
        val texto = "La responsabilidad civil cumple una función preventiva y otra resarcitoria en el derecho argentino actual. " +
            "El daño debe repararse de manera plena, restituyendo a la víctima a la situación anterior al hecho, " +
            "según lo establece el art. 1740 del Cód. Civ. y Com. de la Nación, que unificó el régimen en 2015. " +
            "La reparación puede ser en dinero o en especie, a elección del damnificado."
        for (max in LARGOS) {
            val doc = armar(listOf(PaginaExtraida(1, renglones(texto).joinToString("\n"))), max)
            assertTrue(describir(doc), todasLasTarjetas(doc).none { it.texto.endsWith("Cód.") || it.texto.startsWith("Civ.") })
            tarjetaCon(doc, "del Cód. Civ. y Com. de la Nación, que unificó el régimen en 2015.")
        }
    }

    @Test
    fun `un guion tipografico al final de la linea une la palabra`() {
        // Hallazgo 10 (s07).
        val doc = armar(listOf(PaginaExtraida(1, "La elasticidad mide la sensibilidad de la cantidad deman\u2010\ndada ante cambios en el precio del bien.")))
        assertEquals("La elasticidad mide la sensibilidad de la cantidad demandada ante cambios en el precio del bien.", todasLasTarjetas(doc).single().texto)
    }

    // ---- Ronda 2 ----

    private val cierreDePagina1 = "La elasticidad cruzada mide cómo cambia la cantidad demandada de un bien cuando cambia\n" +
        "el precio de otro bien relacionado, ya sea un sustituto o un complemento en el"
    private val pagina2Cruzada = "consumo, y su signo permite clasificar la relación entre ambos bienes de la canasta.\n" +
        "Cuando es positiva, los bienes son sustitutos; cuando es negativa, son complementarios."

    @Test
    fun `un subtitulo numerado sin punto al pie de la pagina no se toma como nota`() {
        // Hallazgo 2 de la ronda 2: después de una ecuación, de un epígrafe y de una lista sin puntos.
        val cuerpo = renglones(textoCorrido("la elasticidad precio", 10)).joinToString("\n")
        val antesDelTitulo = listOf(
            "La elasticidad precio de la demanda se define como el cociente entre las variaciones\n" +
                "porcentuales de la cantidad y del precio, es decir:\nE = ΔQ% / ΔP%",
            "La curva muestra cómo cambia la cantidad demandada a lo largo de los distintos precios.\n" +
                "Figura 2: Curva de demanda con elasticidad unitaria",
            "Los bienes relacionados pueden ser de dos tipos según cómo se consumen:\n• Sustitutos\n• Complementarios",
        )
        for (antes in antesDelTitulo) {
            val pagina1 = "$cuerpo\n$antes\n3 Elasticidad cruzada\n$cierreDePagina1"
            val doc = armar(listOf(PaginaExtraida(1, pagina1), PaginaExtraida(2, pagina2Cruzada)))
            assertEquals(describir(doc), listOf(null, "3 Elasticidad cruzada"), doc.secciones.map { it.titulo })
            tarjetaCon(doc, "un complemento en el consumo, y su signo permite clasificar")
            assertConservaPalabras(palabrasEsperadas(listOf(PaginaExtraida(1, pagina1), PaginaExtraida(2, pagina2Cruzada))), doc)
        }
    }

    @Test
    fun `la fuente de un cuadro al pie de la pagina no se lleva el parrafo que sigue`() {
        // Hallazgo 2 de la ronda 2: "*Fuente: INDEC" sin llamada en el cuerpo.
        val pagina1 = renglones(textoCorrido("las exportaciones", 8)).joinToString("\n") +
            "\nCuadro 1. Exportaciones por rubro\nCereales 45 %\nCarnes 30 %\n*Fuente: INDEC\n" +
            "Como muestra el cuadro, la economía dependía de unos pocos productos y era muy vulnerable a los"
        val pagina2 = "cambios en los precios internacionales de las materias primas."
        val doc = armar(listOf(PaginaExtraida(1, pagina1), PaginaExtraida(2, pagina2)))
        val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
        assertTrue(texto, texto.contains("muy vulnerable a los cambios en los precios internacionales"))
        assertTrue(texto, texto.indexOf("*Fuente: INDEC") < texto.indexOf("Como muestra el cuadro"))
    }

    private val pagina1ConLlamada = "La crisis de 1930 afectó profundamente a la economía argentina, que dependía de\n" +
        "la exportación de productos agropecuarios1. La caída de los precios redujo los\n" +
        "ingresos del Estado y obligó a replantear el modelo, con el proceso de indus-"

    @Test
    fun `una nota con numero comun y sin punto final no se vuelve titulo`() {
        // Hallazgo 3 de la ronda 2.
        val pagina2 = "trialización por sustitución de importaciones que se consolidó en la década.\n\n" +
            "La industria liviana creció sobre todo en el sector textil y en el alimenticio, y el\n" +
            "Estado promovió nuevas ramas con créditos baratos y con aranceles protectores."
        for (nota in listOf("1 Ver Rapoport, M. (2000), Historia económica, cap. 4", "1 Ibíd., p. 23", "1 Rapoport (2000), p. 45")) {
            val doc = armar(listOf(PaginaExtraida(1, "$pagina1ConLlamada\n$nota"), PaginaExtraida(2, pagina2)))
            assertTrue(describir(doc), doc.secciones.all { it.titulo == null })
            val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
            assertTrue(texto, texto.contains("con el proceso de industrialización por sustitución"))
            assertTrue(texto, texto.contains(nota))
        }
    }

    @Test
    fun `un bloque de notas de siete lineas no se mete en el parrafo que sigue`() {
        // Hallazgo 4 de la ronda 2.
        val pagina1 = "La crisis de 1930 afectó profundamente a la economía argentina, que dependía de\n" +
            "la exportación de productos agropecuarios¹. La caída de los precios² redujo los\n" +
            "ingresos del Estado³ y obligó a replantear el modelo, con el proceso de indus-\n" +
            "¹ Ver Rapoport, M. (2000), Historia económica de la Argentina, Buenos Aires,\n" +
            "Emecé, cap. 4, donde se analiza el impacto de la crisis sobre el comercio exterior\n" +
            "y las finanzas públicas del período.\n" +
            "² Sobre el mismo tema, véase también Gerchunoff, P. y Llach, L. (1998), El ciclo\n" +
            "de la ilusión y el desencanto, Buenos Aires, Ariel, cap. 2.\n" +
            "³ Díaz Alejandro, C. (1970), Ensayos sobre la historia económica argentina, Buenos\n" +
            "Aires, Amorrortu."
        val pagina2 = "trialización por sustitución de importaciones que se consolidó en la década.\n" +
            "La industria liviana creció sobre todo en el sector textil y en el alimenticio."
        for (max in LARGOS) {
            val doc = armar(listOf(PaginaExtraida(1, pagina1), PaginaExtraida(2, pagina2)), max)
            val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
            assertTrue(texto, texto.contains("con el proceso de industrialización por sustitución de importaciones"))
            assertFalse(texto, texto.contains("indus-"))
            val industrializacion = texto.indexOf("industrialización")
            for (nota in listOf("¹ Ver Rapoport", "² Sobre el mismo tema", "³ Díaz Alejandro")) assertTrue(texto, texto.indexOf(nota) > industrializacion)
        }
    }

    /** Un mazo de diapositivas: título, dos viñetas, pie y número de página. */
    private fun mazo(titulos: List<String>): List<PaginaExtraida> = titulos.mapIndexed { i, titulo ->
        val p = i + 1
        PaginaExtraida(p, "$titulo\n• Primera idea de la diapositiva $p sobre el tema.\n• Segunda idea de la diapositiva $p.\nMicroeconomía – Clase 3\n$p")
    }

    @Test
    fun `un titulo de diapositiva en tres diapositivas alternadas no se borra`() {
        // Hallazgo 5 de la ronda 2: EJEMPLO en las diapositivas 4, 6 y 8 de 20.
        val otros = listOf("LA OFERTA", "LA DEMANDA", "EL EQUILIBRIO", "LA ELASTICIDAD", "LOS COSTOS", "LA PRODUCCIÓN", "EL MONOPOLIO",
            "LA COMPETENCIA", "EL OLIGOPOLIO", "LOS IMPUESTOS", "EL CONSUMO", "EL AHORRO", "LA INVERSIÓN", "EL EMPLEO", "LA INFLACIÓN",
            "EL DINERO", "LOS BANCOS")
        var k = 0
        val titulos = (1..20).map { p -> if (p in listOf(4, 6, 8)) "EJEMPLO" else otros[k++] }
        val doc = armar(mazo(titulos))
        assertEquals(listOf(4, 6, 8), doc.secciones.filter { it.titulo == "Ejemplo" }.map { it.paginaInicio })
    }

    @Test
    fun `el mismo titulo de diapositiva en dos tandas separadas abre las dos secciones`() {
        // Hallazgo 10 de la ronda 2.
        val tandas = listOf("LA OFERTA", "EJERCICIOS", "LA DEMANDA", "EJERCICIOS", "COSTOS")
        val doc = armar(mazo(tandas.flatMap { listOf(it, it, it) }))
        assertEquals(listOf("La oferta", "Ejercicios", "La demanda", "Ejercicios", "Costos"), doc.secciones.map { it.titulo })
        assertEquals(listOf(1, 4, 7, 10, 13), doc.secciones.map { it.paginaInicio })
    }

    @Test
    fun `el encabezado de un mazo cuya portada no lo tiene no queda como texto ni como titulo`() {
        // Hallazgo 9 de la ronda 2.
        val titulos = listOf("LA OFERTA", "LA DEMANDA", "EL EQUILIBRIO", "LA ELASTICIDAD", "LOS COSTOS", "LA PRODUCCIÓN", "EL MONOPOLIO", "LA COMPETENCIA", "EL OLIGOPOLIO")
        for (encabezado in listOf("Microeconomía I – Clase 3", "MICROECONOMÍA I – CLASE 3")) {
            val portada = PaginaExtraida(1, "MICROECONOMÍA I\nClase 3: Elasticidad\nProf. Juan García\nFacultad de Ciencias Económicas")
            val diapositivas = titulos.mapIndexed { i, titulo ->
                val p = i + 2
                PaginaExtraida(p, "$encabezado\n$titulo\n• Primera idea de la diapositiva $p sobre el tema.\n• Segunda idea de la diapositiva $p.")
            }
            val doc = armar(listOf(portada) + diapositivas)
            assertTrue(describir(doc), todasLasTarjetas(doc).none { "Clase 3" in it.texto && "–" in it.texto })
            // La portada puede abrir su propia sección ("Microeconomía I"); lo que importa son las diapositivas.
            assertEquals(describir(doc), titulos.map { DetectorTitulos.normalizarTitulo(it) }, doc.secciones.mapNotNull { it.titulo }.takeLast(titulos.size))
            assertEquals(2, doc.secciones.single { it.titulo == "La oferta" }.paginaInicio)
        }
    }

    @Test
    fun `un encabezado de unidad con Nº o Temática abre su seccion`() {
        // Hallazgo 6 de la ronda 2.
        val cuerpo = renglones(textoCorrido("la elasticidad", 4)).joinToString("\n")
        for (titulo in listOf("Unidad Nº 3: Elasticidad", "Unidad Nro. 3 – Elasticidad", "Unidad Temática 2: Oferta y demanda")) {
            val doc = armar(listOf(PaginaExtraida(1, "$titulo\n$cuerpo")))
            assertEquals(listOf(titulo), doc.secciones.map { it.titulo })
        }
    }

    // ---- Ronda 3 ----

    @Test
    fun `un titulo repetido en diapositivas de texto sin vinetas se deja la primera vez`() {
        // Hallazgo 1 de la ronda 3 (v1).
        val granos = listOf("trigo", "maíz", "soja")
        val paginas = granos.mapIndexed { i, g ->
            PaginaExtraida(i + 1, "ELASTICIDAD PRECIO\nMide cuánto cambia la cantidad de $g\ncuando cambia el precio del $g.\nMicroeconomía – Clase 3")
        } + PaginaExtraida(4, "ELASTICIDAD INGRESO\nMide la reacción de la cantidad\nante cambios en el ingreso.\nMicroeconomía – Clase 3")
        val doc = armar(paginas)
        assertEquals(describir(doc), listOf("Elasticidad precio", "Elasticidad ingreso"), doc.secciones.map { it.titulo })
        assertEquals(listOf(1, 4), doc.secciones.map { it.paginaInicio })
    }

    /** Un mazo de 10 diapositivas con encabezado fijo arriba del título, texto corrido envuelto a 50 y número de página. */
    private fun mazoConEncabezado(titulos: List<String>, encabezado: String): List<PaginaExtraida> = titulos.mapIndexed { i, titulo ->
        val p = i + 1
        val texto = renglones(textoCorrido("la diapositiva ${'a' + i}", 2), 50).take(3).joinToString("\n")
        PaginaExtraida(p, "$encabezado\n$titulo\n$texto\n$p")
    }

    @Test
    fun `un titulo repetido debajo de un encabezado fijo se deja aunque el texto no tenga vinetas`() {
        // Hallazgos 1 y 3 de la ronda 3: encabezado fijo arriba y texto corrido sin viñetas.
        val titulos = listOf("TEMARIO", "LA DEMANDA", "LA OFERTA", "ELASTICIDAD PRECIO", "ELASTICIDAD PRECIO", "ELASTICIDAD PRECIO",
            "ELASTICIDAD INGRESO", "LOS COSTOS", "EL EQUILIBRIO", "EJERCICIOS")
        val doc = armar(mazoConEncabezado(titulos, "Microeconomía I – Clase 3"))
        assertEquals(describir(doc), titulos.distinct().map { DetectorTitulos.normalizarTitulo(it) }, doc.secciones.map { it.titulo })
        assertEquals(4, doc.secciones.single { it.titulo == "Elasticidad precio" }.paginaInicio)
        assertTrue(todasLasTarjetas(doc).none { "Clase 3" in it.texto })
    }

    @Test
    fun `un titulo repetido en diapositivas con vinetas largas se deja la primera vez`() {
        // Hallazgo 2 de la ronda 3 (v2): viñetas de 68 a 71 caracteres.
        val paginas = (1..3).map { k ->
            PaginaExtraida(
                k,
                "ELASTICIDAD PRECIO\n• La elasticidad mide la sensibilidad de la cantidad ante el precio ($k)\n" +
                    "• Se calcula como el cociente entre las variaciones porcentuales ($k)\n" +
                    "• Un valor mayor que uno indica una demanda elástica en ese tramo ($k)\nMicroeconomía – Clase 3",
            )
        } + PaginaExtraida(
            4,
            "ELASTICIDAD INGRESO\n• Mide la reacción de la cantidad demandada ante cambios en el ingreso\n" +
                "• Es positiva para los bienes normales y negativa para los inferiores\nMicroeconomía – Clase 3",
        )
        val doc = armar(paginas)
        assertEquals(describir(doc), listOf("Elasticidad precio", "Elasticidad ingreso"), doc.secciones.map { it.titulo })
        assertEquals(9, doc.secciones.first().tarjetas.sumOf { t -> t.texto.lines().count { it.startsWith("•") } })
    }

    @Test
    fun `un titulo repetido debajo de un encabezado de plantilla se deja la primera vez`() {
        // Hallazgo 3 de la ronda 3 (v3).
        val paginas = (1..6).map { k ->
            val titulo = if (k <= 3) "ELASTICIDAD PRECIO" else "ELASTICIDAD INGRESO"
            val tema = if (k <= 3) "el tema" else "el ingreso"
            PaginaExtraida(k, "Microeconomía – Clase 3\n$titulo\n• Primera idea número $k sobre $tema.\n• Segunda idea número $k.")
        }
        val doc = armar(paginas)
        assertEquals(describir(doc), listOf("Elasticidad precio", "Elasticidad ingreso"), doc.secciones.map { it.titulo })
        assertEquals(listOf(1, 4), doc.secciones.map { it.paginaInicio })
    }

    @Test
    fun `un mazo con portada sin encabezado y pie con numero conserva los titulos repetidos`() {
        // Hallazgo 3 de la ronda 3: 20 diapositivas, portada sin encabezado.
        val titulos = (2..20).map { p ->
            when (p) {
                in 3..5 -> "ELASTICIDAD PRECIO"
                in 14..16 -> "EJERCICIOS"
                else -> "TEMA ${'A' + p}"
            }
        }
        val portada = PaginaExtraida(1, "MICROECONOMÍA I\nClase 3: Elasticidad\nProf. Juan García")
        val diapositivas = titulos.mapIndexed { i, titulo ->
            val p = i + 2
            PaginaExtraida(p, "Microeconomía I – Clase 3\n$titulo\n• Primera idea de la diapositiva $p.\n• Segunda idea de la diapositiva $p.\nClase 3 | $p / 20")
        }
        val doc = armar(listOf(portada) + diapositivas)
        val secciones = doc.secciones.associate { it.titulo to it.paginaInicio }
        assertEquals(describir(doc), 3, secciones["Elasticidad precio"])
        assertEquals(describir(doc), 14, secciones["Ejercicios"])
        assertTrue(todasLasTarjetas(doc).none { "Clase 3 |" in it.texto || "Microeconomía I –" in it.texto })
    }

    @Test
    fun `los encabezados de seccion de LaTeX con el numero de pagina no se cuelan en las tarjetas`() {
        // Hallazgo 4 de la ronda 3: páginas impresas como el número del PDF más 80.
        val impares = mapOf(
            3 to "4.1. LOS MERCADOS", 5 to "4.2. LA DEMANDA", 7 to "4.2. LA DEMANDA", 9 to "4.3. LA OFERTA", 11 to "4.4. EL EQUILIBRIO",
            13 to "4.4. EL EQUILIBRIO", 15 to "4.5. LOS CONTROLES DE PRECIOS", 17 to "4.5. LOS CONTROLES DE PRECIOS",
        )
        val cuerpo = renglones(textoCorrido("los mercados", 260)).chunked(20)
        assertTrue(cuerpo.size >= 18)
        val paginas = (1..18).map { p ->
            val n = p + 80
            val texto = when {
                p == 1 -> (listOf("Capítulo 4", "La oferta y la demanda", "4.1. Los mercados") + cuerpo[0] + "$n").joinToString("\n")
                p % 2 == 0 -> (listOf("$n CAPÍTULO 4. LA OFERTA Y LA DEMANDA") + cuerpo[p - 1]).joinToString("\n")
                else -> (listOf("${impares.getValue(p)} $n") + cuerpo[p - 1]).joinToString("\n")
            }
            PaginaExtraida(p, texto)
        }
        val doc = armar(paginas)
        assertEquals(describir(doc), listOf("Capítulo 4", "4.1. Los mercados"), doc.secciones.map { it.titulo })
        val esperadas = palabrasEsperadas(paginas) { l -> l.contains("CAPÍTULO 4.") || impares.values.any { l.startsWith(it) } || Limpieza.esNumeroPagina(l) }
        assertConservaPalabras(esperadas, doc)
    }

    @Test
    fun `el encabezado de capitulo de las paginas impares se quita aunque la apertura tenga el mismo titulo`() {
        // Hallazgo 5 de la ronda 3 (v6).
        // Cada capítulo con su texto: la página de apertura empieza un párrafo.
        val capitulo4 = renglones(textoCorrido("la oferta", 230)).chunked(26)
        val capitulo5 = renglones(textoCorrido("la elasticidad", 230)).chunked(26)
        assertTrue(capitulo4.size >= 12 && capitulo5.size >= 12)
        val cuerpo = capitulo4.take(12) + capitulo5.take(12)
        val paginas = (1..24).map { p ->
            val arriba = when {
                p == 1 -> listOf("CAPÍTULO 4", "LA OFERTA Y LA DEMANDA")
                p == 13 -> listOf("CAPÍTULO 5", "LA ELASTICIDAD")
                p % 2 == 0 -> listOf("PARTE II • CÓMO FUNCIONAN LOS MERCADOS")
                p < 13 -> listOf("LA OFERTA Y LA DEMANDA")
                else -> listOf("LA ELASTICIDAD")
            }
            PaginaExtraida(p, (arriba + cuerpo[p - 1] + "${p + 80}").joinToString("\n"))
        }
        val doc = armar(paginas)
        assertEquals(describir(doc), listOf("Capítulo 4 · La oferta y la demanda", "Capítulo 5 · La elasticidad"), doc.secciones.map { it.titulo })
        assertTrue(todasLasTarjetas(doc).none { "LA OFERTA Y LA DEMANDA" in it.texto || "LA ELASTICIDAD" in it.texto || "PARTE II" in it.texto })
    }

    @Test
    fun `un titulo largo seguido directamente del texto abre su seccion`() {
        // Hallazgo 6 de la ronda 3 (v4), en el medio y al principio de la página.
        val antes = "La demanda de un bien depende de su precio, del ingreso de los consumidores, de los precios\n" +
            "de los bienes relacionados y de las preferencias. Cuando cambia alguno de estos factores, la\n" +
            "curva de demanda se desplaza hacia la derecha o hacia la izquierda según el caso analizado."
        val despues = "Un impuesto por unidad vendida introduce una brecha entre el precio que pagan los compradores\n" +
            "y el precio que reciben los vendedores. La cantidad intercambiada disminuye y se genera una\n" +
            "pérdida de eficiencia, cuyo tamaño depende de las elasticidades de la oferta y la demanda."
        for (titulo in listOf("3.1.2 Efectos de los impuestos sobre el equilibrio del mercado", "Unidad 4: Teoría de la producción y de los costos en el corto plazo")) {
            val enElMedio = armar(listOf(PaginaExtraida(1, "$antes\n$titulo\n$despues")))
            assertEquals(describir(enElMedio), listOf(null, titulo), enElMedio.secciones.map { it.titulo })
            val arriba = armar(listOf(PaginaExtraida(1, antes), PaginaExtraida(2, "$titulo\n$despues")))
            assertEquals(describir(arriba), listOf(null, titulo), arriba.secciones.map { it.titulo })
        }
    }

    @Test
    fun `el titulo de una diapositiva seguido de puntos numerados no se pega al primero`() {
        // Hallazgo 6 de la ronda 3: en el mazo, el largo típico es el de los títulos.
        val titulos = listOf("ELASTICIDAD INGRESO", "ELASTICIDAD CRUZADA", "BIENES NORMALES", "BIENES INFERIORES", "BIENES DE GIFFEN")
        val paginas = titulos.mapIndexed { i, titulo ->
            PaginaExtraida(i + 1, "$titulo\n1. Mide la reacción de la cantidad en el caso ${'a' + i}.\n2. Se calcula con variaciones porcentuales en el caso ${'a' + i}.")
        }
        val doc = armar(paginas)
        assertEquals(describir(doc), titulos.map { DetectorTitulos.normalizarTitulo(it) }, doc.secciones.map { it.titulo })
    }

    // ---- Ronda 4: formatos de PDFs reales, con textos inventados ----

    /** Un apunte digital: portada con letras espaciadas, índice de dos páginas, pestaña lateral y pie. */
    private fun guiaDigital(): List<PaginaExtraida> {
        val secciones = listOf(
            "Parte I · 1 · Conceptos y convenciones del curso" to 2,
            "Parte I · 2 · Requisitos previos para practicar" to 1,
            "Parte II · 1 · Procedimientos de cada clase" to 3,
            "Parte II · 2 · Errores frecuentes y cómo evitarlos" to 2,
        )
        val total = 3 + secciones.sumOf { it.second }
        fun pie(p: Int) = "$p / ${total}Manual de prácticas · Cátedra de ejemplo"
        val paginas = mutableListOf(
            PaginaExtraida(1, "M A N U A L  D E  P R Á C T I C A S\nEdición para estudiantes\n${pie(1)}"),
            PaginaExtraida(
                2,
                "Índice\nMapa general del curso 4\nCómo usar este manual 4\nParte I · 1 · Conceptos y convenciones del curso 4\n" +
                    "Primeras definiciones 5\nParte I · 2 · Requisitos previos para practicar 6\nMateriales necesarios 6\n${pie(2)}",
            ),
            PaginaExtraida(
                3,
                "Parte II · 1 · Procedimientos de cada clase 7\nPreparación de la clase 7\nEjecución paso a paso 8\n" +
                    "Parte II · 2 · Errores frecuentes y cómo evitarlos 10\nQué no hacer 11\n${pie(3)}",
            ),
        )
        var p = 4
        for ((s, par) in secciones.withIndex()) {
            val (titulo, paginasDeSeccion) = par
            val pestana = if (s < 2) "PA RT E  I  ·  ${s + 1}" else "PA RT E  I I  ·  ${s - 1}"
            val texto = renglones(textoCorrido("la sección ${'a' + s}", 14 * paginasDeSeccion), 85).chunked(18)
            for (k in 0 until paginasDeSeccion) {
                val arriba = if (k == 0) listOf(titulo) else emptyList()
                paginas.add(PaginaExtraida(p, (arriba + texto[k] + pestana + pie(p)).joinToString("\n")))
                p++
            }
        }
        return paginas
    }

    @Test
    fun `un apunte digital con indice y pestanas arma solo las secciones reales`() {
        // Hallazgos 1, 2 y 3 de la ronda 4.
        val doc = armar(guiaDigital())
        val titulos = doc.secciones.mapNotNull { it.titulo }
        assertEquals(
            describir(doc),
            listOf("Manual de prácticas", "Parte I · 1 · Conceptos y convenciones del curso", "Parte I · 2 · Requisitos previos para practicar",
                "Parte II · 1 · Procedimientos de cada clase", "Parte II · 2 · Errores frecuentes y cómo evitarlos"),
            titulos,
        )
        val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
        assertFalse(texto, texto.contains("PARTE I") || texto.contains("PA RT E") || texto.contains("Manual de prácticas · Cátedra"))
        assertFalse(texto, texto.contains("Mapa general del curso") || texto.contains("Qué no hacer"))
    }

    /** Un libro escaneado con dos páginas del libro por página del PDF, como lo devuelve ML Kit. */
    private fun libroEscaneado(): List<PaginaExtraida> {
        val pie = "Escaneado con CamScanner"
        val paginas = listOf(
            "DERECHO DE PRUEBA\n\nManual para estudiantes\n\nÍNDICE\n\nCapitulo1\n\nLas reglas básicas del sistema\n\n" +
                "Capitulo 2\n\nLas personas y sus atributos\n\n$pie",
            "Capitulo 3\n\nLos bienes y las cosas\n\n1\n\nCapitulo1\n\nLas reglas básicas del sistema\n\n" +
                "Sumario: 1. EI orden de las normas. 2. Las fuentes escritas. 3. La costumbre. 4.\n" +
                "La jurisprudencia. 5. La doctrina. 5.1. Autores clásicos. 5.2. Autores actuales.\n" +
                "6.1. El texto de la norma. 7. Cierre del tema\n\n" +
                "1. EI orden de las normas\n\n" +
                "EI sistema jurídico ordena sus normas de mayor a menor jerarquía, lo gue permite\n" +
                "resolver los conflictos entre ellas (\"). Asimism0, cada norma inferior tiene\n" +
                "gue respetar a la superior, y el juez debe aplicar la de mayor rango cuando las\n\n2\n\n" +
                "dos regulan el mismo caso de manera distinta.\n\n$pie",
            "La regla general admite excepciones () que la ley prevé de manera expresa, y en esos\n" +
                "casos n0 se aplica el orden común sino la norma especial que corresponde.\n\n3\n\n" +
                "2. Las fuentes escritas\n\n" +
                "Las fuentes escritas son la constitución, las leyes y los reglamentos, gue el juez\n" +
                "consulta antes que cualquier otra fuente cuando tiene que decidir un caso.\n\n4\n\n$pie",
            "La costumbre ocupa un lugar menor, pero sigue siendo útil para llenar los vacíos\n" +
                "que dejan las normas escritas en algunos ámbitos del comercio.\n\n5\n\n" +
                "La doctrina, por su parte, orienta la interpretación sin obligar a los jueces.\n\n6\n\n$pie",
        )
        return paginas.mapIndexed { i, t -> PaginaExtraida(i + 1, t) }
    }

    @Test
    fun `un libro escaneado de a dos paginas arma sus secciones sin indice numeros ni errores del OCR`() {
        // Hallazgos 1, 4, 5, 6 y 7 de la ronda 4.
        val doc = armar(libroEscaneado())
        assertEquals(
            describir(doc),
            listOf("Derecho de prueba", "Capitulo 1 · Las reglas básicas del sistema", "1. El orden de las normas", "2. Las fuentes escritas"),
            doc.secciones.mapNotNull { it.titulo },
        )
        val texto = todasLasTarjetas(doc).joinToString("\n") { it.texto }
        for (basura in listOf("CamScanner", "Capitulo 2", "Los bienes y las cosas", "EI ", " gue ", "Asimism0", "n0 ", "()", "(\")")) {
            assertFalse("«$basura» en:\n$texto", texto.contains(basura))
        }
        assertFalse(texto, Regex("(?m)^\\d+$|\\s\\d\\s").containsMatchIn(texto))
        tarjetaCon(doc, "lo que permite resolver los conflictos entre ellas. Asimismo,")
        tarjetaCon(doc, "la de mayor rango cuando las dos regulan el mismo caso de manera distinta.")
        tarjetaCon(doc, "en esos casos no se aplica el orden común")
        tarjetaCon(doc, "Sumario: 1. El orden de las normas.")
        tarjetaCon(doc, "6.1. El texto de la norma. 7. Cierre del tema")
    }

    @Test
    fun `un capitulo con el numero pegado y su subtitulo forman un solo titulo`() {
        // Hallazgo 7 de la ronda 4.
        val texto = "Capítulo2\n\nEl derecho privado: principios generales y sus instituciones\npropias\n\n" +
            "El derecho privado regula las relaciones entre las personas y sus bienes, y se organiza en ramas."
        val doc = armar(listOf(PaginaExtraida(1, texto)))
        assertEquals(listOf("Capítulo 2 · El derecho privado: principios generales y sus instituciones propias"), doc.secciones.map { it.titulo })
    }
}
