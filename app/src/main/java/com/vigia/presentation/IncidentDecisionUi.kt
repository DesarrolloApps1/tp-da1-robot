package com.vigia.presentation

import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RobotState
import com.vigia.domain.rules.IncidentRules

const val ROBOT_RESTRICTED_ZONE_LINE = "Zona restringida. Seguridad fue notificada."
const val PRIVACY_NOTE = "No se guardan imágenes; se registra zona, hora y tu clasificación"

private const val ALERT_ID = "alertId"
private const val DECISION = "decision"

data class DecisionOptionUi(val decision: IncidentDecision, val explanation: String)

// Sigue existiendo después de resolver, para que el diálogo muestre la confirmación
data class IncidentDecisionUi(
    val alert: Alert,
    val options: List<DecisionOptionUi>,
    val robotSaid: String?,
    val privacyNote: String?,
    val lastStatus: CommandStatusUi?,
    val sentDecision: IncidentDecision?,
    val pending: Boolean,
    val canDecide: Boolean,
) {
    val resolution: IncidentDecision? get() = alert.resolution
}

fun incidentDecisions(
    state: RobotState?,
    alerts: List<Alert>,
    commands: List<CommandRecord>,
): Map<String, IncidentDecisionUi> {
    val openId = IncidentRules.openIncident(state, alerts)?.id
    return alerts.filter { it.requiresDecision }.associate { alert ->
        val last = commands.latestFor(CommandType.RESOLVE_INCIDENT, alert.id)
        val person = alert.kind == AlertKind.PERSON_IN_RESTRICTED_ZONE
        val pending = last?.status == CommandStatus.PENDING
        alert.id to IncidentDecisionUi(
            alert = alert,
            options = IncidentDecision.optionsFor(alert.kind).map { DecisionOptionUi(it, it.explanation()) },
            robotSaid = if (person) ROBOT_RESTRICTED_ZONE_LINE else null,
            privacyNote = if (person) PRIVACY_NOTE else null,
            lastStatus = last?.toStatusUi(),
            sentDecision = last?.command?.params?.get(DECISION)?.let { name ->
                IncidentDecision.entries.firstOrNull { it.name == name }
            },
            pending = pending,
            canDecide = alert.id == openId && !pending,
        )
    }
}

// La lista de órdenes viene con la más reciente primero
fun List<CommandRecord>.latestFor(type: CommandType, alertId: String): CommandRecord? =
    firstOrNull { it.command.type == type && it.command.params[ALERT_ID] == alertId }

fun ackAlertParams(alertId: String): Map<String, String> = mapOf(ALERT_ID to alertId)

fun resolveIncidentParams(alertId: String, decision: IncidentDecision): Map<String, String> =
    mapOf(ALERT_ID to alertId, DECISION to decision.name)

private fun IncidentDecision.explanation(): String = when (this) {
    IncidentDecision.WAIT -> "El robot espera a que se libere el paso y sigue la ronda."
    IncidentDecision.SKIP_CHECKPOINT -> "Deja este punto sin verificar y sigue con el siguiente."
    IncidentDecision.RETURN_TO_BASE -> "Interrumpe la ronda y vuelve a la base."
    IncidentDecision.INTRUDER -> "Se activa el procedimiento de seguridad; el robot queda en pausa."
    IncidentDecision.AUTHORIZED_EMPLOYEE -> "Se registra la presencia y el robot sigue la ronda."
    IncidentDecision.FALSE_ALARM -> "Queda registrada para ajustar la ruta; el robot sigue."
}
