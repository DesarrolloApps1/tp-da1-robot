package com.vigia.data.sim

import com.vigia.data.contract.AlertDto
import com.vigia.data.contract.CommandAckDto
import com.vigia.data.contract.CommandDto
import com.vigia.data.contract.ContractJson
import com.vigia.data.contract.TelemetryDto
import com.vigia.data.contract.toDomain
import com.vigia.data.contract.toDto
import com.vigia.data.contract.toEpochMillis
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Clock
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotSummary
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.repository.RobotRepository
import com.vigia.domain.repository.SimulationControls
import com.vigia.domain.rules.CommandRules
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repositorio del robot simulado: intercambia JSON del contrato con [RobotSimulator]
 * como lo haría con un gateway real. El scope y el reloj se inyectan para testear con tiempo virtual.
 */
class SimulatedRobotRepository(
    private val simulator: RobotSimulator,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val tickMillis: Long = 1_000,
    private val ackDelayMillis: Long = 600,
) : RobotRepository, SimulationControls {

    // El simulador no es thread-safe: todo acceso pasa por este lock
    private val lock = Any()
    private val commandCount = AtomicInteger()

    override val robots: StateFlow<List<RobotSummary>> = MutableStateFlow(ROBOTS).asStateFlow()

    private val _telemetry = MutableStateFlow<RobotTelemetry?>(null)
    override val telemetry: StateFlow<RobotTelemetry?> = _telemetry.asStateFlow()

    private val _lastUpdateMillis = MutableStateFlow<Long?>(null)
    override val lastUpdateMillis: StateFlow<Long?> = _lastUpdateMillis.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    override val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val commands: StateFlow<List<CommandRecord>> = _commands.asStateFlow()

    private val _events = MutableSharedFlow<EventRecord>(extraBufferCapacity = 64)
    override val events: SharedFlow<EventRecord> = _events.asSharedFlow()

    private val _speedFactor = MutableStateFlow(1)
    override val speedFactor: StateFlow<Int> = _speedFactor.asStateFlow()

    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                tick()
                delay(tickMillis)
            }
        }
    }

    // Hay un solo robot simulado; Vigía-02 existe solo para mostrar el estado deshabilitado
    override fun select(robotId: String) = Unit

    override suspend fun send(type: CommandType, params: Map<String, String>): CommandRecord {
        val command = Command(
            id = "cmd-" + commandCount.incrementAndGet().toString().padStart(4, '0'),
            type = type,
            params = params,
            issuedAtMillis = clock.now(),
        )
        _commands.update { listOf(CommandRecord(command, CommandStatus.PENDING, null, null)) + it }
        // Corre en el scope del repositorio para que la orden se resuelva aunque se cancele quien la mandó
        return scope.async {
            val ack = withTimeoutOrNull(CommandRules.COMMAND_TIMEOUT_MS) { exchange(command) }
            val record = recordFor(command, ack)
            _commands.update { list -> list.map { if (it.command.id == command.id) record else it } }
            if (record.status == CommandStatus.CONFIRMED) onConfirmed(command)
            emit(commandEvent(record))
            record
        }.await()
    }

    override fun setConnected(connected: Boolean) {
        if (_connected.value == connected) return
        _connected.value = connected
        emit(
            if (connected) event(EventType.CONNECTION, AlertSeverity.INFO, "Conexión restablecida")
            else event(EventType.CONNECTION, AlertSeverity.WARNING, "Se perdió la conexión con el robot"),
        )
    }

    override fun rejectNextCommand() = synchronized(lock) { simulator.forceRejectNext() }

    override fun setBattery(percent: Int) = synchronized(lock) { simulator.setBattery(percent) }

    override fun triggerObstacle() = synchronized(lock) { simulator.obstacleAhead() }

    override fun triggerPersonInRestrictedZone() = synchronized(lock) { simulator.personInRestrictedZone() }

    override fun triggerOpenDoor() = synchronized(lock) { simulator.doorOpen() }

    override fun setSpeedFactor(factor: Int) {
        _speedFactor.value = factor.coerceAtLeast(1)
    }

    private fun tick() {
        val linkUp = _connected.value
        val snapshot = synchronized(lock) {
            simulator.advance(_speedFactor.value.toDouble(), linkUp)
            // Sin enlace no llega nada: la UI ve envejecer los últimos datos
            if (!linkUp) return
            Snapshot(simulator.telemetryJson(), simulator.drainAlerts(), simulator.drainEvents())
        }

        _telemetry.value = ContractJson.decodeFromString(TelemetryDto.serializer(), snapshot.telemetry).toDomain()
        _lastUpdateMillis.value = clock.now()

        val newAlerts = snapshot.alerts.map { ContractJson.decodeFromString(AlertDto.serializer(), it).toDomain() }
        if (newAlerts.isNotEmpty()) _alerts.update { newAlerts.reversed() + it }
        newAlerts.forEach { emit(event(EventType.ALERT, it.severity, it.message, it.zone)) }
        snapshot.events.forEach(::emit)
    }

    private suspend fun exchange(command: Command): CommandAckDto {
        if (!_connected.value) awaitCancellation()
        val json = ContractJson.encodeToString(CommandDto.serializer(), command.toDto(RobotSimulator.ROBOT_ID))
        delay(ackDelayMillis)
        // Si el enlace se cortó en el camino la orden se pierde y vence por timeout
        if (!_connected.value) awaitCancellation()
        val ackJson = synchronized(lock) { simulator.receive(json) }
        return ContractJson.decodeFromString(CommandAckDto.serializer(), ackJson)
    }

    private fun recordFor(command: Command, ack: CommandAckDto?): CommandRecord = when {
        ack == null -> CommandRecord(command, CommandStatus.NO_RESPONSE, null, null)
        ack.result == CommandAckDto.ACCEPTED ->
            CommandRecord(command, CommandStatus.CONFIRMED, null, ack.respondedAt.toEpochMillis())
        else -> CommandRecord(
            command,
            CommandStatus.REJECTED,
            RejectReason.entries.firstOrNull { it.name == ack.reason },
            ack.respondedAt.toEpochMillis(),
        )
    }

    private fun onConfirmed(command: Command) {
        val alertId = command.params["alertId"] ?: return
        when (command.type) {
            CommandType.ACK_ALERT -> updateAlert(alertId) { it.copy(acknowledged = true) }
            CommandType.RESOLVE_INCIDENT -> {
                val decision = IncidentDecision.entries.firstOrNull { it.name == command.params["decision"] } ?: return
                updateAlert(alertId) { it.copy(acknowledged = true, resolution = decision) }
                val zone = _alerts.value.firstOrNull { it.id == alertId }?.zone
                emit(event(EventType.DECISION, AlertSeverity.INFO, "Decisión del guardia: ${decision.label}", zone))
            }
            else -> Unit
        }
    }

    private fun updateAlert(alertId: String, change: (Alert) -> Alert) {
        _alerts.update { list -> list.map { if (it.id == alertId) change(it) else it } }
    }

    private fun commandEvent(record: CommandRecord): EventRecord {
        val reason = record.reason?.let { " (${it.label})" }.orEmpty()
        val severity = if (record.status == CommandStatus.CONFIRMED) AlertSeverity.INFO else AlertSeverity.WARNING
        return event(EventType.COMMAND, severity, "${record.command.type.label}: ${record.status.label}$reason")
    }

    private fun event(type: EventType, severity: AlertSeverity, message: String, zone: String? = null) = EventRecord(
        timestampMillis = clock.now(),
        missionId = _telemetry.value?.mission?.id,
        type = type,
        severity = severity,
        message = message,
        zone = zone,
    )

    private fun emit(event: EventRecord) {
        _events.tryEmit(event)
    }

    private class Snapshot(val telemetry: String, val alerts: List<String>, val events: List<EventRecord>)

    companion object {
        val ROBOTS = listOf(
            RobotSummary(RobotSimulator.ROBOT_ID, "Vigía-01", "Unitree Go2", available = true, note = "Simulado"),
            RobotSummary(
                "go2-vigia-02",
                "Vigía-02",
                "Unitree Go2",
                available = false,
                note = "Fuera de servicio: batería en mantenimiento",
            ),
        )
    }
}
