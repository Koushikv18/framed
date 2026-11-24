package com.aesthetic.framed.domain.coach

import android.content.Context
import android.graphics.Bitmap
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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class CompositionAnalyzer(private val context: Context? = null) : ImageAnalysis.Analyzer {

    private var mediaPipeDetector: ObjectDetector? = null
    private var aestheticScorer: AestheticScorer? = context?.let { AestheticScorer(it) }
    private var frameCount = 0
    private var latestAestheticScore = 7.5f

    private val _compositionState = MutableStateFlow(CompositionState())
    val compositionState: StateFlow<CompositionState> = _compositionState.asStateFlow()

    private var currentHorizonState = HorizonState()
    private var currentZoom = 1.0f

    init {
        initMediaPipe()
    }

    private fun initMediaPipe() {
        if (context == null) return
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setDelegate(Delegate.GPU)

            // Look for model in assets or OTA models directory
            val modelFile = File(context.filesDir, "models/efficientdet_lite2.tflite")
            if (modelFile.exists()) {
                baseOptionsBuilder.setModelAssetPath(modelFile.absolutePath)
            } else {
                baseOptionsBuilder.setModelAssetPath("efficientdet_lite2.tflite")
            }

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(5)
                .setScoreThreshold(0.35f)
                .build()

            mediaPipeDetector = ObjectDetector.createFromOptions(context, options)
        } catch (_: Exception) {
            // Graceful fallback to heuristic edge detection if GPU or model asset isn't ready
            mediaPipeDetector = null
        }
    }

    fun updateHorizonState(horizonState: HorizonState) {
        currentHorizonState = horizonState
    }

    fun updateCurrentZoom(zoom: Float) {
        currentZoom = zoom
    }

    fun setCoachMode(mode: CoachMode) {
        _compositionState.value = _compositionState.value.copy(
            coachMode = mode,
            drawnTargetBox = if (mode != CoachMode.OFF) _compositionState.value.drawnTargetBox else null
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

    /**
     * Tactile One-Tap SCAN Workflow:
     * Analyzes detected subjects, rule-of-thirds intersections, and framing to place
     * an optimal recommended target composition box on the viewfinder.
     */
    fun scanComposition() {
        val state = _compositionState.value
        val primary = state.primarySubject

        val (targetCx, targetCy, boxW, boxH) = if (primary != null) {
            val thirdLinesX = listOf(1f / 3f, 2f / 3f)
            val thirdLinesY = listOf(1f / 3f, 2f / 3f)
            val nearestThirdX = thirdLinesX.minByOrNull { abs(primary.centerX - it) } ?: (1f / 3f)
            val nearestThirdY = thirdLinesY.minByOrNull { abs(primary.centerY - it) } ?: (1f / 3f)

            val cx = (primary.centerX * 0.4f + nearestThirdX * 0.6f).coerceIn(0.25f, 0.75f)
            val cy = (primary.centerY * 0.4f + nearestThirdY * 0.6f).coerceIn(0.25f, 0.75f)
            val w = (primary.normalizedBounds.width() * 1.35f).coerceIn(0.28f, 0.75f)
            val h = (primary.normalizedBounds.height() * 1.35f).coerceIn(0.28f, 0.75f)
            listOf(cx, cy, w, h)
        } else {
            listOf(1f / 3f, 1f / 3f, 0.38f, 0.45f)
        }

        val left = (targetCx - boxW / 2f).coerceIn(0.04f, 0.96f - boxW)
        val top = (targetCy - boxH / 2f).coerceIn(0.04f, 0.96f - boxH)
        val right = left + boxW
        val bottom = top + boxH

        _compositionState.value = _compositionState.value.copy(
            drawnTargetBox = DrawnTargetBox(left, top, right, bottom)
        )
    }

    /**
     * Requirement:
     * Wrap analyze() in try { ... } finally { imageProxy.close() } so the CameraX
     * image stream never stalls or leaks buffers under any condition.
     */
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (_compositionState.value.coachMode == CoachMode.OFF) return

            frameCount++
            val avgLuma = computeAverageLuma(imageProxy)
            val bitmap = imageProxy.toBitmap()

            // Run TFLite AestheticScorer every 5th frame for locked 60-120fps performance
            if (frameCount % 5 == 0 && aestheticScorer != null) {
                latestAestheticScore = aestheticScorer?.scoreAesthetics(bitmap) ?: 7.5f
            }

            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            val subjects = mutableListOf<DetectedSubject>()

            if (mediaPipeDetector != null) {
                try {
                    val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
                    val result: ObjectDetectorResult? = mediaPipeDetector?.detect(mpImage)
                    result?.detections()?.forEach { detection ->
                        val box = detection.boundingBox()
                        val normalized = RectF(
                            (box.left / imageWidth).coerceIn(0f, 1f),
                            (box.top / imageHeight).coerceIn(0f, 1f),
                            (box.right / imageWidth).coerceIn(0f, 1f),
                            (box.bottom / imageHeight).coerceIn(0f, 1f)
                        )
                        val category = detection.categories().firstOrNull()
                        subjects.add(
                            DetectedSubject(
                                normalizedBounds = normalized,
                                trackingId = category?.index() ?: 0,
                                label = category?.categoryName() ?: "Subject"
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Fallback to center heuristic subject
                }
            }

            // Fallback subject if detector found no targets or is initializing
            if (subjects.isEmpty()) {
                subjects.add(
                    DetectedSubject(
                        normalizedBounds = RectF(0.25f, 0.25f, 0.75f, 0.75f),
                        trackingId = 1,
                        label = "Subject"
                    )
                )
            }

            // Primary subject: largest area
            val primary = subjects.maxByOrNull { it.area }

            evaluateComposition(primary, subjects, avgLuma, latestAestheticScore)
        } catch (_: Exception) {
            // Silently recover without blocking pipeline
        } finally {
            imageProxy.close()
        }
    }

    private fun evaluateComposition(
        primary: DetectedSubject?,
        allSubjects: List<DetectedSubject>,
        avgLuma: Float,
        aestheticScore: Float
    ) {
        val currentState = _compositionState.value
        val hints = mutableListOf<String>()

        // 1. Horizon tilt check (Level snap within +/- 0.5 degrees)
        val tiltAngle = abs(currentHorizonState.rollDegrees)
        val isLevel = currentHorizonState.isLevel || tiltAngle <= 0.5f
        if (!isLevel) {
            hints.add("Level horizon")
        }

        // 2. Exposure & Luma Histogram Check (underexposed < 35 or clipped > 225 out of 255)
        if (avgLuma < 0.137f || avgLuma > 0.882f) {
            hints.add("Tap to balance light")
        }

        // 3. Subject Framing & S25 Ultra Lens Coaching
        var compositionScore = 70
        var iouWithTarget = 0.0f
        val activeTargetBox = currentState.drawnTargetBox

        if (primary != null) {
            val cx = primary.centerX
            val cy = primary.centerY
            val area = primary.area

            if (activeTargetBox != null) {
                // Real-time directional deltas between live subject and target frame
                val targetRect = RectF(
                    activeTargetBox.left,
                    activeTargetBox.top,
                    activeTargetBox.right,
                    activeTargetBox.bottom
                )
                val targetCx = targetRect.centerX()
                val targetCy = targetRect.centerY()
                val targetArea = targetRect.width() * targetRect.height()

                iouWithTarget = calculateIoU(primary.normalizedBounds, targetRect)

                // Pan hints (delta between subject and target center)
                val deltaX = cx - targetCx
                val deltaY = cy - targetCy

                if (deltaX < -0.04f) {
                    hints.add("Pan left")
                } else if (deltaX > 0.04f) {
                    hints.add("Pan right")
                }

                // Tilt hints
                if (deltaY < -0.04f) {
                    hints.add("Tilt up")
                } else if (deltaY > 0.04f) {
                    hints.add("Tilt down")
                }

                // Lens/Zoom hint based on subject-to-target area ratio
                if (targetArea > 0f) {
                    val ratio = area / targetArea
                    if (ratio < 0.35f) {
                        when {
                            currentZoom < 1.5f -> hints.add("Switch to 3x")
                            currentZoom < 3.5f -> hints.add("Switch to 5x")
                            else -> hints.add("Move closer")
                        }
                    } else if (ratio > 1.35f) {
                        when {
                            currentZoom >= 1.0f -> hints.add("Step back to 0.6x")
                            else -> hints.add("Move closer")
                        }
                    }
                }

                if (iouWithTarget >= 0.55f) {
                    compositionScore = (85 + (iouWithTarget - 0.55f) * 33f).toInt().coerceIn(85, 100)
                    if (isLevel) {
                        hints.add(0, "Locked on target")
                    }
                } else {
                    compositionScore = (iouWithTarget * 100).toInt().coerceIn(35, 84)
                }
            } else {
                // Pre-scan Auto Frame: General S25 Ultra Lens recommendations & rule-of-thirds
                if (area < 0.08f && currentZoom <= 1.0f) {
                    hints.add("Switch to 3x")
                } else if (area < 0.04f && currentZoom <= 3.0f) {
                    hints.add("Switch to 5x")
                } else if (area > 0.65f && currentZoom >= 1.0f) {
                    hints.add("Step back to 0.6x")
                }

                val thirdLinesX = listOf(1f / 3f, 2f / 3f)
                val thirdLinesY = listOf(1f / 3f, 2f / 3f)
                val minDx = thirdLinesX.minOf { abs(cx - it) }
                val minDy = thirdLinesY.minOf { abs(cy - it) }

                if (minDx > 0.12f && minDy > 0.12f && abs(cx - 0.5f) > 0.15f) {
                    hints.add("Align on thirds")
                } else {
                    compositionScore += 15
                }
            }
        } else {
            hints.add("Searching for subject...")
            compositionScore = 50
        }

        // 4. Blend LAION-Aesthetics V2 neural score (0.0-10.0 scale) into the composition score
        val aestheticBonus = ((aestheticScore - 5.0f) * 3.0f).toInt()
        compositionScore = (compositionScore + aestheticBonus).coerceIn(0, 100)

        if (isLevel) {
            compositionScore = (compositionScore + 5).coerceAtMost(100)
        }

        val isScorePrimed = compositionScore >= 85
        val isTargetLocked = iouWithTarget >= 0.55f

        _compositionState.value = currentState.copy(
            allSubjects = allSubjects,
            primarySubject = primary,
            compositionScore = compositionScore,
            isScorePrimed = isScorePrimed,
            targetIoU = iouWithTarget,
            isTargetLocked = isTargetLocked,
            coachingHints = hints.distinct()
        )
    }

    private fun calculateIoU(r1: RectF, r2: RectF): Float {
        val interLeft = maxOf(r1.left, r2.left)
        val interTop = maxOf(r1.top, r2.top)
        val interRight = minOf(r1.right, r2.right)
        val interBottom = minOf(r1.bottom, r2.bottom)

        if (interLeft < interRight && interTop < interBottom) {
            val interArea = (interRight - interLeft) * (interBottom - interTop)
            val area1 = (r1.right - r1.left) * (r1.bottom - r1.top)
            val area2 = (r2.right - r2.left) * (r2.bottom - r2.top)
            val unionArea = area1 + area2 - interArea
            return if (unionArea > 0f) interArea / unionArea else 0f
        }
        return 0f
    }

    private fun computeAverageLuma(imageProxy: ImageProxy): Float {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        buffer.rewind()
        val totalBytes = buffer.remaining()
        if (totalBytes == 0) return 0.5f

        var sum = 0L
        val step = 16
        var count = 0
        val bytes = ByteArray(totalBytes)
        buffer.get(bytes)

        for (i in 0 until totalBytes step step) {
            sum += (bytes[i].toInt() and 0xFF)
            count++
        }
        return if (count > 0) (sum.toFloat() / count) / 255f else 0.5f
    }
}
