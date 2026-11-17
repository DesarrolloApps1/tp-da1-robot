package com.vigia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.R
import com.vigia.domain.model.RobotSummary
import com.vigia.presentation.StatusTone
import com.vigia.presentation.robotselect.RobotSelectViewModel
import com.vigia.ui.components.StatusChip
import com.vigia.ui.theme.VigiaTheme

@Composable
fun RobotSelectScreen(
    onSelected: () -> Unit,
    viewModel: RobotSelectViewModel = viewModel(factory = RobotSelectViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RobotSelectContent(
        robots = state.robots,
        onSelect = { robotId ->
            viewModel.onSelect(robotId)
            onSelected()
        },
    )
}

@Composable
fun RobotSelectContent(robots: List<RobotSummary>, onSelect: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.robot_select_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Elegí el robot que vas a supervisar en la ronda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            robots.forEach { robot -> RobotItem(robot, onClick = { onSelect(robot.id) }) }
        }
    }
}

// Los robots no disponibles quedan atenuados y sin acción, con el motivo a la vista
@Composable
private fun RobotItem(robot: RobotSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = robot.available,
        modifier = Modifier.fillMaxWidth().alpha(if (robot.available) 1f else 0.55f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(robot.name, style = MaterialTheme.typography.titleMedium)
                Text(robot.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(robot.note, style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(
                text = if (robot.available) "Disponible" else "No disponible",
                tone = if (robot.available) StatusTone.OK else StatusTone.STALE,
            )
        }
    }
}

@Preview
@Composable
private fun RobotSelectPreview() {
    VigiaTheme {
        RobotSelectContent(
            robots = listOf(
                RobotSummary("go2-vigia-01", "Vigía-01", "Unitree Go2", available = true, note = "Simulado"),
                RobotSummary(
                    "go2-vigia-02",
                    "Vigía-02",
                    "Unitree Go2",
                    available = false,
                    note = "Fuera de servicio: batería en mantenimiento",
                ),
            ),
            onSelect = {},
        )
    }
}
