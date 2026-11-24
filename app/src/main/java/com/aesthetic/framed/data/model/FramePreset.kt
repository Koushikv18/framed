package com.aesthetic.framed.data.model

enum class FramePreset(val id: String, val title: String, val description: String) {
    NONE("none", "NONE", "Borderless pure image"),
    WHITE("white", "WHITE", "Fine-art museum white matting"),
    PAPER("paper", "PAPER", "Textured warm parchment with inner shadow"),
    BLACK("black", "BLACK", "Deep matte black gallery matting"),
    NEGATIVE("negative", "NEGATIVE", "35mm film strip with sprocket holes & edge codes"),
    SPECS("specs", "SPECS", "Minimalist border displaying camera & lens EXIF metadata")
}

data class ExportOptions(
    val framePreset: FramePreset = FramePreset.SPECS,
    val isDateStampEnabled: Boolean = true,
    val customDateText: String? = null
)
