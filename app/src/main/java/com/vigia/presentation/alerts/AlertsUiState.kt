package com.vigia.presentation.alerts

import com.vigia.domain.model.Alert
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.RobotState
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.IncidentDecisionUi
import com.vigia.presentation.StatusTone
import com.vigia.presentation.incidentDecisions
import com.vigia.presentation.label
import com.vigia.presentation.latestFor
import com.vigia.presentation.tone
import com.vigia.presentation.toStatusUi

data class AlertItemUi(
    val alert: Alert,
    val kindLabel: String,
    val severityLabel: String,
    val severityTone: StatusTone,
    val ackStatus: CommandStatusUi?,
    val canAcknowledge: Boolean,
    // Solo el incidente que el robot está esperando
    val awaitingDecision: Boolean,
    val resolutionLabel: String?,
    val decision: IncidentDecisionUi?,
)

data class AlertsUiState(
    val active: List<AlertItemUi> = emptyList(),
    val past: List<AlertItemUi> = emptyList(),
) {
    fun decision(alertId: String): IncidentDecisionUi? =
        (active + past).firstOrNull { it.alert.id == alertId }?.decision
}

fun alertsState(state: RobotState?, alerts: List<Alert>, commands: List<CommandRecord>): AlertsUiState {
    val decisions = incidentDecisions(state, alerts, commands)
    val items = alerts.sortedByDescending { it.raisedAtMillis }.map { alert ->
        val ack = commands.latestFor(CommandType.ACK_ALERT, alert.id)
        val decision = decisions[alert.id]
        AlertItemUi(
            alert = alert,
            kindLabel = alert.kind.label(),
            severityLabel = alert.severity.label(),
            severityTone = alert.severity.tone(),
            ackStatus = ack?.toStatusUi(),
            canAcknowledge = !alert.acknowledged && ack?.status != CommandStatus.PENDING,
            awaitingDecision = decision?.canDecide == true || decision?.pending == true,
            resolutionLabel = alert.resolution?.let { "Decisión: ${it.label}" }
                ?: if (alert.acknowledged) "Reconocida" else null,
            decision = decision,
        )
    }
    val (active, past) = items.partition { !it.alert.acknowledged || it.awaitingDecision }
    return AlertsUiState(active, past)
}
