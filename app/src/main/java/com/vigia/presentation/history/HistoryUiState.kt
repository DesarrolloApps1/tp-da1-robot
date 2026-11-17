package com.vigia.presentation.history

import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import com.vigia.presentation.StatusTone
import com.vigia.presentation.formatDuration
import com.vigia.presentation.tone

enum class HistoryFilter(val label: String) { ALL("Todas"), WITH_INCIDENTS("Con incidentes") }

data class MissionItemUi(
    // Id guardado, con la sesión; es el que se usa para buscar los eventos
    val id: String,
    val displayId: String,
    val routeName: String,
    val startedAtMillis: Long,
    val durationText: String?,
    val resultLabel: String,
    val resultTone: StatusTone,
    val verifiedText: String,
    val peopleDetected: Int,
    val openDoors: Int,
    val hasIncidents: Boolean,
)

data class EventItemUi(
    val id: Long,
    val timestampMillis: Long,
    val typeLabel: String,
    val message: String,
    val zone: String?,
    val tone: StatusTone,
)

data class HistoryUiState(
    val missions: List<MissionItemUi> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val expandedId: String? = null,
    // Eventos de la misión abierta, del más viejo al más nuevo
    val timeline: List<EventItemUi> = emptyList(),
    val hasHistory: Boolean = false,
)

/**
 * @param missions lo más nuevo primero, como lo devuelve el repositorio
 * @param incidentMissionIds misiones con al menos una alerta durante la ronda
 * @param timeline eventos de [expandedId], lo más nuevo primero
 */
fun historyState(
    missions: List<MissionRecord>,
    incidentMissionIds: Set<String>,
    filter: HistoryFilter,
    expandedId: String?,
    timeline: List<EventRecord>,
): HistoryUiState {
    val items = missions.map { it.toUi(it.id in incidentMissionIds) }
    return HistoryUiState(
        missions = if (filter == HistoryFilter.WITH_INCIDENTS) items.filter { it.hasIncidents } else items,
        filter = filter,
        expandedId = expandedId,
        timeline = timeline.asReversed().map { it.toUi() },
        hasHistory = missions.isNotEmpty(),
    )
}

private fun MissionRecord.toUi(hasIncidents: Boolean): MissionItemUi {
    // Sin hora de fin: la app se cerró con la ronda en curso y no se sabe cuánto duró
    val interrupted = result == MissionResult.ABORTED && endedAtMillis == null
    return MissionItemUi(
        id = id,
        displayId = id.substringBefore('@'),
        routeName = routeName,
        startedAtMillis = startedAtMillis,
        durationText = endedAtMillis?.let { formatDuration(((it - startedAtMillis) / 1_000).coerceAtLeast(0)) },
        resultLabel = if (interrupted) "Interrumpida" else result.label(),
        resultTone = result.tone(),
        verifiedText = "$checkpointsVerified/$checkpointsTotal",
        peopleDetected = peopleDetected,
        openDoors = openDoors,
        hasIncidents = hasIncidents,
    )
}

private fun EventRecord.toUi() = EventItemUi(
    id = id,
    timestampMillis = timestampMillis,
    typeLabel = type.label(),
    message = message,
    zone = zone,
    tone = severity.tone(),
)

private fun MissionResult.label(): String = when (this) {
    MissionResult.IN_PROGRESS -> "En curso"
    MissionResult.COMPLETED -> "Completada"
    MissionResult.CANCELLED -> "Cancelada"
    MissionResult.RETURNED_TO_BASE -> "Vuelta a la base"
    MissionResult.ABORTED -> "Abortada"
}

private fun MissionResult.tone(): StatusTone = when (this) {
    MissionResult.IN_PROGRESS -> StatusTone.NEUTRAL
    MissionResult.COMPLETED -> StatusTone.OK
    MissionResult.CANCELLED, MissionResult.RETURNED_TO_BASE -> StatusTone.WARNING
    MissionResult.ABORTED -> StatusTone.ERROR
}

private fun EventType.label(): String = when (this) {
    EventType.MISSION -> "Misión"
    EventType.CHECKPOINT -> "Punto de control"
    EventType.ALERT -> "Alerta"
    EventType.DECISION -> "Decisión"
    EventType.COMMAND -> "Orden"
    EventType.CONNECTION -> "Conexión"
}
