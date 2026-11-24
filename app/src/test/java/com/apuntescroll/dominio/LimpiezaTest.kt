package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimpiezaTest {

    private fun normalizar(texto: String) = Limpieza.normalizarLineas(texto)

    /** Oraciones distintas para el cuerpo de cada página (sin palabras en común entre sí). */
    private val CUERPOS = listOf(
        "La demanda cae.", "Sube la oferta.", "Hay equilibrio.", "Cambian los gustos.", "Llega el invierno.", "Crece el empleo.",
    )

    @Test
    fun `cambia los finales de linea de Windows y de Mac viejo por saltos comunes`() {
        assertEquals(listOf("uno", "dos", "tres"), normalizar("uno\r\ndos\rtres"))
    }

    @Test
    fun `reemplaza las ligaduras por letras comunes`() {
        assertEquals(
            listOf("eficiente flujo staff office affluent best"),
            normalizar("eﬁciente ﬂujo staﬀ oﬃce aﬄuent beﬆ"),
        )
    }

    @Test
    fun `cambia espacios raros por espacios y colapsa los repetidos`() {
        assertEquals(listOf("10 % del PBI"), normalizar("  10\u00A0%\u2009\u2009del \t PBI  "))
    }

    @Test
    fun `quita guiones blandos y caracteres de ancho cero`() {
        assertEquals(listOf("palabra elasticidad"), normalizar("pala\u00ADbra elas\u200Bti\u200Dcidad\uFEFF"))
    }

    @Test
    fun `un guion blando al final de la linea cuenta como guion`() {
        assertEquals(listOf("la cantidad deman-", "dada"), normalizar("la cantidad deman\u00AD\ndada"))
    }

    @Test
    fun `normaliza las comillas tipograficas y compone los acentos separados`() {
        assertEquals(listOf("\"oferta\" y 'demanda' en economía"), normalizar("“oferta” y ‘demanda’ en economía"))
    }

    @Test
    fun `una pagina en blanco no da lineas`() {
        assertEquals(emptyList<String>(), normalizar(" \n\t\n "))
    }

    @Test
    fun `las vinetas de distintas fuentes quedan como un punto y espacio`() {
        val lineas = listOf("• uno", "●dos", "▪ tres", "◦ cuatro", "\uF0B7 cinco", "‣ seis", "· siete", "- ocho", "– nueve", "* diez")
        val limpias = lineas.flatMap { normalizar(it) }.map(Limpieza::limpiarLinea)
        assertEquals(
            listOf("• uno", "• dos", "• tres", "• cuatro", "• cinco", "• seis", "• siete", "• ocho", "• nueve", "• diez"),
            limpias,
        )
    }

    @Test
    fun `un guion pegado a un numero no es una vineta`() {
        assertEquals("-5 es un número negativo", Limpieza.limpiarLinea("-5 es un número negativo"))
    }

    @Test
    fun `las lineas de puro simbolo se descartan`() {
        for (linea in listOf("..........", "* * *", "|", "~", "_____", "•", "→")) {
            assertEquals("«$linea»", "", Limpieza.limpiarLinea(linea))
        }
    }

    @Test
    fun `un renglon de indice queda como item con el texto y sin la pagina`() {
        assertEquals("• 1.2 Elasticidad precio", Limpieza.limpiarLinea("1.2 Elasticidad precio ............... 14"))
        assertEquals("• Introducción", Limpieza.limpiarLinea("Introducción. . . . . . . . 3"))
        assertEquals("", Limpieza.limpiarLinea("................ 12"))
    }

    @Test
    fun `reconoce los numeros de pagina sueltos`() {
        for (linea in listOf("12", "- 12 -", "– 7 –", "Página 12", "Pág. 12 de 40", "pág 3", "p. 9", "12/40", "3 de 40", "[5]", "iv")) {
            assertTrue("«$linea»", Limpieza.esNumeroPagina(linea))
        }
        for (linea in listOf("12 empresas", "Unidad 12", "Pág. doce", "1.2 Elasticidad")) {
            assertFalse("«$linea»", Limpieza.esNumeroPagina(linea))
        }
    }

    @Test
    fun `la clave de repeticion ignora mayusculas acentos y el tipo de guion pero conserva los numeros`() {
        assertEquals(Limpieza.claveRepeticion("Economía I – Unidad 3"), Limpieza.claveRepeticion("ECONOMIA I - Unidad 3"))
        assertEquals("economia i - unidad 3", Limpieza.claveRepeticion("Economía  I – Unidad 3"))
        assertFalse(Limpieza.claveRepeticion("Ejercicio 1") == Limpieza.claveRepeticion("Ejercicio 2"))
    }

    @Test
    fun `un contador de pagina pegado a un encabezado se compara sin el numero`() {
        // Página 12 con "12" y página 13 con "13": misma clave de contador.
        val a = Limpieza.clavesDeBorde("Economía I – Unidad 3     12", 12)
        val b = Limpieza.clavesDeBorde("Economía I – Unidad 3     13", 13)
        assertTrue(a.intersect(b.toSet()).isNotEmpty())
        // El número que sigue a una etiqueta no es un contador aunque coincida con la página.
        assertEquals(listOf("ejercicio 4"), Limpieza.clavesDeBorde("Ejercicio 4", 4))
        assertEquals(listOf("unidad n° 2"), Limpieza.clavesDeBorde("Unidad N° 2", 2))
    }

    @Test
    fun `un renglon de indice no se confunde con el titulo que se repite arriba de otra pagina`() {
        val paginas = listOf(
            listOf("ÍNDICE", "1. Introducción ........ 3", "2.1 La demanda ........ 3"),
            listOf("1. Introducción", "La microeconomía estudia decisiones.", "2.1 La demanda", "La demanda muestra cantidades."),
            listOf("Otra página con texto.", "Y su final."),
        )
        assertEquals(paginas, Limpieza.quitarEncabezadosYPies(paginas))
    }

    @Test
    fun `quita encabezados y pies que se repiten en al menos el 40 por ciento de las paginas`() {
        val paginas = (1..5).map { n ->
            listOf("Economía I – Unidad 3", "Facultad de Ciencias Económicas – UBA", CUERPOS[n - 1], "Prof. Pérez – Pág. $n")
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals(listOf(CUERPOS[i]), lineas)
    }

    @Test
    fun `no toma como encabezado una linea que se repite en menos del 40 por ciento`() {
        val paginas = (1..6).map { n -> listOf(if (n <= 2) "Nota importante" else "Inicio ${"x".repeat(n)}", CUERPOS[n - 1]) }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals("Nota importante", limpias[0][0])
        assertEquals("Nota importante", limpias[1][0])
    }

    @Test
    fun `con menos de tres paginas no busca encabezados pero si quita numeros de pagina`() {
        val paginas = listOf(
            listOf("Apunte de Economía", "Primer párrafo.", "1"),
            listOf("Apunte de Economía", "Segundo párrafo.", "- 2 -"),
        )
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(listOf("Apunte de Economía", "Primer párrafo."), limpias[0])
        assertEquals(listOf("Apunte de Economía", "Segundo párrafo."), limpias[1])
    }

    @Test
    fun `solo quita encabezados en los bordes y no la misma frase en el medio del texto`() {
        val paginas = (1..3).map { listOf("Economía I – Unidad 3", CUERPOS[it - 1], "Economía I – Unidad 3", "Medio.", CUERPOS[it + 2], "Fin ${"z".repeat(it)}.") }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(listOf(CUERPOS[0], "Economía I – Unidad 3", "Medio.", CUERPOS[3], "Fin z."), limpias[0])
    }

    @Test
    fun `quita el numero de pagina que queda arriba del encabezado`() {
        val paginas = (1..3).map { listOf("$it", "Economía I – Unidad 3", CUERPOS[it - 1]) }
        assertEquals(CUERPOS.take(3).map { listOf(it) }, Limpieza.quitarEncabezadosYPies(paginas))
    }

    @Test
    fun `un encabezado que cambia por unidad se quita si se repite en tres paginas seguidas`() {
        val unidades = listOf("Unidad 3 – Elasticidad", "Unidad 4 – Costos", "Unidad 5 – Mercados")
        // Páginas de libro (muchas líneas): no son diapositivas.
        val cuerpos = (0 until 9).map { i -> (1..20).map { j -> "Renglón $j de la página ${"x".repeat(i + 1)}." } }
        val paginas = (0 until 9).map { i -> listOf(unidades[i / 3]) + cuerpos[i] }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals(cuerpos[i], lineas)
    }

    @Test
    fun `una linea repetida en dos paginas salteadas de diez no es encabezado`() {
        val paginas = (0 until 10).map { i -> listOf(if (i == 2 || i == 7) "Ejercicios" else "Inicio ${"y".repeat(i + 1)}", "Cuerpo ${"x".repeat(i + 1)}.") }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals("Ejercicios", limpias[2][0])
        assertEquals("Ejercicios", limpias[7][0])
    }

    @Test
    fun `las paginas sin texto no cuentan para el 40 por ciento`() {
        val conTexto = (1..3).map { listOf("Encabezado del apunte", CUERPOS[it - 1]) }
        val paginas = conTexto + List(5) { emptyList() }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(listOf(CUERPOS[0]), limpias[0])
    }

    // ---- Revisión: números que no son contadores de página (hallazgos 1 y 4) ----

    /** Enunciado distinto para cada ejercicio, en dos renglones. */
    private fun enunciado(n: Int) = listOf(
        "Dada la función de demanda Q = ${100 + 7 * n} - ${n + 1}P, calcule la elasticidad precio",
        "cuando el precio vale ${n * 5} pesos y diga si la demanda es elástica en ese punto ($n).",
    )

    @Test
    fun `las etiquetas Ejercicio N de una guia de trabajos practicos no se toman como encabezado`() {
        // 8 páginas con 2 o 3 ejercicios cada una (20 en total), cada uno con su enunciado.
        val porPagina = listOf(3, 2, 3, 2, 3, 2, 3, 2)
        var n = 0
        val paginas = porPagina.map { cantidad -> (1..cantidad).flatMap { n++; listOf("Ejercicio $n") + enunciado(n) } }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(paginas, limpias)
        assertEquals((1..20).map { "Ejercicio $it" }, limpias.flatten().filter { it.startsWith("Ejercicio") })
    }

    @Test
    fun `un ejercicio por pagina con el mismo numero que la pagina no es un contador de pagina`() {
        val paginas = (1..5).map { i -> listOf("Ejercicio $i") + enunciado(i) }
        assertEquals(paginas, Limpieza.quitarEncabezadosYPies(paginas))
    }

    @Test
    fun `Unidad N sola en las paginas impares no se pierde`() {
        val nombres = listOf("Introducción a la economía", "Oferta y demanda", "Elasticidad", "Costos de producción", "Mercados")
        val paginas = (1..10).map { p ->
            val cuerpo = listOf("Texto propio del tramo ${marca(p)}, con un desarrollo que no se repite en ninguna otra.", "Cierre $p.")
            if (p % 2 == 1) listOf("Unidad ${(p + 1) / 2}", nombres[p / 2]) + cuerpo else cuerpo
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(paginas, limpias)
    }

    @Test
    fun `los totales al pie de cada ejercicio no se pierden`() {
        val paginas = (1..5).map { i -> listOf("Ejercicio $i") + enunciado(i) + "Total del Activo: $ ${i * 1500}" }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals("Total del Activo: $ ${(i + 1) * 1500}", lineas.last())
    }

    @Test
    fun `un contador de pagina corrido pegado al encabezado se sigue quitando`() {
        // La numeración impresa empieza en 12 aunque sea la página 1 del PDF.
        val paginas = (1..4).map { p -> listOf("Economía I – Unidad 3          ${p + 11}") + enunciado(p) }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals(enunciado(i + 1), lineas)
    }

    @Test
    fun `una respuesta numerica al final de la pagina no es un numero de pagina`() {
        val pagina = listOf("Se igualan la oferta y la demanda.", "Por lo tanto, la cantidad demandada de equilibrio es:", "80")
        assertEquals(listOf(pagina), Limpieza.quitarEncabezadosYPies(listOf(pagina)))
    }

    @Test
    fun `las celdas de una tabla escaneada al pie de la pagina no se pierden`() {
        // ML Kit devuelve cada celda como un bloque aparte.
        val pagina = listOf("Precio", "", "Cantidad", "", "10", "", "100", "", "8", "", "140")
        assertEquals(listOf(pagina), Limpieza.quitarEncabezadosYPies(listOf(pagina), listOf(8)))
    }

    @Test
    fun `un numero suelto que no sigue la numeracion del documento se conserva`() {
        val paginas = (1..4).map { p -> enunciado(p) + if (p < 4) "$p" else "80" }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for (p in 1..3) assertEquals(enunciado(p), limpias[p - 1])
        assertEquals(enunciado(4) + "80", limpias[3])
    }

    @Test
    fun `los numeros de pagina impresos distintos de los del pdf se quitan si la diferencia se repite`() {
        val paginas = listOf(7, 8).map { p -> enunciado(p) + "${p + 5}" }
        assertEquals(listOf(enunciado(7), enunciado(8)), Limpieza.quitarEncabezadosYPies(paginas, listOf(7, 8)))
    }

    // ---- Revisión: diapositivas y libros (hallazgos 5 y 6) ----

    @Test
    fun `el titulo repetido en varias diapositivas se deja la primera vez`() {
        val pie = "Microeconomía – Clase 3"
        val paginas = listOf(
            listOf("ELASTICIDAD PRECIO", "• Mide cuánto cambia la cantidad ante el precio.", "• Se calcula con variaciones porcentuales.", pie),
            listOf("ELASTICIDAD PRECIO", "• Es mayor con sustitutos cercanos.", "• Crece con el horizonte temporal.", pie),
            listOf("ELASTICIDAD PRECIO", "• Define si el gasto sube o baja.", "• Se usa para fijar precios.", pie),
            listOf("ELASTICIDAD INGRESO", "• Mide la reacción de la cantidad ante el ingreso.", "• Es positiva para los bienes normales.", pie),
        )
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(paginas[0].dropLast(1), limpias[0])
        assertEquals(paginas[1].subList(1, 3), limpias[1])
        assertEquals(paginas[2].subList(1, 3), limpias[2])
        assertEquals(paginas[3].dropLast(1), limpias[3])
    }

    /** Letras distintas para cada número ("b", "c"... "ba"), para que el cuerpo no repita el número de página. */
    private fun marca(n: Int): String = if (n < 26) ('a' + n).toString() else marca(n / 26) + ('a' + n % 26)

    /** Un renglón de libro distinto para cada página y posición. */
    private fun renglon(pagina: Int, j: Int) = "Renglón $j del tramo ${marca(pagina)}, con texto corrido que llega hasta el margen derecho."

    @Test
    fun `un encabezado de unidad que aparece recien en la segunda pagina de un libro se quita entero`() {
        val paginas = (1..6).map { p ->
            val cuerpo = (1..20).map { renglon(p, it) }
            when (p) {
                1 -> listOf("UNIDAD 3: ELASTICIDAD") + cuerpo
                in 2..5 -> listOf("Unidad 3 – Elasticidad") + cuerpo
                else -> listOf("UNIDAD 4: COSTOS") + cuerpo
            }
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        assertEquals(paginas[0], limpias[0])
        for (p in 2..5) assertEquals((1..20).map { renglon(p, it) }, limpias[p - 1])
        assertEquals(paginas[5], limpias[5])
    }

    @Test
    fun `los encabezados de capitulo de las paginas impares de un libro se quitan`() {
        val capitulo = { n: Int -> if (n < 86) "CAPÍTULO 4 • LA OFERTA Y LA DEMANDA" else if (n < 92) "CAPÍTULO 5 • LA ELASTICIDAD" else "CAPÍTULO 6 • LA POLÍTICA ECONÓMICA" }
        val numeros = (80..97).toList()
        val paginas = numeros.map { n ->
            val encabezado = if (n % 2 == 0) "$n PARTE II • CÓMO FUNCIONAN LOS MERCADOS" else "${capitulo(n)} $n"
            listOf(encabezado) + (1..20).map { renglon(n, it) }
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas, numeros)
        for ((i, n) in numeros.withIndex()) assertEquals((1..20).map { renglon(n, it) }, limpias[i])
    }

    // ---- Revisión: guiones tipográficos (hallazgo 10) ----

    @Test
    fun `los guiones tipograficos de fin de linea pasan a guion comun`() {
        assertEquals(listOf("la cantidad deman-", "dada"), normalizar("la cantidad deman\u2010\ndada"))
        assertEquals(listOf("la cantidad deman-", "dada"), normalizar("la cantidad deman\u2011\ndada"))
    }

    // ---- Ronda 2 ----

    /** [cantidad] páginas de libro de 20 renglones distintos, con [extra] arriba o abajo de cada una. */
    private fun libroCon(cantidad: Int, arriba: Boolean, extra: (Int) -> String?): List<List<String>> = (1..cantidad).map { p ->
        val cuerpo = (1..20).map { renglon(p, it) }
        val linea = extra(p)
        when {
            linea == null -> cuerpo
            arriba -> listOf(linea) + cuerpo
            else -> cuerpo + linea
        }
    }

    @Test
    fun `los pies con el contador de pagina en el medio de la linea se quitan`() {
        // Hallazgo 1 de la ronda 2.
        val pies = listOf<(Int) -> String>(
            { p -> "Apunte de cátedra – Economía I Página $p de 8" },
            { p -> "Página $p de 8 Economía I – FCE UBA" },
            { p -> "J. Pérez (UBA) Elasticidad 12 de mayo $p / 8" },
            { p -> "Hoja $p de 8" },
            { p -> "Economía I, p.$p" },
        )
        for (pie in pies) {
            val limpias = Limpieza.quitarEncabezadosYPies(libroCon(8, arriba = false, pie))
            for ((i, lineas) in limpias.withIndex()) assertEquals(pie(i + 1), (1..20).map { renglon(i + 1, it) }, lineas)
        }
        val limpias = Limpieza.quitarEncabezadosYPies(libroCon(8, arriba = true) { p -> "Pág. $p – Microeconomía I" })
        for ((i, lineas) in limpias.withIndex()) assertEquals((1..20).map { renglon(i + 1, it) }, lineas)
    }

    @Test
    fun `un encabezado por unidad se quita aunque cada unidad dure dos paginas`() {
        // Hallazgo 7 de la ronda 2: 5 unidades de 2 páginas.
        val encabezados = listOf<(Int) -> String>(
            { p -> "Resumen de Economía I – Unidad ${(p + 1) / 2}" },
            { p -> "Economía I – Unidad ${(p + 1) / 2}          $p" },
            { p -> "ECONOMÍA I – UNIDAD ${(p + 1) / 2}" },
        )
        for (encabezado in encabezados) {
            val limpias = Limpieza.quitarEncabezadosYPies(libroCon(10, arriba = true, encabezado))
            for ((i, lineas) in limpias.withIndex()) assertEquals(encabezado(i + 1), (1..20).map { renglon(i + 1, it) }, lineas)
        }
    }

    @Test
    fun `un ejercicio por pagina con el mismo encabezado no se toma como encabezado por unidad`() {
        // Guarda del hallazgo 7: el número de la etiqueta cambia en cada página.
        val conPuntaje = libroCon(10, arriba = true) { p -> "Ejercicio $p (2 puntos)" }
        assertEquals(conPuntaje, Limpieza.quitarEncabezadosYPies(conPuntaje))
        val conTitulo = libroCon(10, arriba = true) { p -> "Guía de trabajos prácticos – Ejercicio $p" }
        assertEquals(conTitulo, Limpieza.quitarEncabezadosYPies(conTitulo))
    }

    @Test
    fun `una linea repetida en tres paginas alternadas no se borra`() {
        // Hallazgo 5 de la ronda 2.
        val tp = libroCon(12, arriba = false) { p -> if (p in listOf(2, 4, 6)) "Justifique su respuesta." else null }
        assertEquals(tp, Limpieza.quitarEncabezadosYPies(tp))
        val texto = libroCon(14, arriba = false) { p -> if (p in listOf(3, 5, 7)) "Fuente: elaboración propia sobre datos del INDEC." else null }
        assertEquals(texto, Limpieza.quitarEncabezadosYPies(texto))
    }

    @Test
    fun `un encabezado de libro sin numero en cinco paginas impares se quita`() {
        // Hallazgo 5 de la ronda 2: la regla de páginas alternadas sigue sirviendo para libros.
        val paginas = libroCon(20, arriba = true) { p ->
            when {
                p % 2 == 1 -> "PARTE II • CÓMO FUNCIONAN LOS MERCADOS"
                p <= 10 -> "CAPÍTULO 4 • LA OFERTA Y LA DEMANDA"
                else -> "CAPÍTULO 5 • LA ELASTICIDAD"
            }
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals((1..20).map { renglon(i + 1, it) }, lineas)
    }

    @Test
    fun `los numeros de pagina de un cuadernillo con dos numeraciones se quitan`() {
        // Hallazgo 8 de la ronda 2: páginas 1 a 5 impresas 45 a 49, 6 a 12 impresas 112 a 118.
        val paginas = libroCon(12, arriba = false) { p -> if (p <= 5) "${p + 44}" else "${p + 106}" }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) assertEquals((1..20).map { renglon(i + 1, it) }, lineas)
    }

    // ---- Ronda 4 ----

    @Test
    fun `la pestana lateral de seccion se quita aunque la seccion dure una pagina`() {
        // Hallazgo 3 de la ronda 4: "PARTE I · 1" arriba del pie, secciones de 1 a 4 páginas.
        val paginasPorSeccion = listOf(3, 1, 4, 2, 1, 3, 2, 4, 1, 3, 2, 4)
        val pestanas = paginasPorSeccion.flatMapIndexed { s, cantidad ->
            val parte = if (s < 6) "I" else "II"
            List(cantidad) { "PARTE $parte · ${s % 6 + 1}" }
        }
        val total = pestanas.size
        val paginas = pestanas.mapIndexed { i, pestana ->
            val p = i + 1
            val arriba = when (p) { 1 -> listOf("Parte I"); 16 -> listOf("PARTE II"); else -> emptyList() }
            arriba + (1..20).map { renglon(p, it) } + pestana + "$p / $total Manual de prácticas · Cátedra"
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) {
            val p = i + 1
            val arriba = when (p) { 1 -> listOf("Parte I"); 16 -> listOf("PARTE II"); else -> emptyList() }
            assertEquals("página $p", arriba + (1..20).map { renglon(p, it) }, lineas)
        }
    }

    @Test
    fun `los numeros de pagina intercalados de un escaneo de a dos paginas se quitan`() {
        // Hallazgo 4 de la ronda 4: dos páginas del libro por página del PDF, cada una con su número.
        val paginas = (1..5).map { p ->
            val a = 2 * p + 17
            val bloqueA = listOf("Primer tramo del libro en la hoja ${marca(p)}, que termina con una frase", "completa en este renglón.")
            val bloqueB = if (p == 3) {
                listOf("Segundo tramo de la hoja ${marca(p)}, con un dato suelto abajo.", "", "35", "", "Y una frase que sigue en el tramo de las")
            } else {
                listOf("Segundo tramo de la hoja ${marca(p)}, con una frase que también termina.")
            }
            bloqueA + "" + "$a" + "" + bloqueB + "" + "${a + 1}"
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        val numeros = limpias.flatten().filter { it.isNotEmpty() && it.all(Char::isDigit) }
        assertEquals(listOf("35"), numeros)
    }

    @Test
    fun `al quitar un numero intercalado la frase que sigue en minuscula queda en el mismo bloque`() {
        val paginas = (1..3).map { p ->
            val a = 2 * p + 17
            listOf("Una frase del libro en la hoja ${marca(p)} que sigue en la página de", "", "$a", "",
                "abajo sin cortarse en la hoja ${marca(p)} y termina acá.", "", "${a + 1}")
        }
        val limpias = Limpieza.quitarEncabezadosYPies(paginas)
        for ((i, lineas) in limpias.withIndex()) {
            val m = marca(i + 1)
            assertEquals(listOf("Una frase del libro en la hoja $m que sigue en la página de", "abajo sin cortarse en la hoja $m y termina acá.", ""), lineas)
        }
    }
}
