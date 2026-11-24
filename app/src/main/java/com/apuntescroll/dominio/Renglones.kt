package com.apuntescroll.dominio

/** Preguntas sobre una línea suelta que usan el armado de párrafos y la clasificación. */
internal object Renglones {

    private const val CIERRES = "\"'»”’)]"
    private const val FINALES = ".:?!…"

    /** Palabras que no cierran una frase: si una línea termina en ellas, la frase sigue abajo. */
    private val PALABRAS_QUE_SIGUEN = setOf(
        "a", "al", "como", "con", "de", "del", "e", "el", "en", "entre", "la", "las", "lo", "los",
        "ni", "o", "para", "por", "que", "se", "sin", "sobre", "su", "sus", "u", "un", "una",
        "unas", "unos", "y",
    )

    private val ENUMERACIONES = listOf(
        // a) · (a) · A)
        Regex("^\\(?[a-zA-Z]\\)\\s+\\S"),
        // 1) · (1)
        Regex("^\\(?\\d{1,2}\\)\\s+\\S"),
        // iv) · (ii) · IV)
        Regex("^\\(?(?:[ivx]{1,4}|[IVX]{1,4})\\)\\s+\\S"),
    )

    /** "1. x", "1.- x", "1- x": numeración de un solo nivel con punto o guion. */
    private val NUMERADO_SIMPLE = Regex("^\\d{1,2}(?:\\.-?|-)\\s+\\S")

    fun esVineta(linea: String): Boolean = linea.startsWith("• ")

    /** Ítem con su marca explícita: viñeta "• " o enumeración con paréntesis ("a)", "1)", "iv)"). */
    fun esItemMarcado(linea: String): Boolean = esVineta(linea) || ENUMERACIONES.any { it.containsMatchIn(linea) }

    /** Ítem numerado de un nivel: "1. x", "2.- x", "3- x". */
    fun esNumeradoSimple(linea: String): Boolean = NUMERADO_SIMPLE.containsMatchIn(linea)

    /**
     * true si la línea arranca con una marca de enumeración o de título numerado: "a) ", "1) ",
     * "1. ", "2.3 Título", "IV - Título", "A. Título".
     */
    fun esEnumeracion(linea: String): Boolean =
        ENUMERACIONES.any { it.containsMatchIn(linea) } || esNumeradoSimple(linea) ||
            DetectorTitulos.numeracion(linea) != null

    /**
     * true si la línea termina como termina una frase: en `.` `:` `?` `!` `…` (con comillas o
     * paréntesis de cierre opcionales después) o directamente en `»` `"` `)`.
     */
    fun terminaFrase(linea: String): Boolean {
        if (linea.isEmpty()) return false
        if (linea.last() in CIERRES) return true
        return linea.last() in FINALES
    }

    /** true si la línea termina de forma que la frase sigue sí o sí: coma, guion o "de", "la", "y"... */
    fun terminaAbierta(linea: String): Boolean {
        if (linea.isEmpty()) return false
        if (linea.last() in ",-(") return true
        val ultima = linea.substringAfterLast(' ').lowercase()
        return ultima in PALABRAS_QUE_SIGUEN
    }

    /** true si la línea empieza como empieza una oración o un bloque: mayúscula, ¿, ¡, comillas o número. */
    fun empiezaComoOracion(linea: String): Boolean {
        val c = linea.firstOrNull() ?: return false
        return c.isUpperCase() || c.isDigit() || c in "¿¡«\"'(["
    }

    fun empiezaEnMinuscula(linea: String): Boolean = linea.firstOrNull()?.isLowerCase() == true

    /** true si la línea empieza con un número volado ("¹", "²"...), como una nota al pie. */
    fun empiezaConVolado(linea: String): Boolean = linea.firstOrNull()?.category == CharCategory.OTHER_NUMBER
}
