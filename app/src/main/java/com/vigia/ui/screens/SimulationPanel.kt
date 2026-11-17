package com.vigia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.presentation.simulation.SimulationUiState
import com.vigia.presentation.simulation.SimulationViewModel
import com.vigia.ui.theme.VigiaTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationPanel(
    onDismiss: () -> Unit,
    viewModel: SimulationViewModel = viewModel(factory = SimulationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SimulationPanelContent(
            state,
            SimulationActions(
                onConnectedChange = viewModel::onConnectedChange,
                onRejectNext = viewModel::onRejectNext,
                onBattery = viewModel::onBattery,
                onObstacle = viewModel::onObstacle,
                onPerson = viewModel::onPerson,
                onOpenDoor = viewModel::onOpenDoor,
                onSpeed = viewModel::onSpeed,
            ),
        )
    }
}

class SimulationActions(
    val onConnectedChange: (Boolean) -> Unit = {},
    val onRejectNext: () -> Unit = {},
    val onBattery: (Int) -> Unit = {},
    val onObstacle: () -> Unit = {},
    val onPerson: () -> Unit = {},
    val onOpenDoor: () -> Unit = {},
    val onSpeed: (Int) -> Unit = {},
)

@Composable
fun SimulationPanelContent(state: SimulationUiState, actions: SimulationActions) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Panel de simulación (solo demo)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fuerza situaciones del robot simulado. No forma parte de la app del guardia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "Robot: ${state.robotStateLabel ?: "sin datos"} · Batería: ${state.batteryPercent?.let { "$it %" } ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { ConnectionControl(state.connected, actions.onConnectedChange) }
        item {
            ControlRow("Rechazar próxima orden", "La próxima orden que mandes vuelve rechazada") {
                OutlinedButton(onClick = actions.onRejectNext) { Text("Rechazar") }
            }
        }
        item { HorizontalDivider() }
        item { BatteryControl(state.batteryPercent, actions.onBattery) }
        item { HorizontalDivider() }
        item { EventControls(state, actions) }
        item { HorizontalDivider() }
        item { SpeedControl(state.speedFactor, actions.onSpeed) }
    }
}

@Composable
private fun ConnectionControl(connected: Boolean, onChange: (Boolean) -> Unit) {
    ControlRow(
        "Conexión con el robot",
        if (connected) "Apagala para simular la pérdida de enlace" else "Sin enlace: la telemetría no llega",
    ) {
        Switch(checked = connected, onCheckedChange = onChange)
    }
}

@Composable
private fun BatteryControl(percent: Int?, onBattery: (Int) -> Unit) {
    // Muestra lo elegido en el slider hasta que la telemetría trae el porcentaje nuevo
    var draft by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(percent) { draft = null }
    val shown = draft ?: percent?.toFloat() ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Batería: ${shown.roundToInt()} %", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = shown,
            onValueChange = { draft = it },
            onValueChangeFinished = { draft?.let { onBattery(it.roundToInt()) } },
            valueRange = 0f..100f,
            enabled = percent != null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 14).forEach { quick ->
                OutlinedButton(onClick = { onBattery(quick) }, enabled = percent != null) { Text("$quick %") }
            }
        }
    }
}

@Composable
private fun EventControls(state: SimulationUiState, actions: SimulationActions) {
    val needsPatrol = if (state.canTriggerIncident) null else "Solo con el robot patrullando"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eventos", style = MaterialTheme.typography.titleSmall)
        ControlRow("Obstáculo en el pasillo", needsPatrol) {
            OutlinedButton(onClick = actions.onObstacle, enabled = state.canTriggerIncident) { Text("Disparar") }
        }
        ControlRow("Persona en zona restringida", needsPatrol) {
            OutlinedButton(onClick = actions.onPerson, enabled = state.canTriggerIncident) { Text("Disparar") }
        }
        ControlRow("Puerta abierta", "Salida de emergencia lateral") {
            OutlinedButton(onClick = actions.onOpenDoor) { Text("Disparar") }
        }
    }
}

@Composable
private fun SpeedControl(speedFactor: Int, onSpeed: (Int) -> Unit) {
    ControlRow("Velocidad de la simulación", "x5 acelera la ronda para la demo") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimulationViewModel.SPEEDS.forEach { factor ->
                FilterChip(
                    selected = speedFactor == factor,
                    onClick = { onSpeed(factor) },
                    label = { Text("x$factor") },
                )
            }
        }
    }
}

@Composable
private fun ControlRow(title: String, supporting: String?, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        control()
    }
}

@Preview(heightDp = 900)
@Composable
private fun SimulationPanelPreview() {
    VigiaTheme {
        Surface {
            SimulationPanelContent(
                SimulationUiState(batteryPercent = 64, robotStateLabel = "Patrullando", canTriggerIncident = true),
                SimulationActions(),
            )
        }
    }
}
