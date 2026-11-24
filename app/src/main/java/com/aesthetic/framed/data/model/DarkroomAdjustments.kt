package com.aesthetic.framed.data.model

enum class FilmPreset(
    val id: String,
    val title: String,
    val description: String,
    val grainDefault: Float,
    val halationDefault: Float
) {
    ORIGINAL(
        id = "original",
        title = "ORIGINAL",
        description = "Standard sensor balance",
        grainDefault = 0.0f,
        halationDefault = 0.0f
    ),
    N1600(
        id = "n1600",
        title = "N1600",
        description = "High-contrast moody color, teal shadows & heavy grain",
        grainDefault = 0.45f,
        halationDefault = 0.25f
    ),
    P400(
        id = "p400",
        title = "P400",
        description = "Warm pastel portra-style, soft golden skin tones",
        grainDefault = 0.25f,
        halationDefault = 0.35f
    ),
    RELIC(
        id = "relic",
        title = "RELIC",
        description = "Faded 70s slide, lifted blacks, amber & vintage green",
        grainDefault = 0.35f,
        halationDefault = 0.40f
    ),
    MONO(
        id = "mono",
        title = "MONO",
        description = "High-contrast black & white with silver halide grain",
        grainDefault = 0.50f,
        halationDefault = 0.15f
    )
}

data class DarkroomAdjustments(
    val preset: FilmPreset = FilmPreset.ORIGINAL,
    val exposure: Float = 0.0f, // -2.0 .. +2.0
    val contrast: Float = 1.0f, // 0.5 .. 2.0
    val highlights: Float = 0.0f, // -1.0 .. +1.0
    val shadows: Float = 0.0f, // -1.0 .. +1.0

    // RGB Tone Curve shifts
    val curveRed: Float = 0.0f, // -1.0 .. +1.0
    val curveGreen: Float = 0.0f,
    val curveBlue: Float = 0.0f,

    // 6-Channel HSL Mixer (Hue: -1..1, Sat: -1..1, Lum: -1..1)
    val hslRedSat: Float = 0.0f,
    val hslOrangeSat: Float = 0.0f,
    val hslYellowSat: Float = 0.0f,
    val hslGreenSat: Float = 0.0f,
    val hslBlueSat: Float = 0.0f,
    val hslMagentaSat: Float = 0.0f,

    // Organic textures & optical artifacts
    val grainIntensity: Float = 0.15f, // 0.0 .. 1.0
    val halationIntensity: Float = 0.20f, // 0.0 .. 1.0
    val lightLeakIntensity: Float = 0.0f // 0.0 .. 1.0
)
