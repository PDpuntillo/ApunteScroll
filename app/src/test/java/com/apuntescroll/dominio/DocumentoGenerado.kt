package com.apuntescroll.dominio

import kotlin.random.Random

/**
 * Un apunte inventado al azar (siempre igual para la misma semilla), con encabezado y pie en cada
 * página, títulos numerados, listas con ítems de varias líneas, palabras cortadas con guion y
 * párrafos que cruzan de página. Sirve para comprobar que no se pierde texto.
 */
class DocumentoGenerado(semilla: Int) {

    private val azar = Random(semilla)

    private val palabras = listOf(
        "demanda", "oferta", "precio", "mercado", "consumidores", "empresas", "ingreso", "elasticidad",
        "costos", "producción", "equilibrio", "bienes", "servicios", "sustitutos", "complementarios",
        "inflación", "salario", "empleo", "inversión", "consumo", "ahorro", "estado", "impuestos",
        "política", "monetaria", "fiscal", "de", "la", "el", "los", "las", "y", "en", "que", "con", "por",
        "para", "un", "una", "se", "su", "más", "menos", "cuando", "como", "entre", "sobre", "también",
    )

    val encabezado = "Microeconomía – Apuntes de clase"
    val pie = "Cátedra Pérez"

    val paginas: List<PaginaExtraida>

    fun esBorde(linea: String) = linea == encabezado || linea == pie || linea.startsWith("Página ")

    init {
        val lineas = mutableListOf<String>()
        var titulo = 1
        repeat(azar.nextInt(15, 40)) {
            when (azar.nextInt(10)) {
                0 -> lineas.add("$titulo.${azar.nextInt(1, 9)} ${mayuscula(frase(2, 5))}").also { titulo++ }
                1 -> {
                    lineas.add(mayuscula(frase(4, 9)) + ":")
                    repeat(azar.nextInt(2, 6)) { lineas.addAll(envolver("• " + mayuscula(frase(3, 25)) + ".")) }
                }
                else -> lineas.addAll(envolver((1..azar.nextInt(1, 6)).joinToString(" ") { mayuscula(frase(5, 30)) + "." }))
            }
        }
        val porPagina = 28
        paginas = lineas.chunked(porPagina).mapIndexed { i, cuerpo ->
            PaginaExtraida(i + 1, (listOf(encabezado) + cuerpo + listOf(pie, "Página ${i + 1}")).joinToString("\n"))
        }
    }

    private fun frase(min: Int, max: Int) = (1..azar.nextInt(min, max)).joinToString(" ") { palabras[azar.nextInt(palabras.size)] }

    private fun mayuscula(texto: String) = texto.replaceFirstChar { it.uppercaseChar() }

    /** Corta el texto en líneas de hasta 80 caracteres; a veces parte una palabra larga con guion. */
    private fun envolver(texto: String): List<String> {
        val resultado = mutableListOf<String>()
        var linea = StringBuilder()
        for (palabra in texto.split(" ")) {
            val cabe = linea.isEmpty() || linea.length + 1 + palabra.length <= 80
            if (cabe) {
                if (linea.isNotEmpty()) linea.append(' ')
                linea.append(palabra)
                continue
            }
            val partible = palabra.length >= 8 && palabra.first().isLowerCase() && palabra.all { it.isLetter() }
            if (partible && azar.nextBoolean()) {
                val corte = azar.nextInt(3, palabra.length - 3)
                resultado.add("$linea ${palabra.substring(0, corte)}-")
                linea = StringBuilder(palabra.substring(corte))
            } else {
                resultado.add(linea.toString())
                linea = StringBuilder(palabra)
            }
        }
        if (linea.isNotEmpty()) resultado.add(linea.toString())
        return resultado
    }
}
