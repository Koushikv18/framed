package com.aesthetic.framed.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.framed.data.model.FlashMode
import com.aesthetic.framed.data.model.ManualExposureState
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioBorder
import com.aesthetic.framed.ui.theme.StudioDark
import com.aesthetic.framed.ui.theme.StudioSurface
import com.aesthetic.framed.ui.theme.StudioText
import com.aesthetic.framed.ui.theme.StudioTextMuted
import com.aesthetic.framed.ui.theme.StudioTextSecondary

private val ISO_VALUES = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
private val SHUTTER_SPEEDS = listOf(
    1_000_000_000L / 2000L,
    1_000_000_000L / 1000L,
    1_000_000_000L / 500L,
    1_000_000_000L / 250L,
    1_000_000_000L / 125L,
    1_000_000_000L / 60L,
    1_000_000_000L / 30L,
    1_000_000_000L / 15L,
    1_000_000_000L / 8L,
    1_000_000_000L / 4L,
    1_000_000_000L / 2L,
    1_000_000_000L
)

@Composable
fun ManualControlsDrawer(
    state: ManualExposureState,
    onManualToggle: (Boolean) -> Unit,
    onIsoSelected: (Int) -> Unit,
    onShutterSpeedSelected: (Long) -> Unit,
    onExposureCompensationChanged: (Int) -> Unit,
    onCycleFlash: () -> Unit,
    onToggleRaw: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(StudioDark.copy(alpha = 0.96f))
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Grab handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(StudioBorder)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Mode Header: MANUAL vs AUTO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (state.isManualMode) "MANUAL EXPOSURE" else "AUTO EXPOSURE (AE)",
                    color = if (state.isManualMode) EmeraldLevel else StudioText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (state.isManualMode) "Direct sensor sensitivity & timing" else "Auto-metering with EV bias",
                    color = StudioTextMuted,
                    fontSize = 10.sp
                )
            }

            Switch(
                checked = state.isManualMode,
                onCheckedChange = onManualToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StudioDark,
                    checkedTrackColor = EmeraldLevel,
                    uncheckedThumbColor = StudioTextMuted,
                    uncheckedTrackColor = StudioSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isManualMode) {
            // ISO Selector
            Text(
                text = "ISO SENSITIVITY: ${state.isoDisplay}",
                color = StudioTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ISO_VALUES.forEach { isoValue ->
                    val isSelected = state.iso == isoValue
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) StudioText else StudioSurface)
                            .clickable { onIsoSelected(isoValue) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$isoValue",
                            color = if (isSelected) StudioDark else StudioText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Speed Selector
            Text(
                text = "SHUTTER SPEED: ${state.shutterSpeedDisplay}",
                color = StudioTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SHUTTER_SPEEDS.forEach { speedNanos ->
                    val isSelected = state.shutterSpeedNanos == speedNanos
                    val seconds = speedNanos.toDouble() / 1_000_000_000.0
                    val speedLabel = if (seconds >= 1.0) "${seconds.toInt()}s" else "1/${Math.round(1.0 / seconds)}s"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) StudioText else StudioSurface)
                            .clickable { onShutterSpeedSelected(speedNanos) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = speedLabel,
                            color = if (isSelected) StudioDark else StudioText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            // EV Compensation Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXPOSURE COMPENSATION",
                    color = StudioTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = state.evDisplay,
                    color = EmeraldLevel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = state.exposureCompensationIndex.toFloat(),
                onValueChange = { onExposureCompensationChanged(it.toInt()) },
                valueRange = -6f..6f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = StudioText,
                    activeTrackColor = StudioText,
                    inactiveTrackColor = StudioSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Controls Row: Flash & RAW DNG
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioSurface)
                    .clickable { onCycleFlash() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FLASH: ",
                    color = StudioTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = state.flashMode.title,
                    color = if (state.flashMode != FlashMode.OFF) EmeraldLevel else StudioText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // DNG RAW Button with S25 Ultra Telephoto Guardrail
            val isRestricted = state.isTelephotoRawRestricted
            val rawAvailable = state.isRawSupported && !isRestricted
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isRestricted -> com.aesthetic.framed.ui.theme.AmberRetro.copy(alpha = 0.15f)
                            state.isRawEnabled && rawAvailable -> EmeraldLevel.copy(alpha = 0.2f)
                            else -> StudioSurface
                        }
                    )
                    .clickable(enabled = rawAvailable) {
                        onToggleRaw(!state.isRawEnabled)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAW DNG: ",
                    color = if (rawAvailable) StudioTextSecondary else StudioTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = when {
                        isRestricted -> "JPEG (TELE)"
                        !state.isRawSupported -> "N/A"
                        state.isRawEnabled -> "ON"
                        else -> "OFF"
                    },
                    color = when {
                        isRestricted -> com.aesthetic.framed.ui.theme.AmberRetro
                        !state.isRawSupported -> StudioTextMuted
                        state.isRawEnabled -> EmeraldLevel
                        else -> StudioText
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
