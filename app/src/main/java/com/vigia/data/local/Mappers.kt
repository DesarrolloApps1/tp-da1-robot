package com.vigia.data.local

import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult

fun MissionRecord.toEntity() = MissionEntity(
    id = id,
    routeId = routeId,
    routeName = routeName,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    result = result.name,
    checkpointsVerified = checkpointsVerified,
    checkpointsTotal = checkpointsTotal,
    peopleDetected = peopleDetected,
    openDoors = openDoors,
)

fun MissionEntity.toDomain() = MissionRecord(
    id = id,
    routeId = routeId,
    routeName = routeName,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    result = MissionResult.valueOf(result),
    checkpointsVerified = checkpointsVerified,
    checkpointsTotal = checkpointsTotal,
    peopleDetected = peopleDetected,
    openDoors = openDoors,
)

fun EventRecord.toEntity() = EventEntity(
    id = id,
    timestampMillis = timestampMillis,
    missionId = missionId,
    type = type.name,
    severity = severity.name,
    message = message,
    zone = zone,
)

fun EventEntity.toDomain() = EventRecord(
    id = id,
    timestampMillis = timestampMillis,
    missionId = missionId,
    type = EventType.valueOf(type),
    severity = AlertSeverity.valueOf(severity),
    message = message,
    zone = zone,
)
