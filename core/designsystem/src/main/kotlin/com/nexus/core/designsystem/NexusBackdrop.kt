package com.nexus.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nexus.core.model.BackgroundStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nexus backdrop: either the user's wallpaper or an original geometric field
 * (grid, orbit, aurora) with a very low frame cost. Motion can be disabled by the
 * user or by the night automation; blur is applied only when explicitly enabled.
 */
@Composable
fun NexusBackdrop(
    config: NexusThemeConfig,
    style: BackgroundStyle,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val phase = remember { Animatable(0f) }

    LaunchedEffect(style, config.reduceMotion) {
        if (config.reduceMotion) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        val duration = (34_000 * config.animationSpeed.coerceAtLeast(0.2f))
            .toInt()
            .coerceIn(12_000, 90_000)
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(1f, animationSpec = tween(durationMillis = duration, easing = LinearEasing))
        }
    }

    Box(modifier.fillMaxSize().background(background)) {
        val layerModifier = if (config.blurEnabled) {
            Modifier.matchParentSize().blur(22.dp)
        } else {
            Modifier.matchParentSize()
        }
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                modifier = layerModifier,
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.30f),
                            0.55f to background.copy(alpha = 0.62f),
                            1f to background.copy(alpha = 0.94f),
                        ),
                    ),
            )
        } else {
            Box(layerModifier) {
                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    drawNexusField(style, phase.value, accent, secondary)
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to background.copy(alpha = 0.25f),
                            1f to background.copy(alpha = 0.92f),
                        ),
                    ),
            )
        }
        content()
    }
}

private fun DrawScope.drawNexusField(
    style: BackgroundStyle,
    phase: Float,
    accent: Color,
    secondary: Color,
) {
    val width = size.width
    val height = size.height
    val angle = phase * 2f * Math.PI.toFloat()
    when (style) {
        BackgroundStyle.VOID -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.78f, height * 0.18f),
                    radius = width * 0.9f,
                ),
                radius = width * 0.9f,
                center = androidx.compose.ui.geometry.Offset(width * 0.78f, height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.8f),
                    radius = width * 0.8f,
                ),
                radius = width * 0.8f,
                center = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.8f),
            )
        }

        BackgroundStyle.GRID -> {
            val step = 54.dp.toPx()
            val dotRadius = 1.1.dp.toPx()
            var x = step
            while (x < width) {
                drawLine(
                    color = accent.copy(alpha = 0.055f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
            }
            var y = step
            while (y < height) {
                drawLine(
                    color = accent.copy(alpha = 0.055f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += step
            }
            var dotX = step
            while (dotX < width) {
                var dotY = step
                while (dotY < height) {
                    val pulse = (sin((dotX + dotY) * 0.01f + angle) + 1f) / 2f
                    drawCircle(
                        color = accent.copy(alpha = 0.10f + pulse * 0.12f),
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(dotX, dotY),
                    )
                    dotY += step * 2
                }
                dotX += step * 2
            }
        }

        BackgroundStyle.ORBIT -> {
            val topCenter = androidx.compose.ui.geometry.Offset(width * 0.82f, height * 0.16f)
            val lowerCenter = androidx.compose.ui.geometry.Offset(width * 0.12f, height * 0.82f)
            for (index in 1..4) {
                drawCircle(
                    color = accent.copy(alpha = 0.075f),
                    radius = index * 64.dp.toPx(),
                    center = topCenter,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            for (index in 1..3) {
                drawCircle(
                    color = secondary.copy(alpha = 0.055f),
                    radius = index * 74.dp.toPx(),
                    center = lowerCenter,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val orbitRadius = 128.dp.toPx()
            drawCircle(
                color = accent.copy(alpha = 0.75f),
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    topCenter.x + cos(angle) * orbitRadius,
                    topCenter.y + sin(angle) * orbitRadius,
                ),
            )
            val secondRadius = 148.dp.toPx()
            drawCircle(
                color = secondary.copy(alpha = 0.6f),
                radius = 2.2.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    lowerCenter.x + cos(angle + 2.1f) * secondRadius,
                    lowerCenter.y + sin(angle + 2.1f) * secondRadius,
                ),
            )
        }

        BackgroundStyle.AURORA -> {
            val drift = sin(angle) * width * 0.08f
            val blobs = listOf(
                Triple(
                    androidx.compose.ui.geometry.Offset(width * 0.2f + drift, height * 0.28f),
                    accent.copy(alpha = 0.18f),
                    width * 0.6f,
                ),
                Triple(
                    androidx.compose.ui.geometry.Offset(width * 0.85f - drift, height * 0.55f),
                    secondary.copy(alpha = 0.12f),
                    width * 0.55f,
                ),
                Triple(
                    androidx.compose.ui.geometry.Offset(width * 0.4f + drift * 0.4f, height * 0.85f),
                    accent.copy(alpha = 0.08f),
                    width * 0.7f,
                ),
            )
            blobs.forEach { (center, color, radius) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}
