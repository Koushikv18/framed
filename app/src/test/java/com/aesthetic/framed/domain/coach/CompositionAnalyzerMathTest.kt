package com.aesthetic.framed.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompositionAnalyzerMathTest {

    data class TestRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun calculateIoU(r1: TestRect, r2: TestRect): Float {
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

    private fun calculateThirdsDistance(cx: Float, cy: Float): Float {
        val thirdLinesX = listOf(1f / 3f, 2f / 3f)
        val thirdLinesY = listOf(1f / 3f, 2f / 3f)
        val minDx = thirdLinesX.minOf { abs(cx - it) }
        val minDy = thirdLinesY.minOf { abs(cy - it) }
        return Math.hypot(minDx.toDouble(), minDy.toDouble()).toFloat()
    }

    @Test
    fun testIoUIdenticalBoxes_returnsOne() {
        val r1 = TestRect(0.2f, 0.2f, 0.6f, 0.6f)
        val r2 = TestRect(0.2f, 0.2f, 0.6f, 0.6f)
        val iou = calculateIoU(r1, r2)
        assertEquals(1.0f, iou, 0.0001f)
    }

    @Test
    fun testIoUNonOverlappingBoxes_returnsZero() {
        val r1 = TestRect(0.0f, 0.0f, 0.2f, 0.2f)
        val r2 = TestRect(0.5f, 0.5f, 0.8f, 0.8f)
        val iou = calculateIoU(r1, r2)
        assertEquals(0.0f, iou, 0.0001f)
    }

    @Test
    fun testIoUPartialOverlap_calculatesCorrectRatio() {
        // Area 1: 0.4 * 0.4 = 0.16
        // Area 2: 0.4 * 0.4 = 0.16
        // Intersection: [0.2, 0.2] to [0.4, 0.4] -> 0.2 * 0.2 = 0.04
        // Union: 0.16 + 0.16 - 0.04 = 0.28
        // IoU: 0.04 / 0.28 = 0.142857
        val r1 = TestRect(0.0f, 0.0f, 0.4f, 0.4f)
        val r2 = TestRect(0.2f, 0.2f, 0.6f, 0.6f)
        val iou = calculateIoU(r1, r2)
        assertEquals(0.04f / 0.28f, iou, 0.001f)
    }

    @Test
    fun testThirdsIntersectionDistance_exactCenter() {
        val dist = calculateThirdsDistance(1f / 3f, 1f / 3f)
        assertEquals(0.0f, dist, 0.0001f)
    }

    @Test
    fun testThirdsIntersectionDistance_farCenter() {
        // Center at (0.5, 0.5): distance to nearest third (1/3 or 2/3) is ~0.1667 in X and Y
        val dist = calculateThirdsDistance(0.5f, 0.5f)
        assertTrue(dist > 0.2f)
    }
}
