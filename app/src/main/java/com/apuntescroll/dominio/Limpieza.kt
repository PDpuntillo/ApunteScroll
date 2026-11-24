package com.apuntescroll.dominio

import java.text.Normalizer

/**
 * Primer paso del armado (PLAN.md, sección 3.2): deja el texto crudo de cada página en líneas
 * limpias. Las líneas vacías se conservan como "" porque separan párrafos.
 *
 * 1. [normalizarLineas]: saltos de línea, ligaduras, comillas, espacios raros y caracteres
 *    invisibles.
 * 2. [quitarEncabezadosYPies]: líneas repetidas arriba o abajo de las páginas y números de
 *    página sueltos.
 * 3. [limpiarLinea]: viñetas, renglones de índice y líneas de puro símbolo.
 */
internal object Limpieza {

    /** Una línea se repite si aparece arriba o abajo en al menos esta fracción de las páginas. */
    const val PROPORCION_REPETIDA = 0.4

    /** Con menos páginas con texto no se buscan encabezados ni pies repetidos. */
    const val MIN_PAGINAS_CON_TEXTO = 3

    /** Cuántas líneas (no vacías) de arriba y de abajo de cada página se miran. */
    private const val LINEAS_DE_BORDE = 2

    /** Guion blando al final de una línea: ahí sí cortaba una palabra, así que vale como guion. */
    private val GUION_BLANDO_FINAL = Regex("\\u00AD[ \\t]*(?=\\r|\\n|$)")

    /**
     * Normaliza los caracteres del texto y lo devuelve en líneas recortadas, sin espacios dobles.
     * Los guiones blandos se quitan, salvo al final de una línea, donde pasan a ser "-". Antes de
     * colapsar los espacios se unen las letras espaciadas de los títulos, y al final se corrigen los
     * errores típicos del OCR ([Correcciones]).
     */
    fun normalizarLineas(texto: String): List<String> {
        if (texto.isBlank()) return emptyList()
        val nfc = Normalizer.normalize(texto, Normalizer.Form.NFC).replace(GUION_BLANDO_FINAL, "-")
        val sb = StringBuilder(nfc.length)
        var i = 0
        while (i < nfc.length) {
            val c = nfc[i]
            if (c == '\r') {
                sb.append('\n')
                if (i + 1 < nfc.length && nfc[i + 1] == '\n') i++
            } else {
                val r = reemplazo(c)
                if (r == null) sb.append(c) else sb.append(r)
            }
            i++
        }
        return sb.split('\n').map { Correcciones.corregirOcr(normalizarEspacios(Correcciones.unirLetrasEspaciadas(it))) }
    }

    private fun reemplazo(c: Char): String? = when (c) {
        'ﬀ' -> "ff"
        'ﬁ' -> "fi"
        'ﬂ' -> "fl"
        'ﬃ' -> "ffi"
        'ﬄ' -> "ffl"
        'ﬅ', 'ﬆ' -> "st"
        // Guion blando, espacios de ancho cero, BOM.
        '\u00AD', '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF' -> ""
        '\u2028', '\u2029', '\u0085' -> "\n"
        // Guiones tipográficos de corte de palabra (U+2010, U+2011): valen como guion común.
        '\u2010', '\u2011' -> "-"
        '“', '”', '„', '‟' -> "\""
        '‘', '’', '‚', '‛' -> "'"
        '\n' -> null
        else -> when {
            c == '\u00A0' || c == '\u202F' || c == '\u205F' || c == '\u3000' || c in '\u2000'..'\u200A' -> " "
            c < ' ' || c in '\u007F'..'\u009F' -> " "
            else -> null
        }
    }

