package com.apuntescroll.datos.bd

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MateriaDao {

    @Query(
        """
        SELECT m.*,
            (SELECT COUNT(*) FROM documentos d WHERE d.materiaId = m.id) AS documentos,
            (SELECT COUNT(*) FROM tarjetas t JOIN documentos d ON t.documentoId = d.id
                WHERE d.materiaId = m.id) AS tarjetas,
            (SELECT COUNT(*) FROM documentos d WHERE d.materiaId = m.id
                AND d.estado IN ('PENDIENTE', 'EXTRAYENDO', 'ARMANDO')) AS procesando
        FROM materias m
        ORDER BY m.nombre COLLATE NOCASE
        """,
    )
    fun observarConResumen(): Flow<List<MateriaConResumen>>

    @Query("SELECT * FROM materias WHERE id = :id")
    fun observar(id: Long): Flow<Materia?>

    @Query("SELECT * FROM materias ORDER BY nombre COLLATE NOCASE")
    suspend fun todas(): List<Materia>

    @Query("SELECT * FROM materias WHERE id = :id")
    suspend fun obtener(id: Long): Materia?

    @Insert
    suspend fun insertar(materia: Materia): Long

    @Update
    suspend fun actualizar(materia: Materia)

    @Delete
    suspend fun borrar(materia: Materia)
}

@Dao
interface DocumentoDao {

    @Query("SELECT * FROM documentos WHERE materiaId = :materiaId ORDER BY creado DESC")
    fun observarPorMateria(materiaId: Long): Flow<List<Documento>>

    @Query("SELECT * FROM documentos WHERE id = :id")
    fun observar(id: Long): Flow<Documento?>

    @Query("SELECT * FROM documentos WHERE id = :id")
    suspend fun obtener(id: Long): Documento?

    @Query("SELECT * FROM documentos WHERE materiaId = :materiaId")
    suspend fun porMateria(materiaId: Long): List<Documento>

    @Query("SELECT * FROM documentos WHERE estado IN ('PENDIENTE', 'EXTRAYENDO', 'ARMANDO')")
    suspend fun sinTerminar(): List<Documento>

    /** Documentos listos cuyas tarjetas salieron de un armado anterior a [version]. */
    @Query("SELECT id FROM documentos WHERE estado = 'LISTO' AND versionArmado < :version")
    suspend fun armadosAntesDe(version: Int): List<Long>

    @Query("UPDATE documentos SET versionArmado = :version, largoArmado = :largo WHERE id = :id")
    suspend fun fijarArmado(id: Long, version: Int, largo: Int)

    @Query("SELECT archivo FROM documentos")
    suspend fun archivos(): List<String>

    @Insert
    suspend fun insertar(documento: Documento): Long

    @Query("UPDATE documentos SET estado = :estado, error = :error WHERE id = :id")
    suspend fun cambiarEstado(id: Long, estado: EstadoDocumento, error: String? = null)

    @Query("UPDATE documentos SET paginas = :paginas WHERE id = :id")
    suspend fun fijarPaginas(id: Long, paginas: Int)

    @Query("UPDATE documentos SET paginasProcesadas = :procesadas, paginasOcr = :ocr WHERE id = :id")
    suspend fun fijarProgreso(id: Long, procesadas: Int, ocr: Int)

    @Delete
    suspend fun borrar(documento: Documento)
}

@Dao
interface PaginaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(pagina: PaginaTexto)

    @Query("SELECT * FROM paginas WHERE documentoId = :documentoId ORDER BY numero")
    suspend fun deDocumento(documentoId: Long): List<PaginaTexto>
}

@Dao
interface TarjetaDao {

    @Transaction
    @Query("SELECT * FROM secciones WHERE documentoId = :documentoId ORDER BY orden")
    fun observarSecciones(documentoId: Long): Flow<List<SeccionConTarjetas>>

