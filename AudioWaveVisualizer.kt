package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RkCyan
import com.example.ui.theme.RkCyanDark

@Composable
fun AudioWaveVisualizer(
    isListening: Boolean,
    rmsLevel: Float,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val barCount = 18
    val transition = rememberInfiniteTransition(label = "audio_wave")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    val isActive = isListening || isSpeaking

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val offsetFactor = Math.sin((i.toDouble() / barCount) * Math.PI).toFloat()
            val targetHeightDp = if (isListening) {
                val dynamicHeight = 6.dp + (30.dp * offsetFactor * rmsLevel * (0.6f + 0.4f * phase))
                dynamicHeight.coerceIn(4.dp, 36.dp)
            } else if (isSpeaking) {
                val dynamicHeight = 8.dp + (24.dp * offsetFactor * (0.5f + 0.5f * Math.sin(phase.toDouble() * Math.PI * 2 + i * 0.4).toFloat().coerceAtLeast(0f)))
                dynamicHeight.coerceIn(4.dp, 32.dp)
            } else {
                (4.dp + (8.dp * offsetFactor * phase)).coerceIn(3.dp, 12.dp)
            }

            val barAlpha = if (isActive) {
                0.7f + 0.3f * offsetFactor
            } else {
                0.25f + 0.25f * offsetFactor
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(targetHeightDp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i % 2 == 0) RkCyan.copy(alpha = barAlpha)
                        else RkCyanDark.copy(alpha = barAlpha)
                    )
            )
            if (i < barCount - 1) {
                Box(modifier = Modifier.width(3.dp))
            }
        }
    }
}
