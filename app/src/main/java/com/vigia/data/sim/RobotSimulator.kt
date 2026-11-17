package com.vigia.data.sim

import com.vigia.data.contract.AlertDto
import com.vigia.data.contract.CommandAckDto
import com.vigia.data.contract.CommandDto
import com.vigia.data.contract.ContractJson
import com.vigia.data.contract.TelemetryDto
import com.vigia.data.contract.toDomain
import com.vigia.data.contract.toDto
import com.vigia.data.contract.toIsoString
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Checkpoint
import com.vigia.domain.model.CheckpointType
import com.vigia.domain.model.Clock
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.Facility
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.MissionResult
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.Route
import com.vigia.domain.model.ServiceCounters
import com.vigia.domain.rules.BatteryRules
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Go2 simulado del lado robot. Habla solo JSON del contrato y no usa corrutinas:
 * el tiempo avanza únicamente con [advance], así que es determinístico.
 */
class RobotSimulator(private val clock: Clock) {

    private class ActiveMission(val id: String, val route: Route) {
        val total = route.checkpointIds.count { it != Facility.BASE_ID }
        var targetIndex = 1
        var dwellRemaining = 0.0
        var frozenProgress: Double? = null
        val targetId get() = route.checkpointIds[targetIndex]
        val isLastTarget get() = targetIndex == route.checkpointIds.lastIndex
    }

    private val base = checkpoint(Facility.BASE_ID)

    // Punto del plano más lejano a la base, para degradar la señal con la distancia
    private val maxDistance = hypot(Facility.WIDTH_METERS - base.x, Facility.HEIGHT_METERS - base.y)

    private var seq = 0L
    private var battery = INITIAL_BATTERY
    private var state = RobotState.DOCKED
    private var x = base.x
    private var y = base.y
    private var heading = 0.0

    private var mission: ActiveMission? = null
    private var missionCount = 0
    private var checkpointsVerified = 0
    private var peopleInRestrictedZone = 0
    private var openDoors = 0

    private var pendingDecision: Alert? = null
    private var waitRemaining = 0.0
    private var secondsWithoutLink = 0.0
    private var lowBatteryAlerted = false
    private var criticalBatteryAlerted = false
    private var rejectNext = false

    private var alertCount = 0
    private val raisedAlertIds = mutableSetOf<String>()
    private val pendingAlerts = mutableListOf<String>()
    private val pendingEvents = mutableListOf<EventRecord>()

    // Avanza el tiempo simulado; linkUp = false acumula tiempo sin enlace para el regreso autónomo
    fun advance(seconds: Double, linkUp: Boolean = true) {
        secondsWithoutLink = if (linkUp) 0.0 else secondsWithoutLink + seconds
        updateBattery(seconds)
        checkBattery()
        checkLink()
        when (state) {
            RobotState.PATROLLING -> patrol(seconds)
            RobotState.RETURNING -> goBase(seconds)
            RobotState.INCIDENT -> waitObstacle(seconds)
            else -> Unit
        }
    }

    fun telemetryJson(): String = ContractJson.encodeToString(TelemetryDto.serializer(), telemetry().toDto())

    fun receive(commandJson: String): String {
        val command = ContractJson.decodeFromString(CommandDto.serializer(), commandJson).toDomain()
        // La detención operativa nunca se rechaza (PLAN.md, sección 5)
        val reason = if (rejectNext && command.type != CommandType.OPERATIONAL_STOP) {
            rejectNext = false
            RejectReason.FORCED
        } else {
            apply(command)
        }
        val ack = CommandAckDto(
            commandId = command.id,
            result = if (reason == null) CommandAckDto.ACCEPTED else CommandAckDto.REJECTED,
            reason = reason?.name,
            respondedAt = clock.now().toIsoString(),
        )
        return ContractJson.encodeToString(CommandAckDto.serializer(), ack)
    }

    fun drainAlerts(): List<String> = pendingAlerts.toList().also { pendingAlerts.clear() }

    // Eventos de misión y de puntos de control para el historial
    fun drainEvents(): List<EventRecord> = pendingEvents.toList().also { pendingEvents.clear() }

    fun forceRejectNext() {
        rejectNext = true
    }

    fun setBattery(percent: Int) {
        battery = percent.coerceIn(0, 100).toDouble()
    }

    fun obstacleAhead() {
        if (state != RobotState.PATROLLING) return
        openIncident(raise(AlertSeverity.CRITICAL, AlertKind.OBSTACLE, zone(), "Obstáculo bloquea el paso", true))
    }

