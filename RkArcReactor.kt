package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RkAccentGreen
import com.example.ui.theme.RkAccentOrange
import com.example.ui.theme.RkAccentPurple
import com.example.ui.theme.RkCyan
import com.example.ui.theme.RkCyanDark
import com.example.ui.theme.RkDarkNavy
import com.example.ui.theme.RkGlowCyan

@Composable
fun RkArcReactor(
    state: AssistantState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rk_reactor")

    val isListening = state == AssistantState.LISTENING
    val isThinking = state == AssistantState.THINKING
    val isSpeaking = state == AssistantState.SPEAKING

    // Continuous rotation for outer tech ring
    val ringRotationSpeed = when (state) {
        AssistantState.LISTENING -> 3500
        AssistantState.THINKING -> 2000
        AssistantState.SPEAKING -> 4000
        AssistantState.IDLE -> 12000
    }

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ringRotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    // Reverse rotation for inner accents
    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 2500 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_rotation"
    )

    // Idle and speaking breathing pulse
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 600 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val currentScale = when (state) {
        AssistantState.LISTENING -> (1f + rmsLevel * 0.28f).coerceIn(1f, 1.4f)
        AssistantState.SPEAKING -> idlePulse
        AssistantState.THINKING -> 1.02f
        AssistantState.IDLE -> idlePulse
    }

    val primaryColor = when (state) {
        AssistantState.LISTENING -> RkAccentGreen
        AssistantState.THINKING -> RkAccentPurple
        AssistantState.SPEAKING -> RkCyan
        AssistantState.IDLE -> RkCyan
    }

    Box(
        modifier = modifier
            .size(220.dp)
            .testTag("arc_reactor_orb"),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Canvas Drawings
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(currentScale)
        ) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f - 16.dp.toPx()

            // 1. Ambient Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isListening || isThinking || isSpeaking) 0.5f else 0.18f),
                        primaryColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.35f
                ),
                radius = baseRadius * 1.35f,
                center = centerOffset
            )

            // 2. Outer segmented tech ring
            val segments = 12
            val sweep = 360f / segments
            for (i in 0 until segments) {
                val startAngle = rotation + (i * sweep)
                drawArc(
                    color = primaryColor.copy(alpha = if (isListening || isThinking) 0.85f else 0.45f),
                    startAngle = startAngle,
                    sweepAngle = sweep * 0.65f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - baseRadius, centerOffset.y - baseRadius),
                    size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Middle dashed ring with reverse spin
            val midRadius = baseRadius * 0.78f
            drawCircle(
                color = RkCyanDark.copy(alpha = if (isListening) 0.9f else 0.5f),
                radius = midRadius,
                center = centerOffset,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 25f), reverseRotation)
                )
            )

            // 4. Reactor core reticle ticks
            val innerRingRadius = baseRadius * 0.58f
            val tickCount = 16
            for (i in 0 until tickCount) {
                val angleRad = Math.toRadians((i * (360f / tickCount) + rotation).toDouble())
                val innerPoint = Offset(
                    x = centerOffset.x + (innerRingRadius - 8.dp.toPx()) * Math.cos(angleRad).toFloat(),
                    y = centerOffset.y + (innerRingRadius - 8.dp.toPx()) * Math.sin(angleRad).toFloat()
                )
                val outerPoint = Offset(
                    x = centerOffset.x + innerRingRadius * Math.cos(angleRad).toFloat(),
                    y = centerOffset.y + innerRingRadius * Math.sin(angleRad).toFloat()
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.8f),
                    start = innerPoint,
                    end = outerPoint,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. High-intensity reactor core boundary
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F1E38),
                        RkDarkNavy
                    ),
                    center = centerOffset,
                    radius = innerRingRadius
                ),
                radius = innerRingRadius - 2.dp.toPx(),
                center = centerOffset
            )

            drawCircle(
                color = primaryColor.copy(alpha = if (isListening || isThinking) 0.9f else 0.4f),
                radius = innerRingRadius,
                center = centerOffset,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // Center Clickable Touch Core
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isListening || isThinking) 0.35f else 0.15f),
                            Color(0xFF0A1224)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = true, color = primaryColor),
                    onClick = onClick
                )
                .testTag("listen_button"),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                AssistantState.LISTENING -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening...",
                        modifier = Modifier.size(38.dp),
                        tint = RkAccentGreen
                    )
                }
                AssistantState.THINKING -> {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Thinking...",
                        modifier = Modifier.size(38.dp),
                        tint = RkAccentPurple
                    )
                }
                AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Speaking...",
                        modifier = Modifier.size(38.dp),
                        tint = RkCyan
                    )
                }
                AssistantState.IDLE -> {
                    Text(
                        text = "RK",
                        color = RkCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
