package com.vigia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import com.vigia.presentation.history.EventItemUi
import com.vigia.presentation.history.HistoryFilter
import com.vigia.presentation.history.HistoryUiState
import com.vigia.presentation.history.HistoryViewModel
import com.vigia.presentation.history.MissionItemUi
import com.vigia.presentation.history.historyState
import com.vigia.ui.components.ConfirmDialog
import com.vigia.ui.components.StatusChip
import com.vigia.ui.components.color
import com.vigia.ui.theme.VigiaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone = ZoneId.systemDefault()
private val startFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(zone)
private val eventFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state,
        onToggle = viewModel::onToggle,
        onFilter = viewModel::onFilter,
        onClear = viewModel::onClear,
    )
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onToggle: (String) -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    onClear: () -> Unit,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rondas guardadas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { confirmingClear = true },
                    enabled = state.hasHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Borrar historial") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        if (state.missions.isEmpty()) {
            item {
                Text(
                    if (state.filter == HistoryFilter.ALL) "Todavía no hay rondas guardadas." else "No hay rondas con incidentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.missions, key = { it.id }) { mission ->
            val expanded = mission.id == state.expandedId
            MissionCard(mission, timeline = if (expanded) state.timeline else null, onClick = { onToggle(mission.id) })
        }
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = "¿Borrar el historial?",
            message = "Se eliminan todas las rondas y sus eventos guardados en este dispositivo. No se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = {
                onClear()
                confirmingClear = false
            },
            onDismiss = { confirmingClear = false },
            destructive = true,
        )
    }
}

// Tocar la tarjeta abre o cierra la línea de tiempo de la ronda
@Composable
private fun MissionCard(mission: MissionItemUi, timeline: List<EventItemUi>?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mission.routeName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(mission.resultLabel, mission.resultTone)
            }
            val started = startFormat.format(Instant.ofEpochMilli(mission.startedAtMillis))
            Text(
                listOfNotNull(mission.displayId, started, mission.durationText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Puntos verificados ${mission.verifiedText} · Personas ${mission.peopleDetected} · " +
                    "Puertas abiertas ${mission.openDoors}",
                style = MaterialTheme.typography.bodyMedium,
            )
            timeline?.let { Timeline(it) }
        }
    }
}

@Composable
private fun Timeline(events: List<EventItemUi>) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    if (events.isEmpty()) {
        Text("Sin eventos guardados para esta ronda.", style = MaterialTheme.typography.bodySmall)
        return
    }
    events.forEach { event ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                eventFormat.format(Instant.ofEpochMilli(event.timestampMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(event.typeLabel, style = MaterialTheme.typography.labelMedium, color = event.tone.color())
                Text(event.message, style = MaterialTheme.typography.bodySmall)
                event.zone?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val previewMissions = listOf(
    MissionRecord("m-0002@abc", "zonas-criticas", "Zonas críticas", 120_000, null, MissionResult.IN_PROGRESS, 1, 2, 1, 0),
    MissionRecord("m-0001@abc", "ronda-completa", "Ronda completa", 0, 121_000, MissionResult.COMPLETED, 8, 8, 0, 1),
    MissionRecord("m-0003@old", "ronda-perimetral", "Ronda perimetral", 0, null, MissionResult.ABORTED, 2, 3, 0, 0),
)

private val previewEvents = listOf(
    EventRecord(3, 60_000, "m-0001@abc", EventType.ALERT, AlertSeverity.WARNING, "Puerta abierta detectada", "Salida de emergencia"),
    EventRecord(2, 10_000, "m-0001@abc", EventType.CHECKPOINT, AlertSeverity.INFO, "Punto verificado: Hall de entrada", "Hall de entrada"),
    EventRecord(1, 0, "m-0001@abc", EventType.MISSION, AlertSeverity.INFO, "Misión m-0001 iniciada: Ronda completa", null),
)

@Preview(heightDp = 900)
@Composable
private fun HistoryPreview() {
    VigiaTheme {
        Surface {
            HistoryContent(
                historyState(previewMissions, setOf("m-0001@abc"), HistoryFilter.ALL, "m-0001@abc", previewEvents),
                onToggle = {},
                onFilter = {},
                onClear = {},
            )
        }
    }
}
