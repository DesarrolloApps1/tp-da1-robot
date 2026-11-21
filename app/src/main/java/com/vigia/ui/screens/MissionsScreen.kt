package com.vigia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import com.vigia.domain.rules.CommandAvailability
import com.vigia.presentation.missions.MissionsUiState
import com.vigia.presentation.missions.MissionsViewModel
import com.vigia.presentation.missions.RouteItemUi
import com.vigia.presentation.missions.missionsState
import com.vigia.ui.components.Notice
import com.vigia.ui.components.StatusChip
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme

@Composable
fun MissionsScreen(
    onStarted: () -> Unit,
    viewModel: MissionsViewModel = viewModel(factory = MissionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Con el inicio confirmado, el guardia sigue la ronda desde Supervisión
    LaunchedEffect(state.startConfirmed) {
        if (state.startConfirmed) {
            viewModel.onStartHandled()
            onStarted()
        }
    }
    MissionsContent(state, onStart = viewModel::onStart)
}

@Composable
fun MissionsContent(state: MissionsUiState, onStart: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Rondas disponibles", style = MaterialTheme.typography.titleMedium) }
        item { StartAvailability(state.start) }
        items(state.routes, key = { it.id }) { route ->
            RouteCard(route, canStart = state.start.enabled, onStart = { onStart(route.id) })
        }
    }
}

// El motivo va una sola vez arriba: vale para las tres rutas
@Composable
private fun StartAvailability(start: CommandAvailability) {
    start.reason?.let { Notice("No se puede iniciar: $it", StatusColors.warning) }
    start.warning?.let { Notice(it, StatusColors.warning) }
}

@Composable
private fun RouteCard(route: RouteItemUi, canStart: Boolean, onStart: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${route.checkpointCount} puntos de control · ${route.lengthMeters} m · " +
                    "Duración estimada: ${route.durationText}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                route.checkpointNames.joinToString(" → ", prefix = "Base → ", postfix = " → Base"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = canStart) { Text("Iniciar") }
                route.lastStatus?.let { StatusChip(it.label, it.tone) }
            }
        }
    }
}

@Preview(heightDp = 800)
@Composable
private fun MissionsPreview() {
    VigiaTheme {
        Surface {
            MissionsContent(
                missionsState(
                    RobotTelemetry(
                        robotId = "go2-vigia-01",
                        seq = 1,
                        timestampMillis = 0,
                        rssi = -60,
                        latencyMs = 80,
                        batteryPercent = 24,
                        minutesLeft = 20,
                        charging = true,
                        state = RobotState.DOCKED,
                        mission = null,
                        pose = Pose(3.0, 3.0, 0.0, "Central"),
                        speed = 0.0,
                        moving = false,
                        service = ServiceCounters(0, 0, 0),
                    ),
                    lastUpdateMillis = 0,
                    alerts = emptyList(),
                    commands = emptyList(),
                    now = 0,
                ),
                onStart = {},
            )
        }
    }
}
