package com.aesthetic.framed.data.model

data class PhotoMetadata(
    val deviceModel: String = "FRAMED CAMERA",
    val focalLength: String = "24mm eq.",
    val aperture: String = "f/1.8",
    val shutterSpeed: String = "1/250s",
    val iso: String = "ISO 100",
    val captureDate: String = "",
    val lcdDateStamp: String = "'98 11 24"
) {
    val editorialSpecsLine: String
        get() = "$deviceModel  •  $focalLength  $aperture  •  $shutterSpeed  $iso"
}
