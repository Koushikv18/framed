package com.aesthetic.framed.domain.recipe

import android.content.Context
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.ExportOptions
import com.aesthetic.framed.data.model.FilmPreset
import com.aesthetic.framed.data.model.FramePreset
import org.json.JSONObject
import java.io.File

data class EditRecipe(
    val id: String,
    val sourceUriString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val adjustments: DarkroomAdjustments = DarkroomAdjustments(),
    val exportOptions: ExportOptions = ExportOptions()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("id", id)
        root.put("sourceUriString", sourceUriString)
        root.put("timestamp", timestamp)

        val adj = JSONObject().apply {
            put("preset", adjustments.preset.name)
            // Light
            put("exposure", adjustments.exposure.toDouble())
            put("contrast", adjustments.contrast.toDouble())
            put("highlights", adjustments.highlights.toDouble())
            put("shadows", adjustments.shadows.toDouble())
            put("whites", adjustments.whites.toDouble())
            put("blacks", adjustments.blacks.toDouble())
            put("fade", adjustments.fade.toDouble())
            put("temperature", adjustments.temperature.toDouble())
            put("tint", adjustments.tint.toDouble())

            // HSL
            put("hslRedHue", adjustments.hslRedHue.toDouble())
            put("hslRedSat", adjustments.hslRedSat.toDouble())
            put("hslRedLum", adjustments.hslRedLum.toDouble())

            put("hslOrangeHue", adjustments.hslOrangeHue.toDouble())
            put("hslOrangeSat", adjustments.hslOrangeSat.toDouble())
            put("hslOrangeLum", adjustments.hslOrangeLum.toDouble())

            put("hslYellowHue", adjustments.hslYellowHue.toDouble())
            put("hslYellowSat", adjustments.hslYellowSat.toDouble())
            put("hslYellowLum", adjustments.hslYellowLum.toDouble())

            put("hslGreenHue", adjustments.hslGreenHue.toDouble())
            put("hslGreenSat", adjustments.hslGreenSat.toDouble())
            put("hslGreenLum", adjustments.hslGreenLum.toDouble())

            put("hslBlueHue", adjustments.hslBlueHue.toDouble())
            put("hslBlueSat", adjustments.hslBlueSat.toDouble())
            put("hslBlueLum", adjustments.hslBlueLum.toDouble())

            put("hslMagentaHue", adjustments.hslMagentaHue.toDouble())
            put("hslMagentaSat", adjustments.hslMagentaSat.toDouble())
            put("hslMagentaLum", adjustments.hslMagentaLum.toDouble())

            // Color grading
            put("shadowsTintR", adjustments.shadowsTintR.toDouble())
            put("shadowsTintG", adjustments.shadowsTintG.toDouble())
            put("shadowsTintB", adjustments.shadowsTintB.toDouble())

            put("midtonesTintR", adjustments.midtonesTintR.toDouble())
            put("midtonesTintG", adjustments.midtonesTintG.toDouble())
            put("midtonesTintB", adjustments.midtonesTintB.toDouble())

            put("highlightsTintR", adjustments.highlightsTintR.toDouble())
            put("highlightsTintG", adjustments.highlightsTintG.toDouble())
            put("highlightsTintB", adjustments.highlightsTintB.toDouble())

            // Curves & Calib
            put("curveMaster", adjustments.curveMaster.toDouble())
            put("curveRed", adjustments.curveRed.toDouble())
            put("curveGreen", adjustments.curveGreen.toDouble())
            put("curveBlue", adjustments.curveBlue.toDouble())
            put("calibRed", adjustments.calibRed.toDouble())
            put("calibGreen", adjustments.calibGreen.toDouble())
            put("calibBlue", adjustments.calibBlue.toDouble())

            // Effects
            put("grainIntensity", adjustments.grainIntensity.toDouble())
            put("grainSize", adjustments.grainSize.toDouble())
            put("halationIntensity", adjustments.halationIntensity.toDouble())
            put("glowIntensity", adjustments.glowIntensity.toDouble())
            put("lightLeakIntensity", adjustments.lightLeakIntensity.toDouble())
            put("lightLeakMode", adjustments.lightLeakMode)
            put("scratchesIntensity", adjustments.scratchesIntensity.toDouble())
            put("dustIntensity", adjustments.dustIntensity.toDouble())
            put("vignette", adjustments.vignette.toDouble())
            put("motionBlur", adjustments.motionBlur.toDouble())
        }
        root.put("adjustments", adj)

        val exp = JSONObject().apply {
            put("framePreset", exportOptions.framePreset.name)
            put("isDateStampEnabled", exportOptions.isDateStampEnabled)
            exportOptions.customDateText?.let { put("customDateText", it) }
        }
        root.put("exportOptions", exp)

        return root
    }

    companion object {
        fun fromJson(json: JSONObject): EditRecipe {
            val id = json.getString("id")
            val uri = json.getString("sourceUriString")
            val time = json.optLong("timestamp", System.currentTimeMillis())

            val adjObj = json.optJSONObject("adjustments")
            val adjustments = if (adjObj != null) {
                DarkroomAdjustments(
                    preset = try { FilmPreset.valueOf(adjObj.getString("preset")) } catch (_: Exception) { FilmPreset.ORIGINAL },
                    exposure = adjObj.optDouble("exposure", 0.0).toFloat(),
                    contrast = adjObj.optDouble("contrast", 1.0).toFloat(),
                    highlights = adjObj.optDouble("highlights", 0.0).toFloat(),
                    shadows = adjObj.optDouble("shadows", 0.0).toFloat(),
                    whites = adjObj.optDouble("whites", 0.0).toFloat(),
                    blacks = adjObj.optDouble("blacks", 0.0).toFloat(),
                    fade = adjObj.optDouble("fade", 0.0).toFloat(),
                    temperature = adjObj.optDouble("temperature", 0.0).toFloat(),
                    tint = adjObj.optDouble("tint", 0.0).toFloat(),

                    hslRedHue = adjObj.optDouble("hslRedHue", 0.0).toFloat(),
                    hslRedSat = adjObj.optDouble("hslRedSat", 0.0).toFloat(),
                    hslRedLum = adjObj.optDouble("hslRedLum", 0.0).toFloat(),

                    hslOrangeHue = adjObj.optDouble("hslOrangeHue", 0.0).toFloat(),
                    hslOrangeSat = adjObj.optDouble("hslOrangeSat", 0.0).toFloat(),
                    hslOrangeLum = adjObj.optDouble("hslOrangeLum", 0.0).toFloat(),

                    hslYellowHue = adjObj.optDouble("hslYellowHue", 0.0).toFloat(),
                    hslYellowSat = adjObj.optDouble("hslYellowSat", 0.0).toFloat(),
                    hslYellowLum = adjObj.optDouble("hslYellowLum", 0.0).toFloat(),

                    hslGreenHue = adjObj.optDouble("hslGreenHue", 0.0).toFloat(),
                    hslGreenSat = adjObj.optDouble("hslGreenSat", 0.0).toFloat(),
                    hslGreenLum = adjObj.optDouble("hslGreenLum", 0.0).toFloat(),

                    hslBlueHue = adjObj.optDouble("hslBlueHue", 0.0).toFloat(),
                    hslBlueSat = adjObj.optDouble("hslBlueSat", 0.0).toFloat(),
                    hslBlueLum = adjObj.optDouble("hslBlueLum", 0.0).toFloat(),

                    hslMagentaHue = adjObj.optDouble("hslMagentaHue", 0.0).toFloat(),
                    hslMagentaSat = adjObj.optDouble("hslMagentaSat", 0.0).toFloat(),
                    hslMagentaLum = adjObj.optDouble("hslMagentaLum", 0.0).toFloat(),

                    shadowsTintR = adjObj.optDouble("shadowsTintR", 0.0).toFloat(),
                    shadowsTintG = adjObj.optDouble("shadowsTintG", 0.0).toFloat(),
                    shadowsTintB = adjObj.optDouble("shadowsTintB", 0.0).toFloat(),

                    midtonesTintR = adjObj.optDouble("midtonesTintR", 0.0).toFloat(),
                    midtonesTintG = adjObj.optDouble("midtonesTintG", 0.0).toFloat(),
                    midtonesTintB = adjObj.optDouble("midtonesTintB", 0.0).toFloat(),

                    highlightsTintR = adjObj.optDouble("highlightsTintR", 0.0).toFloat(),
                    highlightsTintG = adjObj.optDouble("highlightsTintG", 0.0).toFloat(),
                    highlightsTintB = adjObj.optDouble("highlightsTintB", 0.0).toFloat(),

                    curveMaster = adjObj.optDouble("curveMaster", 0.0).toFloat(),
                    curveRed = adjObj.optDouble("curveRed", 0.0).toFloat(),
                    curveGreen = adjObj.optDouble("curveGreen", 0.0).toFloat(),
                    curveBlue = adjObj.optDouble("curveBlue", 0.0).toFloat(),
                    calibRed = adjObj.optDouble("calibRed", 0.0).toFloat(),
                    calibGreen = adjObj.optDouble("calibGreen", 0.0).toFloat(),
                    calibBlue = adjObj.optDouble("calibBlue", 0.0).toFloat(),

                    grainIntensity = adjObj.optDouble("grainIntensity", 0.15).toFloat(),
                    grainSize = adjObj.optDouble("grainSize", 1.0).toFloat(),
                    halationIntensity = adjObj.optDouble("halationIntensity", 0.20).toFloat(),
                    glowIntensity = adjObj.optDouble("glowIntensity", 0.0).toFloat(),
                    lightLeakIntensity = adjObj.optDouble("lightLeakIntensity", 0.0).toFloat(),
                    lightLeakMode = adjObj.optInt("lightLeakMode", 0),
                    scratchesIntensity = adjObj.optDouble("scratchesIntensity", 0.0).toFloat(),
                    dustIntensity = adjObj.optDouble("dustIntensity", 0.0).toFloat(),
                    vignette = adjObj.optDouble("vignette", 0.0).toFloat(),
                    motionBlur = adjObj.optDouble("motionBlur", 0.0).toFloat()
                )
            } else {
                DarkroomAdjustments()
            }

            val expObj = json.optJSONObject("exportOptions")
            val exportOptions = if (expObj != null) {
                ExportOptions(
                    framePreset = try { FramePreset.valueOf(expObj.getString("framePreset")) } catch (_: Exception) { FramePreset.SPECS },
                    isDateStampEnabled = expObj.optBoolean("isDateStampEnabled", true),
                    customDateText = expObj.optString("customDateText").takeIf { it.isNotBlank() }
                )
            } else {
                ExportOptions()
            }

            return EditRecipe(
                id = id,
                sourceUriString = uri,
                timestamp = time,
                adjustments = adjustments,
                exportOptions = exportOptions
            )
        }
    }
}

class EditRecipeStore(private val context: Context) {

    private val recipeDir: File
        get() = File(context.filesDir, "recipes").apply {
            if (!exists()) mkdirs()
        }

    fun saveRecipe(recipe: EditRecipe) {
        try {
            val file = File(recipeDir, "${recipe.id}.json")
            file.writeText(recipe.toJson().toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecipe(id: String): EditRecipe? {
        return try {
            val file = File(recipeDir, "$id.json")
            if (file.exists()) {
                val json = JSONObject(file.readText())
                EditRecipe.fromJson(json)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteRecipe(id: String): Boolean {
        return try {
            val file = File(recipeDir, "$id.json")
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    fun getAllRecipes(): List<EditRecipe> {
        return try {
            recipeDir.listFiles { _, name -> name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        EditRecipe.fromJson(JSONObject(file.readText()))
                    } catch (_: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.timestamp }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
