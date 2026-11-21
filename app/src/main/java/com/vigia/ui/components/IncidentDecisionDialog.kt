package com.vigia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RobotState
import com.vigia.presentation.DecisionOptionUi
import com.vigia.presentation.IncidentDecisionUi
import com.vigia.presentation.incidentDecisions
import com.vigia.presentation.label
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme

// Decisión del guardia ante un incidente; se elige primero y se envía aparte para evitar toques accidentales
@Composable
fun IncidentDecisionDialog(
    ui: IncidentDecisionUi,
    onDecide: (IncidentDecision) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(ui.alert.id) { mutableStateOf<IncidentDecision?>(null) }
    val resolution = ui.resolution

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusColors.error) },
        title = { Text(ui.alert.kind.label()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${ui.alert.message} · ${ui.alert.zone}", style = MaterialTheme.typography.bodyMedium)
                ui.robotSaid?.let {
                    Text(
                        "El robot dijo: \"$it\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (resolution != null) {
                    Text("Decisión registrada: ${resolution.label}", style = MaterialTheme.typography.titleSmall)
                } else {
                    DecisionOptions(ui.options, selected, enabled = ui.canDecide, onSelect = { selected = it })
                }
                ui.lastStatus?.let { status ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(status.label, status.tone)
                        ui.sentDecision?.let { Text(it.label, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (resolution == null && !ui.canDecide && !ui.pending) {
                    Text(
                        "El robot ya no espera esta decisión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.privacyNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (resolution == null) {
                TextButton(
                    onClick = { selected?.let(onDecide) },
                    enabled = ui.canDecide && selected != null,
                ) { Text("Enviar decisión") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (resolution != null) "Cerrar" else "Más tarde") }
        },
    )
}

@Composable
private fun DecisionOptions(
    options: List<DecisionOptionUi>,
    selected: IncidentDecision?,
    enabled: Boolean,
    onSelect: (IncidentDecision) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.decision == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.decision) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option.decision == selected, onClick = null, enabled = enabled)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(option.decision.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        option.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun IncidentDecisionDialogPreview() {
    val alert = Alert(
        "al-0004",
        AlertSeverity.CRITICAL,
        AlertKind.PERSON_IN_RESTRICTED_ZONE,
        "Laboratorio",
        "Persona detectada en zona restringida",
        0,
        requiresDecision = true,
    )
    VigiaTheme {
        IncidentDecisionDialog(
            ui = incidentDecisions(RobotState.INCIDENT, listOf(alert), emptyList()).getValue(alert.id),
            onDecide = {},
            onDismiss = {},
        )
    }
}
