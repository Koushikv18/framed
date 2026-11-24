package com.aesthetic.framed.data.model

import android.graphics.RectF

enum class CoachMode(val title: String) {
    OFF("OFF"),
    AUTO_FRAME("AUTO"),
    DRAW_TO_TRACK("TRACK")
}

data class DetectedSubject(
    val normalizedBounds: RectF,
    val trackingId: Int? = null,
    val label: String? = null
) {
    val centerX: Float get() = normalizedBounds.centerX()
    val centerY: Float get() = normalizedBounds.centerY()
    val area: Float get() = normalizedBounds.width() * normalizedBounds.height()
}

data class DrawnTargetBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val normalizedBounds: RectF
        get() = RectF(
            minOf(left, right),
            minOf(top, bottom),
            maxOf(left, right),
            maxOf(top, bottom)
        )

    fun calculateIoU(other: RectF): Float {
        val target = normalizedBounds
        val intersectLeft = maxOf(target.left, other.left)
        val intersectTop = maxOf(target.top, other.top)
        val intersectRight = minOf(target.right, other.right)
        val intersectBottom = minOf(target.bottom, other.bottom)

        val intersectWidth = maxOf(0f, intersectRight - intersectLeft)
        val intersectHeight = maxOf(0f, intersectBottom - intersectTop)
        val intersectArea = intersectWidth * intersectHeight

        val targetArea = target.width() * target.height()
        val otherArea = other.width() * other.height()
        val unionArea = targetArea + otherArea - intersectArea

        return if (unionArea > 0f) intersectArea / unionArea else 0f
    }
}

enum class CoachStatus(val title: String) {
    BUNDLED("AI ACTIVE"),
    HEURISTIC("HEURISTIC"),
    UNAVAILABLE("COACH UNAVAILABLE")
}

data class CompositionState(
    val coachMode: CoachMode = CoachMode.OFF,
    val coachStatus: CoachStatus = CoachStatus.BUNDLED,
    val primarySubject: DetectedSubject? = null,
    val allSubjects: List<DetectedSubject> = emptyList(),
    val drawnTargetBox: DrawnTargetBox? = null,
    val isDrawing: Boolean = false,
    val compositionScore: Int = 0,
    val isScorePrimed: Boolean = false,
    val targetIoU: Float = 0f,
    val isTargetLocked: Boolean = false,
    val coachingHints: List<String> = emptyList()
) {
    // Shutter primes green when Auto Frame score >= 85% or Draw-to-Track IoU >= 0.55
    val isShutterPrimed: Boolean
        get() = when (coachMode) {
            CoachMode.AUTO_FRAME -> compositionScore >= 85
            CoachMode.DRAW_TO_TRACK -> targetIoU >= 0.55f
            CoachMode.OFF -> false
        }
}
