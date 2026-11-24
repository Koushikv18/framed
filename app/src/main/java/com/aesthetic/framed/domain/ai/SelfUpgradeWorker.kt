package com.aesthetic.framed.domain.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aesthetic.framed.BuildConfig
import com.aesthetic.framed.data.model.CustomFilmPreset
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.FilmPreset
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Tier 3 — Self-Upgrading Aesthetic Trends & OTA Model Loop.
 * 1. Periodically and on-demand fetches fresh curated aesthetic photography trends
 *    and generates trending presets saved to filesDir/presets/trending_presets.json.
 * 2. Checks remote model manifest (MODEL_MANIFEST_URL) and downloads newer TFLite weights
 *    with SHA-256 verification into filesDir/models/.
 */
class SelfUpgradeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            syncTrendingPresets()
            checkAndDownloadModelWeights()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun syncTrendingPresets() {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val presets = mutableListOf<CustomFilmPreset>()

        if (apiKey.isNotBlank()) {
            try {
                val model = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = apiKey)
                val prompt = """
                    Generate 3 trending aesthetic film recipes inspired by contemporary editorial photography,
                    cinema 35mm film stocks, and fine-art galleries (e.g. CineLux 800T, Tokyo Pastel, Desert Amber).
                    Return ONLY a JSON array of 3 objects with schema:
                    [
                      {
                        "id": "cinelux_800t",
                        "title": "CINELUX 800T",
                        "description": "Tungsten balanced, cyan shadows & rich red halation bloom",
                        "temperature": -0.15,
                        "tint": 0.05,
                        "contrast": 0.18,
                        "highlights": -0.22,
                        "shadows": 0.14,
                        "grain": 0.38,
                        "halation": 0.45,
                        "shadowTintR": -0.08,
                        "shadowTintG": 0.05,
                        "shadowTintB": 0.12,
                        "highlightTintR": 0.18,
                        "highlightTintG": 0.06,
                        "highlightTintB": -0.04
                      }
                    ]
                """.trimIndent()

                val response = model.generateContent(prompt)
                val text = response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: ""
                val array = JSONArray(text)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    presets.add(
                        CustomFilmPreset(
                            id = obj.optString("id", "trend_$i"),
                            title = obj.optString("title", "TRENDING ${i + 1}"),
                            description = obj.optString("description", "Contemporary aesthetic color grade"),
                            adjustments = DarkroomAdjustments(
                                temperature = obj.optDouble("temperature", 0.0).toFloat(),
                                tint = obj.optDouble("tint", 0.0).toFloat(),
                                contrast = obj.optDouble("contrast", 0.1).toFloat(),
                                highlights = obj.optDouble("highlights", -0.1).toFloat(),
                                shadows = obj.optDouble("shadows", 0.1).toFloat(),
                                grainIntensity = obj.optDouble("grain", 0.3).toFloat(),
                                halationIntensity = obj.optDouble("halation", 0.35).toFloat(),
                                shadowsTintR = obj.optDouble("shadowTintR", 0.0).toFloat(),
                                shadowsTintG = obj.optDouble("shadowTintG", 0.0).toFloat(),
                                shadowsTintB = obj.optDouble("shadowTintB", 0.0).toFloat(),
                                highlightsTintR = obj.optDouble("highlightTintR", 0.0).toFloat(),
                                highlightsTintG = obj.optDouble("highlightTintG", 0.0).toFloat(),
                                highlightsTintB = obj.optDouble("highlightTintB", 0.0).toFloat()
                            )
                        )
                    )
                }
            } catch (_: Exception) {
                // Fall back to curated trending presets
            }
        }

        if (presets.isEmpty()) {
            // Built-in curated trending presets (CineLux 800T, Tokyo Pastel, Nordic Minimal)
            presets.add(
                CustomFilmPreset(
                    id = "cinelux_800t",
                    title = "CINELUX 800T",
                    description = "Tungsten balanced, cyan shadows & rich red halation bloom",
                    adjustments = DarkroomAdjustments(
                        temperature = -0.15f,
                        tint = 0.04f,
                        contrast = 0.16f,
                        highlights = -0.20f,
                        shadows = 0.12f,
                        grainIntensity = 0.40f,
                        halationIntensity = 0.45f,
                        shadowsTintB = 0.12f,
                        highlightsTintR = 0.18f
                    )
                )
            )
            presets.add(
                CustomFilmPreset(
                    id = "tokyo_pastel",
                    title = "TOKYO PASTEL",
                    description = "Soft medium-format Japanese aesthetic, lifted shadows, mint & rose",
                    adjustments = DarkroomAdjustments(
                        temperature = 0.08f,
                        tint = 0.06f,
                        contrast = -0.05f,
                        highlights = -0.30f,
                        shadows = 0.25f,
                        fade = 0.10f,
                        grainIntensity = 0.22f,
                        halationIntensity = 0.20f,
                        shadowsTintG = 0.08f,
                        highlightsTintR = 0.12f
                    )
                )
            )
            presets.add(
                CustomFilmPreset(
                    id = "nordic_minimal",
                    title = "NORDIC MINIMAL",
                    description = "Subtle desaturated coolness, crisp monochrome contrast with deep blacks",
                    adjustments = DarkroomAdjustments(
                        temperature = -0.10f,
                        tint = -0.02f,
                        contrast = 0.22f,
                        highlights = -0.15f,
                        shadows = -0.08f,
                        grainIntensity = 0.18f,
                        halationIntensity = 0.10f,
                        vignette = 0.15f
                    )
                )
            )
        }

        savePresetsToFile(presets)
    }

    private fun savePresetsToFile(presets: List<CustomFilmPreset>) {
        val presetsDir = File(applicationContext.filesDir, "presets").apply {
            if (!exists()) mkdirs()
        }
        val file = File(presetsDir, "trending_presets.json")
        val jsonArray = JSONArray()

        for (p in presets) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("description", p.description)
                put("temperature", p.adjustments.temperature.toDouble())
                put("tint", p.adjustments.tint.toDouble())
                put("contrast", p.adjustments.contrast.toDouble())
                put("highlights", p.adjustments.highlights.toDouble())
                put("shadows", p.adjustments.shadows.toDouble())
                put("fade", p.adjustments.fade.toDouble())
                put("grain", p.adjustments.grainIntensity.toDouble())
                put("halation", p.adjustments.halationIntensity.toDouble())
                put("shadowTintR", p.adjustments.shadowsTintR.toDouble())
                put("shadowTintG", p.adjustments.shadowsTintG.toDouble())
                put("shadowTintB", p.adjustments.shadowsTintB.toDouble())
                put("highlightTintR", p.adjustments.highlightsTintR.toDouble())
                put("highlightTintG", p.adjustments.highlightsTintG.toDouble())
                put("highlightTintB", p.adjustments.highlightsTintB.toDouble())
            }
            jsonArray.put(obj)
        }

        file.writeText(jsonArray.toString(2))
    }

    private fun checkAndDownloadModelWeights() {
        val manifestUrl = BuildConfig.MODEL_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return

        try {
            val request = Request.Builder().url(manifestUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return

            val manifestText = response.body?.string() ?: return
            val manifestJson = JSONObject(manifestText)

            val modelUrl = manifestJson.optString("model_url")
            val expectedSha256 = manifestJson.optString("sha256")
            if (modelUrl.isBlank()) return

            val modelRequest = Request.Builder().url(modelUrl).build()
            val modelResponse = httpClient.newCall(modelRequest).execute()
            if (!modelResponse.isSuccessful) return

            val modelBytes = modelResponse.body?.bytes() ?: return

            // Verify SHA-256
            if (expectedSha256.isNotBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(modelBytes)
                val calculatedSha = hashBytes.joinToString("") { "%02x".format(it) }
                if (!calculatedSha.equals(expectedSha256, ignoreCase = true)) {
                    return
                }
            }

            val modelsDir = File(applicationContext.filesDir, "models").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(modelsDir, "aesthetic_scorer_v2.tflite")
            FileOutputStream(targetFile).use { it.write(modelBytes) }
        } catch (_: Exception) {
            // Network or parsing failure; keep current model
        }
    }

    companion object {
        fun loadTrendingPresets(context: Context): List<CustomFilmPreset> {
            val file = File(context.filesDir, "presets/trending_presets.json")
            if (!file.exists()) return emptyList()

            return try {
                val array = JSONArray(file.readText())
                val list = mutableListOf<CustomFilmPreset>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CustomFilmPreset(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            description = obj.optString("description"),
                            adjustments = DarkroomAdjustments(
                                temperature = obj.optDouble("temperature", 0.0).toFloat(),
                                tint = obj.optDouble("tint", 0.0).toFloat(),
                                contrast = obj.optDouble("contrast", 0.0).toFloat(),
                                highlights = obj.optDouble("highlights", 0.0).toFloat(),
                                shadows = obj.optDouble("shadows", 0.0).toFloat(),
                                fade = obj.optDouble("fade", 0.0).toFloat(),
                                grainIntensity = obj.optDouble("grain", 0.2).toFloat(),
                                halationIntensity = obj.optDouble("halation", 0.2).toFloat(),
                                shadowsTintR = obj.optDouble("shadowTintR", 0.0).toFloat(),
                                shadowsTintG = obj.optDouble("shadowTintG", 0.0).toFloat(),
                                shadowsTintB = obj.optDouble("shadowTintB", 0.0).toFloat(),
                                highlightsTintR = obj.optDouble("highlightTintR", 0.0).toFloat(),
                                highlightsTintG = obj.optDouble("highlightTintG", 0.0).toFloat(),
                                highlightsTintB = obj.optDouble("highlightTintB", 0.0).toFloat()
                            )
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
