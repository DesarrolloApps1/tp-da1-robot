package com.vigia.domain.model

enum class EventType { MISSION, CHECKPOINT, ALERT, DECISION, COMMAND, CONNECTION }

data class EventRecord(
    val id: Long = 0,
    val timestampMillis: Long,
    val missionId: String?,
    val type: EventType,
    val severity: AlertSeverity,
    val message: String,
    val zone: String?,
)
