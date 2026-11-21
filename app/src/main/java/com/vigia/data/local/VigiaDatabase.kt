package com.vigia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MissionEntity::class, EventEntity::class], version = 2, exportSchema = false)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "vigia.db"

        // Versión 2: el tipo de alerta de cada evento, para el filtro "Con incidentes"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN alertKind TEXT")
            }
        }
    }
}
