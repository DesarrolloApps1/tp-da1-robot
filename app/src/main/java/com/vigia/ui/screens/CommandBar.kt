package com.vigia.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.RobotState
import com.vigia.presentation.supervision.CommandUi
import com.vigia.presentation.supervision.SupervisionUiState
import com.vigia.ui.components.HoldToStopButton
import com.vigia.ui.components.StatusChip
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme

private enum class SlotStyle { PRIMARY, DANGER, TONAL }

// Órdenes de la misión arriba y la detención aparte, separada y en rojo
@Composable
fun CommandBar(
    commands: Map<CommandType, CommandUi>,
    state: RobotState?,
    canChooseRoute: Boolean,
    onCommand: (CommandType) -> Unit,
    onChooseRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val slot = Modifier.weight(1f)
                when {
                    // Iniciar se hace desde Misiones; acá solo se navega hasta ahí
                    canChooseRoute -> CommandSlot(
                        "Elegir ronda",
                        commands.getValue(CommandType.START_MISSION),
                        slot,
                        enabled = true,
                        onClick = onChooseRoute,
                    )
                    state == RobotState.PAUSED -> CommandSlot("Reanudar", commands.getValue(CommandType.RESUME_MISSION), slot) {
                        onCommand(CommandType.RESUME_MISSION)
                    }
                    else -> CommandSlot("Pausar", commands.getValue(CommandType.PAUSE_MISSION), slot) {
                        onCommand(CommandType.PAUSE_MISSION)
                    }
                }
                CommandSlot("Cancelar", commands.getValue(CommandType.CANCEL_MISSION), slot, SlotStyle.DANGER) {
                    onCommand(CommandType.CANCEL_MISSION)
                }
                CommandSlot("Volver a la base", commands.getValue(CommandType.RETURN_TO_BASE), slot, SlotStyle.TONAL) {
                    onCommand(CommandType.RETURN_TO_BASE)
                }
            }
            HorizontalDivider()
            val stop = commands.getValue(CommandType.OPERATIONAL_STOP)
            HoldToStopButton(
                onStop = { onCommand(CommandType.OPERATIONAL_STOP) },
                warning = stop.availability.warning,
                status = stop.lastStatus?.let { status -> { StatusChip(status.label, status.tone) } },
            )
        }
    }
}

// Botón con el motivo si está deshabilitado, la advertencia si la hay y el estado de la última orden
@Composable
private fun CommandSlot(
    label: String,
    ui: CommandUi,
    modifier: Modifier,
    style: SlotStyle = SlotStyle.PRIMARY,
    enabled: Boolean = ui.availability.enabled,
    onClick: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val buttonModifier = Modifier.fillMaxWidth()
        val padding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
        val content: @Composable RowScope.() -> Unit = { Text(label, textAlign = TextAlign.Center) }
        when (style) {
            SlotStyle.PRIMARY -> Button(onClick, buttonModifier, enabled, contentPadding = padding, content = content)
            SlotStyle.DANGER -> OutlinedButton(
                onClick,
                buttonModifier,
                enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusColors.error),
                border = BorderStroke(1.dp, if (enabled) StatusColors.error else MaterialTheme.colorScheme.outline),
                contentPadding = padding,
                content = content,
            )
            SlotStyle.TONAL -> FilledTonalButton(
                onClick,
                buttonModifier,
                enabled,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = padding,
                content = content,
            )
        }
        val availability = ui.availability
        val small = MaterialTheme.typography.bodySmall
        when {
            !availability.enabled && availability.reason != null ->
                Text(availability.reason, style = small, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            availability.warning != null ->
                Text(availability.warning, style = small, color = StatusColors.warning, textAlign = TextAlign.Center)
        }
        ui.lastStatus?.let { StatusChip(it.label, it.tone) }
    }
}

@Preview(widthDp = 380)
@Composable
private fun CommandBarPreview() {
    val state = SupervisionUiState.Initial
    VigiaTheme {
        CommandBar(state.commands, state.state, state.canChooseRoute, onCommand = {}, onChooseRoute = {})
    }
}
