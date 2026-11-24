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
uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform vec3 uCurveRGB;
uniform vec3 uHslSat1; // Red, Orange, Yellow
uniform vec3 uHslSat2; // Green, Blue, Magenta
uniform float uGrain;
uniform float uHalation;
uniform float uLightLeak;
uniform vec2 uResolution;

// RGB to HSV conversion
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV to RGB conversion
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
    vec4 baseSample = texture(uTexture, uv);
    vec3 col = baseSample.rgb;

    // 1. Exposure adjustment (linear gain)
    col *= pow(2.0, uExposure);

    // 2. Highlights / Shadows separation
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    float highlightMask = smoothstep(0.5, 1.0, luma);
    float shadowMask = 1.0 - smoothstep(0.0, 0.5, luma);
    col += col * (uHighlights * 0.4 * highlightMask);
    col += (1.0 - col) * (uShadows * 0.4 * shadowMask);

    // 3. Contrast
    col = applyContrast(col, uContrast);

    // 4. RGB Tone Curve Bias
    col.r = clamp(col.r + uCurveRGB.r * 0.15, 0.0, 1.0);
    col.g = clamp(col.g + uCurveRGB.g * 0.15, 0.0, 1.0);
    col.b = clamp(col.b + uCurveRGB.b * 0.15, 0.0, 1.0);

    // 5. Film Presets
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
        col = col * 0.92 + vec3(0.08, 0.06, 0.02); // Lifted blacks
        col.g = mix(col.g * 1.08, col.g, luma);     // Olive shadows
        col.r = mix(col.r, col.r * 1.12, luma);     // Amber highlights
        col = applyContrast(col, 0.95);
    } else if (uPreset == 4) {
        // MONO: High-contrast rich dynamic B&W
        float monoLuma = dot(col, vec3(0.299, 0.587, 0.114));
        monoLuma = pow(monoLuma, 1.15);
        col = applyContrast(vec3(monoLuma), 1.3);
    }

    // 6. 6-Channel HSL Mixer
    if (uPreset != 4) {
        vec3 hsv = rgb2hsv(col);
        float hueDeg = hsv.x * 360.0;
        float satMod = 0.0;

        // Red (0° / 360°)
        float wRed = max(0.0, 1.0 - abs(hueDeg > 180.0 ? hueDeg - 360.0 : hueDeg) / 30.0);
        satMod += wRed * uHslSat1.x;

        // Orange (~30°)
        float wOrange = max(0.0, 1.0 - abs(hueDeg - 30.0) / 30.0);
        satMod += wOrange * uHslSat1.y;

        // Yellow (~60°)
        float wYellow = max(0.0, 1.0 - abs(hueDeg - 60.0) / 30.0);
        satMod += wYellow * uHslSat1.z;

        // Green (~120°)
        float wGreen = max(0.0, 1.0 - abs(hueDeg - 120.0) / 45.0);
        satMod += wGreen * uHslSat2.x;

        // Blue (~220°)
        float wBlue = max(0.0, 1.0 - abs(hueDeg - 220.0) / 45.0);
        satMod += wBlue * uHslSat2.y;

        // Magenta (~300°)
        float wMagenta = max(0.0, 1.0 - abs(hueDeg - 300.0) / 45.0);
        satMod += wMagenta * uHslSat2.z;

        hsv.y = clamp(hsv.y * (1.0 + satMod), 0.0, 1.0);
        col = hsv2rgb(hsv);
    }

    // 7. Halation (Warm orange highlight bloom)
    if (uHalation > 0.001) {
        float highlight = smoothstep(0.70, 1.0, luma);
        vec3 halationTint = vec3(1.0, 0.35, 0.08) * uHalation * highlight * 0.45;
        col = clamp(col + halationTint, 0.0, 1.0);
    }

    // 8. Light Leaks overlay
    if (uLightLeak > 0.001) {
        float distTL = length(uv - vec2(0.0, 0.0));
        float leakMask = smoothstep(0.9, 0.0, distTL) * uLightLeak;
        vec3 leakColor = vec3(1.0, 0.42, 0.12) * leakMask * 0.7;
        col = clamp(col + leakColor, 0.0, 1.0);
    }

    // 9. Procedural Organic Film Grain
    if (uGrain > 0.001) {
        float noise = (hash(uv * uResolution * 0.85) - 0.5) * 2.0;
        // Modulate grain by midtone luminance curve
        float grainWeight = 1.0 - 4.0 * (luma - 0.5) * (luma - 0.5);
        grainWeight = clamp(grainWeight, 0.15, 1.0);
        col += noise * uGrain * grainWeight * 0.18;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), baseSample.a);
}
"""

        private val QUAD_VERTICES = floatArrayOf(
            // x, y, u, v
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f
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

        // Setup VAO and VBO
        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        vaoId = vaos[0]
        vboId = vbos[0]

        GLES30.glBindVertexArray(vaoId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, QUAD_VERTICES.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        // aPosition
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)

        // aTexCoord
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

        // Bind source texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTexture"), 0)

        // Uniforms
        val presetInt = when (adjustments.preset) {
            FilmPreset.ORIGINAL -> 0
            FilmPreset.N1600 -> 1
            FilmPreset.P400 -> 2
            FilmPreset.RELIC -> 3
            FilmPreset.MONO -> 4
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uPreset"), presetInt)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uExposure"), adjustments.exposure)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uContrast"), adjustments.contrast)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uHighlights"), adjustments.highlights)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uShadows"), adjustments.shadows)

        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(programId, "uCurveRGB"),
            adjustments.curveRed,
            adjustments.curveGreen,
            adjustments.curveBlue
        )

        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(programId, "uHslSat1"),
            adjustments.hslRedSat,
            adjustments.hslOrangeSat,
            adjustments.hslYellowSat
        )

        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(programId, "uHslSat2"),
            adjustments.hslGreenSat,
            adjustments.hslBlueSat,
            adjustments.hslMagentaSat
        )

        val activeGrain = maxOf(adjustments.grainIntensity, adjustments.preset.grainDefault)
        val activeHalation = maxOf(adjustments.halationIntensity, adjustments.preset.halationDefault)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGrain"), activeGrain)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uHalation"), activeHalation)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uLightLeak"), adjustments.lightLeakIntensity)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(programId, "uResolution"), width.toFloat(), height.toFloat())

        // Draw quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Requirement 4:
     * Full-resolution offline FBO export using the exact same OpenGL ES 3.0 shader pipeline
     * to guarantee zero visual preview/export mismatch.
     */
    fun renderOffscreen(
        sourceBitmap: Bitmap,
        adjustments: DarkroomAdjustments
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        // 1. Setup EGL Context
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
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
        val eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // 2. Initialize GL
        initGL()

        // 3. Create Source Texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, sourceBitmap, 0)

        // 4. Render to FBO surface
        render(textureId, width, height, adjustments)
        GLES30.glFinish()

        // 5. Read back pixels
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outputBitmap.copyPixelsFromBuffer(pixelBuffer)

        // 6. Cleanup
        GLES30.glDeleteTextures(1, textures, 0)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)

        return outputBitmap
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