    fun personInRestrictedZone() {
        if (state != RobotState.PATROLLING) return
        peopleInRestrictedZone++
        val zone = Facility.checkpoints
            .filter { it.type == CheckpointType.RESTRICTED }
            .minBy { hypot(it.x - x, it.y - y) }
            .name
        openIncident(
            raise(AlertSeverity.CRITICAL, AlertKind.PERSON_IN_RESTRICTED_ZONE, zone, "Persona detectada en zona restringida", true),
        )
    }

    fun doorOpen() {
        openDoors++
        raise(AlertSeverity.WARNING, AlertKind.DOOR_OPEN, checkpoint(EMERGENCY_EXIT_ID).name, "Puerta abierta detectada", false)
    }

    private fun telemetry(): RobotTelemetry {
        val signal = (hypot(x - base.x, y - base.y) / maxDistance).coerceIn(0.0, 1.0)
        val moving = state == RobotState.RETURNING ||
            (state == RobotState.PATROLLING && (mission?.dwellRemaining ?: 0.0) <= 0.0)
        return RobotTelemetry(
            robotId = ROBOT_ID,
            seq = ++seq,
            timestampMillis = clock.now(),
            rssi = (RSSI_NEAR + (RSSI_FAR - RSSI_NEAR) * signal).roundToInt(),
            latencyMs = (LATENCY_NEAR + (LATENCY_FAR - LATENCY_NEAR) * signal).roundToInt(),
            batteryPercent = battery.toInt(),
            minutesLeft = (battery / DRAIN_MOVING / 60).toInt(),
            charging = state == RobotState.DOCKED && battery < 100.0,
            state = state,
            mission = mission?.let {
                MissionProgress(
                    id = it.id,
                    routeId = it.route.id,
                    progress = it.frozenProgress ?: progressOf(it),
                    currentCheckpointId = if (state == RobotState.RETURNING) Facility.BASE_ID else it.targetId,
                    checkpointsVerified = checkpointsVerified,
                    checkpointsTotal = it.total,
                )
            },
            pose = Pose(x, y, heading, zone()),
            speed = if (moving) SPEED else 0.0,
            moving = moving,
            service = ServiceCounters(checkpointsVerified, peopleInRestrictedZone, openDoors),
        )
    }

    private fun apply(command: Command): RejectReason? {
        val params = command.params
        return when (command.type) {
            CommandType.START_MISSION -> start(params["routeId"] ?: DEFAULT_ROUTE_ID)
            CommandType.PAUSE_MISSION -> transition(RobotState.PATROLLING, RobotState.PAUSED)
            CommandType.RESUME_MISSION -> when {
                state != RobotState.PAUSED -> RejectReason.INVALID_STATE
                !BatteryRules.canResume(battery.toInt()) -> RejectReason.BATTERY_TOO_LOW
                else -> transition(RobotState.PAUSED, RobotState.PATROLLING)
            }
            CommandType.CANCEL_MISSION -> cancel()
            CommandType.RETURN_TO_BASE -> {
                if (state == RobotState.DOCKED || state == RobotState.RETURNING) return RejectReason.INVALID_STATE
                startReturn()
                null
            }
            CommandType.OPERATIONAL_STOP -> {
                clearIncident()
                state = RobotState.STOPPED
                null
            }
            CommandType.ACK_ALERT -> if (params["alertId"] in raisedAlertIds) null else RejectReason.UNKNOWN_ALERT
            CommandType.RESOLVE_INCIDENT -> resolve(params["alertId"], params["decision"])
        }
    }

    private fun transition(from: RobotState, to: RobotState): RejectReason? {
        if (state != from) return RejectReason.INVALID_STATE
        state = to
        return null
    }

    private fun start(routeId: String): RejectReason? {
        if (state != RobotState.DOCKED && state != RobotState.IDLE) return RejectReason.ROBOT_BUSY
        if (!BatteryRules.canStart(battery.toInt())) return RejectReason.BATTERY_TOO_LOW
        val route = Facility.route(routeId) ?: return RejectReason.INVALID_STATE
        val active = ActiveMission("m-" + (++missionCount).toString().padStart(4, '0'), route)
        mission = active
        checkpointsVerified = 0
        peopleInRestrictedZone = 0
        openDoors = 0
        state = RobotState.PATROLLING
        event(EventType.MISSION, AlertSeverity.INFO, "Misión ${active.id} iniciada: ${route.name}", null)
        return null
    }

