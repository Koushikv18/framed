package com.aesthetic.framed.ui.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.framed.data.model.CoachMode
import com.aesthetic.framed.data.model.CompositionState
import com.aesthetic.framed.data.model.DrawnTargetBox
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioBorder
import com.aesthetic.framed.ui.theme.StudioDark
import com.aesthetic.framed.ui.theme.StudioText

@Composable
fun CoachOverlay(
    state: CompositionState,
    onTargetBoxDrawn: (DrawnTargetBox?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.coachMode == CoachMode.OFF) return

    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (state.coachMode == CoachMode.DRAW_TO_TRACK) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartOffset = offset
                                dragCurrentOffset = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragCurrentOffset = change.position
                            },
                            onDragEnd = {
                                val start = dragStartOffset
                                val end = dragCurrentOffset
                                if (start != null && end != null) {
                                    val left = minOf(start.x, end.x) / size.width
                                    val top = minOf(start.y, end.y) / size.height
                                    val right = maxOf(start.x, end.x) / size.width
                                    val bottom = maxOf(start.y, end.y) / size.height

                                    // Only accept if meaningful box size
                                    if ((right - left) > 0.05f && (bottom - top) > 0.05f) {
                                        onTargetBoxDrawn(DrawnTargetBox(left, top, right, bottom))
                                    } else {
                                        onTargetBoxDrawn(null)
                                    }
                                }
                                dragStartOffset = null
                                dragCurrentOffset = null
                            },
                            onDragCancel = {
                                dragStartOffset = null
                                dragCurrentOffset = null
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Subtle Rule of Thirds Grid
            val gridColor = Color.White.copy(alpha = 0.18f)
            val strokeOne = 1.dp.toPx()

            // Vertical thirds lines
            drawLine(
                color = gridColor,
                start = Offset(canvasWidth * 0.333f, 0f),
                end = Offset(canvasWidth * 0.333f, canvasHeight),
                strokeWidth = strokeOne
            )
            drawLine(
                color = gridColor,
                start = Offset(canvasWidth * 0.667f, 0f),
                end = Offset(canvasWidth * 0.667f, canvasHeight),
                strokeWidth = strokeOne
            )

            // Horizontal thirds lines
            drawLine(
                color = gridColor,
                start = Offset(0f, canvasHeight * 0.333f),
                end = Offset(canvasWidth, canvasHeight * 0.333f),
                strokeWidth = strokeOne
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, canvasHeight * 0.667f),
                end = Offset(canvasWidth, canvasHeight * 0.667f),
                strokeWidth = strokeOne
            )

            // 2. Draw Detected Subject Bounding Box (Corner Brackets)
            state.primarySubject?.let { subject ->
                val bounds = subject.normalizedBounds
                val left = bounds.left * canvasWidth
                val top = bounds.top * canvasHeight
                val right = bounds.right * canvasWidth
                val bottom = bounds.bottom * canvasHeight
                val width = right - left
                val height = bottom - top

                val bracketLength = minOf(width, height) * 0.22f
                val bracketStroke = if (state.isShutterPrimed) 2.5.dp.toPx() else 1.5.dp.toPx()
                val bracketColor = if (state.isShutterPrimed) EmeraldLevel else Color.White.copy(alpha = 0.85f)

                // Top-Left
                drawLine(bracketColor, Offset(left, top), Offset(left + bracketLength, top), bracketStroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLength), bracketStroke, StrokeCap.Round)

                // Top-Right
                drawLine(bracketColor, Offset(right, top), Offset(right - bracketLength, top), bracketStroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLength), bracketStroke, StrokeCap.Round)

                // Bottom-Left
                drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLength, bottom), bracketStroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLength), bracketStroke, StrokeCap.Round)

                // Bottom-Right
                drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLength, bottom), bracketStroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLength), bracketStroke, StrokeCap.Round)
            }

            // 3. Draw-to-track drawn box
            val currentStart = dragStartOffset
            val currentEnd = dragCurrentOffset
            if (currentStart != null && currentEnd != null) {
                // Active dragging preview
                val rectLeft = minOf(currentStart.x, currentEnd.x)
                val rectTop = minOf(currentStart.y, currentEnd.y)
                val rectWidth = maxOf(currentStart.x, currentEnd.x) - rectLeft
                val rectHeight = maxOf(currentStart.y, currentEnd.y) - rectTop

                drawRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            } else {
                state.drawnTargetBox?.let { target ->
                    val tLeft = target.left * canvasWidth
                    val tTop = target.top * canvasHeight
                    val tWidth = (target.right - target.left) * canvasWidth
                    val tHeight = (target.bottom - target.top) * canvasHeight

                    val boxColor = if (state.isTargetLocked) EmeraldLevel else Color.White.copy(alpha = 0.5f)

                    drawRect(
                        color = boxColor,
                        topLeft = Offset(tLeft, tTop),
                        size = Size(tWidth, tHeight),
                        style = Stroke(
                            width = if (state.isTargetLocked) 2.dp.toPx() else 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                        )
                    )
                }
            }
        }

        // 4. Floating HUD Coaching Typography & Score Dial
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Score pill / IoU Lock readout
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioDark.copy(alpha = 0.90f))
                    .border(1.dp, if (state.isShutterPrimed) EmeraldLevel else StudioBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.coachMode == CoachMode.AUTO_FRAME) {
                    Text(
                        text = "SCORE",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.compositionScore}%",
                        color = if (state.isScorePrimed) EmeraldLevel else StudioText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (state.coachMode == CoachMode.DRAW_TO_TRACK) {
                    val iouPercent = (state.targetIoU * 100).toInt()
                    Text(
                        text = if (state.drawnTargetBox == null) "DRAW TARGET BOX" else if (state.isTargetLocked) "LOCKED" else "TRACKING",
                        color = if (state.isTargetLocked) EmeraldLevel else Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (state.drawnTargetBox != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$iouPercent% IoU",
                            color = if (state.isTargetLocked) EmeraldLevel else StudioText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Dynamic Live Coaching Hints
            AnimatedVisibility(
                visible = state.coachingHints.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.coachingHints.take(2).forEach { hint ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.isShutterPrimed) EmeraldLevel.copy(alpha = 0.2f) else StudioDark.copy(alpha = 0.85f))
                                .border(0.5.dp, if (state.isShutterPrimed) EmeraldLevel else StudioBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = hint.uppercase(),
                                color = if (state.isShutterPrimed) EmeraldLevel else StudioText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