    @Query("SELECT COUNT(*) FROM tarjetas")
    fun observarTotal(): Flow<Int>

    /**
     * Cuántas tarjetas hay y el id más alto. Cambia cuando se agregan, se borran o se rearman
     * tarjetas (los ids nunca se reusan), no cuando se marcan.
     */
    @Query("SELECT COUNT(*) AS total, COALESCE(MAX(id), 0) AS ultimoId FROM tarjetas")
    fun observarFirma(): Flow<FirmaTarjetas>

    /** Cuántas tarjetas con id hasta [ultimoId] siguen en la base. */
    @Query("SELECT COUNT(*) FROM tarjetas WHERE id <= :ultimoId")
    suspend fun cuantasHasta(ultimoId: Long): Int

    @Query("SELECT * FROM tarjetas WHERE documentoId = :documentoId ORDER BY id")
    suspend fun deDocumento(documentoId: Long): List<Tarjeta>

    @Query("SELECT * FROM secciones WHERE documentoId = :documentoId ORDER BY orden")
    suspend fun seccionesDe(documentoId: Long): List<Seccion>

    /** Todas las tarjetas de documentos listos, con lo que necesita el feed. */
    @Query(
        """
        SELECT t.id, t.texto, t.pagina, t.orden AS ordenTarjeta, t.caja, t.vecesVista, t.ultimaVista,
            t.guardada, t.documentoId, d.titulo AS tituloDocumento,
            t.seccionId, s.orden AS ordenSeccion, s.titulo AS tituloSeccion,
            s.paginaInicio AS paginaInicioSeccion, m.id AS materiaId, m.nombre AS nombreMateria,
            m.emoji, m.color
        FROM tarjetas t
        JOIN secciones s ON s.id = t.seccionId
        JOIN documentos d ON d.id = t.documentoId
        JOIN materias m ON m.id = d.materiaId
        WHERE d.estado = 'LISTO'
        """,
    )
    suspend fun paraFeed(): List<com.apuntescroll.feed.TarjetaFeed>

    /** Las tarjetas de una sección, en orden, con los mismos datos que el feed (para ver el hilo). */
    @Query(
        """
        SELECT t.id, t.texto, t.pagina, t.orden AS ordenTarjeta, t.caja, t.vecesVista, t.ultimaVista,
            t.guardada, t.documentoId, d.titulo AS tituloDocumento,
            t.seccionId, s.orden AS ordenSeccion, s.titulo AS tituloSeccion,
            s.paginaInicio AS paginaInicioSeccion, m.id AS materiaId, m.nombre AS nombreMateria,
            m.emoji, m.color
        FROM tarjetas t
        JOIN secciones s ON s.id = t.seccionId
        JOIN documentos d ON d.id = t.documentoId
        JOIN materias m ON m.id = d.materiaId
        WHERE t.seccionId = :seccionId
        ORDER BY t.orden
        """,
    )
    fun observarSeccion(seccionId: Long): Flow<List<com.apuntescroll.feed.TarjetaFeed>>

    @Query("UPDATE tarjetas SET vecesVista = vecesVista + 1, ultimaVista = :ahora WHERE id = :id")
    suspend fun marcarVista(id: Long, ahora: Long)

    @Query("UPDATE tarjetas SET caja = :caja, proximoRepaso = :proximoRepaso WHERE id IN (:ids)")
    suspend fun fijarCaja(ids: List<Long>, caja: Int, proximoRepaso: Long?)

    @Query("UPDATE tarjetas SET guardada = :guardada WHERE id IN (:ids)")
    suspend fun fijarGuardada(ids: List<Long>, guardada: Boolean)

    @Query("DELETE FROM secciones WHERE documentoId = :documentoId")
    suspend fun borrarArmado(documentoId: Long)

    @Insert
    suspend fun insertarSeccion(seccion: Seccion): Long

    @Insert
    suspend fun insertarTarjetas(tarjetas: List<Tarjeta>)
}
