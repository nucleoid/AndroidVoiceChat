package ai.openclaw.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import ai.openclaw.android.voice.VoiceChatManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated voice orb that reflects the current voice chat state:
 * - IDLE: Static orb with subtle glow
 * - LISTENING: Pulsing rings expanding outward
 * - PROCESSING: Spinning/rotating animation
 * - SPEAKING: Waveform-like rings with varying amplitude
 * - PAUSED: Dimmed with slow pulse
 */
@Composable
fun VoiceOrb(
    state: VoiceChatManager.State,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    // Pulse animation for listening
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceChatManager.State.LISTENING -> 1200
                    VoiceChatManager.State.PAUSED -> 3000
                    else -> 1500
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )

    Canvas(modifier = modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.minDimension / 4

        when (state) {
            VoiceChatManager.State.IDLE -> {
                drawIdleOrb(center, baseRadius, primary, surface)
            }
            VoiceChatManager.State.LISTENING -> {
                drawListeningOrb(center, baseRadius, pulseScale, ringAlpha, primary, tertiary)
            }
            VoiceChatManager.State.PROCESSING -> {
                drawProcessingOrb(center, baseRadius, rotation, primary, tertiary)
            }
            VoiceChatManager.State.SPEAKING -> {
                drawSpeakingOrb(center, baseRadius, wavePhase, ringAlpha, primary, tertiary)
            }
            VoiceChatManager.State.PAUSED -> {
                drawPausedOrb(center, baseRadius, pulseScale, primary.copy(alpha = 0.5f), surface)
            }
        }
    }
}

private fun DrawScope.drawIdleOrb(
    center: Offset, radius: Float, primary: Color, surface: Color,
) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.2f), Color.Transparent),
            center = center,
            radius = radius * 1.8f,
        ),
        radius = radius * 1.8f,
        center = center,
    )
    // Core orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.8f), primary.copy(alpha = 0.4f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawListeningOrb(
    center: Offset, radius: Float, scale: Float, alpha: Float, primary: Color, accent: Color,
) {
    // Expanding rings
    for (i in 0..2) {
        val ringScale = scale + i * 0.15f
        drawCircle(
            color = primary.copy(alpha = (alpha - i * 0.2f).coerceAtLeast(0.1f)),
            radius = radius * ringScale,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
    }
    // Core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent, primary),
            center = center,
            radius = radius * 0.8f,
        ),
        radius = radius * 0.8f,
        center = center,
    )
}

private fun DrawScope.drawProcessingOrb(
    center: Offset, radius: Float, rotation: Float, primary: Color, accent: Color,
) {
    // Spinning dots
    val dotCount = 8
    for (i in 0 until dotCount) {
        val angle = Math.toRadians((rotation + i * 360.0 / dotCount).toDouble())
        val dotRadius = radius * 0.08f * (1f + (i.toFloat() / dotCount) * 0.5f)
        val dotCenter = Offset(
            center.x + (radius * 0.9f * cos(angle)).toFloat(),
            center.y + (radius * 0.9f * sin(angle)).toFloat(),
        )
        drawCircle(
            color = primary.copy(alpha = 0.3f + (i.toFloat() / dotCount) * 0.7f),
            radius = dotRadius,
            center = dotCenter,
        )
    }
    // Core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.6f), primary.copy(alpha = 0.3f)),
            center = center,
            radius = radius * 0.6f,
        ),
        radius = radius * 0.6f,
        center = center,
    )
}

private fun DrawScope.drawSpeakingOrb(
    center: Offset, radius: Float, phase: Float, alpha: Float, primary: Color, accent: Color,
) {
    // Waveform rings with varying amplitude
    for (i in 0..3) {
        val waveAmplitude = radius * 0.1f * sin(phase + i * 1.5f).toFloat()
        val ringRadius = radius * (0.8f + i * 0.15f) + waveAmplitude
        drawCircle(
            color = accent.copy(alpha = (alpha - i * 0.15f).coerceAtLeast(0.1f)),
            radius = ringRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f - i),
        )
    }
    // Core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent, primary),
            center = center,
            radius = radius * 0.7f,
        ),
        radius = radius * 0.7f,
        center = center,
    )
}

private fun DrawScope.drawPausedOrb(
    center: Offset, radius: Float, scale: Float, primary: Color, surface: Color,
) {
    // Slow pulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.3f), Color.Transparent),
            center = center,
            radius = radius * scale,
        ),
        radius = radius * scale,
        center = center,
    )
    // Dimmed core
    drawCircle(
        color = surface.copy(alpha = 0.6f),
        radius = radius * 0.6f,
        center = center,
    )
    // Pause bars
    val barWidth = radius * 0.12f
    val barHeight = radius * 0.5f
    val barGap = radius * 0.2f
    drawRect(
        color = primary,
        topLeft = Offset(center.x - barGap - barWidth, center.y - barHeight / 2),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
    )
    drawRect(
        color = primary,
        topLeft = Offset(center.x + barGap, center.y - barHeight / 2),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
    )
}
