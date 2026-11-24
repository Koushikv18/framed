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
            put("exposure", adjustments.exposure.toDouble())
            put("contrast", adjustments.contrast.toDouble())
            put("highlights", adjustments.highlights.toDouble())
            put("shadows", adjustments.shadows.toDouble())
            put("curveRed", adjustments.curveRed.toDouble())
            put("curveGreen", adjustments.curveGreen.toDouble())
            put("curveBlue", adjustments.curveBlue.toDouble())
            put("hslRedSat", adjustments.hslRedSat.toDouble())
            put("hslOrangeSat", adjustments.hslOrangeSat.toDouble())
            put("hslYellowSat", adjustments.hslYellowSat.toDouble())
            put("hslGreenSat", adjustments.hslGreenSat.toDouble())
            put("hslBlueSat", adjustments.hslBlueSat.toDouble())
            put("hslMagentaSat", adjustments.hslMagentaSat.toDouble())
            put("grainIntensity", adjustments.grainIntensity.toDouble())
            put("halationIntensity", adjustments.halationIntensity.toDouble())
            put("lightLeakIntensity", adjustments.lightLeakIntensity.toDouble())
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
                    curveRed = adjObj.optDouble("curveRed", 0.0).toFloat(),
                    curveGreen = adjObj.optDouble("curveGreen", 0.0).toFloat(),
                    curveBlue = adjObj.optDouble("curveBlue", 0.0).toFloat(),
                    hslRedSat = adjObj.optDouble("hslRedSat", 0.0).toFloat(),
                    hslOrangeSat = adjObj.optDouble("hslOrangeSat", 0.0).toFloat(),
                    hslYellowSat = adjObj.optDouble("hslYellowSat", 0.0).toFloat(),
                    hslGreenSat = adjObj.optDouble("hslGreenSat", 0.0).toFloat(),
                    hslBlueSat = adjObj.optDouble("hslBlueSat", 0.0).toFloat(),
                    hslMagentaSat = adjObj.optDouble("hslMagentaSat", 0.0).toFloat(),
                    grainIntensity = adjObj.optDouble("grainIntensity", 0.15).toFloat(),
                    halationIntensity = adjObj.optDouble("halationIntensity", 0.20).toFloat(),
                    lightLeakIntensity = adjObj.optDouble("lightLeakIntensity", 0.0).toFloat()
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
