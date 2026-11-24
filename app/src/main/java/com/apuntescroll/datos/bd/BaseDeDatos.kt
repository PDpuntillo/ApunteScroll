package com.apuntescroll.datos.bd

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Materia::class, Documento::class, PaginaTexto::class, Seccion::class, Tarjeta::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: versionArmado y largoArmado en documentos (para rearmar con un armado nuevo).
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class BaseDeDatos : RoomDatabase() {
    abstract fun materias(): MateriaDao
    abstract fun documentos(): DocumentoDao
    abstract fun paginas(): PaginaDao
    abstract fun tarjetas(): TarjetaDao

    companion object {
        fun crear(context: Context): BaseDeDatos =
            Room.databaseBuilder(context, BaseDeDatos::class.java, "apuntescroll.db")
                // Red de seguridad mientras la app no está publicada: un cambio de esquema sin
                // migración borra la base. Antes de la primera versión en Google Play esto se saca
                // y cada cambio lleva su migración (los esquemas quedan en app/schemas).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
