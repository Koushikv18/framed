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

enum class HslChannel(val title: String) {
    RED("RED"),
    ORANGE("ORANGE"),
    YELLOW("YELLOW"),
    GREEN("GREEN"),
    BLUE("BLUE"),
    MAGENTA("MAGENTA")
}

data class DarkroomAdjustments(
    val preset: FilmPreset = FilmPreset.ORIGINAL,

    // --- 1. LIGHT CONTROLS ---
    val exposure: Float = 0.0f,     // -2.0 .. +2.0
    val contrast: Float = 1.0f,     // 0.5 .. 2.0
    val highlights: Float = 0.0f,   // -1.0 .. +1.0
    val shadows: Float = 0.0f,      // -1.0 .. +1.0
    val whites: Float = 0.0f,       // -1.0 .. +1.0
    val blacks: Float = 0.0f,       // -1.0 .. +1.0
    val fade: Float = 0.0f,         // 0.0 .. 1.0 (lift black level)
    val temperature: Float = 0.0f,  // -1.0 .. +1.0 (cool to warm)
    val tint: Float = 0.0f,         // -1.0 .. +1.0 (green to magenta)

    // --- 2. 6-CHANNEL HSL MIXER (Hue, Saturation, Luminance) ---
    val hslRedHue: Float = 0.0f,
    val hslRedSat: Float = 0.0f,
    val hslRedLum: Float = 0.0f,

    val hslOrangeHue: Float = 0.0f,
    val hslOrangeSat: Float = 0.0f,
    val hslOrangeLum: Float = 0.0f,

    val hslYellowHue: Float = 0.0f,
    val hslYellowSat: Float = 0.0f,
    val hslYellowLum: Float = 0.0f,

    val hslGreenHue: Float = 0.0f,
    val hslGreenSat: Float = 0.0f,
    val hslGreenLum: Float = 0.0f,

    val hslBlueHue: Float = 0.0f,
    val hslBlueSat: Float = 0.0f,
    val hslBlueLum: Float = 0.0f,

    val hslMagentaHue: Float = 0.0f,
    val hslMagentaSat: Float = 0.0f,
    val hslMagentaLum: Float = 0.0f,

    // --- 3. 3-WAY COLOR GRADING (Shadows, Midtones, Highlights) ---
    val shadowsTintR: Float = 0.0f,
    val shadowsTintG: Float = 0.0f,
    val shadowsTintB: Float = 0.0f,

    val midtonesTintR: Float = 0.0f,
    val midtonesTintG: Float = 0.0f,
    val midtonesTintB: Float = 0.0f,

    val highlightsTintR: Float = 0.0f,
    val highlightsTintG: Float = 0.0f,
    val highlightsTintB: Float = 0.0f,

    // --- 4. MULTI-POINT RGB CURVES & PRIMARY CALIBRATION ---
    val curveMaster: Float = 0.0f,
    val curveRed: Float = 0.0f,
    val curveGreen: Float = 0.0f,
    val curveBlue: Float = 0.0f,

    val calibRed: Float = 0.0f,
    val calibGreen: Float = 0.0f,
    val calibBlue: Float = 0.0f,

    // --- 5. OPTICAL & ANALOG EFFECTS ---
    val grainIntensity: Float = 0.15f,     // 0.0 .. 1.0
    val grainSize: Float = 1.0f,          // 0.5 .. 2.0
    val halationIntensity: Float = 0.20f,  // 0.0 .. 1.0
    val glowIntensity: Float = 0.0f,      // 0.0 .. 1.0 (Pro-Mist highlight bloom)
    val lightLeakIntensity: Float = 0.0f,  // 0.0 .. 1.0
    val lightLeakMode: Int = 0,           // 0: Corner Warm, 1: Top Streak, 2: Rainbow Prism
    val scratchesIntensity: Float = 0.0f,  // 0.0 .. 1.0
    val dustIntensity: Float = 0.0f,       // 0.0 .. 1.0
    val vignette: Float = 0.0f,           // 0.0 .. 1.0
    val motionBlur: Float = 0.0f          // 0.0 .. 1.0
)
