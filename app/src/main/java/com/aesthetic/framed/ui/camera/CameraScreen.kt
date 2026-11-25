package com.aesthetic.framed.ui.camera

import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aesthetic.framed.data.model.CameraAspectRatio
import com.aesthetic.framed.data.model.CoachMode
import com.aesthetic.framed.domain.camera.CameraEngine
import com.aesthetic.framed.domain.sensor.HorizonSensorManager
import com.aesthetic.framed.ui.camera.components.CoachOverlay
import com.aesthetic.framed.ui.camera.components.HorizonLevelIndicator
import com.aesthetic.framed.ui.camera.components.ManualControlsDrawer
import com.aesthetic.framed.ui.camera.components.ShutterButton
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioBlack
import com.aesthetic.framed.ui.theme.StudioBorder
import com.aesthetic.framed.ui.theme.StudioDark
import com.aesthetic.framed.ui.theme.StudioSurface
import com.aesthetic.framed.ui.theme.StudioText
import com.aesthetic.framed.ui.theme.StudioTextMuted

@Composable
fun CameraScreen(
    cameraEngine: CameraEngine,
    horizonSensorManager: HorizonSensorManager,
    onPhotoCaptured: (Uri) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val exposureState by cameraEngine.exposureState.collectAsState()
    val currentRatio by cameraEngine.currentAspectRatio.collectAsState()
    val horizonState by horizonSensorManager.horizonState.collectAsState()
    val compositionState by cameraEngine.compositionAnalyzer.compositionState.collectAsState()

    var isDrawerOpen by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isAutoCaptureEnabled by remember { mutableStateOf(false) }
    var autoCaptureProgress by remember { mutableStateOf(0f) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Alignment, Zoom, and Horizon Lock-in Condition
    val isFullyLockedIn = if (compositionState.coachMode == CoachMode.OFF) {
        horizonState.isLevel
    } else {
        (compositionState.compositionScore >= 85 || compositionState.targetIoU >= 0.55f) && horizonState.isLevel
    }

    // Trigger subtle haptic tick on lock-in transition
    var wasLockedIn by remember { mutableStateOf(false) }
    LaunchedEffect(isFullyLockedIn) {
        if (isFullyLockedIn && !wasLockedIn) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasLockedIn = isFullyLockedIn
    }

    // Hands-free Auto-Capture timer: 800ms continuous lock-in
    LaunchedEffect(isFullyLockedIn, isAutoCaptureEnabled) {
        if (isAutoCaptureEnabled && isFullyLockedIn) {
            val startTime = System.currentTimeMillis()
            val duration = 800L
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                autoCaptureProgress = progress
                if (progress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.takePhoto(
                        onSuccess = { uri ->
                            lastCapturedUri = uri
                            Toast.makeText(context, "Auto-captured: $uri", Toast.LENGTH_SHORT).show()
                            onPhotoCaptured(uri)
                        },
                        onError = { exc ->
                            Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                    autoCaptureProgress = 0f
                    kotlinx.coroutines.delay(1200)
                    break
                }
                kotlinx.coroutines.delay(16)
            }
        } else {
            autoCaptureProgress = 0f
        }
    }

    LaunchedEffect(horizonState) {
        cameraEngine.compositionAnalyzer.updateHorizonState(horizonState)
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    horizonSensorManager.startListening()
                    cameraEngine.resumeCamera(lifecycleOwner, previewView)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    horizonSensorManager.stopListening()
                    cameraEngine.pauseCamera()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        horizonSensorManager.startListening()
        cameraEngine.initialize(lifecycleOwner, previewView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            horizonSensorManager.stopListening()
            cameraEngine.pauseCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Aspect Ratio switcher, Coach Mode switcher, Status Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(StudioBlack)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aspect Ratio Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CameraAspectRatio.values().forEach { ratio ->
                        val isSelected = ratio == currentRatio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) StudioText else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    cameraEngine.setAspectRatio(ratio, lifecycleOwner, previewView)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = ratio.displayName,
                                color = if (isSelected) StudioBlack else StudioTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Coach Mode Switcher Pill (OFF, AUTO, TRACK)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CoachMode.values().forEach { mode ->
                        val isSelected = mode == compositionState.coachMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) {
                                        if (compositionState.isShutterPrimed) EmeraldLevel else StudioText
                                    } else Color.Transparent
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    cameraEngine.compositionAnalyzer.setCoachMode(mode)
                                }
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mode.title,
                                color = if (isSelected) StudioBlack else StudioTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Flash Mode Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            cameraEngine.cycleFlashMode()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = exposureState.flashMode.title,
                        color = StudioText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Central Viewfinder Container with Dynamic Aspect Ratio letterboxing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val targetRatio = currentRatio.ratioFloat

                    Box(
                        modifier = Modifier
                            .aspectRatio(targetRatio, matchHeightConstraintsFirst = maxHeight * targetRatio > maxWidth)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, StudioBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    ) {
                        // CameraX live preview
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Real-time Horizon Level Indicator Overlay
                        HorizonLevelIndicator(
                            horizonState = horizonState,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Composition Coach Overlay (Auto Frame & Draw-to-Track)
                        CoachOverlay(
                            state = compositionState,
                            onTargetBoxDrawn = { box ->
                                cameraEngine.compositionAnalyzer.setDrawnTarget(box)
                            },
                            onScanRequested = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cameraEngine.compositionAnalyzer.scanComposition()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Bar: Quad-Lens Switcher, Manual drawer toggle, Shutter Button, Quick Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioBlack)
                    .padding(bottom = 28.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // S25 Ultra Quad-Lens Switcher dynamically read from CameraCharacteristics
                val lensList by cameraEngine.availableLenses.collectAsState()
                val currentLens = lensList.minByOrNull { Math.abs(exposureState.zoomRatio - it.ratio) } ?: lensList.getOrNull(1) ?: lensList[0]

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    lensList.forEach { lens ->
                        val isSelected = Math.abs(exposureState.zoomRatio - lens.ratio) < 0.2f
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) StudioText else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    cameraEngine.setZoomRatio(lens.ratio, lifecycleOwner, previewView)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lens.label,
                                color = if (isSelected) StudioBlack else StudioTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentLens.focalLength}  ${currentLens.aperture}",
                    color = StudioTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                if (exposureState.isTelephotoRawRestricted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TELEPHOTO ACTIVE: 98% JPEG GUARDRAIL",
                        color = com.aesthetic.framed.ui.theme.AmberRetro,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Exposure Status Pill (clickable to toggle drawer)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isDrawerOpen = !isDrawerOpen
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (exposureState.isManualMode) "MANUAL" else "AUTO",
                        color = if (exposureState.isManualMode) EmeraldLevel else StudioTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (exposureState.isManualMode) "${exposureState.isoDisplay}  ${exposureState.shutterSpeedDisplay}" else exposureState.evDisplay,
                        color = StudioText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Manual Drawer Toggle Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(StudioSurface)
                            .border(1.dp, StudioBorder, CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isDrawerOpen = !isDrawerOpen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = if (isDrawerOpen || exposureState.isManualMode) EmeraldLevel else StudioText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Shutter Button & Auto-Capture Toggle Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShutterButton(
                            isPrimedOrLevel = isFullyLockedIn,
                            autoCaptureProgress = autoCaptureProgress,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                cameraEngine.takePhoto(
                                    onSuccess = { uri ->
                                        lastCapturedUri = uri
                                        Toast.makeText(context, "Photo captured: $uri", Toast.LENGTH_SHORT).show()
                                        onPhotoCaptured(uri)
                                    },
                                    onError = { exc ->
                                        Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )

                        // Optional "AUTO" toggle chip beside the shutter button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAutoCaptureEnabled) EmeraldLevel.copy(alpha = 0.2f) else StudioDark)
                                .border(
                                    width = 1.dp,
                                    color = if (isAutoCaptureEnabled) EmeraldLevel else StudioBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isAutoCaptureEnabled = !isAutoCaptureEnabled
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AUTO",
                                color = if (isAutoCaptureEnabled) EmeraldLevel else StudioTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Framed Library navigation button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurface)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenLibrary()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LIB",
                            color = StudioText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Sliding Manual Controls Drawer
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ManualControlsDrawer(
                state = exposureState,
                onManualToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.updateManualMode(it)
                },
                onIsoSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.updateIso(it)
                },
                onShutterSpeedSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.updateShutterSpeed(it)
                },
                onExposureCompensationChanged = { cameraEngine.updateExposureCompensation(it) },
                onCycleFlash = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.cycleFlashMode()
                },
                onToggleRaw = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraEngine.setRawCaptureEnabled(it, lifecycleOwner, previewView)
                }
            )
        }
    }
}