    /**
     * Recorta la línea y colapsa los espacios. Los caracteres de uso privado (los que PdfBox saca
     * de las fuentes Symbol o Wingdings) son viñetas si abren la línea; en otro lugar, espacios.
     */
    private fun normalizarEspacios(linea: String): String {
        val sb = StringBuilder(linea.length)
        var espacioPendiente = false
        for (original in linea) {
            val c = if (original in '\uE000'..'\uF8FF') (if (sb.isEmpty()) '•' else ' ') else original
            if (c == ' ') {
                if (sb.isNotEmpty()) espacioPendiente = true
            } else {
                if (espacioPendiente) sb.append(' ')
                espacioPendiente = false
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Quita encabezados y pies repetidos y números de página sueltos de las páginas de [entrada];
     * [numeros] son los números de página de cada una (por defecto 1, 2, 3...). Antes que nada se
     * quitan los números de página intercalados en el medio de la página (escaneos de a dos páginas).
     *
     * Encabezados y pies (solo con al menos [MIN_PAGINAS_CON_TEXTO] páginas con texto): se miran
     * las 2 primeras y las 2 últimas líneas no vacías de cada página, sin contar los renglones de
     * índice, y se comparan por varias claves ([clavesDeBorde]):
     * - La línea entera ([claveRepeticion]), que conserva los números.
     * - Contador: la línea con un número cambiado por "#", si ese número va con la página
     *   ("Economía I – Unidad 3   12", "Página 5 de 8 · Economía I", "Economía I, p.5"); la
     *   diferencia entre el número y la página tiene que ser la misma en todas. El número que sigue
     *   a una etiqueta ("Ejercicio 3", "Unidad 2", "Tabla 4") nunca es un contador.
     * - Etiqueta: la línea con los números cambiados por "#", si tiene una etiqueta numerada que no
     *   está al principio ("Resumen de Economía I – Unidad 3"): un encabezado que cambia por unidad.
     * - Numerales: la línea con todos sus números, arábigos y romanos, cambiados por "#", anotando
     *   si está arriba o abajo ("PARTE I · 1" → "parte # · #"): la pestaña de sección de un apunte.
     *   Vale como la de etiqueta (ver abajo); como lleva el borde, el título "Parte I" de arriba de
     *   una página no se confunde con la pestaña de abajo.
     *
     * Una clave de línea entera o de contador es de un encabezado o un pie si aparece en al menos
     * el 40 % de las páginas con texto (y en 2 como mínimo) o en 3 páginas seguidas. Los libros
     * alternan encabezados: una clave de contador también vale en 3 páginas pares o impares
     * seguidas, y una de línea entera en 5 si es la primera línea de esas páginas y no son
     * diapositivas; el título del capítulo en su página de apertura (la misma línea, una sola vez,
     * fuera de la racha) no lo impide y se deja. Una clave de etiqueta o de numerales solo vale con
     * el 40 %, si sus números se repiten en páginas vecinas en al menos la mitad de las veces (una
     * unidad o una sección dura más de una página; un ejercicio no) y si no son diapositivas. Se
     * quita de esas posiciones en todas las páginas.
     *
     * Excepción (diapositivas: páginas de menos de [MAX_LINEAS_DIAPOSITIVA] líneas y menos de
     * [MAX_CARACTERES_DIAPOSITIVA] caracteres): primero se separan los encabezados fijos (claves de
     * contador o de etiqueta, las que están en todas o casi todas las páginas y las que van arriba
     * de un título de verdad). Una línea repetida que no es fija y que, saltando los fijos, es
     * siempre la primera de su diapositiva es el título de varias diapositivas: se deja la primera
     * vez de cada tanda de páginas seguidas.
     *
     * Números de página: después se quitan desde cada borde las líneas con una clave repetida y
     * los números de página ([esNumeroPagina]) que corresponden a la página. "Página 12" o "Pág. 12
     * de 40" siempre; un número suelto ("12", "- 12 -", "12/40", "iv") si la diferencia con el
     * número de la página es la más común del documento entre los números sueltos de los bordes
     * (cuando se repite en al menos [MIN_PAGINAS_DESPLAZAMIENTO] páginas; si no, tiene que ser
     * igual al número de la página), la dominante del documento (la que más páginas muestran entre
     * números sueltos y contadores, en al menos 3) o la misma que la de un número suelto de una
     * página vecina (un cuadernillo armado con partes de libros distintos). Así no se pierde una
     * respuesta ("80") ni la última celda de una tabla. En páginas de libro, también se quita la
     * primera o la última línea si es más corta que el texto y tiene el contador con la diferencia
     * dominante, aunque aparezca una sola vez (el encabezado de sección de LaTeX, "4.2. LA DEMANDA
     * 85").
     */
    fun quitarEncabezadosYPies(
        entrada: List<List<String>>,
        numeros: List<Int> = List(entrada.size) { it + 1 },
    ): List<List<String>> {
        require(numeros.size == entrada.size)
        // Primero los números intercalados (con todos, la secuencia se ve completa).
        val paginas = quitarNumerosIntercalados(entrada)
        val conTexto = paginas.count { lineas -> lineas.any { it.isNotEmpty() } }
        val repetidas = if (conTexto >= MIN_PAGINAS_CON_TEXTO) buscarRepetidas(paginas, numeros, conTexto) else Repetidas.NINGUNA
        val numeracion = Numeracion.de(paginas, numeros)
        val dominante = desplazamientoDominante(repetidas, numeracion)
        // Libro o apunte de texto corrido: la mitad de las páginas tiene muchas líneas.
        val largos = paginas.map { lineas -> lineas.count { it.isNotEmpty() } }.filter { it > 0 }.sorted()
        val esLibro = largos.isNotEmpty() && largos[largos.size / 2] >= MAX_LINEAS_DIAPOSITIVA
        var ordinal = -1
        return paginas.indices.map { i ->
            if (paginas[i].any { it.isNotEmpty() }) ordinal++
            quitarBordes(paginas[i], numeros[i], ordinal, repetidas, numeracion, dominante, esLibro)
        }
    }

    private class NumeroSuelto(val pagina: Int, val linea: Int, val valor: Int)

    private val SOLO_NUMERO = Regex("^\\d{1,4}$")

    /**
     * Números de página en el medio de la página (un escaneo con dos páginas del libro por página
     * del PDF): un renglón que es solo un número, solo en su bloque (con líneas vacías o el borde
     * arriba y abajo), se quita si forma con otros una secuencia creciente a lo largo del documento:
     * cada uno de 1 a 3 más que el anterior, a no más de 2 páginas del PDF, como mucho 2 por página
     * y al menos 3 en total en más de una página. Un número suelto que no sigue la secuencia (un
     * dato, una celda) se conserva.
     */
    private fun quitarNumerosIntercalados(paginas: List<List<String>>): List<List<String>> {
        val sueltos = mutableListOf<NumeroSuelto>()
        for ((p, lineas) in paginas.withIndex()) {
            for ((j, l) in lineas.withIndex()) {
                if (!SOLO_NUMERO.matches(l)) continue
                val solo = (j == 0 || lineas[j - 1].isEmpty()) && (j == lineas.lastIndex || lineas[j + 1].isEmpty())
                if (solo) sueltos.add(NumeroSuelto(p, j, l.toInt()))
            }
        }
        val cadenas = mutableListOf<MutableList<NumeroSuelto>>()
        for (n in sueltos) {
            val cadena = cadenas
                .filter { c ->
                    val ultimo = c.last()
                    n.valor - ultimo.valor in 1..3 && n.pagina - ultimo.pagina in 0..2 && c.count { it.pagina == n.pagina } < 2
                }
                .minByOrNull { n.valor - it.last().valor }
            if (cadena != null) cadena.add(n) else cadenas.add(mutableListOf(n))
        }
        val quitar = cadenas.filter { c -> c.size >= 3 && c.first().pagina != c.last().pagina }.flatten()
        if (quitar.isEmpty()) return paginas
        val porPagina = quitar.groupBy({ it.pagina }, { it.linea })
        return paginas.mapIndexed { p, lineas ->
            val fuera = porPagina[p]?.toSet() ?: return@mapIndexed lineas
            sinNumeros(lineas, fuera)
        }
    }

    /**
     * Quita las líneas [fuera] (números de página intercalados). Si la frase de arriba sigue abajo
     * en minúscula (el cambio de página del libro), también quita las líneas vacías del medio para
     * que el párrafo se una.
     */
    private fun sinNumeros(lineas: List<String>, fuera: Set<Int>): List<String> {
        val resultado = mutableListOf<String>()
        var j = 0
        while (j < lineas.size) {
            if (j !in fuera) {
                resultado.add(lineas[j])
                j++
                continue
            }
            val anterior = resultado.lastOrNull { it.isNotEmpty() }
            var k = j + 1
            while (k < lineas.size && lineas[k].isEmpty()) k++
            val siguiente = lineas.getOrNull(k)
            if (anterior != null && siguiente != null && !Renglones.terminaFrase(anterior) && Renglones.empiezaEnMinuscula(siguiente)) {
                while (resultado.isNotEmpty() && resultado.last().isEmpty()) resultado.removeAt(resultado.lastIndex)
                j = k
            } else {
                j++
            }
        }
        return resultado
    }

    /** Un encabezado que cambia por unidad ("Unidad 3 – Elasticidad") se repite en páginas seguidas. */
    private const val MIN_PAGINAS_SEGUIDAS = 3

    /** Páginas pares o impares seguidas para una línea entera sin contador (encabezado de libro). */
    private const val MIN_PAGINAS_ALTERNADAS_SIN_CONTADOR = 5

    /** Una página de diapositiva tiene menos líneas que esto. */
    const val MAX_LINEAS_DIAPOSITIVA = 15

    /** Una página de diapositiva tiene menos caracteres que esto (una de texto corrido, muchos más). */
    const val MAX_CARACTERES_DIAPOSITIVA = 700

    /** Páginas que tienen que coincidir en la diferencia entre el número impreso y la página. */
    const val MIN_PAGINAS_DESPLAZAMIENTO = 2

    /** Páginas que tienen que mostrar la misma diferencia para que sea la numeración del documento. */
    private const val MIN_PAGINAS_DOMINANTE = 3

    /** Las claves de "línea con el contador de página cambiado por #" empiezan así. */
    private const val MARCA_CONTADOR = "#contador"

    /** Las claves de "línea con los números de sus etiquetas cambiados por #" empiezan así. */
    private const val MARCA_ETIQUETA = "#etiqueta"

    /** Las claves de "línea con todos sus números, arábigos y romanos, cambiados por #" empiezan así. */
    private const val MARCA_NUMERALES = "#numerales"

    private fun esClaveDeNumeros(clave: String) =
        clave.startsWith(MARCA_CONTADOR) || clave.startsWith(MARCA_ETIQUETA) || clave.startsWith(MARCA_NUMERALES)

    /**
     * Claves de encabezados y pies; para cada título de diapositivas (o título de capítulo en la
     * página de apertura), en qué páginas se deja; y, para cada diferencia entre el número impreso
     * y la página que aparece en las claves de contador repetidas, en qué páginas se vio.
     */
    private class Repetidas(
        val claves: Set<String>,
        val conservar: Map<String, Set<Int>>,
        val contadores: Map<Int, Set<Int>>,
    ) {
        companion object {
            val NINGUNA = Repetidas(emptySet(), emptyMap(), emptyMap())
        }
    }

    /** Una página con texto, vista desde sus bordes. */
    private class PaginaDeBorde(val lineas: List<String>, val noVacias: List<Int>, val claves: Map<Int, List<String>>) {
        val caracteres: Int get() = noVacias.sumOf { lineas[it].length }
    }

    /**
     * Una aparición de una clave: en qué página con texto, en qué línea y, para las claves de
     * etiqueta y de numerales, los números que se cambiaron por "#".
     */
    private class Aparicion(val ordinal: Int, val indice: Int, val pagina: PaginaDeBorde, val numeros: List<String>) {
        val esPrimeraLinea: Boolean get() = indice == pagina.noVacias.first()

        /** La línea que sigue a esta en la página, si hay. */
        val deAbajo: String? get() = pagina.noVacias.firstOrNull { it > indice }?.let { pagina.lineas[it] }
    }

    private fun buscarRepetidas(paginas: List<List<String>>, numeros: List<Int>, conTexto: Int): Repetidas {
        val apariciones = HashMap<String, MutableList<Aparicion>>()
        val bordes = mutableListOf<PaginaDeBorde>()
        for ((i, lineas) in paginas.withIndex()) {
            val noVacias = lineas.indices.filter { lineas[it].isNotEmpty() }
            if (noVacias.isEmpty()) continue
            val claves = indicesDeBorde(lineas).sorted()
                .filterNot { GUIA_DE_PUNTOS.containsMatchIn(lineas[it]) }
                .associateWith { clavesDeBorde(lineas[it], numeros[i]) + clavesDeNumerales(lineas[it], ladosDe(it, noVacias)) }
            val pagina = PaginaDeBorde(lineas, noVacias, claves)
            val vistas = HashSet<String>()
            for ((j, deLaLinea) in claves) {
                val clave0 = claveRepeticion(lineas[j])
                val etiquetas = numerosDeEtiqueta(clave0).map { it.toString() }
                val numerales = numerales(clave0)?.second.orEmpty()
                for (clave in deLaLinea) {
                    if (!vistas.add(clave)) continue
                    val tupla = if (clave.startsWith(MARCA_NUMERALES)) numerales else etiquetas
                    apariciones.getOrPut(clave) { mutableListOf() }.add(Aparicion(bordes.size, j, pagina, tupla))
                }
            }
            bordes.add(pagina)
        }
        // ceil(conTexto * 0,4) con enteros, para no depender del redondeo.
        val minimo = maxOf(2, (conTexto * 4 + 9) / 10)
        val conservar = HashMap<String, Set<Int>>()
        val claves = apariciones.filter { (clave, a) -> esRepetida(clave, a, minimo, conservar) }.keys

        // Encabezados fijos: las claves de contador o de etiqueta, las que están en todas o casi
        // todas las páginas y las que van arriba de un título. No son títulos de diapositiva y, para
        // ver cuál es la primera línea de una diapositiva, se saltean.
        val fijas = claves.filter { clave ->
            val a = apariciones.getValue(clave)
            esClaveDeNumeros(clave) ||
                a.size >= conTexto || (conTexto >= 5 && a.size >= conTexto - 2) ||
                a.count { ap -> ap.deAbajo?.let(::esTituloDeDiapositiva) == true } * 2 > a.size
        }.toSet()
        fun primeraLineaPropia(p: PaginaDeBorde): Int? =
            p.noVacias.firstOrNull { j -> p.claves[j].orEmpty().none { it in fijas } }
        for (clave in claves) {
            if (clave in fijas || clave in conservar) continue
            val a = apariciones.getValue(clave)
            if (a.all { it.indice == primeraLineaPropia(it.pagina) } && sonDiapositivas(a)) {
                // La primera aparición de cada tanda de páginas seguidas.
                conservar[clave] = a.filterIndexed { k, ap -> k == 0 || a[k - 1].ordinal != ap.ordinal - 1 }.map { it.ordinal }.toSet()
            }
        }
        val contadores = HashMap<Int, MutableSet<Int>>()
        for (clave in claves) {
            if (!clave.startsWith(MARCA_CONTADOR)) continue
            val desplazamiento = clave.split(' ')[1].toInt()
            contadores.getOrPut(desplazamiento) { HashSet() }.addAll(apariciones.getValue(clave).map { it.ordinal })
        }
        return Repetidas(claves, conservar, contadores)
    }

    /**
     * true si la clave es de un encabezado o un pie. Para la regla de 5 páginas alternadas, el
     * título del capítulo en la página de apertura (una aparición que no es la primera línea, antes
     * o después de la racha) no la impide: se anota en [conservar] para dejarlo.
     */
    private fun esRepetida(clave: String, a: List<Aparicion>, minimo: Int, conservar: MutableMap<String, Set<Int>>): Boolean {
        val ordinales = a.map { it.ordinal }
        if (clave.startsWith(MARCA_ETIQUETA) || clave.startsWith(MARCA_NUMERALES)) {
            // Los números se repiten en una página vecina en al menos la mitad de las veces (una
            // unidad o una sección dura más de una página; un ejercicio no).
            val porOrdinal = a.associate { it.ordinal to it.numeros }
            val conVecina = a.count { ap ->
                porOrdinal[ap.ordinal - 1] == ap.numeros || porOrdinal[ap.ordinal + 1] == ap.numeros
            }
            return a.size >= minimo && conVecina * 2 >= a.size && !sonDiapositivas(a)
        }
        if (a.size >= minimo || rachaMasLarga(ordinales, 1) >= MIN_PAGINAS_SEGUIDAS) return true
        if (clave.startsWith(MARCA_CONTADOR)) return rachaMasLarga(ordinales, 2) >= MIN_PAGINAS_SEGUIDAS
        if (sonDiapositivas(a)) return false
        val primeras = a.filter { it.esPrimeraLinea }.map { it.ordinal }
        val otras = a.filterNot { it.esPrimeraLinea }
        if (rachaMasLarga(primeras, 2) < MIN_PAGINAS_ALTERNADAS_SIN_CONTADOR || otras.size > 1) return false
        val fuera = otras.all { it.ordinal < primeras.min() || it.ordinal > primeras.max() }
        if (fuera && otras.isNotEmpty()) conservar[clave] = setOf(otras.single().ordinal)
        return fuera
    }

    /**
     * Páginas de diapositivas: pocas líneas (mediana menor que [MAX_LINEAS_DIAPOSITIVA]) y poco
     * texto (mediana menor que [MAX_CARACTERES_DIAPOSITIVA] caracteres), aunque las viñetas sean
     * largas.
     */
    private fun sonDiapositivas(a: List<Aparicion>): Boolean =
        a.map { it.pagina.noVacias.size }.sorted()[a.size / 2] < MAX_LINEAS_DIAPOSITIVA &&
            a.map { it.pagina.caracteres }.sorted()[a.size / 2] < MAX_CARACTERES_DIAPOSITIVA

    /** La línea de abajo de un encabezado de diapositiva es un título de verdad (no una frase cortada). */
    private fun esTituloDeDiapositiva(linea: String): Boolean {
        val l = normalizarVineta(linea)
        return l.isNotEmpty() && !Renglones.esItemMarcado(l) && DetectorTitulos.pareceTitulo(l)
    }

    /**
     * La diferencia entre el número impreso y la página que más páginas muestran, contando las
     * claves de contador repetidas y los números sueltos; null si ninguna llega a
     * [MIN_PAGINAS_DOMINANTE] páginas.
     */
    private fun desplazamientoDominante(repetidas: Repetidas, numeracion: Numeracion): Int? {
        val paginas = HashMap<Int, MutableSet<Int>>()
        for ((d, ordinales) in repetidas.contadores) paginas.getOrPut(d) { HashSet() }.addAll(ordinales)
        for ((d, ordinales) in numeracion.paginasPorDesplazamiento()) paginas.getOrPut(d) { HashSet() }.addAll(ordinales)
        val mejor = paginas.entries.sortedWith(compareByDescending<Map.Entry<Int, Set<Int>>> { it.value.size }.thenBy { kotlin.math.abs(it.key) }.thenBy { it.key }).firstOrNull()
        return if (mejor != null && mejor.value.size >= MIN_PAGINAS_DOMINANTE) mejor.key else null
    }

    /** La racha más larga de páginas con texto separadas por [paso] (1: seguidas; 2: pares o impares). */
    private fun rachaMasLarga(ordinales: List<Int>, paso: Int): Int {
        val conjunto = ordinales.toHashSet()
        var mejor = 0
        for (o in ordinales) {
            if (o - paso in conjunto) continue
            var largo = 1
            while (o + largo * paso in conjunto) largo++
            mejor = maxOf(mejor, largo)
        }
        return mejor
    }

    private fun indicesDeBorde(lineas: List<String>): Set<Int> {
        val noVacias = lineas.indices.filter { lineas[it].isNotEmpty() }
        return (noVacias.take(LINEAS_DE_BORDE) + noVacias.takeLast(LINEAS_DE_BORDE)).toSet()
    }

    private fun quitarBordes(
        lineas: List<String>,
        numero: Int,
        ordinal: Int,
        repetidas: Repetidas,
        numeracion: Numeracion,
        dominante: Int?,
        esLibro: Boolean,
    ): List<String> {
        val noVacias = lineas.filter { it.isNotEmpty() }
        if (noVacias.isEmpty()) return lineas
        val noVaciasIdx = lineas.indices.filter { lineas[it].isNotEmpty() }
        fun repetida(i: Int): Boolean {
            val claves = (clavesDeBorde(lineas[i], numero) + clavesDeNumerales(lineas[i], ladosDe(i, noVaciasIdx))).filter { it in repetidas.claves }
            if (claves.isEmpty()) return false
            if (claves.any(::esClaveDeNumeros)) return true
            return repetidas.conservar[claves.first()]?.contains(ordinal) != true
        }
        // En un libro ([esLibro]), el encabezado con el contador de
        // la numeración del documento se quita aunque aparezca una sola vez ("4.2. LA DEMANDA 85" en
        // LaTeX, también en la última página de un capítulo, que es corta), si es más corto que el
        // texto y el contador no está al principio (ahí suele ir la numeración de un título).
        val largoMediano = noVacias.map { it.length }.sorted()[noVacias.size / 2]
        fun conContadorDelDocumento(i: Int): Boolean {
            if (!esLibro || dominante == null || lineas[i].length * 4 >= largoMediano * 3) return false
            val prefijo = "$MARCA_CONTADOR $dominante "
            return clavesDeBorde(lineas[i], numero).any { it.startsWith(prefijo) && !it.removePrefix(prefijo).startsWith("#") }
        }
        fun sobra(i: Int) = numeracion.esNumeroDePagina(lineas[i], numero, ordinal, dominante) || repetida(i) || conContadorDelDocumento(i)
        val quitar = HashSet<Int>()
        for (i in indicesDeBorde(lineas)) if (repetida(i)) quitar.add(i)
        // Desde arriba y desde abajo, como mucho 3 líneas más por borde.
        var quitadas = 0
        for (i in lineas.indices) {
            if (lineas[i].isEmpty() || i in quitar) continue
            if (quitadas < 3 && sobra(i)) { quitar.add(i); quitadas++ } else break
        }
        quitadas = 0
        for (i in lineas.indices.reversed()) {
            if (lineas[i].isEmpty() || i in quitar) continue
            if (quitadas < 3 && sobra(i)) { quitar.add(i); quitadas++ } else break
        }
        return if (quitar.isEmpty()) lineas else lineas.filterIndexed { i, _ -> i !in quitar }
    }

    /**
     * Forma de comparar líneas repetidas: en minúscula, sin acentos, con todos los guiones iguales
     * y los espacios colapsados. Así "Economía I – Unidad 3" y "ECONOMIA I - Unidad 3" (como sale
     * del OCR) dan la misma clave. Los números y los demás signos se conservan: "Ejercicio 1" y
     * "Ejercicio 2" son líneas distintas, y un renglón de índice ("1. Introducción ...... 3") no se
     * confunde con el título de la página.
     */
    fun claveRepeticion(linea: String): String {
        val sinAcentos = Normalizer.normalize(linea.lowercase(), Normalizer.Form.NFD).replace(MARCAS, "")
        return sinAcentos.replace(GUIONES, "-").replace(ESPACIOS, " ").trim()
    }

    /**
     * Las claves con que se compara una línea del borde: la [claveRepeticion]; una de contador
     * por cada número de hasta 4 cifras que no sigue a una etiqueta (la línea con ese número
     * cambiado por "#", junto con la diferencia entre el número y la [pagina]); y una de etiqueta
     * si la línea tiene una etiqueta numerada que no está al principio.
     */
    internal fun clavesDeBorde(linea: String, pagina: Int): List<String> {
        val completa = claveRepeticion(linea)
        val claves = mutableListOf<String>()
        if (letras(completa) >= 3) claves.add(completa)
        for (rango in numerosDe(completa)) {
            if (siguenAEtiqueta(completa, rango.first) || esParteDeNumeracion(completa, rango)) continue
            // En el medio de la línea, solo después de "página", "p.", "hoja"... o antes de "de 8", "/ 8".
            val enBorde = rango.first == 0 || rango.last == completa.lastIndex
            val conPalabraDePagina = palabraAnterior(completa, rango.first) in PALABRAS_DE_PAGINA
            val conTotal = TOTAL_DE_PAGINAS.containsMatchIn(completa.substring(rango.last + 1))
            if (!enBorde && !conPalabraDePagina && !conTotal) continue
            val base = completa.replaceRange(rango, "#")
            if (letras(base) >= 3) claves.add("$MARCA_CONTADOR ${completa.substring(rango).toInt() - pagina} $base")
        }
        if (numerosDeEtiqueta(completa).isNotEmpty() && !empiezaConEtiqueta(completa)) {
            claves.add("$MARCA_ETIQUETA ${completa.replace(DIGITOS, "#")}")
        }
        return claves
    }

    private fun letras(texto: String) = texto.count { it.isLetter() }

    private fun esLimiteDeNumero(c: Char) = c.isWhitespace() || c in "|•·-/.,:()[]"

    /** Los números de hasta 4 cifras de la clave que están separados del resto por espacios o signos. */
    private fun numerosDe(clave: String): List<IntRange> = DIGITOS.findAll(clave).map { it.range }.filter { r ->
        r.last - r.first < 4 &&
            (r.first == 0 || esLimiteDeNumero(clave[r.first - 1])) &&
            (r.last == clave.lastIndex || esLimiteDeNumero(clave[r.last + 1]))
    }.toList()

    /** Los números que siguen a una etiqueta ("unidad 3", "ejercicio n° 4"), en orden. */
    private fun numerosDeEtiqueta(clave: String): List<Int> =
        numerosDe(clave).filter { siguenAEtiqueta(clave, it.first) }.map { clave.substring(it).toInt() }

    private fun empiezaConEtiqueta(clave: String): Boolean = clave.substringBefore(' ').trimEnd(':', '.') in ETIQUETAS

    private val NUMERAL = Regex("(?<![\\p{L}\\d])(?:\\d{1,4}|[ivxlc]{1,6})(?![\\p{L}\\d])")

    /** La clave con todos sus números (arábigos y romanos) cambiados por "#", y esos números; null si no tiene. */
    private fun numerales(clave: String): Pair<String, List<String>>? {
        val valores = mutableListOf<String>()
        val base = NUMERAL.replace(clave) { m ->
            if (m.value[0].isDigit() || ROMANO.matches(m.value)) {
                valores.add(m.value)
                "#"
            } else {
                m.value
            }
        }
        if (valores.isEmpty() || letras(base) < 3) return null
        return base to valores
    }

    /** En qué borde está la línea [indice]: "arriba" (2 primeras no vacías), "abajo" (2 últimas) o los dos. */
    private fun ladosDe(indice: Int, noVacias: List<Int>): List<String> = listOfNotNull(
        "arriba".takeIf { indice in noVacias.take(LINEAS_DE_BORDE) },
        "abajo".takeIf { indice in noVacias.takeLast(LINEAS_DE_BORDE) },
    )

    /**
     * Claves de numerales, una por borde: la línea con sus números cambiados por "#" ("PARTE I · 1"
     * → "parte # · #"). Sirven para la pestaña de sección que cambia cada pocas páginas. Van con el
     * borde porque la pestaña está siempre en el mismo lugar y el título "Parte I" de un libro, no.
     */
    private fun clavesDeNumerales(linea: String, lados: List<String>): List<String> {
        val base = numerales(claveRepeticion(linea))?.first ?: return emptyList()
        return lados.map { "$MARCA_NUMERALES $it $base" }
    }

    /** Palabras (sin acentos) que numeran contenido: el número que las sigue no es un contador de página. */
    private val ETIQUETAS = setOf(
        "actividad", "anexo", "apendice", "bloque", "capitulo", "caso", "clase", "consigna", "cuadro",
        "ejemplo", "ejercicio", "ejercicios", "figura", "grafico", "item", "leccion", "modulo", "parcial",
        "parte", "paso", "practico", "pregunta", "problema", "punto", "seccion", "semana", "tabla",
        "tarea", "tema", "unidad", "fase", "etapa", "nivel", "diapositiva", "lamina", "teorema",
        "definicion", "propiedad", "lema", "corolario", "ley", "hipotesis", "supuesto", "regla", "nota",
        "apartado", "inciso", "articulo", "art",
    )
    private val MARCAS_DE_NUMERO = setOf("n°", "nº", "n.°", "n.º", "n", "no", "no.", "nro", "nro.", "num", "num.", "#")

    /** true si el número que empieza en [inicio] sigue a una etiqueta ("unidad 3", "ejercicio n° 4"). */
    private fun siguenAEtiqueta(clave: String, inicio: Int): Boolean {
        val palabras = clave.substring(0, inicio).split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (palabras.lastOrNull() in MARCAS_DE_NUMERO) palabras.removeAt(palabras.lastIndex)
        val ultima = palabras.lastOrNull()?.trimEnd(':', '.') ?: return false
        return ultima in ETIQUETAS
    }

    /**
     * true si el número es parte de una numeración o de una cifra con puntos ("1.4", "3.1.2",
     * "1.500") o es la marca de una enumeración al principio ("1. ", "2) "): no es un contador.
     */
    private fun esParteDeNumeracion(clave: String, rango: IntRange): Boolean {
        val antes = rango.first
        val despues = rango.last + 1
        if (despues + 1 < clave.length && clave[despues] == '.' && clave[despues + 1].isDigit()) return true
        if (antes >= 2 && clave[antes - 1] == '.' && clave[antes - 2].isDigit()) return true
        return antes == 0 && despues < clave.length && clave[despues] in ".)"
    }

    /** La palabra justo antes de [inicio] ("p." en "economia i, p.5"; "pagina" en "pagina 5 de 8"). */
    private fun palabraAnterior(clave: String, inicio: Int): String =
        clave.substring(0, inicio).trimEnd().substringAfterLast(' ').substringAfterLast(',')

    /** Palabras (sin acentos) que anuncian el número de página. */
    private val PALABRAS_DE_PAGINA = setOf("pagina", "paginas", "pag", "pag.", "pags.", "p.", "pp.", "hoja", "page", "folio", "fol.", "pg", "pg.")

    /** Lo que sigue a un número de página que lleva el total: "de 8", "/ 8", "of 8". */
    private val TOTAL_DE_PAGINAS = Regex("^\\s*(?:de|/|of)\\s*\\d{1,4}(?!\\d)")

    private val MARCAS = Regex("\\p{Mn}+")
    private val DIGITOS = Regex("\\d+")
    private val GUIONES = Regex("[\u2010\u2011‒–—―−]")
    private val ESPACIOS = Regex("\\s+")

    private enum class TipoNumero { EXPLICITO, ARABIGO, ROMANO }

    private data class NumeroDePagina(val tipo: TipoNumero, val valor: Int)

    // Página 12 · Pág. 12 de 40 · pág 3 · p. 12 · Page 4 of 9
    private val NUMERO_EXPLICITO = Regex(
        "^(?:p[áa]g(?:ina)?s?\\.?|p\\.|page)\\s*(\\d{1,4})(?:\\s*(?:de|/|of)\\s*\\d{1,4})?$",
        RegexOption.IGNORE_CASE,
    )
    private val NUMEROS_SUELTOS = listOf(
        // 12 · - 12 - · – 12 – · | 12
        Regex("^[-–—|\\s]*(\\d{1,4})[-–—|\\s]*$"),
        // 12/40 · 12 de 40
        Regex("^(\\d{1,4})\\s*(?:/|de)\\s*\\d{1,4}$", RegexOption.IGNORE_CASE),
        // [12] · (12)
        Regex("^[\\[(]\\s*(\\d{1,4})\\s*[\\])]$"),
    )
    // Romanos en minúscula (páginas de introducción): iv, xii
    private val ROMANO = Regex("^(?=[ivxlc])c{0,3}(?:xc|xl|l?x{0,3})(?:ix|iv|v?i{0,3})$")
    private val VALORES_ROMANOS = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100)

    private fun leerNumeroPagina(linea: String): NumeroDePagina? {
        val l = linea.trim()
        if (l.isEmpty()) return null
        NUMERO_EXPLICITO.matchEntire(l)?.let { return NumeroDePagina(TipoNumero.EXPLICITO, it.groupValues[1].toInt()) }
        for (r in NUMEROS_SUELTOS) r.matchEntire(l)?.let { return NumeroDePagina(TipoNumero.ARABIGO, it.groupValues[1].toInt()) }
        if (!ROMANO.matches(l)) return null
        var total = 0
        for (k in l.indices) {
            val v = VALORES_ROMANOS.getValue(l[k])
            val siguiente = if (k + 1 < l.length) VALORES_ROMANOS.getValue(l[k + 1]) else 0
            total += if (v < siguiente) -v else v
        }
        return NumeroDePagina(TipoNumero.ROMANO, total)
    }

    /** true si la línea tiene la forma de un número de página ("12", "- 12 -", "Pág. 12 de 40", "12/40", "iv"...). */
    fun esNumeroPagina(linea: String): Boolean = leerNumeroPagina(linea) != null

    /**
     * Cómo va la numeración impresa: para los números sueltos (arábigos y romanos por separado), la
     * diferencia entre el número impreso y el número de página más común en los bordes, y las
     * diferencias que se ven en cada página con texto (para reconocer tramos con otra numeración).
     */
    private class Numeracion(
        private val masComun: Map<TipoNumero, Int>,
        private val porPagina: List<Set<Pair<TipoNumero, Int>>>,
    ) {
        /**
         * true si la línea es el número de esta página: explícito ("Página 12"), con la diferencia
         * más común de los números sueltos, con la [dominante] del documento o con la de una página
         * vecina.
         */
        fun esNumeroDePagina(linea: String, numero: Int, ordinal: Int, dominante: Int?): Boolean {
            val n = leerNumeroPagina(linea) ?: return false
            if (n.tipo == TipoNumero.EXPLICITO) return true
            val desplazamiento = n.valor - numero
            if (desplazamiento == masComun[n.tipo]) return true
            if (n.tipo == TipoNumero.ARABIGO && desplazamiento == dominante) return true
            val propio = n.tipo to desplazamiento
            return porPagina.getOrNull(ordinal - 1)?.contains(propio) == true || porPagina.getOrNull(ordinal + 1)?.contains(propio) == true
        }

        /** Para cada diferencia de los números sueltos arábigos, en qué páginas con texto se vio. */
        fun paginasPorDesplazamiento(): Map<Int, Set<Int>> {
            val resultado = HashMap<Int, MutableSet<Int>>()
            porPagina.forEachIndexed { ordinal, vistos ->
                for ((tipo, d) in vistos) if (tipo == TipoNumero.ARABIGO) resultado.getOrPut(d) { HashSet() }.add(ordinal)
            }
            return resultado
        }

        companion object {
            fun de(paginas: List<List<String>>, numeros: List<Int>): Numeracion {
                val porPagina = mutableListOf<Set<Pair<TipoNumero, Int>>>()
                val conteo = HashMap<Pair<TipoNumero, Int>, Int>()
                for ((i, lineas) in paginas.withIndex()) {
                    val noVacias = lineas.filter { it.isNotEmpty() }
                    if (noVacias.isEmpty()) continue
                    val vistos = HashSet<Pair<TipoNumero, Int>>()
                    for (l in noVacias.take(3) + noVacias.takeLast(3)) {
                        val n = leerNumeroPagina(l) ?: continue
                        if (n.tipo != TipoNumero.EXPLICITO) vistos.add(n.tipo to n.valor - numeros[i])
                    }
                    porPagina.add(vistos)
                    for (v in vistos) conteo[v] = (conteo[v] ?: 0) + 1
                }
                val masComun = listOf(TipoNumero.ARABIGO, TipoNumero.ROMANO).associateWith { tipo ->
                    val mejor = conteo.entries
                        .filter { it.key.first == tipo }
                        .sortedWith(compareByDescending<Map.Entry<Pair<TipoNumero, Int>, Int>> { it.value }.thenBy { kotlin.math.abs(it.key.second) }.thenBy { it.key.second })
                        .firstOrNull()
                    if (mejor != null && mejor.value >= MIN_PAGINAS_DESPLAZAMIENTO) mejor.key.second else 0
                }
                return Numeracion(masComun, porPagina)
            }
        }
    }

    private val VINETA = Regex("^(?:[•●▪◦‣·○■□◆◇►▶➢➤✓✔⁃∙⦁◘❖]\\s*|[-–—*]\\s+)")

    /** Renglón de índice: "Introducción ........ 12" (el texto, una guía de puntos y la página). */
    private val RENGLON_INDICE =
        Regex("^(.*?[\\p{L}\\p{N})])\\s*(?:[.·…_]\\s?){4,}\\s*(?:\\d{1,4}|[ivxlcIVXLC]{1,7})$")
    private val GUIA_DE_PUNTOS = Regex("(?:[.·…_]\\s?){4,}")

    /**
     * Limpieza final de una línea, después de quitar encabezados y pies:
     * - Las viñetas (•, ●, ▪, ◦, , ‣, ·, y "-", "–", "*" seguidos de espacio) pasan a "• ".
     * - Un renglón de índice ("1.2 Elasticidad ....... 14") se convierte en un ítem de lista con
     *   el texto solo ("• 1.2 Elasticidad"), para que nunca se tome como título.
     * - Las líneas sin letras ni números, o sin letras y con una guía de puntos, se descartan.
     */
    fun limpiarLinea(linea: String): String {
        if (linea.isEmpty()) return linea
        val indice = RENGLON_INDICE.matchEntire(linea)
        val limpia = if (indice != null && indice.groupValues[1].any { it.isLetter() }) {
            val texto = normalizarVineta(indice.groupValues[1].trim())
            if (texto.startsWith("• ")) texto else "• $texto"
        } else {
            normalizarVineta(linea)
        }
        if (limpia.none { it.isLetterOrDigit() }) return ""
        if (limpia.none { it.isLetter() } && GUIA_DE_PUNTOS.containsMatchIn(limpia)) return ""
        return limpia
    }

    /** Cambia la viñeta inicial, si hay, por "• ". Una viñeta sin texto queda vacía. */
    fun normalizarVineta(linea: String): String {
        val m = VINETA.find(linea) ?: return linea
        val resto = linea.substring(m.range.last + 1).trim()
        return if (resto.isEmpty()) "" else "• $resto"
    }
}
