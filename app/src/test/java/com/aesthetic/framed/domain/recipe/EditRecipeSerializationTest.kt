package com.aesthetic.framed.domain.recipe

import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.ExportOptions
import com.aesthetic.framed.data.model.FilmPreset
import com.aesthetic.framed.data.model.FramePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class EditRecipeSerializationTest {

    @Test
    fun testEditRecipeToJsonAndFromJson() {
        val adjustments = DarkroomAdjustments(
            preset = FilmPreset.N1600,
            exposure = 0.75f,
            contrast = 1.3f,
            highlights = -0.4f,
            shadows = 0.2f,
            fade = 0.15f,
            temperature = 0.3f,
            tint = -0.1f,
            grainIntensity = 0.45f,
            grainSize = 1.2f,
            halationIntensity = 0.35f,
            vignette = 0.5f
        )
        val exportOptions = ExportOptions(
            framePreset = FramePreset.WHITE,
            isDateStampEnabled = true,
            customDateText = "98 11 24"
        )
        val original = EditRecipe(
            id = "test-recipe-123",
            sourceUriString = "content://media/external/images/media/42",
            timestamp = 1718000000000L,
            adjustments = adjustments,
            exportOptions = exportOptions
        )

        val json = original.toJson()
        val parsed = EditRecipe.fromJson(json)

        assertEquals(original.id, parsed.id)
        assertEquals(original.sourceUriString, parsed.sourceUriString)
        assertEquals(original.timestamp, parsed.timestamp)

        // Verify adjustments
        assertEquals(original.adjustments.preset, parsed.adjustments.preset)
        assertEquals(original.adjustments.exposure, parsed.adjustments.exposure, 0.001f)
        assertEquals(original.adjustments.contrast, parsed.adjustments.contrast, 0.001f)
        assertEquals(original.adjustments.highlights, parsed.adjustments.highlights, 0.001f)
        assertEquals(original.adjustments.shadows, parsed.adjustments.shadows, 0.001f)
        assertEquals(original.adjustments.grainIntensity, parsed.adjustments.grainIntensity, 0.001f)
        assertEquals(original.adjustments.halationIntensity, parsed.adjustments.halationIntensity, 0.001f)
        assertEquals(original.adjustments.vignette, parsed.adjustments.vignette, 0.001f)

        // Verify export options
        assertEquals(original.exportOptions.framePreset, parsed.exportOptions.framePreset)
        assertEquals(original.exportOptions.isDateStampEnabled, parsed.exportOptions.isDateStampEnabled)
        assertEquals(original.exportOptions.customDateText, parsed.exportOptions.customDateText)
    }
}
