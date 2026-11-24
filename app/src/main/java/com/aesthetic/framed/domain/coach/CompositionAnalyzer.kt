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

        // 1. Horizon tilt check
        val tiltAngle = abs(currentHorizonState.rollDegrees)
        val isLevel = currentHorizonState.isLevel
        if (!isLevel && tiltAngle > 2.0f) {
            if (currentHorizonState.rollDegrees > 0f) {
                hints.add("Tilt left")
            } else {
                hints.add("Tilt right")
            }
        }

        // 2. Exposure & Luma Histogram Check
        if (avgLuma < 0.25f) {
            hints.add("Low light: Increase EV")
        } else if (avgLuma > 0.82f) {
            hints.add("Harsh light: Reduce EV")
        }

        // 3. Subject Framing & S25 Ultra Lens Coaching Pills
        var compositionScore = 70
        var iouWithTarget = 0.0f

        if (primary != null) {
            val cx = primary.centerX
            val cy = primary.centerY
            val area = primary.area

            // S25 Ultra Lens recommendations based on subject size & active zoom
            if (area < 0.08f && currentZoom <= 1.0f) {
                hints.add("Zoom in to 3x")
            } else if (area < 0.04f && currentZoom <= 3.0f) {
                hints.add("Zoom in to 5x")
            } else if (area > 0.65f && currentZoom >= 1.0f) {
                hints.add("Step back to 0.6x")
            }

            // Rule of thirds evaluation
            val thirdLinesX = listOf(1f / 3f, 2f / 3f)
            val thirdLinesY = listOf(1f / 3f, 2f / 3f)
            val minDx = thirdLinesX.minOf { abs(cx - it) }
            val minDy = thirdLinesY.minOf { abs(cy - it) }

            if (minDx > 0.12f && minDy > 0.12f && abs(cx - 0.5f) > 0.15f) {
                hints.add("Center or align on thirds")
            } else {
                compositionScore += 15
            }

            // Draw to Track mode evaluation
            val drawnBox = currentState.drawnTargetBox
            if (currentState.coachMode == CoachMode.DRAW_TO_TRACK && drawnBox != null) {
                val targetRect = RectF(drawnBox.left, drawnBox.top, drawnBox.right, drawnBox.bottom)
                iouWithTarget = calculateIoU(primary.normalizedBounds, targetRect)

                if (iouWithTarget >= 0.55f) {
                    hints.clear()
                    hints.add("Locked on target")
                    compositionScore = 95
                } else {
                    hints.add("Align subject in box")
                    compositionScore = (iouWithTarget * 100).toInt().coerceIn(30, 80)
                }
            }
        } else {
            hints.add("Searching for subject...")
            compositionScore = 50
        }

        // 4. Blend LAION-Aesthetics V2 neural score (0.0-10.0 scale) into the composition score
        // Scale 0-10 to bonus points (-10 to +15)
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
            coachingHints = hints
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
