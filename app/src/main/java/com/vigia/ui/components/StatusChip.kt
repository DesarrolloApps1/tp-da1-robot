package com.vigia.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vigia.presentation.StatusTone
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme

@Composable
@ReadOnlyComposable
fun StatusTone.color(): Color = when (this) {
    StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    StatusTone.OK -> StatusColors.ok
    StatusTone.WARNING -> StatusColors.warning
    StatusTone.ERROR -> StatusColors.error
    StatusTone.STALE -> StatusColors.stale
}

// Etiqueta corta de estado; el texto siempre acompaña al color
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val color = tone.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Preview
@Composable
private fun StatusChipPreview() {
    VigiaTheme {
        Surface {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Conectado", StatusTone.OK)
                StatusChip("Señal débil", StatusTone.WARNING)
                StatusChip("Rechazada: Camino bloqueado", StatusTone.ERROR)
                StatusChip("Sin datos", StatusTone.STALE)
            }
        }
    }
}
