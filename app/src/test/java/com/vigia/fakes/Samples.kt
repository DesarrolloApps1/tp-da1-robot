package com.vigia.fakes

import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters

fun sampleTelemetry(
    state: RobotState = RobotState.PATROLLING,
    battery: Int = 80,
    rssi: Int = -60,
    mission: MissionProgress? = MissionProgress("m-0001", "ronda-completa", 0.4, "CP4", 3, 8, listOf("CP1", "CP2", "CP3")),
) = RobotTelemetry(
    robotId = "go2-vigia-01",
    seq = 1,
    timestampMillis = 0,
    rssi = rssi,
    latencyMs = 80,
    batteryPercent = battery,
    minutesLeft = 60,
    charging = false,
    state = state,
    mission = mission,
    pose = Pose(20.0, 5.0, 0.0, "Pasillo A"),
    speed = 0.8,
    moving = true,
    service = ServiceCounters(mission?.checkpointsVerified ?: 0, 0, 0),
)

fun sampleIncident(id: String = "al-0001") = Alert(
    id = id,
    severity = AlertSeverity.CRITICAL,
    kind = AlertKind.OBSTACLE,
    zone = "Pasillo B",
    message = "Obstáculo bloquea el paso",
    raisedAtMillis = 0,
    requiresDecision = true,
)

fun sampleAlert(
    id: String,
    kind: AlertKind,
    raisedAtMillis: Long,
    severity: AlertSeverity = AlertSeverity.WARNING,
    requiresDecision: Boolean = false,
    acknowledged: Boolean = false,
    resolution: IncidentDecision? = null,
) = Alert(id, severity, kind, "Pasillo B", "Alerta de prueba", raisedAtMillis, requiresDecision, acknowledged, resolution)
