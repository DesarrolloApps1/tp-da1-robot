package com.vigia.data.local

import androidx.room.withTransaction
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(private val database: VigiaDatabase) : HistoryRepository {

    private val missionDao = database.missionDao()
    private val eventDao = database.eventDao()

    override val missions: Flow<List<MissionRecord>> =
        missionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override val events: Flow<List<EventRecord>> =
        eventDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun eventsFor(missionId: String): Flow<List<EventRecord>> =
        eventDao.observeForMission(missionId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertMission(record: MissionRecord) = missionDao.upsert(record.toEntity())

    override suspend fun addEvent(event: EventRecord) = eventDao.insert(event.toEntity())

    override suspend fun clear() = database.withTransaction {
        eventDao.clear()
        missionDao.clear()
    }
}
