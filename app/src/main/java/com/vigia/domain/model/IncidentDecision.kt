package com.vigia.domain.model

// Opciones del guardia ante un incidente, según el tipo de alerta
enum class IncidentDecision(val label: String, val appliesTo: AlertKind) {
    WAIT("Esperar", AlertKind.OBSTACLE),
    SKIP_CHECKPOINT("Saltear el punto", AlertKind.OBSTACLE),
    RETURN_TO_BASE("Volver a la base", AlertKind.OBSTACLE),
    INTRUDER("Intruso", AlertKind.PERSON_IN_RESTRICTED_ZONE),
    AUTHORIZED_EMPLOYEE("Empleado autorizado", AlertKind.PERSON_IN_RESTRICTED_ZONE),
    FALSE_ALARM("Falsa alarma", AlertKind.PERSON_IN_RESTRICTED_ZONE);

    companion object {
        fun optionsFor(kind: AlertKind): List<IncidentDecision> = entries.filter { it.appliesTo == kind }
    }
}
