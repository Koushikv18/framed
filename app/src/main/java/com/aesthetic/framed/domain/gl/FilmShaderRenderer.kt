package com.aesthetic.framed.domain.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.FilmPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FilmShaderRenderer {

    companion object {
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040

        private const val VERTEX_SHADER = """#version 300 es
layout (location = 0) in vec2 aPosition;
layout (location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform int uPreset;
uniform vec2 uResolution;

// Light
uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uFade;
uniform float uTemperature;
uniform float uTint;

// 6-Channel HSL
uniform vec3 uHslRed;     // Hue, Sat, Lum
uniform vec3 uHslOrange;
uniform vec3 uHslYellow;
uniform vec3 uHslGreen;
uniform vec3 uHslBlue;
uniform vec3 uHslMagenta;

// 3-Way Color Grading
uniform vec3 uShadowsTint;
uniform vec3 uMidtonesTint;
uniform vec3 uHighlightsTint;

// Curves & Primary Calibration
uniform float uCurveMaster;
uniform vec3 uCurveRGB;
uniform vec3 uCalibRGB;

// Effects
uniform float uGrainIntensity;
uniform float uGrainSize;
uniform float uHalation;
uniform float uGlow;
uniform float uLightLeak;
uniform int uLightLeakMode;
uniform float uScratches;
uniform float uDust;
uniform float uVignette;
uniform float uMotionBlur;

// RGB to HSV
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Procedural hash grain
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// S-curve contrast
vec3 applyContrast(vec3 col, float contrast) {
    return clamp(((col - 0.5) * contrast) + 0.5, 0.0, 1.0);
}

void main() {
    vec2 uv = vTexCoord;
    vec3 col;

    // Optional Motion Blur sampling
    if (uMotionBlur > 0.01) {
        vec2 blurDir = vec2(uMotionBlur * 0.015, uMotionBlur * 0.005);
        vec3 accum = vec3(0.0);
        accum += texture(uTexture, uv - blurDir * 2.0).rgb * 0.12;
        accum += texture(uTexture, uv - blurDir).rgb * 0.24;
        accum += texture(uTexture, uv).rgb * 0.28;
        accum += texture(uTexture, uv + blurDir).rgb * 0.24;
        accum += texture(uTexture, uv + blurDir * 2.0).rgb * 0.12;
        col = accum;
    } else {
        col = texture(uTexture, uv).rgb;
    }

    // 1. Exposure adjustment (linear gain)
    col *= pow(2.0, uExposure);

    // 2. White Balance: Temperature & Tint
    col.r += uTemperature * 0.15;
    col.b -= uTemperature * 0.15;
    col.g += uTint * 0.12;
    col.r += uTint * 0.05;
    col.b += uTint * 0.05;
    col = clamp(col, 0.0, 1.0);

    // 3. Highlights, Shadows, Whites, Blacks
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    float highlightMask = smoothstep(0.5, 1.0, luma);
    float shadowMask = 1.0 - smoothstep(0.0, 0.5, luma);
    float whiteMask = smoothstep(0.75, 1.0, luma);
    float blackMask = 1.0 - smoothstep(0.0, 0.25, luma);

    col += col * (uHighlights * 0.4 * highlightMask);
    col += (1.0 - col) * (uShadows * 0.4 * shadowMask);
    col += col * (uWhites * 0.3 * whiteMask);
    col -= col * (uBlacks * 0.3 * blackMask);

    // Fade (film lifted black level)
    col = mix(col, col * (1.0 - uFade * 0.35) + vec3(uFade * 0.18), shadowMask);

    // 4. Contrast
    col = applyContrast(col, uContrast);

    // 5. RGB Tone Curves & Primary Calibration
    col = clamp(col + vec3(uCurveMaster * 0.15), 0.0, 1.0);
    col.r = clamp(col.r + uCurveRGB.r * 0.15 + uCalibRGB.r * 0.10, 0.0, 1.0);
    col.g = clamp(col.g + uCurveRGB.g * 0.15 + uCalibRGB.g * 0.10, 0.0, 1.0);
    col.b = clamp(col.b + uCurveRGB.b * 0.15 + uCalibRGB.b * 0.10, 0.0, 1.0);

    // 6. Film Presets
    if (uPreset == 1) {
        // N1600: High-contrast moody color, teal shadows, golden midtones
        col = applyContrast(col, 1.25);
        vec3 shadows = vec3(0.0, 0.06, 0.08);
        col = mix(col + shadows * (1.0 - luma), col, luma);
        col.b = mix(col.b, col.b * 1.15, 1.0 - luma);
    } else if (uPreset == 2) {
        // P400: Warm pastel portra-style, soft golden highlights, natural skin
        col = applyContrast(col, 1.05);
        col.r = mix(col.r, col.r * 1.06, luma);
        col.g = mix(col.g, col.g * 1.03, luma);
        col.b = mix(col.b, col.b * 0.94, luma);
        col = clamp(col + vec3(0.02, 0.015, -0.01), 0.0, 1.0);
    } else if (uPreset == 3) {
        // RELIC: Expired 70s slide, lifted blacks, vintage green/amber
        col = col * 0.92 + vec3(0.08, 0.06, 0.02);
        col.g = mix(col.g * 1.08, col.g, luma);
        col.r = mix(col.r * 1.12, luma);
        col = applyContrast(col, 0.95);
    } else if (uPreset == 4) {
        // MONO: Rich dynamic B&W
        float monoLuma = dot(col, vec3(0.299, 0.587, 0.114));
        monoLuma = pow(monoLuma, 1.15);
        col = applyContrast(vec3(monoLuma), 1.3);
    }

    // 7. 6-Channel HSL Mixer (Hue, Saturation, Luminance per channel)
    if (uPreset != 4) {
        vec3 hsv = rgb2hsv(col);
        float hueDeg = hsv.x * 360.0;
        float satMod = 0.0;
        float lumMod = 0.0;
        float hueMod = 0.0;

        // Red (0° / 360°)
        float wRed = max(0.0, 1.0 - abs(hueDeg > 180.0 ? hueDeg - 360.0 : hueDeg) / 30.0);
        satMod += wRed * uHslRed.y;
        lumMod += wRed * uHslRed.z;
        hueMod += wRed * uHslRed.x;

        // Orange (~30°)
        float wOrange = max(0.0, 1.0 - abs(hueDeg - 30.0) / 30.0);
        satMod += wOrange * uHslOrange.y;
        lumMod += wOrange * uHslOrange.z;
        hueMod += wOrange * uHslOrange.x;

        // Yellow (~60°)
        float wYellow = max(0.0, 1.0 - abs(hueDeg - 60.0) / 30.0);
        satMod += wYellow * uHslYellow.y;
        lumMod += wYellow * uHslYellow.z;
        hueMod += wYellow * uHslYellow.x;

        // Green (~120°)
        float wGreen = max(0.0, 1.0 - abs(hueDeg - 120.0) / 45.0);
        satMod += wGreen * uHslGreen.y;
        lumMod += wGreen * uHslGreen.z;
        hueMod += wGreen * uHslGreen.x;

        // Blue (~220°)
        float wBlue = max(0.0, 1.0 - abs(hueDeg - 220.0) / 50.0);
        satMod += wBlue * uHslBlue.y;
        lumMod += wBlue * uHslBlue.z;
        hueMod += wBlue * uHslBlue.x;

        // Magenta (~300°)
        float wMag = max(0.0, 1.0 - abs(hueDeg - 300.0) / 45.0);
        satMod += wMag * uHslMagenta.y;
        lumMod += wMag * uHslMagenta.z;
        hueMod += wMag * uHslMagenta.x;

        hsv.x = fract(hsv.x + hueMod * 0.15);
        hsv.y = clamp(hsv.y + satMod, 0.0, 1.0);
        hsv.z = clamp(hsv.z + lumMod * 0.5, 0.0, 1.0);
        col = hsv2rgb(hsv);
    }

    // 8. 3-Way Color Grading (Shadows, Midtones, Highlights)
    float midToneMask = 1.0 - abs(luma - 0.5) * 2.0;
    col += uShadowsTint * shadowMask * 0.25;
    col += uMidtonesTint * midToneMask * 0.20;
    col += uHighlightsTint * highlightMask * 0.25;
    col = clamp(col, 0.0, 1.0);

    // 9. Multi-sample Halation & Pro-Mist Glow Bloom
    if (uHalation > 0.01 || uGlow > 0.01) {
        float stepSize = 4.0 / max(uResolution.x, uResolution.y);
        vec3 bloomAccum = vec3(0.0);
        bloomAccum += texture(uTexture, uv + vec2(-stepSize, 0.0)).rgb;
        bloomAccum += texture(uTexture, uv + vec2(stepSize, 0.0)).rgb;
        bloomAccum += texture(uTexture, uv + vec2(0.0, -stepSize)).rgb;
        bloomAccum += texture(uTexture, uv + vec2(0.0, stepSize)).rgb;
        bloomAccum *= 0.25;

        float bloomLuma = dot(bloomAccum, vec3(0.299, 0.587, 0.114));
        float edgeContrast = max(0.0, bloomLuma - luma);

        // Halation: Warm orange/red bloom at edge transitions
        vec3 halationColor = vec3(1.0, 0.22, 0.04) * uHalation * edgeContrast * 3.5;
        col += halationColor;

        // Glow (Pro-Mist highlight diffusion)
        vec3 glowColor = bloomAccum * smoothstep(0.6, 1.0, bloomLuma) * uGlow * 0.5;
        col += glowColor;
    }

    // 10. Light Leaks (3 modes)
    if (uLightLeak > 0.01) {
        vec3 leakColor = vec3(0.0);
        if (uLightLeakMode == 0) {
            // Corner warm amber leak
            float distCorner = length(uv - vec2(0.0, 0.0));
            float leak = smoothstep(0.9, 0.1, distCorner);
            leakColor = vec3(1.0, 0.55, 0.15) * leak * uLightLeak;
        } else if (uLightLeakMode == 1) {
            // Top streak leak
            float streak = smoothstep(0.35, 0.0, uv.y) * (0.8 + 0.2 * sin(uv.x * 25.0));
            leakColor = vec3(1.0, 0.40, 0.20) * streak * uLightLeak;
        } else {
            // Rainbow prism leak
            float prismDist = abs(uv.x + uv.y * 0.5 - 0.7);
            float prism = smoothstep(0.2, 0.0, prismDist);
            leakColor = vec3(sin(uv.x * 10.0) * 0.5 + 0.5, cos(uv.y * 10.0) * 0.5 + 0.5, 0.8) * prism * uLightLeak;
        }
        col = screenBlend(col, leakColor);
    }

    // 11. Film Grain normalized to a 1080p reference dimension
    // Ensures exact match between 120fps preview and 50MP export
    if (uGrainIntensity > 0.005) {
        float refScale = min(uResolution.x, uResolution.y) / 1080.0;
        vec2 grainUV = uv * (uResolution / max(refScale * uGrainSize, 0.1));
        float noise = hash(grainUV);
        float grainWeight = (noise - 0.5) * uGrainIntensity * 0.35;
        // Grain is more pronounced in midtones than clipped highlights/blacks
        grainWeight *= (1.0 - 2.0 * abs(luma - 0.5));
        col = clamp(col + vec3(grainWeight), 0.0, 1.0);
    }

    // 12. Analog Scratches & Dust
    if (uScratches > 0.01) {
        float scratch = step(0.996 - uScratches * 0.005, hash(vec2(floor(uv.x * 600.0), 12.3)));
        col = mix(col, vec3(0.9), scratch * 0.4);
    }
    if (uDust > 0.01) {
        float dust = step(0.998 - uDust * 0.003, hash(floor(uv * 400.0)));
        col = mix(col, vec3(0.1), dust * 0.6);
    }

    // 13. Vignette
    if (uVignette > 0.01) {
        vec2 coord = (uv - 0.5) * vec2(1.0, uResolution.y / max(uResolution.x, 1.0));
        float vig = smoothstep(0.75, 0.75 - uVignette * 0.45, length(coord));
        col *= vig;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}

vec3 screenBlend(vec3 base, vec3 blend) {
    return 1.0 - (1.0 - base) * (1.0 - blend);
}
"""

        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        )
    }

    private var programId = 0
    private var vaoId = 0
    private var vboId = 0

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    fun invalidateGL() {
        release()
        programId = 0
        vaoId = 0
        vboId = 0
    }

    fun initGL() {
        if (programId != 0) return

        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Program link failed: $log")
        }

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        vaoId = vaos[0]
        vboId = vbos[0]

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, 2 * 4)

        GLES30.glBindVertexArray(0)
    }

    fun render(
        textureId: Int,
        width: Int,
        height: Int,
        adjustments: DarkroomAdjustments
    ) {
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programId)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTexture"), 0)

        val presetInt = when (adjustments.preset) {
            FilmPreset.ORIGINAL -> 0
            FilmPreset.N1600 -> 1
            FilmPreset.P400 -> 2
            FilmPreset.RELIC -> 3
            FilmPreset.MONO -> 4
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uPreset"), presetInt)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(programId, "uResolution"), width.toFloat(), height.toFloat())

        // Light
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uExposure"), adjustments.exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uContrast"), adjustments.contrast)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uHighlights"), adjustments.highlights)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uShadows"), adjustments.shadows)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uWhites"), adjustments.whites)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uBlacks"), adjustments.blacks)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uFade"), adjustments.fade)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uTemperature"), adjustments.temperature)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uTint"), adjustments.tint)

        // 6-Channel HSL
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslRed"), adjustments.hslRedHue, adjustments.hslRedSat, adjustments.hslRedLum)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslOrange"), adjustments.hslOrangeHue, adjustments.hslOrangeSat, adjustments.hslOrangeLum)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslYellow"), adjustments.hslYellowHue, adjustments.hslYellowSat, adjustments.hslYellowLum)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslGreen"), adjustments.hslGreenHue, adjustments.hslGreenSat, adjustments.hslGreenLum)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslBlue"), adjustments.hslBlueHue, adjustments.hslBlueSat, adjustments.hslBlueLum)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHslMagenta"), adjustments.hslMagentaHue, adjustments.hslMagentaSat, adjustments.hslMagentaLum)

        // 3-Way Color Grading
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uShadowsTint"), adjustments.shadowsTintR, adjustments.shadowsTintG, adjustments.shadowsTintB)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uMidtonesTint"), adjustments.midtonesTintR, adjustments.midtonesTintG, adjustments.midtonesTintB)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uHighlightsTint"), adjustments.highlightsTintR, adjustments.highlightsTintG, adjustments.highlightsTintB)

        // Curves & Calibration
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uCurveMaster"), adjustments.curveMaster)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uCurveRGB"), adjustments.curveRed, adjustments.curveGreen, adjustments.curveBlue)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(programId, "uCalibRGB"), adjustments.calibRed, adjustments.calibGreen, adjustments.calibBlue)

        // Effects
        val activeGrain = maxOf(adjustments.grainIntensity, adjustments.preset.grainDefault)
        val activeHalation = maxOf(adjustments.halationIntensity, adjustments.preset.halationDefault)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGrainIntensity"), activeGrain)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGrainSize"), adjustments.grainSize)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uHalation"), activeHalation)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGlow"), adjustments.glowIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uLightLeak"), adjustments.lightLeakIntensity)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uLightLeakMode"), adjustments.lightLeakMode)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uScratches"), adjustments.scratchesIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uDust"), adjustments.dustIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uVignette"), adjustments.vignette)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uMotionBlur"), adjustments.motionBlur)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Offline headless FBO export:
     * - Uses EGL_OPENGL_ES3_BIT_KHR in eglChooseConfig
     * - Cleans up all EGL/GL resources in finally block
     * - Vertically flips glReadPixels so exported bitmap is right-side up
     */
    fun renderOffscreen(
        sourceBitmap: Bitmap,
        adjustments: DarkroomAdjustments
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        val textures = IntArray(1)

        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            initGL()

            GLES30.glGenTextures(1, textures, 0)
            val textureId = textures[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, sourceBitmap, 0)

            render(textureId, width, height, adjustments)
            GLES30.glFinish()

            val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

            // Vertically flip pixel rows from glReadPixels so exported bitmap is right-side up
            val flippedBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            val rowBytes = width * 4
            val row = ByteArray(rowBytes)
            for (y in height - 1 downTo 0) {
                pixelBuffer.position(y * rowBytes)
                pixelBuffer.get(row, 0, rowBytes)
                flippedBuffer.put(row, 0, rowBytes)
            }
            flippedBuffer.position(0)

            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outputBitmap.copyPixelsFromBuffer(flippedBuffer)
            return outputBitmap
        } finally {
            if (textures[0] != 0) {
                GLES30.glDeleteTextures(1, textures, 0)
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            invalidateGL()
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return shader
    }

    fun release() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
