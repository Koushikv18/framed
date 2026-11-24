package com.aesthetic.framed.domain.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.aesthetic.framed.data.model.ExportOptions
import com.aesthetic.framed.data.model.FramePreset
import com.aesthetic.framed.data.model.PhotoMetadata

object FrameCompositor {

    fun compositeFrame(
        source: Bitmap,
        options: ExportOptions,
        metadata: PhotoMetadata
    ): Bitmap {
        val srcW = source.width
        val srcH = source.height

        if (options.framePreset == FramePreset.NONE) {
            val result = source.copy(Bitmap.Config.ARGB_8888, true)
            if (options.isDateStampEnabled) {
                drawLcdDateStamp(result, metadata.lcdDateStamp)
            }
            return result
        }

        // Calculate matting border dimensions
        val borderX = (srcW * 0.08f).toInt()
        val borderYTop = (srcH * 0.08f).toInt()
        val borderYBottom = if (options.framePreset == FramePreset.SPECS || options.framePreset == FramePreset.NEGATIVE) {
            (srcH * 0.14f).toInt()
        } else {
            (srcH * 0.10f).toInt()
        }

        val canvasW = srcW + borderX * 2
        val canvasH = srcH + borderYTop + borderYBottom

        val composite = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (options.framePreset) {
            FramePreset.WHITE -> {
                backgroundPaint.color = Color.WHITE
                canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), backgroundPaint)
            }
            FramePreset.PAPER -> {
                backgroundPaint.color = Color.rgb(250, 247, 240) // Warm parchment
                canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), backgroundPaint)

                // Subtle inner shadow stroke around photo
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(30, 0, 0, 0)
                    style = Paint.Style.STROKE
                    strokeWidth = (srcW * 0.003f).coerceAtLeast(1f)
                }
                canvas.drawRect(
                    (borderX - 2).toFloat(),
                    (borderYTop - 2).toFloat(),
                    (borderX + srcW + 2).toFloat(),
                    (borderYTop + srcH + 2).toFloat(),
                    shadowPaint
                )
            }
            FramePreset.BLACK -> {
                backgroundPaint.color = Color.rgb(14, 14, 14)
                canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), backgroundPaint)
            }
            FramePreset.NEGATIVE -> {
                backgroundPaint.color = Color.rgb(10, 10, 10)
                canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), backgroundPaint)

                // Draw 35mm film strip sprocket holes
                drawFilmSprockets(canvas, canvasW, canvasH, borderYTop, borderYBottom)
            }
            FramePreset.SPECS -> {
                backgroundPaint.color = Color.rgb(18, 18, 18) // Minimalist dark gallery
                canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), backgroundPaint)
            }
            FramePreset.NONE -> {}
        }

        // Draw the main photo onto the framed canvas
        val photoDest = Rect(borderX, borderYTop, borderX + srcW, borderYTop + srcH)
        canvas.drawBitmap(source, null, photoDest, null)

        // Draw Editorial Specs line if SPECS frame is selected
        if (options.framePreset == FramePreset.SPECS) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(200, 200, 205)
                textSize = (srcH * 0.022f).coerceAtLeast(24f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.08f
            }
            val textY = borderYTop + srcH + (borderYBottom * 0.55f)
            canvas.drawText(metadata.editorialSpecsLine, (canvasW / 2).toFloat(), textY, textPaint)
        }

        // Draw Date Stamp if enabled
        if (options.isDateStampEnabled) {
            val dateStampText = options.customDateText ?: metadata.lcdDateStamp
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 160, 20) // Authentic 90s Amber LCD
                textSize = (srcH * 0.032f).coerceAtLeast(32f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                setShadowLayer(8f, 0f, 0f, Color.argb(160, 255, 140, 0))
            }
            val stampX = (borderX + srcW - (srcW * 0.04f))
            val stampY = (borderYTop + srcH - (srcH * 0.04f))
            canvas.drawText(dateStampText, stampX, stampY, stampPaint)
        }

        return composite
    }

    private fun drawLcdDateStamp(bitmap: Bitmap, dateText: String) {
        val canvas = Canvas(bitmap)
        val h = bitmap.height.toFloat()
        val w = bitmap.width.toFloat()

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 160, 20)
            textSize = (h * 0.032f).coerceAtLeast(32f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(8f, 0f, 0f, Color.argb(160, 255, 140, 0))
        }
        canvas.drawText(dateText, w - (w * 0.04f), h - (h * 0.04f), stampPaint)
    }

    private fun drawFilmSprockets(
        canvas: Canvas,
        w: Int,
        h: Int,
        topBandH: Int,
        bottomBandH: Int
    ) {
        val sprocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 22, 22)
        }
        val sprocketW = (w * 0.035f)
        val sprocketH = (topBandH * 0.45f)
        val spacing = (w * 0.075f)

        var x = (w * 0.04f)
        while (x + sprocketW < w) {
            // Top sprockets
            canvas.drawRoundRect(
                RectF(x, (topBandH * 0.28f), x + sprocketW, (topBandH * 0.28f) + sprocketH),
                8f, 8f, sprocketPaint
            )
            // Bottom sprockets
            val bottomY = h - bottomBandH + (bottomBandH * 0.35f)
            canvas.drawRoundRect(
                RectF(x, bottomY, x + sprocketW, bottomY + sprocketH),
                8f, 8f, sprocketPaint
            )
            x += spacing
        }

        // Film edge markings in amber
        val edgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 140, 20)
            textSize = (topBandH * 0.22f).coerceAtLeast(18f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("FRAMED 400 • 36 EXP", (w * 0.05f), (topBandH * 0.90f), edgeTextPaint)
        canvas.drawText("► 24A", (w * 0.85f), (topBandH * 0.90f), edgeTextPaint)
    }
}
