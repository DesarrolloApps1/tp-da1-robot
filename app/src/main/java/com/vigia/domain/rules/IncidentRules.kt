package com.vigia.domain.rules

import com.vigia.domain.model.Alert
import com.vigia.domain.model.RobotState

object IncidentRules {
    // Abierto mientras el robot espera en INCIDENT una decisión que el guardia todavía no tomó
    fun openIncident(state: RobotState?, alerts: List<Alert>): Alert? =
        if (state != RobotState.INCIDENT) null
        else alerts.firstOrNull { it.requiresDecision && it.resolution == null }
}