    private fun cancel(): RejectReason? {
        if (state !in CANCELLABLE) return RejectReason.INVALID_STATE
        clearIncident()
        finishMission(MissionResult.CANCELLED)
        state = RobotState.IDLE
        return null
    }

    private fun resolve(alertId: String?, decisionName: String?): RejectReason? {
        if (alertId !in raisedAlertIds) return RejectReason.UNKNOWN_ALERT
        val pending = pendingDecision
        if (state != RobotState.INCIDENT || pending == null || pending.id != alertId) return RejectReason.INVALID_STATE
        val decision = IncidentDecision.entries.firstOrNull { it.name == decisionName }
        if (decision == null || decision.appliesTo != pending.kind) return RejectReason.INVALID_STATE

        pendingDecision = null
        when (decision) {
            IncidentDecision.WAIT -> waitRemaining = WAIT_SECONDS
            IncidentDecision.SKIP_CHECKPOINT -> {
                mission?.let {
                    it.dwellRemaining = 0.0
                    if (!it.isLastTarget) it.targetIndex++
                }
                state = RobotState.PATROLLING
            }
            IncidentDecision.RETURN_TO_BASE -> startReturn()
            IncidentDecision.INTRUDER -> state = RobotState.PAUSED
            IncidentDecision.AUTHORIZED_EMPLOYEE, IncidentDecision.FALSE_ALARM -> state = RobotState.PATROLLING
        }
        return null
    }

    private fun updateBattery(seconds: Double) {
        val delta = when (state) {
            RobotState.DOCKED -> CHARGE_RATE
            RobotState.PATROLLING, RobotState.RETURNING -> -DRAIN_MOVING
            else -> -DRAIN_IDLE
        } * seconds
        battery = (battery + delta).coerceIn(0.0, 100.0)
    }

    private fun checkBattery() {
        if (battery >= BatteryRules.LOW) lowBatteryAlerted = false
        if (battery >= BatteryRules.CRITICAL) criticalBatteryAlerted = false
        if (state == RobotState.DOCKED) return

        if (battery < BatteryRules.CRITICAL && !criticalBatteryAlerted) {
            criticalBatteryAlerted = true
            lowBatteryAlerted = true
            raise(AlertSeverity.CRITICAL, AlertKind.CRITICAL_BATTERY, zone(), "Batería crítica: el robot regresa a la base", false)
            if (state !in NO_AUTO_RETURN) startReturn()
        } else if (battery < BatteryRules.LOW && !lowBatteryAlerted) {
            lowBatteryAlerted = true
            raise(AlertSeverity.WARNING, AlertKind.LOW_BATTERY, zone(), "Batería baja: conviene volver a la base", false)
        }
    }

    // Fail-safe del lado robot (PLAN.md, escenario 3)
    private fun checkLink() {
        if (secondsWithoutLink > LINK_LOSS_RETURN_SECONDS && state in LINK_LOSS_RETURN) {
            startReturn()
            event(EventType.CONNECTION, AlertSeverity.WARNING, "Regreso autónomo: más de 60 s sin enlace", zone())
        }
    }

    private fun patrol(seconds: Double) {
        val active = mission ?: return
        var budget = seconds
        while (budget > 0 && state == RobotState.PATROLLING) {
            if (active.dwellRemaining > 0) {
                val used = minOf(budget, active.dwellRemaining)
                active.dwellRemaining -= used
                budget -= used
                if (active.dwellRemaining <= 0) verify(active)
                continue
            }
            val target = checkpoint(active.targetId)
            budget = moveToward(target, budget) ?: return
            arrive(active)
        }
    }

    private fun arrive(active: ActiveMission) {
        when {
            active.isLastTarget -> {
                finishMission(MissionResult.COMPLETED)
                state = RobotState.DOCKED
            }
            active.targetId == Facility.BASE_ID -> active.targetIndex++
            else -> active.dwellRemaining = DWELL_SECONDS
        }
    }

    private fun verify(active: ActiveMission) {
        val point = checkpoint(active.targetId)
        checkpointsVerified++
        event(EventType.CHECKPOINT, AlertSeverity.INFO, "Punto verificado: ${point.name}", point.name)
        active.targetIndex++
    }

    private fun goBase(seconds: Double) {
        moveToward(base, seconds) ?: return
        finishMission(MissionResult.RETURNED_TO_BASE)
        state = RobotState.DOCKED
    }

    private fun waitObstacle(seconds: Double) {
        if (pendingDecision != null || waitRemaining <= 0) return
        waitRemaining -= seconds
        if (waitRemaining <= 0) {
            waitRemaining = 0.0
            state = RobotState.PATROLLING
        }
    }

