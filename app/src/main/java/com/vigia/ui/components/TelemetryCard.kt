package com.vigia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vigia.presentation.StatusTone
import com.vigia.ui.theme.VigiaTheme

// Tarjeta de un dato de telemetría: título, valor grande y detalle opcional
@Composable
fun TelemetryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: StatusTone = StatusTone.NEUTRAL,
    progress: Float? = null,
) {
    val color = tone.color()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = color)
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview
@Composable
private fun TelemetryCardPreview() {
    VigiaTheme {
        Row(Modifier.height(IntrinsicSize.Min).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TelemetryCard(
                "Batería",
                "22 %",
                Modifier.weight(1f).fillMaxHeight(),
                detail = "26 min restantes",
                tone = StatusTone.WARNING,
            )
            TelemetryCard(
                "Misión",
                "Ronda completa",
                Modifier.weight(1f).fillMaxHeight(),
                detail = "42 % · hacia CP4",
                progress = 0.42f,
            )
        }
    }
}
