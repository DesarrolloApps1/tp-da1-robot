package com.vigia.data.contract

import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import java.time.Instant

fun Long.toIsoString(): String = Instant.ofEpochMilli(this).toString()

fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()

fun TelemetryDto.toDomain(): RobotTelemetry = RobotTelemetry(
    robotId = robotId,
    seq = seq,
    timestampMillis = timestamp.toEpochMillis(),
    rssi = connection.rssi,
    latencyMs = connection.latencyMs,
    batteryPercent = battery.percent,
    minutesLeft = battery.minutesLeft,
    charging = battery.charging,
    state = RobotState.valueOf(state),
    mission = mission?.let {
        MissionProgress(
            id = it.id,
            routeId = it.routeId,
            progress = it.progress,
            currentCheckpointId = it.currentCheckpoint,
            checkpointsVerified = it.checkpointsVerified,
            checkpointsTotal = it.checkpointsTotal,
            verifiedCheckpointIds = it.verifiedCheckpoints,
        )
    },
    pose = Pose(pose.x, pose.y, pose.heading, pose.zone),
    speed = motion.speed,
    moving = motion.moving,
    service = ServiceCounters(service.checkpointsVerified, service.peopleInRestrictedZone, service.openDoors),
)

fun RobotTelemetry.toDto(): TelemetryDto = TelemetryDto(
    robotId = robotId,
    seq = seq,
    timestamp = timestampMillis.toIsoString(),
    connection = ConnectionDto(rssi, latencyMs),
    battery = BatteryDto(batteryPercent, minutesLeft, charging),
    state = state.name,
    mission = mission?.let {
        MissionDto(
            id = it.id,
            routeId = it.routeId,
            progress = it.progress,
            currentCheckpoint = it.currentCheckpointId,
            checkpointsVerified = it.checkpointsVerified,
            checkpointsTotal = it.checkpointsTotal,
            verifiedCheckpoints = it.verifiedCheckpointIds,
        )
    },
    pose = PoseDto(pose.x, pose.y, pose.heading, pose.zone),
    motion = MotionDto(speed, moving),
    service = ServiceDto(service.checkpointsVerified, service.peopleInRestrictedZone, service.openDoors),
)

fun CommandDto.toDomain(): Command = Command(
    id = commandId,
    type = CommandType.valueOf(command),
    params = params,
    issuedAtMillis = issuedAt.toEpochMillis(),
)

fun Command.toDto(robotId: String): CommandDto = CommandDto(
    commandId = id,
    robotId = robotId,
    command = type.name,
    params = params,
    issuedAt = issuedAtMillis.toIsoString(),
)

// El reconocimiento y la resolución no viajan en el contrato: los lleva la app
fun AlertDto.toDomain(): Alert = Alert(
    id = alertId,
    severity = AlertSeverity.valueOf(severity),
    kind = AlertKind.valueOf(kind),
    zone = zone,
    message = message,
    raisedAtMillis = raisedAt.toEpochMillis(),
    requiresDecision = requiresDecision,
)

fun Alert.toDto(): AlertDto = AlertDto(
    alertId = id,
    severity = severity.name,
    kind = kind.name,
    zone = zone,
    message = message,
    raisedAt = raisedAtMillis.toIsoString(),
    requiresDecision = requiresDecision,
)
