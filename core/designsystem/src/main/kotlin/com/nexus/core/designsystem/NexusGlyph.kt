package com.nexus.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nexus icon vocabulary: standard navigation glyphs plus original geometric symbols
 * drawn on a 24dp grid. Using our own symbols keeps the identity distinct without
 * pulling in an external icon pack.
 */
enum class NexusGlyphId {
    SEARCH,
    SETTINGS,
    APPS,
    DASHBOARD,
    CALCULATOR,
    NOTES,
    TIMER,
    CALENDAR,
    WEATHER,
    BATTERY,
    BACKUP,
    DIAGNOSTICS,
    PALETTE,
    GRID,
    SHIELD,
    AUTOMATION,
    ACCESSIBILITY,
    PLUS,
    CLOSE,
    BACK,
    FORWARD,
    DOTS,
    CHECK,
    TRASH,
    PLAY,
    PAUSE,
    REFRESH,
    DOWNLOAD,
    LOCK,
    HOME,
    EDIT,
    INFO,
}

private val materialMap: Map<NexusGlyphId, ImageVector> = mapOf(
    NexusGlyphId.SEARCH to Icons.Filled.Search,
    NexusGlyphId.SETTINGS to Icons.Filled.Settings,
    NexusGlyphId.PLUS to Icons.Filled.Add,
    NexusGlyphId.CLOSE to Icons.Filled.Close,
    NexusGlyphId.BACK to Icons.Filled.ArrowBack,
    NexusGlyphId.FORWARD to Icons.Filled.KeyboardArrowRight,
    NexusGlyphId.DOTS to Icons.Filled.MoreVert,
    NexusGlyphId.CHECK to Icons.Filled.Check,
    NexusGlyphId.TRASH to Icons.Filled.Delete,
    NexusGlyphId.PLAY to Icons.Filled.PlayArrow,
    NexusGlyphId.REFRESH to Icons.Filled.Refresh,
    NexusGlyphId.LOCK to Icons.Filled.Lock,
    NexusGlyphId.HOME to Icons.Filled.Home,
    NexusGlyphId.EDIT to Icons.Filled.Edit,
    NexusGlyphId.INFO to Icons.Filled.Info,
)

@Composable
fun NexusGlyph(
    id: NexusGlyphId,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val vector = materialMap[id]
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = tint,
        )
        return
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semanticsModifier)) {
        drawNexusGlyph(id, tint)
    }
}

