package com.vigia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import com.vigia.domain.rules.BatteryLevel
import com.vigia.domain.rules.CommandRules
import com.vigia.presentation.supervision.CommandUi
import com.vigia.presentation.supervision.SupervisionUiState
import com.vigia.presentation.supervision.SupervisionViewModel
import com.vigia.presentation.supervision.supervisionState
import com.vigia.ui.components.ConfirmDialog
import com.vigia.ui.components.FacilityMap
import com.vigia.ui.components.StatusChip
import com.vigia.ui.components.TelemetryCard
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val NO_VALUE = "-"
private val spanish = Locale.forLanguageTag("es-AR")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SupervisionScreen(
    onChooseRoute: () -> Unit,
    viewModel: SupervisionViewModel = viewModel(factory = SupervisionViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SupervisionContent(state, onCommand = { viewModel.onCommand(it) }, onChooseRoute = onChooseRoute)
}

@Composable
fun SupervisionContent(state: SupervisionUiState, onCommand: (CommandType) -> Unit, onChooseRoute: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf<CommandType?>(null) }
    val request: (CommandType) -> Unit = { type ->
        if (CommandRules.requiresConfirmation(type)) confirming = type else onCommand(type)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.stale) StaleBanner(state.ageSeconds)
            ConnectionRow(state)
            state.openIncident?.let { IncidentCard(it) }
            state.batteryWarning?.let { level ->
                BatteryWarningCard(level, state.command(CommandType.RETURN_TO_BASE)) { request(CommandType.RETURN_TO_BASE) }
            }
            FacilityMap(state.map)
            TelemetryGrid(state)
        }
        CommandBar(state.commands, state.state, state.canChooseRoute, onCommand = request, onChooseRoute = onChooseRoute)
    }

    confirming?.let { type ->
        val close = { confirming = null }
        when (type) {
            CommandType.CANCEL_MISSION -> ConfirmDialog(
                title = "¿Cancelar la misión?",
                message = "El robot deja la ronda y queda listo donde está. Los puntos ya verificados quedan en el historial.",
                confirmLabel = "Cancelar misión",
                onConfirm = { onCommand(type); close() },
                onDismiss = close,
                destructive = true,
            )
            else -> ConfirmDialog(
                title = "¿Volver a la base?",
                message = "El robot interrumpe lo que está haciendo y regresa a la base de carga.",
                confirmLabel = "Volver a la base",
                onConfirm = { onCommand(type); close() },
                onDismiss = close,
            )
        }
    }
}

@Composable
private fun StaleBanner(ageSeconds: Long?) {
    val text = ageSeconds?.let { "Sin datos hace $it s. Las órdenes pueden no llegar" }
        ?: "Sin datos del robot. Las órdenes pueden no llegar"
    Notice(text, StatusColors.error, background = StatusColors.stale.copy(alpha = 0.2f))
}