    // Devuelve el tiempo sobrante si llegó al punto, o null si sigue en camino
    private fun moveToward(target: Checkpoint, seconds: Double): Double? {
        val dx = target.x - x
        val dy = target.y - y
        val distance = hypot(dx, dy)
        val reach = seconds * SPEED
        // Rumbo en grados, sentido horario desde el eje x (el eje y del plano apunta hacia abajo)
        if (distance > 0) heading = (Math.toDegrees(atan2(dy, dx)) + 360) % 360
        if (distance <= reach) {
            x = target.x
            y = target.y
            return seconds - distance / SPEED
        }
        x += dx / distance * reach
        y += dy / distance * reach
        return null
    }

    private fun progressOf(active: ActiveMission): Double {
        val ids = active.route.checkpointIds
        val from = checkpoint(ids[active.targetIndex - 1])
        val to = checkpoint(active.targetId)
        val leg = hypot(to.x - from.x, to.y - from.y)
        val fraction = if (leg == 0.0) 1.0 else (1 - hypot(to.x - x, to.y - y) / leg).coerceIn(0.0, 1.0)
        return ((active.targetIndex - 1 + fraction) / (ids.size - 1)).coerceIn(0.0, 1.0)
    }

    private fun startReturn() {
        clearIncident()
        mission?.let {
            it.frozenProgress = it.frozenProgress ?: progressOf(it)
            it.dwellRemaining = 0.0
        }
        state = RobotState.RETURNING
    }

    private fun openIncident(alert: Alert) {
        pendingDecision = alert
        waitRemaining = 0.0
        state = RobotState.INCIDENT
    }

    private fun clearIncident() {
        pendingDecision = null
        waitRemaining = 0.0
    }

    private fun finishMission(result: MissionResult) {
        val active = mission ?: return
        val text = when (result) {
            MissionResult.COMPLETED -> "completada"
            MissionResult.CANCELLED -> "cancelada"
            else -> "terminada con regreso a la base"
        }
        val severity = if (result == MissionResult.COMPLETED) AlertSeverity.INFO else AlertSeverity.WARNING
        event(EventType.MISSION, severity, "Misión ${active.id} $text: $checkpointsVerified/${active.total} puntos", null)
        mission = null
    }

    private fun raise(severity: AlertSeverity, kind: AlertKind, zone: String, message: String, requiresDecision: Boolean): Alert {
        val alert = Alert(
            id = "al-" + (++alertCount).toString().padStart(4, '0'),
            severity = severity,
            kind = kind,
            zone = zone,
            message = message,
            raisedAtMillis = clock.now(),
            requiresDecision = requiresDecision,
        )
        raisedAlertIds += alert.id
        pendingAlerts += ContractJson.encodeToString(AlertDto.serializer(), alert.toDto())
        return alert
    }

    private fun event(type: EventType, severity: AlertSeverity, message: String, zone: String?) {
        pendingEvents += EventRecord(
            timestampMillis = clock.now(),
            missionId = mission?.id,
            type = type,
            severity = severity,
            message = message,
            zone = zone,
        )
    }

    private fun zone(): String = Facility.zoneAt(x, y)

    private fun checkpoint(id: String): Checkpoint = requireNotNull(Facility.checkpoint(id)) { "Punto desconocido: $id" }

    companion object {
        const val ROBOT_ID = "go2-vigia-01"
        const val DEFAULT_ROUTE_ID = "ronda-completa"
        const val EMERGENCY_EXIT_ID = "CP7"

        const val SPEED = 0.8
        const val DWELL_SECONDS = 3.0
        const val WAIT_SECONDS = 10.0
        const val LINK_LOSS_RETURN_SECONDS = 60.0

        const val INITIAL_BATTERY = 100.0
        const val CHARGE_RATE = 0.5
        const val DRAIN_MOVING = 0.2
        const val DRAIN_IDLE = 0.02

        const val RSSI_NEAR = -45.0
        const val RSSI_FAR = -80.0
        const val LATENCY_NEAR = 40.0
        const val LATENCY_FAR = 200.0

        private val CANCELLABLE = setOf(RobotState.PATROLLING, RobotState.PAUSED, RobotState.INCIDENT, RobotState.STOPPED)
        private val NO_AUTO_RETURN = setOf(RobotState.DOCKED, RobotState.RETURNING, RobotState.STOPPED, RobotState.FAULT)
        private val LINK_LOSS_RETURN = setOf(RobotState.PATROLLING, RobotState.PAUSED, RobotState.INCIDENT)
    }
}
