package com.vigia.domain.model

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertKind {
    LOW_BATTERY,
    CRITICAL_BATTERY,
    OBSTACLE,
    PERSON_IN_RESTRICTED_ZONE,
    DOOR_OPEN,
    CONNECTION_LOST,
    FAULT,
}

data class Alert(
    val id: String,
    val severity: AlertSeverity,
    val kind: AlertKind,
    val zone: String,
    val message: String,
    val raisedAtMillis: Long,
    val requiresDecision: Boolean,
    val acknowledged: Boolean = false,
    val resolution: IncidentDecision? = null,
)
