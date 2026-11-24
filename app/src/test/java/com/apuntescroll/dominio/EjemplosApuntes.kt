package com.apuntescroll.dominio

/**
 * Textos de ejemplo que imitan lo que devuelven PdfBox y ML Kit con apuntes reales de la facultad.
 * Las líneas son como las corta el PDF: con guiones de fin de línea, encabezados y pies.
 */
object EjemplosApuntes {

    const val ENCABEZADO_1 = "Economía I – Unidad 3"
    const val ENCABEZADO_2 = "Facultad de Ciencias Económicas – UBA"

    /** Apunte digital de Economía con encabezados, títulos numerados y en mayúsculas, listas y un párrafo que cruza de página. */
    val ECONOMIA = listOf(
        PaginaExtraida(
            1,
            """
            Economía I – Unidad 3
            Facultad de Ciencias Económicas – UBA
            UNIDAD 3: ELASTICIDAD
            3.1 Elasticidad precio de la demanda
            La elasticidad precio de la demanda mide la sensibilidad de la cantidad deman-
            dada ante cambios en el precio del bien. Se calcula como el cociente entre la va-
            riación porcentual de la cantidad demandada y la variación porcentual del precio.
            Por ejemplo, si el precio sube un 10 % y la cantidad cae un 5 %, la elasticidad es
            de 0.5 en valor absoluto, es decir, la demanda es inelástica.
            Según el Dr. Mankiw (cap. 5, pág. 98), los factores que determinan la elasticidad
            son los siguientes:
            • La existencia de bienes sustitutos cercanos: cuantos más sustitutos tenga un
            bien, más elástica será su demanda.
            • La proporción del ingreso destinada al bien.
            • El horizonte temporal considerado, ya que en el largo plazo los consumidores
            pueden adaptar sus hábitos.
            1
            """.trimIndent(),
        ),
        PaginaExtraida(
            2,
            """
            Economía I – Unidad 3
            Facultad de Ciencias Económicas – UBA
            3.2 Clasificación
            Según su valor, la demanda puede clasificarse en:
            1. Elástica
            2. Inelástica
            3. Unitaria
            Cuando la elasticidad es mayor que 1 en valor absoluto, un aumento del precio
            reduce el gasto total de los consumidores. En cambio, cuando es menor que 1, el
            gasto total aumenta. Este resultado es clave para entender las decisiones de las
            empresas que fijan precios, ya que permite anticipar cómo reaccionarán los in-
            gresos ante una suba o una baja de precios y evaluar si conviene modificar la
            estrategia comercial en el corto plazo, en un contexto donde la información es
            imperfecta y los consumidores no siempre reaccionan de manera inmediata ante
            2
            """.trimIndent(),
        ),
        PaginaExtraida(
            3,
            """
            Economía I – Unidad 3
            Facultad de Ciencias Económicas – UBA
            los cambios de precios.
            ELASTICIDAD INGRESO
            La elasticidad ingreso mide cómo varía la cantidad demandada cuando cambia el
            ingreso de los consumidores. Los bienes normales tienen elasticidad ingreso po-
            sitiva, mientras que los bienes inferiores la tienen negativa. J. M. Keynes ya ad-
            vertía sobre la importancia del ingreso en las decisiones de consumo, etc. La
            evidencia empírica para la Argentina muestra valores cercanos a 1.2 para los ali-
            mentos, aunque con diferencias entre regiones.
            Página 3
            """.trimIndent(),
        ),
        PaginaExtraida(
            4,
            """
            Economía I – Unidad 3
            Facultad de Ciencias Económicas – UBA
            3.3 Elasticidad cruzada
            La elasticidad cruzada relaciona la cantidad demandada de un bien con el precio de
            otro bien. Si es positiva, los bienes son sustitutos (por ej. el café y el té); si es
            negativa, son complementarios, como los autos y la nafta.
            Pág. 4 de 4
            """.trimIndent(),
        ),
    )

