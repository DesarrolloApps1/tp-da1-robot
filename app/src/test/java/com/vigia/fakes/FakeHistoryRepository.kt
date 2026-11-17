package com.vigia.fakes

import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// Historial en memoria, lo más nuevo primero; upserts guarda cada escritura en orden
class FakeHistoryRepository(initial: List<MissionRecord> = emptyList()) : HistoryRepository {
    override val missions = MutableStateFlow(initial)
    override val events = MutableStateFlow<List<EventRecord>>(emptyList())
    val upserts = mutableListOf<MissionRecord>()

    override fun eventsFor(missionId: String): Flow<List<EventRecord>> =
        events.map { list -> list.filter { it.missionId == missionId } }

    override suspend fun upsertMission(record: MissionRecord) {
        upserts += record
        missions.value = listOf(record) + missions.value.filterNot { it.id == record.id }
    }

    override suspend fun addEvent(event: EventRecord) {
        events.value = listOf(event.copy(id = events.value.size + 1L)) + events.value
    }

    override suspend fun clear() {
        missions.value = emptyList()
        events.value = emptyList()
    }
}
