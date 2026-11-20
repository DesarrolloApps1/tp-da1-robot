package com.vigia.domain.model

data class RobotTelemetry(
    val robotId: String,
    val seq: Long,
    val timestampMillis: Long,
    val rssi: Int,
    val latencyMs: Int,
    val batteryPercent: Int,
    val minutesLeft: Int,
    val charging: Boolean,
    val state: RobotState,
    val mission: MissionProgress?,
    val pose: Pose,
    val speed: Double,
    val moving: Boolean,
    val service: ServiceCounters,
)

data class MissionProgress(
    val id: String,
    val routeId: String,
    // De 0 a 1
    val progress: Double,
    val currentCheckpointId: String?,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
    // Ids de los puntos ya verificados, en orden; un punto salteado no aparece
    val verifiedCheckpointIds: List<String> = emptyList(),
)

data class Pose(
    val x: Double,
    val y: Double,
    val heading: Double,
    val zone: String,
)

// Las tres variables propias del servicio de ronda
data class ServiceCounters(
    val checkpointsVerified: Int,
    val peopleInRestrictedZone: Int,
    val openDoors: Int,
)
