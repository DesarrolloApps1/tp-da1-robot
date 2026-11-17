package com.vigia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<MissionEntity>>

    @Upsert
    suspend fun upsert(mission: MissionEntity)

    @Query("DELETE FROM missions")
    suspend fun clear()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestampMillis DESC, id DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE missionId = :missionId ORDER BY timestampMillis DESC, id DESC")
    fun observeForMission(missionId: String): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun clear()
}
