package com.aesthetic.framed.ui.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.framed.data.model.HorizonState
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioText
import java.util.Locale

@Composable
fun HorizonLevelIndicator(
    horizonState: HorizonState,
    modifier: Modifier = Modifier
) {
    val levelColor by animateColorAsState(
        targetValue = if (horizonState.isLevel) EmeraldLevel else StudioText.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = 200),
        label = "horizonColor"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val halfWidth = size.width / 2f

            // Static center reticle (fixed reference marks)
            val reticleLength = 16.dp.toPx()
            val reticleGap = 20.dp.toPx()

            // Left fixed reference tick
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(center.x - reticleGap - reticleLength, center.y),
                end = Offset(center.x - reticleGap, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Right fixed reference tick
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(center.x + reticleGap, center.y),
                end = Offset(center.x + reticleGap + reticleLength, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Dynamic rotated artificial horizon bar
            rotate(
                degrees = -horizonState.rollDegrees,
                pivot = center
            ) {
                val barHalfLength = 70.dp.toPx()
                val barGap = 26.dp.toPx()

                // Left bar
                drawLine(
                    color = levelColor,
                    start = Offset(center.x - barHalfLength, center.y),
                    end = Offset(center.x - barGap, center.y),
                    strokeWidth = if (horizonState.isLevel) 3.5.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Right bar
                drawLine(
                    color = levelColor,
                    start = Offset(center.x + barGap, center.y),
                    end = Offset(center.x + barHalfLength, center.y),
                    strokeWidth = if (horizonState.isLevel) 3.5.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Sub-degree numeric readout
        val readoutText = if (horizonState.isLevel) {
            "LEVEL 0.0°"
        } else {
            String.format(Locale.US, "%+.1f°", horizonState.rollDegrees)
        }

        Text(
            text = readoutText,
            color = levelColor,
            fontSize = 11.sp,
            fontWeight = if (horizonState.isLevel) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 70.dp)
        )
    }
}
