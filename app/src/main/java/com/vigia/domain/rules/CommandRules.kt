package com.vigia.domain.rules

import com.vigia.domain.model.CommandType
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry

data class CommandContext(
    val telemetry: RobotTelemetry?,
    val stale: Boolean,
    val openIncident: Boolean,
    val pendingTypes: Set<CommandType>,
    val hasUnacknowledgedAlert: Boolean,
)

data class CommandAvailability(
    val enabled: Boolean,
    val reason: String?,
    val warning: String?,
)

// Habilitación de órdenes según la tabla de la sección 5 de PLAN.md
object CommandRules {
    const val COMMAND_TIMEOUT_MS = 5_000L
    const val STOP_HOLD_MS = 1_500L

    const val STALE_WARNING = "Sin datos recientes: la orden puede no llegar"

    fun availability(type: CommandType, ctx: CommandContext): CommandAvailability {
        if (type in ctx.pendingTypes) return disabled("Hay una orden igual pendiente")
        if (type == CommandType.OPERATIONAL_STOP) return enabled(ctx)

        val telemetry = ctx.telemetry ?: return disabled("Sin datos del robot")
        val state = telemetry.state
        val battery = telemetry.batteryPercent

        val reason = when (type) {
            CommandType.START_MISSION -> when {
                ctx.stale -> "Sin datos recientes del robot"
                state != RobotState.DOCKED && state != RobotState.IDLE -> "El robot tiene que estar en base o listo"
                !BatteryRules.canStart(battery) -> "Batería por debajo del ${BatteryRules.MIN_TO_START} %"
                ctx.openIncident -> "Hay un incidente abierto"
                else -> null
            }
            CommandType.PAUSE_MISSION ->
                if (state != RobotState.PATROLLING) "Solo se puede pausar durante la patrulla" else null
            CommandType.RESUME_MISSION -> when {
                state != RobotState.PAUSED -> "La misión no está en pausa"
                !BatteryRules.canResume(battery) -> "Batería por debajo del ${BatteryRules.MIN_TO_RESUME} %"
                ctx.openIncident -> "Hay un incidente abierto"
                else -> null
            }
            CommandType.CANCEL_MISSION ->
                if (state !in CANCELLABLE) "No hay una misión en curso" else null
            CommandType.RETURN_TO_BASE -> when (state) {
                RobotState.DOCKED -> "El robot ya está en la base"
                RobotState.RETURNING -> "El robot ya está regresando"
                else -> null
            }
            CommandType.ACK_ALERT ->
                if (!ctx.hasUnacknowledgedAlert) "No hay alertas sin reconocer" else null
            CommandType.RESOLVE_INCIDENT ->
                if (!ctx.openIncident) "No hay incidentes abiertos" else null
            CommandType.OPERATIONAL_STOP -> null
        }
        return if (reason != null) disabled(reason) else enabled(ctx)
    }

    fun requiresConfirmation(type: CommandType): Boolean =
        type == CommandType.CANCEL_MISSION || type == CommandType.RETURN_TO_BASE

    fun requiresHold(type: CommandType): Boolean = type == CommandType.OPERATIONAL_STOP

    private val CANCELLABLE = setOf(RobotState.PATROLLING, RobotState.PAUSED, RobotState.INCIDENT)

    private fun enabled(ctx: CommandContext) =
        CommandAvailability(enabled = true, reason = null, warning = if (ctx.stale) STALE_WARNING else null)

    private fun disabled(reason: String) = CommandAvailability(enabled = false, reason = reason, warning = null)
}
