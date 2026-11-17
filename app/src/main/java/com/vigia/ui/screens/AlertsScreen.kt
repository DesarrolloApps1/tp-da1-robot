package com.vigia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RobotState
import com.vigia.presentation.alerts.AlertItemUi
import com.vigia.presentation.alerts.AlertsUiState
import com.vigia.presentation.alerts.AlertsViewModel
import com.vigia.presentation.alerts.alertsState
import com.vigia.ui.components.IncidentDecisionDialog
import com.vigia.ui.components.StatusChip
import com.vigia.ui.components.color
import com.vigia.ui.theme.VigiaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val alertTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertsContent(state, onAcknowledge = viewModel::onAcknowledge, onDecide = viewModel::onDecide)
}

@Composable
fun AlertsContent(
    state: AlertsUiState,
    onAcknowledge: (String) -> Unit,
    onDecide: (String, IncidentDecision) -> Unit,
) {
    var deciding by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Activas (${state.active.size})") }
        if (state.active.isEmpty()) {
            item { EmptyText("No hay alertas activas.") }
        }
        items(state.active, key = { it.alert.id }) { item ->
            AlertCard(item, onAcknowledge = { onAcknowledge(item.alert.id) }, onDecide = { deciding = item.alert.id })
        }
        if (state.past.isNotEmpty()) {
            item { SectionTitle("Reconocidas y resueltas") }
            items(state.past, key = { it.alert.id }) { item ->
                AlertCard(item, onAcknowledge = {}, onDecide = {}, modifier = Modifier.alpha(0.6f))
            }
        }
    }

    deciding?.let { state.decision(it) }?.let { ui ->
        IncidentDecisionDialog(
            ui = ui,
            onDecide = { onDecide(ui.alert.id, it) },
            onDismiss = { deciding = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// El borde lleva el color de la severidad; el texto de la severidad acompaña siempre al color
@Composable
private fun AlertCard(
    item: AlertItemUi,
    onAcknowledge: () -> Unit,
    onDecide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alert = item.alert
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, item.severityTone.color().copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(item.severityLabel, item.severityTone)
                Text(item.kindLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${alert.zone} · ${alertTimeFormat.format(Instant.ofEpochMilli(alert.raisedAtMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.resolutionLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.awaitingDecision) {
                    Button(
                        onClick = onDecide,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("Decidir") }
                }
                if (!alert.acknowledged) {
                    OutlinedButton(onClick = onAcknowledge, enabled = item.canAcknowledge) { Text("Reconocer") }
                }
                item.ackStatus?.let { StatusChip(it.label, it.tone) }
            }
        }
    }
}

private val previewAlerts = listOf(
    Alert(
        "al-0004",
        AlertSeverity.CRITICAL,
        AlertKind.PERSON_IN_RESTRICTED_ZONE,
        "Laboratorio",
        "Persona detectada en zona restringida",
        3_000,
        requiresDecision = true,
    ),
    Alert("al-0003", AlertSeverity.WARNING, AlertKind.DOOR_OPEN, "Salida de emergencia", "Puerta abierta detectada", 2_000, false),
    Alert(
        "al-0002",
        AlertSeverity.CRITICAL,
        AlertKind.OBSTACLE,
        "Pasillo B",
        "Obstáculo bloquea el paso",
        1_000,
        requiresDecision = true,
        acknowledged = true,
        resolution = IncidentDecision.SKIP_CHECKPOINT,
    ),
)

@Preview(heightDp = 900)
@Composable
private fun AlertsPreview() {
    VigiaTheme {
        Surface {
            AlertsContent(
                alertsState(RobotState.INCIDENT, previewAlerts, emptyList()),
                onAcknowledge = {},
                onDecide = { _, _ -> },
            )
        }
    }
}
