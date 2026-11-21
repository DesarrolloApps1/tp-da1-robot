package com.vigia.domain.usecase

import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.Facility
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import com.vigia.domain.repository.HistoryRepository
import com.vigia.domain.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pasa al historial lo que reporta el robot: cada evento tal cual y un registro por misión
 * armado a partir de la telemetría. Solo guarda zona, hora, tipo y la clasificación del guardia.
 *
 * Los ids de misión del simulador se reinician en cada ejecución de la app, así que se guardan
 * con [sessionId] para no pisar las rondas de ejecuciones anteriores.
 */
class HistoryRecorder(
    private val robot: RobotRepository,
    private val history: HistoryRepository,
    private val sessionId: String,
) {

    // Telemetría y eventos llegan por corrutinas distintas: el lock ordena el acceso a este estado
    private val lock = Mutex()
    private var current: MissionRecord? = null
    private var lastFinished: MissionRecord? = null
    private var lastState: RobotState? = null

    // Cierres que informó el robot antes de que la telemetría mostrara el fin de la ronda
    private val reportedEnds = mutableMapOf<String, EventRecord>()

    // UNDISPATCHED: events no tiene replay, hay que estar suscripto antes de que el robot emita
    fun start(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            robot.events.collect { lock.withLock { onEvent(it) } }
        }
        closeStaleMissions()
        robot.telemetry.filterNotNull().collect { lock.withLock { onTelemetry(it) } }
    }

    private fun recordId(missionId: String): String = "$missionId@$sessionId"

    private suspend fun onEvent(event: EventRecord) {
        val id = event.missionId?.let(::recordId)
        history.addEvent(event.copy(missionId = id))
        if (id == null || event.missionResult == null) return

        // Si la ronda terminó sin enlace, la telemetría ya la cerró con un resultado deducido: se corrige
        val finished = lastFinished
        if (finished?.id == id) {
            val corrected = finished.copy(result = event.missionResult, endedAtMillis = event.timestampMillis)
            if (corrected != finished) history.upsertMission(corrected)
            lastFinished = corrected
        } else {
            reportedEnds[id] = event
        }
    }

    // Una misión que quedó en curso es de una ejecución anterior que se cerró sin terminarla
    private suspend fun closeStaleMissions() {
        history.missions.first()
            .filter { it.result == MissionResult.IN_PROGRESS }
            .forEach { history.upsertMission(it.copy(result = MissionResult.ABORTED)) }
    }

    private suspend fun onTelemetry(telemetry: RobotTelemetry) {
        val mission = telemetry.mission
        val active = current
        if (active != null && active.id != mission?.id?.let(::recordId)) {
            // Los contadores del servicio conservan los valores de la ronda hasta que empieza otra
            val counters = if (mission == null) telemetry.service else null
            // El resultado que informó el robot gana; deducirlo del estado solo sirve si no llegó
            val end = reportedEnds.remove(active.id)
            val result = end?.missionResult ?: resultFor(telemetry.state)
            save(active.finished(result, end?.timestampMillis ?: telemetry.timestampMillis, counters))
        }
        if (mission != null) save(inProgress(mission, telemetry.service, telemetry.timestampMillis))
        lastState = telemetry.state
    }

    private suspend fun save(record: MissionRecord) {
        if (record == current) return
        history.upsertMission(record)
        if (record.result == MissionResult.IN_PROGRESS) {
            current = record
        } else {
            current = null
            lastFinished = record
        }
    }

    private fun inProgress(mission: MissionProgress, service: ServiceCounters, nowMillis: Long): MissionRecord {
        val id = recordId(mission.id)
        return MissionRecord(
            id = id,
            routeId = mission.routeId,
            routeName = Facility.route(mission.routeId)?.name ?: mission.routeId,
            startedAtMillis = current?.takeIf { it.id == id }?.startedAtMillis ?: nowMillis,
            endedAtMillis = null,
            result = MissionResult.IN_PROGRESS,
            checkpointsVerified = service.checkpointsVerified,
            checkpointsTotal = mission.checkpointsTotal,
            peopleDetected = service.peopleInRestrictedZone,
            openDoors = service.openDoors,
        )
    }

    private fun MissionRecord.finished(result: MissionResult, nowMillis: Long, service: ServiceCounters?) = copy(
        endedAtMillis = nowMillis,
        result = result,
        checkpointsVerified = service?.checkpointsVerified ?: checkpointsVerified,
        peopleDetected = service?.peopleInRestrictedZone ?: peopleDetected,
        openDoors = service?.openDoors ?: openDoors,
    )

    // Cancelar deja al robot listo; volver a la base pasa por RETURNING; terminar la ronda lo deja en la base
    private fun resultFor(state: RobotState): MissionResult = when {
        state == RobotState.IDLE -> MissionResult.CANCELLED
        lastState == RobotState.RETURNING -> MissionResult.RETURNED_TO_BASE
        state == RobotState.DOCKED -> MissionResult.COMPLETED
        else -> MissionResult.ABORTED
    }
}
