package com.apuntescroll.intercepcion.reglas

/** Qué pasa al abrir una red (PLAN.md, sección 7). */
enum class ModoIntercepcion {
    PASE,
    REEMPLAZO_TOTAL,
    RECORDATORIO;

    companion object {
        fun desdeNombre(s: String?): ModoIntercepcion = entries.firstOrNull { it.name == s } ?: PASE
    }
}

data class ConfigRed(val activada: Boolean = true, val modo: ModoIntercepcion = ModoIntercepcion.PASE) {
    companion object {
        /** La red de prueba arranca apagada: no se intercepta la calculadora sin pedirlo. */
        fun porDefecto(red: RedConocida) = ConfigRed(activada = !red.dePrueba)
    }
}

/** Lo que eligió el usuario, tal como lo lee el motor en cada evento. */
data class ConfigIntercepcion(
    /** Hay un consentimiento de la versión actual del aviso. */
    val consentida: Boolean,
    val redes: Map<String, ConfigRed>,
    val tarjetasPase: Int,
    val minutosPase: Int,
) {
    fun de(red: RedConocida): ConfigRed = redes[red.paquete] ?: ConfigRed.porDefecto(red)

    companion object {
        fun claveActivada(paquete: String) = "red_${paquete}_activada"
        fun claveModo(paquete: String) = "red_${paquete}_modo"
        const val CLAVE_TARJETAS = "pase_tarjetas"
        const val CLAVE_MINUTOS = "pase_minutos"

        /**
         * Arma la configuración con lo guardado. [leer] devuelve el valor de una clave (o null si
         * no está); lo que falta o no es válido toma el valor por defecto.
         */
        fun desde(consentida: Boolean, leer: (String) -> Any?, catalogo: CatalogoRedes, debug: Boolean): ConfigIntercepcion {
            val redes = catalogo.redes.associate { red ->
                val porDefecto = ConfigRed.porDefecto(red)
                red.paquete to ConfigRed(
                    activada = leer(claveActivada(red.paquete)) as? Boolean ?: porDefecto.activada,
                    modo = ModoIntercepcion.desdeNombre(leer(claveModo(red.paquete)) as? String),
                )
            }
            return ConfigIntercepcion(
                consentida = consentida,
                redes = redes,
                tarjetasPase = OpcionesPase.tarjetas(leer(CLAVE_TARJETAS) as? Int ?: OpcionesPase.TARJETAS_DEFECTO),
                minutosPase = OpcionesPase.minutos(leer(CLAVE_MINUTOS) as? Int ?: OpcionesPase.MINUTOS_DEFECTO, debug),
            )
        }
    }
}

/** El usuario aceptó el aviso [version] en [en] (reloj de pared). */
data class Consentimiento(val en: Long, val version: Int) {
    fun vigente(actual: Int = VERSION_AVISO) = version >= actual
}

/**
 * Versión del aviso de intercepción. Se sube si cambia lo que el servicio lee o guarda: el
 * consentimiento anterior deja de valer y el aviso se vuelve a mostrar.
 */
const val VERSION_AVISO = 1

object OpcionesPase {
    val TARJETAS = listOf(5, 10, 15, 20, 30)
    const val TARJETAS_DEFECTO = 15
    val MINUTOS = listOf(5, 10, 15, 20, 30)
    const val MINUTOS_DEFECTO = 10

    fun tarjetas(n: Int): Int = if (n in TARJETAS) n else TARJETAS_DEFECTO

    /** [debug] suma 1 minuto, para probar el vencimiento. */
    fun minutos(m: Int, debug: Boolean): Int = if (m in MINUTOS || (debug && m == 1)) m else MINUTOS_DEFECTO

    /** Las opciones de minutos que se muestran en Ajustes. */
    fun opcionesMinutos(debug: Boolean): List<Int> = if (debug) listOf(1) + MINUTOS else MINUTOS
}

/** Qué es un paquete que pasó a primer plano (ClasificadorPaquetes). */
sealed interface TipoPaquete {
    data class Red(val red: RedConocida) : TipoPaquete
    data object Propio : TipoPaquete
    data object Transitorio : TipoPaquete
    data object Otro : TipoPaquete
}

enum class MotivoIntercepcion {
    ABRIO_RED,
    PASE_VENCIDO;

    companion object {
        fun desdeNombre(s: String?) = entries.firstOrNull { it.name == s } ?: ABRIO_RED
    }
}

/** Lo que pasa en el teléfono y no depende de qué app está al frente. */
data class Entorno(val pantallaEnUso: Boolean, val appFijada: Boolean, val enLlamada: Boolean)

/**
 * "Ir igual" (o una llamada) deja pasar a [red] durante una visita. [salioEn]: desde cuándo hay
 * otra app al frente (null mientras se está en la red).
 */
data class Visita(val red: String, val porLlamada: Boolean, val salioEn: Long? = null)

/** La pantalla de apuntes se vio con [red] desde [desde] hasta [hasta] (null: se sigue viendo). */
data class VistaFeed(val red: String, val desde: Long, val hasta: Long? = null)

enum class Razon {
    SIN_CONSENTIMIENTO,
    RED_APAGADA,
    VISITA_LLAMADA,
    VISITA_PERMITIDA,
    PASE_VIGENTE,
    FEED_A_LA_VISTA,
    PANTALLA_NO_EN_USO,
    APP_FIJADA,
    EN_LLAMADA,
}

sealed interface Decision {
    data class Interceptar(val red: RedConocida) : Decision
    data class Dejar(val razon: Razon) : Decision
}

/** Cómo deja pasar la pantalla interceptada: "Ir igual" (visita) o "Entrar" (pase). */
enum class ComoPasa { VISITA, PASE }

enum class Tarea { REVISAR, REVISAR_DESBLOQUEO, COMPROBAR_APARICION, VENCER_PASE, AVISO_ULTIMO_MINUTO }

/** Lo que el motor le pide al coordinador (CoordinadorIntercepcion.ejecutar). */
sealed interface Accion {
    /** [reintento]: lo pidió el vigía; F5 no lo cuenta como intercepción. */
    data class Lanzar(val red: RedConocida, val motivo: MotivoIntercepcion, val reintento: Boolean = false) : Accion
    data class Programar(val tarea: Tarea, val enMs: Long) : Accion
    data class Cancelar(val tarea: Tarea) : Accion
    data class AvisoNoAparece(val activo: Boolean) : Accion
    data object AvisoPelea : Accion
    data object AvisoBucle : Accion
    data class AvisoUltimoMinuto(val red: RedConocida) : Accion
    data object OlvidarPaseVencido : Accion
}

/** Lo que el motor le pregunta al resto (lo implementa CoordinadorIntercepcion; en pruebas, ContextoFalso). */
interface ContextoMotor {
    val config: ConfigIntercepcion

    /** Paquete del teclado actual (es transitorio), o null si no se sabe. */
    val teclado: String?

    fun paseRestanteMs(): Long
    fun hayPaseVencido(): Boolean

    /** Se lee solo cuando hace falta (ReglasIntercepcion, regla 7). */
    fun entorno(): Entorno
}
