package com.apuntescroll.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class DivisorOracionesTest {

    private fun dividir(texto: String) = DivisorOraciones.dividir(texto)

    @Test
    fun `divide en punto seguido de mayuscula`() {
        assertEquals(
            listOf("La oferta sube.", "La demanda baja.", "El precio cae."),
            dividir("La oferta sube. La demanda baja. El precio cae."),
        )
    }

    @Test
    fun `divide en signos de pregunta y exclamacion y respeta los de apertura`() {
        assertEquals(
            listOf("¿Qué es la elasticidad?", "Es una medida de sensibilidad.", "¡Importante!", "Entra en el parcial."),
            dividir("¿Qué es la elasticidad? Es una medida de sensibilidad. ¡Importante! Entra en el parcial."),
        )
    }

    @Test
    fun `no divide si sigue minuscula`() {
        assertEquals(listOf("¿Por qué? porque sí, y además… la demanda cae."), dividir("¿Por qué? porque sí, y además… la demanda cae."))
    }

    @Test
    fun `incluye comillas y parentesis de cierre en la oracion`() {
        assertEquals(
            listOf("Smith habló de la \"mano invisible.\"", "Luego vino Ricardo (y su teoría.)", "«¿Es justo?»", "Depende."),
            dividir("Smith habló de la \"mano invisible.\" Luego vino Ricardo (y su teoría.) «¿Es justo?» Depende."),
        )
    }

    @Test
    fun `divide antes de un numero o de comillas de apertura`() {
        assertEquals(listOf("La crisis llegó en 2001.", "2002 fue peor.", "\"Nunca más\", dijeron."), dividir("La crisis llegó en 2001. 2002 fue peor. \"Nunca más\", dijeron."))
    }

    @Test
    fun `no divide despues de abreviaturas de tratamiento ni de referencias`() {
        val texto = "Según el Dr. Mankiw y la Lic. Gómez (cap. 5, pág. 98, fig. 3) el art. 14 bis y el inc. 22 lo ratifican. Ver también Prof. Ruiz, Sra. Paz e Ing. Díaz."
        assertEquals(
            listOf(
                "Según el Dr. Mankiw y la Lic. Gómez (cap. 5, pág. 98, fig. 3) el art. 14 bis y el inc. 22 lo ratifican.",
                "Ver también Prof. Ruiz, Sra. Paz e Ing. Díaz.",
            ),
            dividir(texto),
        )
    }

    @Test
    fun `no divide despues de ej p pp vol ni Ud`() {
        val texto = "Por ej. Argentina exporta soja. Ver p. 12 y pp. 30-34 del vol. 2 que Ud. ya leyó."
        assertEquals(listOf("Por ej. Argentina exporta soja.", "Ver p. 12 y pp. 30-34 del vol. 2 que Ud. ya leyó."), dividir(texto))
    }

    @Test
    fun `no divide en iniciales de un nombre`() {
        assertEquals(
            listOf("J. M. Keynes publicó la Teoría general en 1936.", "A. Smith lo precedió."),
            dividir("J. M. Keynes publicó la Teoría general en 1936. A. Smith lo precedió."),
        )
    }

    @Test
    fun `divide despues de una letra mayuscula sola si sigue un inicio tipico de oracion`() {
        assertEquals(listOf("Sube el precio del bien X.", "El consumo cae."), dividir("Sube el precio del bien X. El consumo cae."))
    }

    @Test
    fun `no divide en decimales ni en miles`() {
        assertEquals(listOf("La elasticidad es 0.5 y el ingreso creció 1.000 pesos, es decir, un 3,5 %."), dividir("La elasticidad es 0.5 y el ingreso creció 1.000 pesos, es decir, un 3,5 %."))
    }

    @Test
    fun `EE UU y S A no cortan salvo que siga un inicio tipico de oracion`() {
        assertEquals(listOf("Exporta a EE. UU. Brasil y Chile."), dividir("Exporta a EE. UU. Brasil y Chile."))
        assertEquals(listOf("La firma es Arcor S.A. Fabrica golosinas."), dividir("La firma es Arcor S.A. Fabrica golosinas."))
        assertEquals(listOf("Exporta a EE. UU.", "La balanza mejora."), dividir("Exporta a EE. UU. La balanza mejora."))
    }

    @Test
    fun `etc corta solo cuando sigue un inicio tipico de oracion`() {
        assertEquals(listOf("Ropa, comida, etc.", "La demanda varía."), dividir("Ropa, comida, etc. La demanda varía."))
        assertEquals(listOf("Ropa, comida, etc. Keynes lo estudió."), dividir("Ropa, comida, etc. Keynes lo estudió."))
    }

    @Test
    fun `los puntos suspensivos cortan si sigue mayuscula pero no dentro de una cita`() {
        assertEquals(listOf("Y entonces...", "Todo cambió."), dividir("Y entonces... Todo cambió."))
        assertEquals(listOf("Dijo que (...) La cita sigue."), dividir("Dijo que (...) La cita sigue."))
    }

    @Test
    fun `no divide en direcciones web ni correos`() {
        assertEquals(listOf("Ver www.uba.ar o escribir a apuntes@fce.uba.ar para más datos."), dividir("Ver www.uba.ar o escribir a apuntes@fce.uba.ar para más datos."))
    }

    @Test
    fun `un texto sin puntuacion final es una sola oracion`() {
        assertEquals(listOf("Sin punto final"), dividir("  Sin punto final  "))
        assertEquals(emptyList<String>(), dividir("   "))
    }

    @Test
    fun `los rangos apuntan al texto original`() {
        val texto = "Primera oración.  Segunda oración."
        val rangos = DivisorOraciones.rangos(texto)
        assertEquals(listOf(0..15, 18..33), rangos)
    }

    // ---- Revisión: abreviaturas argentinas y marca de enumeración (hallazgos 9 y 3) ----

    @Test
    fun `no divide en abreviaturas de citas legales lugares e instituciones`() {
        val oraciones = listOf(
            "Se mudó a Bs. As. en 1890.",
            "Lo establece el art. 1710 del Cód. Civ. y Com. de la Nación.",
            "Lo resolvió la Cám. Nac. Civ., Sala A, en 2015.",
            "Lo regula el Dec. 1023/01 sobre contrataciones.",
            "La Res. 125/08 fijó retenciones móviles.",
            "Estudió en la Univ. Nac. de La Plata.",
            "Firmó el Cdor. Pérez el balance.",
            "Consta en el Expte. N° 4512/2019 del juzgado.",
            "Obra a fs. 12 del expediente de la Pcia. de Buenos Aires.",
        )
        for (o in oraciones) assertEquals(listOf(o), dividir(o))
        assertEquals(oraciones, dividir(oraciones.joinToString(" ")))
    }

    @Test
    fun `As Nac y Res cierran la oracion si sigue un inicio tipico`() {
        assertEquals(listOf("Vive en Bs. As.", "La ciudad es grande."), dividir("Vive en Bs. As. La ciudad es grande."))
    }

    @Test
    fun `la marca de enumeracion del principio no corta la oracion`() {
        assertEquals(
            listOf("1. Hay muchos compradores.", "Esto implica una demanda horizontal."),
            dividir("1. Hay muchos compradores. Esto implica una demanda horizontal."),
        )
        assertEquals(listOf("IV. Costos de la empresa."), dividir("IV. Costos de la empresa."))
    }

    // ---- Ronda 2 (hallazgo 11) ----

    @Test
    fun `Pcia Cdor y Univ cierran la oracion si sigue un inicio tipico`() {
        assertEquals(listOf("La sede está en la Pcia.", "Luego se mudó."), dividir("La sede está en la Pcia. Luego se mudó."))
        assertEquals(listOf("Lo firmó el Cdor.", "Luego lo revisó el auditor."), dividir("Lo firmó el Cdor. Luego lo revisó el auditor."))
        assertEquals(listOf("Se graduó en la Univ.", "Después hizo un posgrado."), dividir("Se graduó en la Univ. Después hizo un posgrado."))
    }

    @Test
    fun `Dec Expte Cdor y Univ siguen sin cortar antes de un numero o un nombre`() {
        for (o in listOf("Lo regula el Dec. 1023/01 sobre contrataciones.", "Consta en el Expte. N° 4512/2019 del juzgado.", "Firmó el Cdor. Pérez el balance.", "Estudió en la Univ. Nac. de La Plata.")) {
            assertEquals(listOf(o), dividir(o))
        }
    }
}
