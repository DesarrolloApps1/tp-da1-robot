package com.vigia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.domain.model.Facility
import com.vigia.domain.model.Pose
import com.vigia.presentation.FacilityMapState
import com.vigia.ui.theme.Palette
import com.vigia.ui.theme.StatusColors
import com.vigia.ui.theme.VigiaTheme
import kotlin.math.cos
import kotlin.math.sin

private class MapColors(
    val line: Color,
    val label: Color,
    val restricted: Color,
    val route: Color,
    val verified: Color,
    val pending: Color,
    val robot: Color,
)

// Plano de la planta baja dibujado a escala; todo en gris cuando los datos son antiguos
@Composable
fun FacilityMap(state: FacilityMapState, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val grey = StatusColors.stale
    val colors = if (state.stale) {
        MapColors(Palette.Line, grey, grey, grey, grey, grey, grey)
    } else {
        MapColors(
            line = Palette.Line,
            label = Palette.Muted,
            restricted = StatusColors.error,
            route = MaterialTheme.colorScheme.primary,
            verified = StatusColors.ok,
            pending = Palette.Ink,
            robot = MaterialTheme.colorScheme.primary,
        )
    }
    val description = state.robot?.let { "Plano de la planta baja. Robot en ${it.zone}" }
        ?: "Plano de la planta baja. Sin posición del robot"

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio((Facility.WIDTH_METERS / Facility.HEIGHT_METERS).toFloat())
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        val scale = size.width / Facility.WIDTH_METERS.toFloat()
        drawZones(scale, colors, textMeasurer)
        drawRoute(state, scale, colors)
        drawCheckpoints(state, scale, colors, textMeasurer)
        state.robot?.let { drawRobot(it, scale, colors.robot) }
    }
}

private fun point(x: Double, y: Double, scale: Float) = Offset(x.toFloat() * scale, y.toFloat() * scale)

private fun DrawScope.drawZones(scale: Float, colors: MapColors, textMeasurer: TextMeasurer) {
    val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    Facility.zones.forEach { zone ->
        val topLeft = point(zone.left, zone.top, scale)
        val size = Size(((zone.right - zone.left) * scale).toFloat(), ((zone.bottom - zone.top) * scale).toFloat())
        if (zone.restricted) {
            drawRect(colors.restricted.copy(alpha = 0.12f), topLeft, size)
            drawRect(colors.restricted, topLeft, size, style = Stroke(1.5.dp.toPx(), pathEffect = dashes))
        } else {
            drawRect(colors.line, topLeft, size, style = Stroke(1.dp.toPx()))
        }
        val label = textMeasurer.measure(
            zone.name,
            TextStyle(color = if (zone.restricted) colors.restricted else colors.label, fontSize = 8.sp),
            constraints = Constraints(maxWidth = (size.width - 4.dp.toPx()).toInt().coerceAtLeast(0)),
        )
        drawText(label, topLeft = topLeft + Offset(2.dp.toPx(), 1.dp.toPx()))
    }
}

private fun DrawScope.drawRoute(state: FacilityMapState, scale: Float, colors: MapColors) {
    val points = state.routeId?.let(Facility::route)?.checkpointIds
        ?.mapNotNull(Facility::checkpoint)
        ?.map { point(it.x, it.y, scale) }
        ?: return
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, colors.route.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// El rótulo de CP7 pisa el nombre de "Salida de emergencia", así que va debajo del punto
private val LABELS_BELOW = setOf("CP7")

private fun DrawScope.drawCheckpoints(state: FacilityMapState, scale: Float, colors: MapColors, textMeasurer: TextMeasurer) {
    val routeIds = state.routeId?.let(Facility::route)?.checkpointIds.orEmpty().toSet()
    val radius = 4.dp.toPx()
    Facility.checkpoints.forEach { checkpoint ->
        val center = point(checkpoint.x, checkpoint.y, scale)
        val onRoute = checkpoint.id in routeIds
        when {
            checkpoint.id == Facility.BASE_ID -> drawRect(
                colors.route,
                center - Offset(radius, radius),
                Size(radius * 2, radius * 2),
            )
            checkpoint.id in state.verifiedIds -> drawCircle(colors.verified, radius, center)
            onRoute -> drawCircle(colors.pending, radius, center, style = Stroke(1.5.dp.toPx()))
            else -> drawCircle(colors.label.copy(alpha = 0.5f), radius * 0.7f, center)
        }
        if (checkpoint.id == state.currentCheckpointId) {
            drawCircle(colors.route, radius * 2, center, style = Stroke(1.5.dp.toPx()))
        }
        val label = textMeasurer.measure(checkpoint.id, TextStyle(color = colors.label, fontSize = 8.sp))
        val below = if (checkpoint.id in LABELS_BELOW) 8.dp.toPx() else 0f
        drawText(label, topLeft = center + Offset(radius + 1.dp.toPx(), below - label.size.height / 2f))
    }
}

// Rumbo en grados, sentido horario desde el eje x, igual que el eje y del plano (hacia abajo)
private fun DrawScope.drawRobot(pose: Pose, scale: Float, color: Color) {
    val center = point(pose.x, pose.y, scale)
    val radians = Math.toRadians(pose.heading)
    val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
    val normal = Offset(-direction.y, direction.x)
    val tip = center + direction * 18.dp.toPx()
    val head = 5.dp.toPx()

    drawLine(color, center, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        val back = tip - direction * head
        lineTo((back + normal * head * 0.6f).x, (back + normal * head * 0.6f).y)
        lineTo((back - normal * head * 0.6f).x, (back - normal * head * 0.6f).y)
        close()
    }
    drawPath(arrow, color)
    drawCircle(color.copy(alpha = 0.25f), 12.dp.toPx(), center)
    drawCircle(color, 6.dp.toPx(), center)
}

@Preview(widthDp = 380)
@Composable
private fun FacilityMapPreview() {
    VigiaTheme {
        FacilityMap(
            FacilityMapState(
                routeId = "ronda-completa",
                verifiedIds = setOf("CP1", "CP2", "CP3"),
                currentCheckpointId = "CP4",
                robot = Pose(26.0, 4.4, 350.0, "Laboratorio"),
            ),
            Modifier.padding(8.dp),
        )
    }
}

@Preview(widthDp = 380)
@Composable
private fun FacilityMapStalePreview() {
    VigiaTheme {
        FacilityMap(
            FacilityMapState(
                routeId = "zonas-criticas",
                verifiedIds = setOf("CP4"),
                currentCheckpointId = "CP6",
                robot = Pose(30.0, 10.0, 90.0, "Biblioteca"),
                stale = true,
            ),
            Modifier.padding(8.dp),
        )
    }
}
