package com.vigia.data.contract

import kotlinx.serialization.Serializable

// Mensajes del contrato JSON propuesto (PLAN.md, sección 8); los timestamps viajan en ISO-8601 UTC

@Serializable
data class TelemetryDto(
    val type: String = "telemetry",
    val robotId: String,
    val seq: Long,
    val timestamp: String,
    val connection: ConnectionDto,
    val battery: BatteryDto,
    val state: String,
    val mission: MissionDto?,
    val pose: PoseDto,
    val motion: MotionDto,
    val service: ServiceDto,
)

@Serializable
data class ConnectionDto(val rssi: Int, val latencyMs: Int)

@Serializable
data class BatteryDto(val percent: Int, val minutesLeft: Int, val charging: Boolean)

@Serializable
data class MissionDto(
    val id: String,
    val routeId: String,
    val progress: Double,
    val currentCheckpoint: String?,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
)

@Serializable
data class PoseDto(val x: Double, val y: Double, val heading: Double, val zone: String)

@Serializable
data class MotionDto(val speed: Double, val moving: Boolean)

@Serializable
data class ServiceDto(val checkpointsVerified: Int, val peopleInRestrictedZone: Int, val openDoors: Int)

@Serializable
data class CommandDto(
    val type: String = "command",
    val commandId: String,
    val robotId: String,
    val command: String,
    val params: Map<String, String>,
    val issuedAt: String,
)

@Serializable
data class CommandAckDto(
    val type: String = "command_ack",
    val commandId: String,
    val result: String,
    val reason: String?,
    val respondedAt: String,
) {
    companion object {
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
    }
}

@Serializable
data class AlertDto(
    val type: String = "alert",
    val alertId: String,
    val severity: String,
    val kind: String,
    val zone: String,
    val message: String,
    val raisedAt: String,
    val requiresDecision: Boolean,
)
