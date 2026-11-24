package com.aesthetic.framed.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.aesthetic.framed.BuildConfig
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.DrawnTargetBox
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class DirectorAdvice(
    val critique: String,
    val targetBox: DrawnTargetBox?,
    val suggestedAdjustments: DarkroomAdjustments,
    val isOnlineResult: Boolean = true
)

object AestheticDirectorService {

    /**
     * Tier 2 — Live Online AI Director (Gemini 3.8 Flash).
     * Sends downscaled 768px frame for real-time editorial art-direction critique,
     * target framing box, and custom darkroom color grade.
     * Gracefully falls back to Tier 1 Edge AI when offline or without API key.
     */
    suspend fun analyzeFrame(context: Context, sourceBitmap: Bitmap): DirectorAdvice = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        if (apiKey.isBlank()) {
            return@withContext generateEdgeFallbackAdvice(sourceBitmap)
        }

        try {
            // Downscale to max 768px on long edge for ultra-fast roundtrip
            val maxEdge = maxOf(sourceBitmap.width, sourceBitmap.height)
            val scale = if (maxEdge > 768) 768f / maxEdge.toFloat() else 1f
            val targetW = (sourceBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (sourceBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val prompt = """
                You are a world-class editorial art director and photographer inspired by curated aesthetics and Framed 2.0.
                Analyze this photo and provide:
                1. A 1-line editorial critique with exact angle/lighting/pose advice.
                2. Normalized optimal target bounding box [left, top, right, bottom] in 0.0..1.0 coordinates.
                3. A Darkroom color grade recipe in JSON.

                Respond ONLY with raw JSON matching this schema:
                {
                  "critique": "Tilt down slightly to capture shadow geometry; cool the midtones for high-fashion mood.",
                  "box": [0.20, 0.15, 0.80, 0.85],
                  "temperature": 0.15,
                  "tint": 0.05,
                  "contrast": 0.20,
                  "highlights": -0.25,
                  "shadows": 0.15,
                  "whites": -0.10,
                  "blacks": 0.05,
                  "fade": 0.08,
                  "grain": 0.25,
                  "halation": 0.30,
                  "shadowTintR": -0.05,
                  "shadowTintG": 0.02,
                  "shadowTintB": 0.10,
                  "highlightTintR": 0.12,
                  "highlightTintG": 0.08,
                  "highlightTintB": -0.02
                }
            """.trimIndent()

            val response = generativeModel.generateContent(
                content {
                    image(scaledBitmap)
                    text(prompt)
                }
            )

            if (scaledBitmap != sourceBitmap) scaledBitmap.recycle()

            val text = response.text?.trim() ?: ""
            val cleanJson = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleanJson)
            val critique = json.optString("critique", "Balanced editorial exposure with natural depth.")
            val boxArray = json.optJSONArray("box")

            val targetBox = if (boxArray != null && boxArray.length() >= 4) {
                DrawnTargetBox(
                    left = boxArray.getDouble(0).toFloat().coerceIn(0f, 1f),
                    top = boxArray.getDouble(1).toFloat().coerceIn(0f, 1f),
                    right = boxArray.getDouble(2).toFloat().coerceIn(0f, 1f),
                    bottom = boxArray.getDouble(3).toFloat().coerceIn(0f, 1f)
                )
            } else {
                DrawnTargetBox(0.15f, 0.15f, 0.85f, 0.85f)
            }

            val adjustments = DarkroomAdjustments(
                temperature = json.optDouble("temperature", 0.0).toFloat().coerceIn(-1f, 1f),
                tint = json.optDouble("tint", 0.0).toFloat().coerceIn(-1f, 1f),
                contrast = json.optDouble("contrast", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                highlights = json.optDouble("highlights", 0.0).toFloat().coerceIn(-1f, 1f),
                shadows = json.optDouble("shadows", 0.0).toFloat().coerceIn(-1f, 1f),
                whites = json.optDouble("whites", 0.0).toFloat().coerceIn(-1f, 1f),
                blacks = json.optDouble("blacks", 0.0).toFloat().coerceIn(-1f, 1f),
                fade = json.optDouble("fade", 0.0).toFloat().coerceIn(0f, 1f),
                grainIntensity = json.optDouble("grain", 0.25).toFloat().coerceIn(0f, 1f),
                halationIntensity = json.optDouble("halation", 0.20).toFloat().coerceIn(0f, 1f),
                shadowsTintR = json.optDouble("shadowTintR", 0.0).toFloat().coerceIn(-1f, 1f),
                shadowsTintG = json.optDouble("shadowTintG", 0.0).toFloat().coerceIn(-1f, 1f),
                shadowsTintB = json.optDouble("shadowTintB", 0.0).toFloat().coerceIn(-1f, 1f),
                highlightsTintR = json.optDouble("highlightTintR", 0.0).toFloat().coerceIn(-1f, 1f),
                highlightsTintG = json.optDouble("highlightTintG", 0.0).toFloat().coerceIn(-1f, 1f),
                highlightsTintB = json.optDouble("highlightTintB", 0.0).toFloat().coerceIn(-1f, 1f)
            )

            DirectorAdvice(
                critique = critique,
                targetBox = targetBox,
                suggestedAdjustments = adjustments,
                isOnlineResult = true
            )
        } catch (_: Exception) {
            generateEdgeFallbackAdvice(sourceBitmap)
        }
    }

    private fun generateEdgeFallbackAdvice(bitmap: Bitmap): DirectorAdvice {
        var totalR = 0f
        var totalG = 0f
        var totalB = 0f
        var sampleCount = 0
        val step = 16

        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                totalR += (pixel shr 16 and 0xFF) / 255f
                totalG += (pixel shr 8 and 0xFF) / 255f
                totalB += (pixel and 0xFF) / 255f
                sampleCount++
            }
        }

        val avgR = if (sampleCount > 0) totalR / sampleCount else 0.5f
        val avgG = if (sampleCount > 0) totalG / sampleCount else 0.5f
        val avgB = if (sampleCount > 0) totalB / sampleCount else 0.5f
        val avgLuma = 0.299f * avgR + 0.587f * avgG + 0.114f * avgB

        val isWarm = avgR > avgB + 0.05f
        val isLowKey = avgLuma < 0.40f

        val critique = when {
            isLowKey && isWarm -> "Moody chiaroscuro detected; lift shadows slightly to preserve shadow roll-off."
            isLowKey -> "Subtle cool tones; center subject on golden ratio for cinematic depth."
            isWarm -> "Warm ambient golden hour; enhance halation bloom and soften contrast."
            else -> "Balanced daytime exposure; apply Portra 400 pastel curves and subtle film grain."
        }

        val fallbackAdjustments = DarkroomAdjustments(
            temperature = if (isWarm) -0.08f else 0.12f,
            tint = 0.03f,
            contrast = if (isLowKey) 1.15f else 1.08f,
            highlights = -0.18f,
            shadows = if (isLowKey) 0.22f else 0.10f,
            fade = 0.06f,
            grainIntensity = 0.24f,
            halationIntensity = 0.28f,
            shadowsTintB = 0.08f,
            highlightsTintR = 0.12f,
            highlightsTintG = 0.06f
        )

        return DirectorAdvice(
            critique = critique,
            targetBox = DrawnTargetBox(0.20f, 0.20f, 0.80f, 0.80f),
            suggestedAdjustments = fallbackAdjustments,
            isOnlineResult = false
        )
    }
}
