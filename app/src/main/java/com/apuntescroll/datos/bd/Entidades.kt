package com.apuntescroll.datos.bd

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Colores de materia. Se guarda el nombre; el tono exacto lo decide el tema (claro u oscuro). */
enum class ColorMateria { INDIGO, AZUL, TURQUESA, VERDE, AMBAR, NARANJA, ROJO, ROSA, VIOLETA, GRIS }

@Entity(tableName = "materias")
data class Materia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val color: ColorMateria,
    val emoji: String,
    /** Día del parcial (LocalDate.toEpochDay()), si se cargó. Se usa desde F4. */
    val fechaParcial: Long? = null,
    val creada: Long,
)

/** En qué paso del procesamiento está un PDF (PLAN.md, sección 3). */
enum class EstadoDocumento {
    /** Importado, esperando al worker. */
    PENDIENTE,

    /** Sacando el texto página por página (con OCR en las escaneadas). */
    EXTRAYENDO,

    /** Limpiando el texto y cortándolo en tarjetas. */
    ARMANDO,
    LISTO,
    ERROR,
}

/** De dónde salió el texto de una página. */
enum class OrigenTexto { TEXTO, OCR }

@Entity(
    tableName = "documentos",
    foreignKeys = [
        ForeignKey(entity = Materia::class, parentColumns = ["id"], childColumns = ["materiaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("materiaId")],
)
data class Documento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materiaId: Long,
    val titulo: String,
    /** Nombre del archivo dentro de la carpeta privada de PDFs (ver pdf/ArchivosPdf.kt). */
    val archivo: String,
    val paginas: Int = 0,
    val estado: EstadoDocumento = EstadoDocumento.PENDIENTE,
    val paginasProcesadas: Int = 0,
    val paginasOcr: Int = 0,
    /** Mensaje para mostrar cuando [estado] es ERROR. */
    val error: String? = null,
    val creado: Long,
    /** Versión de ArmadorTarjetas con la que se armaron las tarjetas (0: todavía no se armaron). */
    @ColumnInfo(defaultValue = "0") val versionArmado: Int = 0,
    /** Largo máximo de tarjeta con el que se armaron (0: todavía no se armaron). */
    @ColumnInfo(defaultValue = "0") val largoArmado: Int = 0,
)

/** Texto crudo de cada página. Se guarda para poder seguir si el worker se corta y para rearmar. */
@Entity(
    tableName = "paginas",
    primaryKeys = ["documentoId", "numero"],
    foreignKeys = [
        ForeignKey(entity = Documento::class, parentColumns = ["id"], childColumns = ["documentoId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PaginaTexto(
    val documentoId: Long,
    /** Empieza en 1. */
    val numero: Int,
    val texto: String,
    val origen: OrigenTexto,
)

@Entity(
    tableName = "secciones",
    foreignKeys = [
        ForeignKey(entity = Documento::class, parentColumns = ["id"], childColumns = ["documentoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("documentoId")],
)
data class Seccion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentoId: Long,
    val orden: Int,
    /** null si el apunte no tiene títulos: la UI muestra "Página N". */
    val titulo: String?,
    val paginaInicio: Int,
)

@Entity(
    tableName = "tarjetas",
    foreignKeys = [
        ForeignKey(entity = Documento::class, parentColumns = ["id"], childColumns = ["documentoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Seccion::class, parentColumns = ["id"], childColumns = ["seccionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("documentoId"), Index("seccionId")],
)
data class Tarjeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentoId: Long,
    val seccionId: Long,
    /** Posición dentro de la sección. */
    val orden: Int,
    val texto: String,
    val pagina: Int,
    val origen: OrigenTexto,
    // Repaso espaciado (se usa desde F2/F4; PLAN.md, sección 5).
    val caja: Int = 0,
    val proximoRepaso: Long? = null,
    val vecesVista: Int = 0,
    val ultimaVista: Long? = null,
    val guardada: Boolean = false,
    val editada: Boolean = false,
)

data class SeccionConTarjetas(
    @Embedded val seccion: Seccion,
    @Relation(parentColumn = "id", entityColumn = "seccionId")
    val tarjetas: List<Tarjeta>,
)

/** Ver TarjetaDao.observarFirma. */
data class FirmaTarjetas(val total: Int, val ultimoId: Long)

/** Materia con los números que muestra la Biblioteca. */
data class MateriaConResumen(
    @Embedded val materia: Materia,
    val documentos: Int,
    val tarjetas: Int,
    /** Documentos que todavía se están procesando (PENDIENTE, EXTRAYENDO o ARMANDO). */
    val procesando: Int,
)
