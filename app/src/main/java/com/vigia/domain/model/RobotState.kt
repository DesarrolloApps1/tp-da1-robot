package com.vigia.domain.model

enum class RobotState(val label: String) {
    DOCKED("En base"),
    IDLE("Listo"),
    PATROLLING("Patrullando"),
    PAUSED("En pausa"),
    INCIDENT("Incidente"),
    RETURNING("Regresando"),
    STOPPED("Detenido"),
    FAULT("Falla"),
}
