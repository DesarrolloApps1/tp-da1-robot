package com.vigia.domain.model

enum class CommandType(val label: String) {
    START_MISSION("Iniciar misión"),
    PAUSE_MISSION("Pausar"),
    RESUME_MISSION("Reanudar"),
    CANCEL_MISSION("Cancelar misión"),
    RETURN_TO_BASE("Regresar a punto seguro"),
    OPERATIONAL_STOP("Detención operativa"),
    ACK_ALERT("Reconocer alerta"),
    RESOLVE_INCIDENT("Resolver incidente"),
}

enum class CommandStatus(val label: String) {
    PENDING("Pendiente"),
    CONFIRMED("Confirmada"),
    REJECTED("Rechazada"),
    NO_RESPONSE("Sin respuesta"),
}

enum class RejectReason(val label: String) {
    BATTERY_TOO_LOW("Batería insuficiente"),
    ROBOT_BUSY("El robot está ocupado"),
    INVALID_STATE("Estado no válido para la orden"),
    PATH_BLOCKED("Camino bloqueado"),
    UNKNOWN_ALERT("Alerta desconocida"),
    FORCED("Rechazo forzado desde la simulación"),
}

data class Command(
    val id: String,
    val type: CommandType,
    val params: Map<String, String>,
    val issuedAtMillis: Long,
)

data class CommandRecord(
    val command: Command,
    val status: CommandStatus,
    val reason: RejectReason?,
    val respondedAtMillis: Long?,
)
