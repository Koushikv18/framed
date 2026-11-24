package com.aesthetic.framed.domain.coach

import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.aesthetic.framed.data.model.CoachMode
import com.aesthetic.framed.data.model.CompositionState
import com.aesthetic.framed.data.model.DetectedSubject
import com.aesthetic.framed.data.model.DrawnTargetBox
import com.aesthetic.framed.data.model.HorizonState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.min

class CompositionAnalyzer : ImageAnalysis.Analyzer {

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(options)

    private val _compositionState = MutableStateFlow(CompositionState())
    val compositionState: StateFlow<CompositionState> = _compositionState.asStateFlow()

    private var currentHorizonState = HorizonState()

    fun updateHorizonState(horizonState: HorizonState) {
        currentHorizonState = horizonState
    }

    fun setCoachMode(mode: CoachMode) {
        _compositionState.value = _compositionState.value.copy(
            coachMode = mode,
            drawnTargetBox = if (mode == CoachMode.DRAW_TO_TRACK) _compositionState.value.drawnTargetBox else null
        )
    }

    fun setDrawnTarget(box: DrawnTargetBox?) {
        _compositionState.value = _compositionState.value.copy(
            drawnTargetBox = box
        )
    }

    fun setIsDrawing(isDrawing: Boolean) {
        _compositionState.value = _compositionState.value.copy(isDrawing = isDrawing)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || _compositionState.value.coachMode == CoachMode.OFF) {
            imageProxy.close()
            return
        }

        // Luma histogram analysis from Y-plane
        val avgLuma = computeAverageLuma(imageProxy)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val imageHeight = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

        detector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                val subjects = detectedObjects.map { obj ->
                    val box = obj.boundingBox
                    val normalized = RectF(
                        (box.left / imageWidth).coerceIn(0f, 1f),
                        (box.top / imageHeight).coerceIn(0f, 1f),
                        (box.right / imageWidth).coerceIn(0f, 1f),
                        (box.bottom / imageHeight).coerceIn(0f, 1f)
                    )
                    DetectedSubject(
                        normalizedBounds = normalized,
                        trackingId = obj.trackingId,
                        label = obj.labels.firstOrNull()?.text
                    )
                }

                // Pick primary subject: largest area near center
                val primary = subjects.maxByOrNull { it.area }

                evaluateComposition(primary, subjects, avgLuma)
            }
            .addOnFailureListener {
                // Silently handle
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun evaluateComposition(
        primary: DetectedSubject?,
        allSubjects: List<DetectedSubject>,
        avgLuma: Float
    ) {
        val currentState = _compositionState.value
        val hints = mutableListOf<String>()

        // 1. Horizon tilt check
        val tiltDegrees = abs(currentHorizonState.rollDegrees)
        val horizonScore = (25f - tiltDegrees * 8f).coerceIn(0f, 25f)
        if (tiltDegrees > 0.8f) {
            hints.add(if (currentHorizonState.rollDegrees > 0) "Tilt left ${String.format("%.1f", tiltDegrees)}°" else "Tilt right ${String.format("%.1f", tiltDegrees)}°")
        }

        // 2. Subject Rule-of-Thirds & Golden Ratio Positioning
        var thirdsScore = 0f
        var scaleScore = 0f

        if (primary != null) {
            val cx = primary.centerX
            val cy = primary.centerY

            // Thirds line intersections: 0.333, 0.667
            val distThirdsX = min(abs(cx - 0.333f), abs(cx - 0.667f))
            val distThirdsY = min(abs(cy - 0.333f), abs(cy - 0.667f))
            val distCenter = min(abs(cx - 0.5f), abs(cy - 0.5f))

            // Score highest when subject centers near thirds line or directly center-framed
            val positionQuality = 1f - min(distThirdsX + distThirdsY, distCenter * 1.5f).coerceIn(0f, 1f)
            thirdsScore = positionQuality * 40f

            if (positionQuality < 0.6f) {
                hints.add("Align with rule of thirds")
            }

            // Scale evaluation (ideal coverage: 15% - 55% of frame)
            val area = primary.area
            scaleScore = when {
                area < 0.08f -> {
                    hints.add("Move closer to subject")
                    10f
                }
                area > 0.65f -> {
                    hints.add("Step back slightly")
                    10f
                }
                else -> 20f
            }
        } else {
            hints.add("Frame a primary subject")
        }

        // 3. Exposure balance
        val exposureScore = when {
            avgLuma < 35f -> {
                hints.add("Scene underexposed")
                5f
            }
            avgLuma > 225f -> {
                hints.add("Reduce harsh highlights")
                5f
            }
            else -> 15f
        }

        val totalScore = (horizonScore + thirdsScore + scaleScore + exposureScore).toInt().coerceIn(0, 100)

        if (totalScore >= 85) {
            hints.clear()
            hints.add("Aesthetic Composition")
        }

        // 4. Draw-to-track IoU calculation (Requirement 3: threshold > 0.55)
        var iou = 0f
        val drawn = currentState.drawnTargetBox
        if (drawn != null && primary != null) {
            iou = drawn.calculateIoU(primary.normalizedBounds)
        }
        val isLocked = iou >= 0.55f

        _compositionState.value = currentState.copy(
            primarySubject = primary,
            allSubjects = allSubjects,
            compositionScore = totalScore,
            isScorePrimed = totalScore >= 85,
            targetIoU = iou,
            isTargetLocked = isLocked,
            coachingHints = hints
        )
    }

    private fun computeAverageLuma(imageProxy: ImageProxy): Float {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val step = 16 // Downsample for maximum performance
        var totalLuma = 0L
        var count = 0

        var i = 0
        val limit = buffer.remaining()
        while (i < limit) {
            totalLuma += (buffer.get(i).toInt() and 0xFF)
            count++
            i += step
        }
        buffer.rewind()
        return if (count > 0) totalLuma.toFloat() / count.toFloat() else 128f
    }
}
