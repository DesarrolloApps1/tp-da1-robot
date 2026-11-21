package com.vigia.domain.rules

import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.RobotState

object IncidentRules {
    // Abierto mientras el robot espera en INCIDENT una decisión que el guardia todavía no tomó
    fun openIncident(state: RobotState?, alerts: List<Alert>): Alert? =
        if (state != RobotState.INCIDENT) null
        else alerts.firstOrNull { it.requiresDecision && it.resolution == null }

    // Lo que encontró la ronda en el edificio. La batería, la conexión y las fallas del robot no cuentan
    val INCIDENT_KINDS = setOf(AlertKind.OBSTACLE, AlertKind.PERSON_IN_RESTRICTED_ZONE, AlertKind.DOOR_OPEN)

    fun isIncident(event: EventRecord): Boolean = event.type == EventType.ALERT && event.alertKind in INCIDENT_KINDS
}