    /** Páginas escaneadas pasadas por ML Kit: una línea por renglón, bloques separados por líneas vacías y algo de ruido. */
    val OCR_COSTOS = listOf(
        PaginaExtraida(
            7,
            """
            ECONOMIA I - UNIDAD 4

            4.1 Costos de produccion

            Los costos de produccion son los gastos en que
            incurre una empresa para producir bienes y
            servicios. Se dividen en costos fijos y costos
            variables.

            Los costos fijos no dependen del nivel de
            produccion: por ej. el alquiler del local o los
            seguros. Los costos variables, en cambio,
            aumentan con la cantidad producida.

            |

            Ejemplos de costos variables:
            - Materias primas
            - Mano de obra directa
            - Energia utilizada en la planta

            12
            """.trimIndent(),
        ),
        PaginaExtraida(
            8,
            """
            4.2 Costo marginal

            El costo marginal es el aumento del costo total
            al producir una unidad adicional. Se calcula
            como CMg = ΔCT / ΔQ y suele tener forma de U.

            ~

            Cuando el costo marginal es menor que el costo
            medio, el costo medio baja; cuando es mayor, el
            costo medio sube. Por eso la curva de CMg corta
            a la de costo medio en su minimo.

            13
            """.trimIndent(),
        ),
    )

    const val ENCABEZADO_HISTORIA = "Historia Económica y Social Argentina"

    /** Resumen sin títulos, con un párrafo que sigue en la página siguiente. */
    val HISTORIA_SIN_TITULOS = listOf(
        PaginaExtraida(
            1,
            """
            Historia Económica y Social Argentina
            La crisis de 1930 afectó profundamente a la economía argentina, que dependía de
            la exportación de productos agropecuarios. La caída de los precios internacionales
            redujo los ingresos del Estado y obligó a replantear el modelo de crecimiento.
            En respuesta, el gobierno creó la Junta Nacional de Granos y el Banco Central, que
            comenzó a funcionar en 1935. Estas instituciones buscaban regular los mercados y
            controlar la moneda en un contexto de fuerte incertidumbre, con el objetivo de
            - 1 -
            """.trimIndent(),
        ),
        PaginaExtraida(
            2,
            """
            Historia Económica y Social Argentina
            evitar nuevas crisis financieras. Al mismo tiempo, comenzó un proceso de indus-
            trialización por sustitución de importaciones.
            La industria liviana creció durante la década, sobre todo en el sector textil y en
            el alimenticio. Muchos trabajadores migraron del interior hacia el Gran Buenos
            Aires, lo que transformó la estructura social del país.
            - 2 -
            """.trimIndent(),
        ),
        PaginaExtraida(
            3,
            """
            Historia Económica y Social Argentina
            Hacia 1945, la participación de la industria en el producto ya superaba a la del
            agro. Este cambio tuvo consecuencias políticas duraderas.
            - 3 -
            """.trimIndent(),
        ),
    )

    /** Portada, índice con guías de puntos y el comienzo del apunte, con títulos numerados de dos niveles. */
    val PORTADA_E_INDICE = listOf(
        PaginaExtraida(
            1,
            """
            UNIVERSIDAD DE BUENOS AIRES
            FACULTAD DE CIENCIAS ECONÓMICAS
            MICROECONOMÍA
            Apunte de cátedra – 2024
            """.trimIndent(),
        ),
        PaginaExtraida(
            2,
            """
            ÍNDICE
            1. Introducción .................................. 3
            2. El mercado ..................................... 3
            2.1 La demanda ................................... 3
            .................................................
            """.trimIndent(),
        ),
        PaginaExtraida(
            3,
            """
            1. Introducción
            La microeconomía estudia las decisiones de los consumidores y de las empresas, y
            cómo interactúan en los mercados para fijar precios y cantidades de equilibrio.
            2. El mercado
            Un mercado es un conjunto de compradores y vendedores de un bien o de un servicio.
            2.1 La demanda
            La demanda muestra la cantidad que los consumidores desean comprar a cada precio.
            """.trimIndent(),
        ),
    )

