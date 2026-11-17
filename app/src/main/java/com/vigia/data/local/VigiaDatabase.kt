package com.vigia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MissionEntity::class, EventEntity::class], version = 1, exportSchema = false)
abstract class VigiaDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "vigia.db"
    }
}
