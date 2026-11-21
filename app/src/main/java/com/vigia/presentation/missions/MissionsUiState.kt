package com.vigia.presentation.missions

import com.vigia.domain.model.Alert
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.Facility
import com.vigia.domain.model.PatrolPace
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.rules.CommandAvailability
import com.vigia.domain.rules.CommandRules
import com.vigia.domain.rules.Freshness
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.commandContext
import com.vigia.presentation.formatDuration
import com.vigia.presentation.toStatusUi
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.vigia.domain.model.Route as PatrolRoute

private const val ROUTE_ID = "routeId"

data class RouteItemUi(
    val id: String,
    val name: String,
    val checkpointNames: List<String>,
    val checkpointCount: Int,
    val lengthMeters: Int,
    val estimatedSeconds: Long,
    val durationText: String,
    val lastStatus: CommandStatusUi?,
)

data class MissionsUiState(
    val routes: List<RouteItemUi>,
    // Iniciar es la misma orden para todas las rutas: se habilita o no para todas juntas
    val start: CommandAvailability,
    // En true cuando el robot confirmó el inicio; la pantalla vuelve a Supervisión
    val startConfirmed: Boolean = false,
) {
    companion object {
        val Initial = missionsState(null, null, emptyList(), emptyList(), now = 0)
    }
}

fun missionsState(
    telemetry: RobotTelemetry?,
    lastUpdateMillis: Long?,
    alerts: List<Alert>,
    commands: List<CommandRecord>,
    now: Long,
): MissionsUiState {
    val context = commandContext(telemetry, Freshness.isStale(lastUpdateMillis, now), alerts, commands)
    return MissionsUiState(
        routes = Facility.routes.map { it.toUi(commands.latestStartFor(it.id)) },
        start = CommandRules.availability(CommandType.START_MISSION, context),
    )
}

fun startMissionParams(routeId: String): Map<String, String> = mapOf(ROUTE_ID to routeId)

// La lista de órdenes viene con la más reciente primero
private fun List<CommandRecord>.latestStartFor(routeId: String): CommandRecord? =
    firstOrNull { it.command.type == CommandType.START_MISSION && it.command.params[ROUTE_ID] == routeId }

private fun PatrolRoute.toUi(lastStart: CommandRecord?): RouteItemUi {
    val seconds = PatrolPace.estimatedSeconds(this).roundToLong()
    return RouteItemUi(
        id = id,
        name = name,
        checkpointNames = checkpointIds.filter { it != Facility.BASE_ID }.mapNotNull { Facility.checkpoint(it)?.name },
        checkpointCount = PatrolPace.checkpointsToVerify(this),
        lengthMeters = Facility.routeLengthMeters(this).roundToInt(),
        estimatedSeconds = seconds,
        durationText = formatDuration(seconds),
        lastStatus = lastStart?.toStatusUi(),
    )
}
