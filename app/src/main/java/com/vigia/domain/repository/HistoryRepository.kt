package com.vigia.domain.repository

import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.MissionRecord
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val missions: Flow<List<MissionRecord>>
    val events: Flow<List<EventRecord>>

    fun eventsFor(missionId: String): Flow<List<EventRecord>>

    suspend fun upsertMission(record: MissionRecord)

    suspend fun addEvent(event: EventRecord)

    suspend fun clear()
}
