package com.vigia.presentation

import com.vigia.domain.model.Alert
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.rules.CommandContext
import com.vigia.domain.rules.IncidentRules

// Lo que CommandRules necesita saber, armado igual en todas las pantallas que mandan órdenes
fun commandContext(
    telemetry: RobotTelemetry?,
    stale: Boolean,
    alerts: List<Alert>,
    commands: List<CommandRecord>,
) = CommandContext(
    telemetry = telemetry,
    stale = stale,
    openIncident = IncidentRules.openIncident(telemetry?.state, alerts) != null,
    pendingTypes = commands.filter { it.status == CommandStatus.PENDING }.mapTo(mutableSetOf()) { it.command.type },
    hasUnacknowledgedAlert = alerts.any { !it.acknowledged },
)