    /**
     * Páginas con cosas difíciles: el número de página en la misma línea del encabezado, una
     * fórmula, una tabla, una nota al pie, bibliografía con iniciales, subtítulos sin numerar y
     * preguntas numeradas con paréntesis.
     */
    val EJERCICIOS = listOf(
        PaginaExtraida(
            1,
            """
            Economía I – Unidad 3                                       12
            2.4 Gasto total y elasticidad
            El gasto total de los consumidores es igual al precio por la cantidad, es decir:
            GT = P × Q
            donde P es el precio y Q la cantidad demandada. Si la demanda es elástica, una baja
            del precio aumenta el gasto total¹.
            Tabla 1. Ejemplo numérico
            Precio Cantidad Gasto
            10 100 1000
            8 140 1120
            6 170 1020
            Como muestra la tabla, el gasto primero sube y después baja. Esto ocurre porque la
            elasticidad cambia a lo largo de la curva de demanda lineal.
            ¹ Ver Mankiw, N. G. (2012), Principios de economía, cap. 5.
            """.trimIndent(),
        ),
        PaginaExtraida(
            2,
            """
            Economía I – Unidad 3                                       13
            BIBLIOGRAFÍA
            Mankiw, N. G. (2012). Principios de economía. México: Cengage.
            Krugman, P. y Wells, R. (2015). Microeconomía. Barcelona: Reverté.
            Varian, H. R. (2010). Microeconomía intermedia. Barcelona: Antoni Bosch.
            Preguntas de repaso
            1) ¿Qué mide la elasticidad precio?
            2) ¿Cuándo una demanda es inelástica?
            3) ¿Qué relación hay entre elasticidad y gasto total?
            """.trimIndent(),
        ),
        PaginaExtraida(
            3,
            """
            Economía I – Unidad 3                                       14
            Actividad práctica
            "La elasticidad no es constante a lo largo de una recta", afirma el texto. ¿Es cierto?
            Justifique su respuesta con un ejemplo y un gráfico.
            Para pensar: ¿qué pasa con el gasto si el precio sube un 20 %?
            """.trimIndent(),
        ),
    )

    /** Apunte de Derecho con oraciones muy largas, abreviaturas y referencias a artículos. */
    val DERECHO = listOf(
        PaginaExtraida(
            1,
            """
            Capítulo 2 - Derechos y garantías
            El art. 14 de la Constitución Nacional reconoce a todos los habitantes de la Nación el
            derecho de trabajar y ejercer toda industria lícita, de navegar y comerciar, de peticio-
            nar a las autoridades, de entrar, permanecer, transitar y salir del territorio argentino,
            de publicar sus ideas por la prensa sin censura previa, de usar y disponer de su propie-
            dad, de asociarse con fines útiles, de profesar libremente su culto y de enseñar y apren-
            der, todo ello conforme a las leyes que reglamenten su ejercicio, lo que según la Corte
            Suprema implica que ningún derecho es absoluto y que el Estado puede limitarlos de ma-
            nera razonable siempre que no los altere en su esencia, criterio que la Corte reiteró en
            numerosos fallos a lo largo del siglo XX y que la doctrina mayoritaria comparte sin re-
            servas; el art. 14 bis, incorporado en 1957, agregó los derechos del trabajador, entre
            ellos la jornada limitada, el descanso y las vacaciones pagadas, la retribución justa,
            el salario mínimo vital y móvil y la protección contra el despido arbitrario.
            Según la Dra. Gelli (págs. 120-135), estas garantías deben leerse junto con los tratados
            internacionales del art. 75 inc. 22, que tienen jerarquía constitucional desde 1994.
            """.trimIndent(),
        ),
    )
}
