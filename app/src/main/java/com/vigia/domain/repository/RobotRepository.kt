package com.vigia.domain.repository

import com.vigia.domain.model.Alert
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.RobotSummary
import com.vigia.domain.model.RobotTelemetry
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// Única puerta de la app a la telemetría, las órdenes y los eventos del robot
interface RobotRepository {
    val robots: StateFlow<List<RobotSummary>>
    val telemetry: StateFlow<RobotTelemetry?>
    val lastUpdateMillis: StateFlow<Long?>
    val alerts: StateFlow<List<Alert>>

    // La más reciente primero
    val commands: StateFlow<List<CommandRecord>>
    val events: SharedFlow<EventRecord>

    fun select(robotId: String)

    // Devuelve el registro final, después del ack o del timeout
    suspend fun send(type: CommandType, params: Map<String, String> = emptyMap()): CommandRecord
}
