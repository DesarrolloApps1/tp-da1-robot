package com.vigia.domain.model

enum class CheckpointType { SAFE_POINT, DOOR, PASSAGE, RESTRICTED }

// Coordenadas en metros sobre el plano de la planta baja
data class Checkpoint(
    val id: String,
    val name: String,
    val type: CheckpointType,
    val x: Double,
    val y: Double,
)
