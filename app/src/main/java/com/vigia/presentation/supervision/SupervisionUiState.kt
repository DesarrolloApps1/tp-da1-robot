package com.vigia.presentation.supervision

import com.vigia.domain.model.Alert
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.Facility
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import com.vigia.domain.rules.BatteryLevel
import com.vigia.domain.rules.BatteryRules
import com.vigia.domain.rules.CommandAvailability
import com.vigia.domain.rules.CommandRules
import com.vigia.domain.rules.Freshness
import com.vigia.domain.rules.IncidentRules
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.FacilityMapState
import com.vigia.presentation.IncidentDecisionUi
import com.vigia.presentation.StatusTone
import com.vigia.presentation.commandContext
import com.vigia.presentation.incidentDecisions
import com.vigia.presentation.toStatusUi

const val WEAK_SIGNAL_RSSI = -75

data class CommandUi(val availability: CommandAvailability, val lastStatus: CommandStatusUi?)

data class MissionUi(
    val routeName: String,
    val progressPercent: Int,
    val currentCheckpointName: String?,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
)

// Los tonos ya vienen en STALE cuando los datos son antiguos: la pantalla no decide colores
data class SupervisionUiState(
    val telemetry: RobotTelemetry?,
    val stale: Boolean,
    val connectionLabel: String,
    val connectionTone: StatusTone,
    val lastUpdateMillis: Long?,
    val ageSeconds: Long?,
    val ageText: String,
    val batteryLevel: BatteryLevel?,
    val batteryTone: StatusTone,
    // Advertencia del escenario 2; no aplica con el robot cargando en la base
    val batteryWarning: BatteryLevel?,
    val stateTone: StatusTone,
    val dataTone: StatusTone,
    val mission: MissionUi?,
    val peopleTone: StatusTone,
    val doorsTone: StatusTone,
    val openIncident: Alert?,
    // Por id de alerta, para que el diálogo siga abierto hasta ver la confirmación
    val decisions: Map<String, IncidentDecisionUi>,
    val map: FacilityMapState,
    val commands: Map<CommandType, CommandUi>,
    val canChooseRoute: Boolean,
) {
    val state: RobotState? get() = telemetry?.state
    val pose: Pose? get() = telemetry?.pose
    val service: ServiceCounters? get() = telemetry?.service

    fun command(type: CommandType): CommandUi = commands.getValue(type)

    companion object {
        val Initial = supervisionState(null, null, emptyList(), emptyList(), now = 0)
    }
}

fun supervisionState(
    telemetry: RobotTelemetry?,
    lastUpdateMillis: Long?,
    alerts: List<Alert>,
    commands: List<CommandRecord>,
    now: Long,
): SupervisionUiState {
    val stale = Freshness.isStale(lastUpdateMillis, now)
    val age = Freshness.ageSeconds(lastUpdateMillis, now)
    val incident = IncidentRules.openIncident(telemetry?.state, alerts)
    fun tone(fresh: StatusTone) = if (stale) StatusTone.STALE else fresh

    val context = commandContext(telemetry, stale, alerts, commands)
    // La lista viene con la más reciente primero
    val latest = commands.distinctBy { it.command.type }.associateBy { it.command.type }
    val battery = telemetry?.let { BatteryRules.level(it.batteryPercent) }
    val service = telemetry?.service

    val (connectionLabel, connectionTone) = when {
        telemetry == null || stale -> "Sin datos" to StatusTone.ERROR
        telemetry.rssi < WEAK_SIGNAL_RSSI -> "Señal débil" to StatusTone.WARNING
        else -> "Conectado" to StatusTone.OK
    }

    return SupervisionUiState(
        telemetry = telemetry,
        stale = stale,
        connectionLabel = connectionLabel,
        connectionTone = connectionTone,
        lastUpdateMillis = lastUpdateMillis,
        ageSeconds = age,
        ageText = age?.let { "hace $it s" } ?: "Sin datos",
        batteryLevel = battery,
        batteryTone = tone(battery.tone()),
        batteryWarning = battery?.takeIf { it != BatteryLevel.OK && telemetry.state != RobotState.DOCKED },
        stateTone = tone(telemetry?.state.tone()),
        dataTone = tone(StatusTone.NEUTRAL),
        mission = telemetry?.mission?.toUi(),
        peopleTone = tone(if ((service?.peopleInRestrictedZone ?: 0) > 0) StatusTone.ERROR else StatusTone.NEUTRAL),
        doorsTone = tone(if ((service?.openDoors ?: 0) > 0) StatusTone.WARNING else StatusTone.NEUTRAL),
        openIncident = incident,
        decisions = incidentDecisions(telemetry?.state, alerts, commands),
        map = FacilityMapState(
            routeId = telemetry?.mission?.routeId,
            verifiedIds = telemetry?.mission?.verifiedIds().orEmpty(),
            currentCheckpointId = telemetry?.mission?.currentCheckpointId,
            robot = telemetry?.pose,
            stale = stale,
        ),
        commands = CommandType.entries.associateWith {
            CommandUi(CommandRules.availability(it, context), latest[it]?.toStatusUi())
        },
        canChooseRoute = telemetry?.state == RobotState.DOCKED || telemetry?.state == RobotState.IDLE,
    )
}

private fun BatteryLevel?.tone(): StatusTone = when (this) {
    null -> StatusTone.NEUTRAL
    BatteryLevel.OK -> StatusTone.OK
    BatteryLevel.LOW -> StatusTone.WARNING
    BatteryLevel.CRITICAL -> StatusTone.ERROR
}

private fun RobotState?.tone(): StatusTone = when (this) {
    null -> StatusTone.NEUTRAL
    RobotState.DOCKED, RobotState.IDLE, RobotState.PATROLLING, RobotState.RETURNING -> StatusTone.OK
    RobotState.PAUSED, RobotState.STOPPED -> StatusTone.WARNING
    RobotState.INCIDENT, RobotState.FAULT -> StatusTone.ERROR
}

private fun MissionProgress.toUi() = MissionUi(
    routeName = Facility.route(routeId)?.name ?: routeId,
    progressPercent = (progress * 100).toInt(),
    currentCheckpointName = currentCheckpointId?.let { Facility.checkpoint(it)?.name },
    checkpointsVerified = checkpointsVerified,
    checkpointsTotal = checkpointsTotal,
)

// La telemetría solo trae la cuenta: se marcan los primeros puntos de la ruta (un punto salteado corre la marca)
private fun MissionProgress.verifiedIds(): Set<String> =
    Facility.route(routeId)?.checkpointIds
        ?.filter { it != Facility.BASE_ID }
        ?.take(checkpointsVerified)
        ?.toSet()
        .orEmpty()
