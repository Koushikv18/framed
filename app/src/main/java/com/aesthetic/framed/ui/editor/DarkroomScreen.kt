package com.aesthetic.framed.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.ExportOptions
import com.aesthetic.framed.data.model.FilmPreset
import com.aesthetic.framed.data.model.FramePreset
import com.aesthetic.framed.domain.gl.FilmShaderRenderer
import com.aesthetic.framed.ui.theme.AmberRetro
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.StudioBlack
import com.aesthetic.framed.ui.theme.StudioBorder
import com.aesthetic.framed.ui.theme.StudioDark
import com.aesthetic.framed.ui.theme.StudioSurface
import com.aesthetic.framed.ui.theme.StudioText
import com.aesthetic.framed.ui.theme.StudioTextMuted
import com.aesthetic.framed.ui.theme.StudioTextSecondary
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private enum class DarkroomTab(val title: String) {
    TONE("TONE"),
    CURVES("CURVES"),
    HSL("HSL"),
    EFFECT("EFFECT"),
    FRAME("FRAME")
}

@Composable
fun DarkroomScreen(
    sourceBitmap: Bitmap,
    onBack: () -> Unit,
    onExportRequested: (Bitmap, DarkroomAdjustments, ExportOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var adjustments by remember { mutableStateOf(DarkroomAdjustments()) }
    var exportOptions by remember { mutableStateOf(ExportOptions()) }
    var activeTab by remember { mutableStateOf(DarkroomTab.TONE) }

    // Compare original button interaction source
    val compareInteraction = remember { MutableInteractionSource() }
    val isComparingOriginal by compareInteraction.collectIsPressedAsState()

    val activeAdjustments = if (isComparingOriginal) DarkroomAdjustments() else adjustments

    val lifecycleOwner = LocalLifecycleOwner.current
    val glRenderer = remember { FilmShaderRenderer() }
    val glSurfaceView = remember {
        DarkroomGLView(context, sourceBitmap, glRenderer).apply {
            setAdjustments(activeAdjustments)
        }
    }

    DisposableEffect(activeAdjustments) {
        glSurfaceView.setAdjustments(activeAdjustments)
        glSurfaceView.requestRender()
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceView.onResume()
                    glSurfaceView.requestRender()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
            glSurfaceView.releaseGL()
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
            // Top Bar: Back, Preset Title, Export Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = StudioText, fontSize = 14.sp)
                }

                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isComparingOriginal) "ORIGINAL (BEFORE)" else adjustments.preset.title,
                        color = if (isComparingOriginal) StudioTextMuted else EmeraldLevel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "FRAME: ${exportOptions.framePreset.title}",
                        color = StudioTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Export Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldLevel)
                        .clickable { onExportRequested(sourceBitmap, adjustments, exportOptions) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "EXPORT",
                        color = StudioBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Viewport hosting GLSurfaceView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageAspect = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()
                Box(
                    modifier = Modifier
                        .aspectRatio(imageAspect)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                ) {
                    AndroidView(
                        factory = { glSurfaceView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Hold to Compare badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioDark.copy(alpha = 0.85f))
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = compareInteraction, indication = null) {}
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "HOLD TO COMPARE",
                            color = StudioTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Presets Horizontal Reel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDark)
                    .padding(vertical = 10.dp, horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilmPreset.values().forEach { preset ->
                    val isSelected = adjustments.preset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) StudioText else StudioSurface)
                            .border(1.dp, if (isSelected) StudioText else StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                adjustments = adjustments.copy(
                                    preset = preset,
                                    grainIntensity = preset.grainDefault,
                                    halationIntensity = preset.halationDefault
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.title,
                            color = if (isSelected) StudioBlack else StudioText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Adjustment Category Tabs (TONE, CURVES, HSL, EFFECT, FRAME)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioBlack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DarkroomTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clickable { activeTab = tab }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) EmeraldLevel else StudioTextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Active Tab Controls Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioDark.copy(alpha = 0.95f))
                    .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                when (activeTab) {
                    DarkroomTab.TONE -> {
                        AdjustmentSlider(
                            name = "EXPOSURE",
                            value = adjustments.exposure,
                            range = -2.0f..2.0f,
                            onValueChange = { adjustments = adjustments.copy(exposure = it) }
                        )
                        AdjustmentSlider(
                            name = "CONTRAST",
                            value = adjustments.contrast,
                            range = 0.5f..2.0f,
                            onValueChange = { adjustments = adjustments.copy(contrast = it) }
                        )
                        AdjustmentSlider(
                            name = "HIGHLIGHTS",
                            value = adjustments.highlights,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(highlights = it) }
                        )
                        AdjustmentSlider(
                            name = "SHADOWS",
                            value = adjustments.shadows,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(shadows = it) }
                        )
                    }
                    DarkroomTab.CURVES -> {
                        AdjustmentSlider(
                            name = "RED CURVE BIAS",
                            value = adjustments.curveRed,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(curveRed = it) }
                        )
                        AdjustmentSlider(
                            name = "GREEN CURVE BIAS",
                            value = adjustments.curveGreen,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(curveGreen = it) }
                        )
                        AdjustmentSlider(
                            name = "BLUE CURVE BIAS",
                            value = adjustments.curveBlue,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(curveBlue = it) }
                        )
                    }
                    DarkroomTab.HSL -> {
                        AdjustmentSlider(
                            name = "RED SATURATION",
                            value = adjustments.hslRedSat,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(hslRedSat = it) }
                        )
                        AdjustmentSlider(
                            name = "ORANGE (SKIN)",
                            value = adjustments.hslOrangeSat,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(hslOrangeSat = it) }
                        )
                        AdjustmentSlider(
                            name = "GREEN SATURATION",
                            value = adjustments.hslGreenSat,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(hslGreenSat = it) }
                        )
                        AdjustmentSlider(
                            name = "BLUE / SKY",
                            value = adjustments.hslBlueSat,
                            range = -1.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(hslBlueSat = it) }
                        )
                    }
                    DarkroomTab.EFFECT -> {
                        AdjustmentSlider(
                            name = "FILM GRAIN",
                            value = adjustments.grainIntensity,
                            range = 0.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(grainIntensity = it) }
                        )
                        AdjustmentSlider(
                            name = "HALATION (BLOOM)",
                            value = adjustments.halationIntensity,
                            range = 0.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(halationIntensity = it) }
                        )
                        AdjustmentSlider(
                            name = "LIGHT LEAKS",
                            value = adjustments.lightLeakIntensity,
                            range = 0.0f..1.0f,
                            onValueChange = { adjustments = adjustments.copy(lightLeakIntensity = it) }
                        )
                    }
                    DarkroomTab.FRAME -> {
                        // Frame Presets selector
                        Text(
                            text = "GALLERY MATTING PRESET",
                            color = StudioTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FramePreset.values().forEach { preset ->
                                val isSelected = exportOptions.framePreset == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) StudioText else StudioSurface)
                                        .border(1.dp, if (isSelected) StudioText else StudioBorder, RoundedCornerShape(8.dp))
                                        .clickable { exportOptions = exportOptions.copy(framePreset = preset) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset.title,
                                        color = if (isSelected) StudioBlack else StudioText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Retro Amber LCD Date Stamp Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "RETRO AMBER DATE STAMP",
                                    color = if (exportOptions.isDateStampEnabled) AmberRetro else StudioText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "90s compact camera LCD imprint ('98 11 24)",
                                    color = StudioTextMuted,
                                    fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = exportOptions.isDateStampEnabled,
                                onCheckedChange = { exportOptions = exportOptions.copy(isDateStampEnabled = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StudioDark,
                                    checkedTrackColor = AmberRetro,
                                    uncheckedThumbColor = StudioTextMuted,
                                    uncheckedTrackColor = StudioSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                color = StudioTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format("%.2f", value),
                color = StudioText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = StudioText,
                activeTrackColor = StudioText,
                inactiveTrackColor = StudioSurface
            )
        )
    }
}

private class DarkroomGLView(
    context: Context,
    private val sourceBitmap: Bitmap,
    private val renderer: FilmShaderRenderer
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private var textureId = 0
    private var activeAdjustments = DarkroomAdjustments()
    private var viewWidth = 0
    private var viewHeight = 0

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setAdjustments(adjustments: DarkroomAdjustments) {
        this.activeAdjustments = adjustments
    }

    fun releaseGL() {
        queueEvent {
            if (textureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            renderer.release()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.initGL()

        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, sourceBitmap, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        if (textureId != 0 && viewWidth > 0 && viewHeight > 0) {
            renderer.render(textureId, viewWidth, viewHeight, activeAdjustments)
        }
    }
}
