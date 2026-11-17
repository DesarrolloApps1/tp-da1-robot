package com.vigia.presentation.simulation

import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry

data class SimulationUiState(
    val connected: Boolean = true,
    val speedFactor: Int = 1,
    val batteryPercent: Int? = null,
    val robotStateLabel: String? = null,
    // El simulador ignora obstáculo y persona fuera de una ronda
    val canTriggerIncident: Boolean = false,
)

fun simulationState(connected: Boolean, speedFactor: Int, telemetry: RobotTelemetry?) = SimulationUiState(
    connected = connected,
    speedFactor = speedFactor,
    batteryPercent = telemetry?.batteryPercent,
    robotStateLabel = telemetry?.state?.label,
    canTriggerIncident = telemetry?.state == RobotState.PATROLLING,
)
