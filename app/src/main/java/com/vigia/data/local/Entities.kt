package com.vigia.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Los enums se guardan por nombre para no depender de TypeConverters
@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey val id: String,
    val routeId: String,
    val routeName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val result: String,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
    val peopleDetected: Int,
    val openDoors: Int,
)

// Sin imágenes ni datos personales: solo zona, hora, tipo y el mensaje (con la clasificación del guardia)
@Entity(tableName = "events", indices = [Index("missionId")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val missionId: String?,
    val type: String,
    val severity: String,
    val message: String,
    val zone: String?,
    // Nombre del AlertKind en eventos ALERT; null en el resto y en eventos guardados antes de la versión 2
    val alertKind: String? = null,
)
