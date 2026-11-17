package com.vigia.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vigia.domain.rules.CommandRules
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme
import kotlinx.coroutines.launch

const val STOP_DISCLAIMER = "Detención simulada por software. No reemplaza la parada de emergencia física."

// Detención operativa: hay que mantenerlo apretado para evitar toques accidentales
@Composable
fun HoldToStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Long = CommandRules.STOP_HOLD_MS,
    warning: String? = null,
    status: (@Composable () -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnStop by rememberUpdatedState(onStop)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(72.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Detención operativa. Mantener apretado 1,5 segundos"
                }
                .pointerInput(holdMillis) {
                    detectTapGestures(
                        onPress = {
                            val hold = scope.launch {
                                progress.animateTo(1f, tween(holdMillis.toInt(), easing = LinearEasing))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnStop()
                            }
                            tryAwaitRelease()
                            hold.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = StatusColors.error.copy(alpha = 0.3f),
                strokeWidth = 4.dp,
            )
            Surface(Modifier.fillMaxSize().padding(8.dp), shape = CircleShape, color = StatusColors.error) {
                Box(contentAlignment = Alignment.Center) {
                    Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Mantené apretado 1,5 s para detener", style = MaterialTheme.typography.titleSmall)
            Text(STOP_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = StatusColors.warning)
            status?.invoke()
        }
    }
}

@Preview
@Composable
private fun HoldToStopButtonPreview() {
    VigiaTheme {
        Surface { HoldToStopButton(onStop = {}, Modifier.padding(12.dp), warning = CommandRules.STALE_WARNING) }
    }
}