@Composable
private fun ConnectionRow(state: SupervisionUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(state.connectionLabel, state.connectionTone)
        val telemetry = state.telemetry
        if (telemetry != null && !state.stale) {
            Text(
                "Latencia ${telemetry.latencyMs} ms · ${telemetry.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// PR 7 suma la decisión desde acá; por ahora solo se avisa
@Composable
private fun IncidentCard(alert: Alert) {
    Notice("Incidente: ${alert.message} (${alert.zone}). Esperando tu decisión.", StatusColors.error)
}

// Escenario 2: aviso de batería con acceso directo a volver a la base
@Composable
private fun BatteryWarningCard(level: BatteryLevel, returnUi: CommandUi, onReturn: () -> Unit) {
    val critical = level == BatteryLevel.CRITICAL
    val color = if (critical) StatusColors.error else StatusColors.warning
    val text = if (critical) {
        "Batería crítica: el robot vuelve a la base y no se puede reanudar."
    } else {
        "Batería baja: conviene volver a la base."
    }
    Notice(text, color) {
        Button(
            onClick = onReturn,
            enabled = returnUi.availability.enabled,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MaterialTheme.colorScheme.background),
        ) { Text("Volver a la base") }
        returnUi.availability.reason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(
    text: String,
    color: Color,
    background: Color = color.copy(alpha = 0.12f),
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = BorderStroke(1.dp, color),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            actions?.invoke()
        }
    }
}

@Composable
private fun TelemetryGrid(state: SupervisionUiState) {
    val telemetry = state.telemetry
    val mission = state.mission
    val service = state.service
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            TelemetryCard(
                "Batería",
                telemetry?.let { "${it.batteryPercent} %" } ?: NO_VALUE,
                m,
                detail = telemetry?.let { if (it.charging) "Cargando" else "${it.minutesLeft} min restantes" },
                tone = state.batteryTone,
            )
        },
        { m -> TelemetryCard("Estado", state.state?.label ?: NO_VALUE, m, tone = state.stateTone) },
        { m ->
            TelemetryCard(
                "Misión",
                mission?.routeName ?: "Sin misión activa",
                m,
                detail = mission?.let { mi ->
                    listOfNotNull("${mi.progressPercent} %", mi.currentCheckpointName?.let { "hacia $it" }).joinToString(" · ")
                },
                tone = state.dataTone,
                progress = mission?.let { it.progressPercent / 100f },
            )
        },
        { m ->
            TelemetryCard(
                "Ubicación",
                state.pose?.zone ?: NO_VALUE,
                m,
                detail = state.pose?.let { "x ${decimal(it.x)} m · y ${decimal(it.y)} m" },
                tone = state.dataTone,
            )
        },
        { m ->
            TelemetryCard(
                "Movimiento",
                telemetry?.let { "${decimal(it.speed)} m/s" } ?: NO_VALUE,
                m,
                detail = telemetry?.let { "Rumbo ${it.pose.heading.roundToInt()}°" },
                tone = state.dataTone,
            )
        },
        { m ->
            TelemetryCard(
                "Última actualización",
                state.ageText,
                m,
                detail = state.lastUpdateMillis?.let { clockFormat.format(Instant.ofEpochMilli(it)) },
                tone = state.dataTone,
            )
        },
        { m ->
            TelemetryCard(
                "Puntos verificados",
                mission?.let { "${it.checkpointsVerified}/${it.checkpointsTotal}" }
                    ?: service?.checkpointsVerified?.toString()
                    ?: NO_VALUE,
                m,
                detail = if (mission == null && service != null) "Última ronda" else null,
                tone = state.dataTone,
            )
        },
        { m ->
            TelemetryCard(
                "Personas en zona restringida",
                service?.peopleInRestrictedZone?.toString() ?: NO_VALUE,
                m,
                tone = state.peopleTone,
            )
        },
        { m -> TelemetryCard("Puertas abiertas", service?.openDoors?.toString() ?: NO_VALUE, m, tone = state.doorsTone) },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun decimal(value: Double): String = String.format(spanish, "%.1f", value)

private val previewTelemetry = RobotTelemetry(
    robotId = "go2-vigia-01",
    seq = 120,
    timestampMillis = 0,
    rssi = -62,
    latencyMs = 85,
    batteryPercent = 64,
    minutesLeft = 72,
    charging = false,
    state = RobotState.PATROLLING,
    mission = MissionProgress("m-0007", "ronda-completa", 0.42, "CP4", 3, 8),
    pose = Pose(26.0, 4.4, 350.0, "Laboratorio"),
    speed = 0.8,
    moving = true,
    service = ServiceCounters(3, 0, 1),
)

@Preview(heightDp = 1400)
@Composable
private fun SupervisionPreview() {
    VigiaTheme {
        Surface {
            SupervisionContent(
                supervisionState(previewTelemetry, 10_000, emptyList(), emptyList(), now = 11_000),
                onCommand = {},
                onChooseRoute = {},
            )
        }
    }
}

@Preview(heightDp = 1400)
@Composable
private fun SupervisionStaleLowBatteryPreview() {
    val telemetry = previewTelemetry.copy(state = RobotState.INCIDENT, batteryPercent = 22, rssi = -78)
    val alert = Alert("al-0003", AlertSeverity.CRITICAL, AlertKind.OBSTACLE, "Pasillo B", "Obstáculo bloquea el paso", 0, true)
    VigiaTheme {
        Surface {
            SupervisionContent(
                supervisionState(telemetry, 10_000, listOf(alert), emptyList(), now = 22_000),
                onCommand = {},
                onChooseRoute = {},
            )
        }
    }
}
