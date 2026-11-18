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
    // Solo en eventos ALERT: qué alerta fue, para saber si cuenta como incidente
    val alertKind: AlertKind? = null,
    // Solo en el evento MISSION que cierra la ronda: cómo terminó según el robot
    val missionResult: MissionResult? = null,
)
