package com.vigia.presentation

import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity

fun AlertKind.label(): String = when (this) {
    AlertKind.LOW_BATTERY -> "Batería baja"
    AlertKind.CRITICAL_BATTERY -> "Batería crítica"
    AlertKind.OBSTACLE -> "Obstáculo en el camino"
    AlertKind.PERSON_IN_RESTRICTED_ZONE -> "Persona en zona restringida"
    AlertKind.DOOR_OPEN -> "Puerta abierta"
    AlertKind.CONNECTION_LOST -> "Conexión perdida"
    AlertKind.FAULT -> "Falla del robot"
}

fun AlertSeverity.label(): String = when (this) {
    AlertSeverity.INFO -> "Informativa"
    AlertSeverity.WARNING -> "Advertencia"
    AlertSeverity.CRITICAL -> "Crítica"
}

fun AlertSeverity.tone(): StatusTone = when (this) {
    AlertSeverity.INFO -> StatusTone.NEUTRAL
    AlertSeverity.WARNING -> StatusTone.WARNING
    AlertSeverity.CRITICAL -> StatusTone.ERROR
}
