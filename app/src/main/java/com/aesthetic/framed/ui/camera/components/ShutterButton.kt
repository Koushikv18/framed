package com.aesthetic.framed.ui.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aesthetic.framed.ui.theme.EmeraldLevel

@Composable
fun ShutterButton(
    isPrimedOrLevel: Boolean = false,
    autoCaptureProgress: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "shutterScale"
    )

    val ringColor = if (isPrimedOrLevel) EmeraldLevel else Color.White

    Box(
        modifier = modifier
            .size(86.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Smooth Circular Progress Arc for Auto-Capture (800ms lock-in)
        if (autoCaptureProgress > 0f) {
            Canvas(modifier = Modifier.size(84.dp)) {
                drawArc(
                    color = EmeraldLevel,
                    startAngle = -90f,
                    sweepAngle = autoCaptureProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Outer concentric ring illuminated in vibrant Emerald Green (#10B981)
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    color = ringColor.copy(alpha = if (isPrimedOrLevel) 0.95f else 0.4f),
                    shape = CircleShape
                )
        )

        // Inner solid tactile shutter pad
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (isPrimedOrLevel) EmeraldLevel.copy(alpha = 0.2f) else Color.White)
        )

        // Core white/emerald disc
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (isPrimedOrLevel) EmeraldLevel else Color.White)
        )
    }
}