private fun DrawScope.drawNexusGlyph(id: NexusGlyphId, tint: Color) {
    val unit = size.minDimension / 24f
    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.9f) {
        drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth = width * unit, cap = StrokeCap.Round)
    }
    val stroke = Stroke(width = 1.9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 2f, filled: Boolean = false) {
        drawRoundRect(
            color = tint,
            topLeft = point(left, top),
            size = Size((right - left) * unit, (bottom - top) * unit),
            cornerRadius = CornerRadius(radius * unit),
            style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke,
        )
    }
    fun circle(cx: Float, cy: Float, radius: Float, filled: Boolean = false, width: Float = 1.9f) {
        drawCircle(
            color = tint,
            radius = radius * unit,
            center = point(cx, cy),
            style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width * unit),
        )
    }

    when (id) {
        NexusGlyphId.APPS -> {
            for (x in listOf(7f, 12f, 17f)) {
                for (y in listOf(7f, 12f, 17f)) {
                    circle(x, y, 1.35f, filled = true)
                }
            }
        }

        NexusGlyphId.DASHBOARD -> {
            roundRect(3.5f, 3.5f, 10.5f, 10.5f, 1.6f)
            roundRect(13.5f, 3.5f, 20.5f, 10.5f, 1.6f)
            roundRect(3.5f, 13.5f, 10.5f, 20.5f, 1.6f)
            roundRect(13.5f, 13.5f, 20.5f, 20.5f, 1.6f)
        }

        NexusGlyphId.CALCULATOR -> {
            roundRect(5f, 2.5f, 19f, 21.5f, 2.4f)
            line(8f, 6.5f, 16f, 6.5f)
            for (x in listOf(8f, 12f, 16f)) {
                for (y in listOf(11f, 15f, 19f)) {
                    circle(x, y, 0.9f, filled = true)
                }
            }
        }

        NexusGlyphId.NOTES -> {
            roundRect(4.5f, 3f, 19.5f, 21f, 2.2f)
            line(8f, 8f, 16f, 8f)
            line(8f, 12f, 16f, 12f)
            line(8f, 16f, 13f, 16f)
        }

        NexusGlyphId.TIMER -> {
            circle(12f, 13f, 7.2f)
            line(12f, 13f, 12f, 9f, 2f)
            line(12f, 13f, 15f, 14.5f, 2f)
            line(9.5f, 2.5f, 14.5f, 2.5f, 2f)
            line(12f, 2.5f, 12f, 5f, 2f)
        }

        NexusGlyphId.CALENDAR -> {
            roundRect(3.5f, 5f, 20.5f, 21f, 2.2f)
            line(3.5f, 9f, 20.5f, 9f)
            line(8f, 2.8f, 8f, 6.2f, 2f)
            line(16f, 2.8f, 16f, 6.2f, 2f)
            circle(8f, 13f, 1f, filled = true)
            circle(12f, 13f, 1f, filled = true)
            circle(16f, 13f, 1f, filled = true)
            circle(8f, 17f, 1f, filled = true)
            circle(12f, 17f, 1f, filled = true)
        }

        NexusGlyphId.WEATHER -> {
            circle(8.5f, 8.5f, 3.2f)
            for (index in 0 until 8) {
                val angle = index * Math.PI.toFloat() / 4f
                line(
                    8.5f + cos(angle) * 5.1f,
                    8.5f + sin(angle) * 5.1f,
                    8.5f + cos(angle) * 6.3f,
                    8.5f + sin(angle) * 6.3f,
                    1.5f,
                )
            }
            drawRoundRect(
                color = tint,
                topLeft = point(8f, 12.5f),
                size = Size(11f * unit, 6f * unit),
                cornerRadius = CornerRadius(3f * unit),
                style = androidx.compose.ui.graphics.drawscope.Fill,
            )
            circle(13.5f, 18.5f, 3f, filled = true)
            circle(17f, 15.5f, 2.6f, filled = true)
        }

        NexusGlyphId.BATTERY -> {
            roundRect(2.5f, 7f, 19f, 17f, 2.4f)
            drawRoundRect(
                color = tint,
                topLeft = point(20.5f, 9.6f),
                size = Size(1.8f * unit, 4.8f * unit),
                cornerRadius = CornerRadius(0.8f * unit),
            )
            drawRoundRect(
                color = tint,
                topLeft = point(4.5f, 9f),
                size = Size(9f * unit, 6f * unit),
                cornerRadius = CornerRadius(1f * unit),
                style = androidx.compose.ui.graphics.drawscope.Fill,
            )
        }

        NexusGlyphId.BACKUP -> {
            roundRect(3.5f, 10f, 20.5f, 21f, 2.2f)
            line(12f, 3f, 12f, 14.5f, 2f)
            line(12f, 3f, 8f, 7f, 2f)
            line(12f, 3f, 16f, 7f, 2f)
            line(8f, 15f, 16f, 15f)
        }

        NexusGlyphId.DIAGNOSTICS -> {
            line(2.5f, 13f, 7f, 13f)
            line(7f, 13f, 9.5f, 6.5f)
            line(9.5f, 6.5f, 13.5f, 19f)
            line(13.5f, 19f, 16f, 13f)
            line(16f, 13f, 21.5f, 13f)
        }

        NexusGlyphId.PALETTE -> {
            circle(12f, 12f, 9f)
            circle(12f, 12f, 3.2f)
            circle(8f, 8.5f, 2.2f, filled = true)
            circle(16f, 8.5f, 2.2f, filled = true)
            circle(8f, 16f, 2.2f, filled = true)
        }

        NexusGlyphId.GRID -> {
            roundRect(3.5f, 3.5f, 20.5f, 20.5f, 2.2f)
            line(9.2f, 3.5f, 9.2f, 20.5f, 1.4f)
            line(14.8f, 3.5f, 14.8f, 20.5f, 1.4f)
            line(3.5f, 9.2f, 20.5f, 9.2f, 1.4f)
            line(3.5f, 14.8f, 20.5f, 14.8f, 1.4f)
        }

        NexusGlyphId.SHIELD -> {
            val path = Path().apply {
                moveTo(12f * unit, 2.5f * unit)
                lineTo(20f * unit, 6.2f * unit)
                lineTo(20f * unit, 12f * unit)
                quadraticBezierTo(20f * unit, 19.5f * unit, 12f * unit, 21.5f * unit)
                quadraticBezierTo(4f * unit, 19.5f * unit, 4f * unit, 12f * unit)
                lineTo(4f * unit, 6.2f * unit)
                close()
            }
            drawPath(path, tint, style = stroke)
            line(8.5f, 12f, 11f, 14.6f, 2f)
            line(11f, 14.6f, 15.8f, 9.2f, 2f)
        }

        NexusGlyphId.AUTOMATION -> {
            drawArc(
                color = tint,
                startAngle = -60f,
                sweepAngle = 230f,
                useCenter = false,
                topLeft = point(4f, 4f),
                size = Size(16f * unit, 16f * unit),
                style = stroke,
            )
            drawArc(
                color = tint,
                startAngle = 120f,
                sweepAngle = 230f,
                useCenter = false,
                topLeft = point(4f, 4f),
                size = Size(16f * unit, 16f * unit),
                style = stroke,
            )
            val arrow = Path().apply {
                moveTo(18.6f * unit, 3.4f * unit)
                lineTo(21.4f * unit, 7.4f * unit)
                lineTo(16.8f * unit, 7.8f * unit)
                close()
            }
            drawPath(arrow, tint)
            val arrow2 = Path().apply {
                moveTo(5.4f * unit, 20.6f * unit)
                lineTo(2.6f * unit, 16.6f * unit)
                lineTo(7.2f * unit, 16.2f * unit)
                close()
            }
            drawPath(arrow2, tint)
        }

        NexusGlyphId.ACCESSIBILITY -> {
            circle(12f, 5f, 2.5f)
            line(12f, 8f, 12f, 15f, 2f)
            line(5f, 10f, 19f, 10f, 2f)
            line(12f, 15f, 8f, 21f, 2f)
            line(12f, 15f, 16f, 21f, 2f)
        }

        NexusGlyphId.PAUSE -> {
            roundRect(6f, 4f, 10f, 20f, 1.5f, filled = true)
            roundRect(14f, 4f, 18f, 20f, 1.5f, filled = true)
        }

        NexusGlyphId.DOWNLOAD -> {
            line(12f, 3f, 12f, 15f, 2f)
            line(12f, 15f, 7.5f, 10.5f, 2f)
            line(12f, 15f, 16.5f, 10.5f, 2f)
            line(4f, 20f, 20f, 20f, 2f)
        }

        else -> circle(12f, 12f, 8f)
    }
}

private fun cos(value: Float): Float = kotlin.math.cos(value)

private fun sin(value: Float): Float = kotlin.math.sin(value)
