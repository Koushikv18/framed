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

private enum class DragMode {
    NONE, DRAW_NEW, MOVE_CENTER, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR
}

@Composable
fun CoachOverlay(
    state: CompositionState,
    onTargetBoxDrawn: (DrawnTargetBox?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.coachMode == CoachMode.OFF) return

    var activeDragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var currentBox by remember(state.drawnTargetBox) { mutableStateOf(state.drawnTargetBox) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.coachMode, currentBox) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartOffset = offset
                        val box = currentBox
                        if (box != null) {
                            val pxLeft = box.left * size.width
                            val pxTop = box.top * size.height
                            val pxRight = box.right * size.width
                            val pxBottom = box.bottom * size.height
                            val handleRadius = 36.dp.toPx()

                            fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
                                val dx = x1 - x2
                                val dy = y1 - y2
                                return kotlin.math.sqrt(dx * dx + dy * dy)
                            }

                            activeDragMode = when {
                                dist(offset.x, offset.y, pxLeft, pxTop) <= handleRadius -> DragMode.RESIZE_TL
                                dist(offset.x, offset.y, pxRight, pxTop) <= handleRadius -> DragMode.RESIZE_TR
                                dist(offset.x, offset.y, pxLeft, pxBottom) <= handleRadius -> DragMode.RESIZE_BL
                                dist(offset.x, offset.y, pxRight, pxBottom) <= handleRadius -> DragMode.RESIZE_BR
                                offset.x in pxLeft..pxRight && offset.y in pxTop..pxBottom -> DragMode.MOVE_CENTER
                                state.coachMode == CoachMode.DRAW_TO_TRACK -> DragMode.DRAW_NEW
                                else -> DragMode.NONE
                            }
                        } else if (state.coachMode == CoachMode.DRAW_TO_TRACK) {
                            activeDragMode = DragMode.DRAW_NEW
                        } else {
                            activeDragMode = DragMode.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val box = currentBox
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        when (activeDragMode) {
                            DragMode.DRAW_NEW -> {
                                val start = dragStartOffset ?: return@detectDragGestures
                                val cur = change.position
                                val l = minOf(start.x, cur.x) / w
                                val t = minOf(start.y, cur.y) / h
                                val r = maxOf(start.x, cur.x) / w
                                val b = maxOf(start.y, cur.y) / h
                                currentBox = DrawnTargetBox(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
                            }
                            DragMode.MOVE_CENTER -> {
                                if (box != null) {
                                    val dx = dragAmount.x / w
                                    val dy = dragAmount.y / h
                                    val boxW = box.right - box.left
                                    val boxH = box.bottom - box.top
                                    val newL = (box.left + dx).coerceIn(0f, 1f - boxW)
                                    val newT = (box.top + dy).coerceIn(0f, 1f - boxH)
                                    currentBox = DrawnTargetBox(newL, newT, newL + boxW, newT + boxH)
                                }
                            }
                            DragMode.RESIZE_TL -> {
                                if (box != null) {
                                    val newL = (box.left + dragAmount.x / w).coerceIn(0f, box.right - 0.05f)
                                    val newT = (box.top + dragAmount.y / h).coerceIn(0f, box.bottom - 0.05f)
                                    currentBox = DrawnTargetBox(newL, newT, box.right, box.bottom)
                                }
                            }
                            DragMode.RESIZE_TR -> {
                                if (box != null) {
                                    val newR = (box.right + dragAmount.x / w).coerceIn(box.left + 0.05f, 1f)
                                    val newT = (box.top + dragAmount.y / h).coerceIn(0f, box.bottom - 0.05f)
                                    currentBox = DrawnTargetBox(box.left, newT, newR, box.bottom)
                                }
                            }
                            DragMode.RESIZE_BL -> {
                                if (box != null) {
                                    val newL = (box.left + dragAmount.x / w).coerceIn(0f, box.right - 0.05f)
                                    val newB = (box.bottom + dragAmount.y / h).coerceIn(box.top + 0.05f, 1f)
                                    currentBox = DrawnTargetBox(newL, box.top, box.right, newB)
                                }
                            }
                            DragMode.RESIZE_BR -> {
                                if (box != null) {
                                    val newR = (box.right + dragAmount.x / w).coerceIn(box.left + 0.05f, 1f)
                                    val newB = (box.bottom + dragAmount.y / h).coerceIn(box.top + 0.05f, 1f)
                                    currentBox = DrawnTargetBox(box.left, box.top, newR, newB)
                                }
                            }
                            DragMode.NONE -> {}
                        }
                    },
                    onDragEnd = {
                        val finalBox = currentBox
                        if (finalBox != null && (finalBox.right - finalBox.left) > 0.05f && (finalBox.bottom - finalBox.top) > 0.05f) {
                            onTargetBoxDrawn(finalBox)
                        } else {
                            currentBox = null
                            onTargetBoxDrawn(null)
                        }
                        activeDragMode = DragMode.NONE
                        dragStartOffset = null
                    },
                    onDragCancel = {
                        activeDragMode = DragMode.NONE
                        dragStartOffset = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Subtle Rule of Thirds Grid
            val gridColor = Color.White.copy(alpha = 0.16f)
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

            // 3. Interactive Target Frame with Corner Drag Handles
            val activeBox = currentBox ?: state.drawnTargetBox
            activeBox?.let { target ->
                val tLeft = target.left * canvasWidth
                val tTop = target.top * canvasHeight
                val tWidth = (target.right - target.left) * canvasWidth
                val tHeight = (target.bottom - target.top) * canvasHeight
                val tRight = target.right * canvasWidth
                val tBottom = target.bottom * canvasHeight

                val boxColor = if (state.isTargetLocked) EmeraldLevel else Color.White.copy(alpha = 0.65f)

                drawRect(
                    color = boxColor,
                    topLeft = Offset(tLeft, tTop),
                    size = Size(tWidth, tHeight),
                    style = Stroke(
                        width = if (state.isTargetLocked) 2.dp.toPx() else 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )

                // 4 Interactive Corner Drag Handles
                val handleRadius = 4.dp.toPx()
                drawCircle(color = boxColor, radius = handleRadius, center = Offset(tLeft, tTop))
                drawCircle(color = boxColor, radius = handleRadius, center = Offset(tRight, tTop))
                drawCircle(color = boxColor, radius = handleRadius, center = Offset(tLeft, tBottom))
                drawCircle(color = boxColor, radius = handleRadius, center = Offset(tRight, tBottom))
            }
        }

        // 4. Floating HUD Coaching Typography & Score Dial
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            // Dynamic Live Coaching Hints (including S25 Ultra Zoom / Lens guidance)
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
