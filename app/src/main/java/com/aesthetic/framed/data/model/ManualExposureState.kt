package com.aesthetic.framed.data.model

import java.util.Locale

enum class FlashMode(val title: String) {
    OFF("OFF"),
    ON("ON"),
    AUTO("AUTO"),
    TORCH("TORCH")
}

data class ManualExposureState(
    val isManualMode: Boolean = false,
    val iso: Int = 100,
    val shutterSpeedNanos: Long = 1_000_000_000L / 125L, // 1/125s
    val exposureCompensationIndex: Int = 0,
    val flashMode: FlashMode = FlashMode.OFF,
    val isRawSupported: Boolean = false,
    val isRawEnabled: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val isTelephotoRawRestricted: Boolean = false
) {
    val shutterSpeedDisplay: String
        get() {
            val seconds = shutterSpeedNanos.toDouble() / 1_000_000_000.0
            return if (seconds >= 1.0) {
                String.format(Locale.US, "%.1fs", seconds)
            } else {
                val denominator = Math.round(1.0 / seconds)
                "1/${denominator}s"
            }
        }

    val isoDisplay: String
        get() = "ISO $iso"

    val evDisplay: String
        get() {
            val ev = exposureCompensationIndex * 0.5f
            return if (ev >= 0) String.format(Locale.US, "+%.1f EV", ev) else String.format(Locale.US, "%.1f EV", ev)
        }
}
