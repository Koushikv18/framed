package com.aesthetic.framed.domain.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.aesthetic.framed.data.model.PhotoMetadata
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExifHelper {

    /**
     * Resolves complete android.graphics.Matrix supporting all 8 EXIF orientation constants:
     * NORMAL, FLIP_HORIZONTAL, ROTATE_180, FLIP_VERTICAL, TRANSPOSE, ROTATE_90, TRANSVERSE, ROTATE_270.
     */
    fun getOrientationMatrix(context: Context, uri: Uri): Matrix {
        val matrix = Matrix()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> {}
                }
            }
        } catch (_: Exception) {}
        return matrix
    }

    /**
     * Decodes photo applying exact EXIF rotation/flip matrix.
     * When maxDimension is specified (e.g. 2048px for editor preview), uses inSampleSize
     * followed by Bitmap.createScaledBitmap to strictly cap the long edge for 120Hz locked scrubbing.
     */
    fun decodeRotatedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int? = null
    ): Bitmap? {
        return try {
            val orientationMatrix = getOrientationMatrix(context, uri)

            val options = BitmapFactory.Options()
            if (maxDimension != null) {
                options.inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                val maxOriginal = maxOf(options.outWidth, options.outHeight)
                var sampleSize = 1
                while (maxOriginal / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }
                options.inSampleSize = sampleSize
                options.inJustDecodeBounds = false
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Apply 8-orientation EXIF Matrix transformation
            val transformed = if (!orientationMatrix.isIdentity) {
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            // Enforce exact max long-edge cap (e.g. 2048px) if requested
            if (maxDimension != null) {
                val longEdge = maxOf(transformed.width, transformed.height)
                if (longEdge > maxDimension) {
                    val scale = maxDimension.toFloat() / longEdge.toFloat()
                    val targetWidth = (transformed.width * scale).toInt().coerceAtLeast(1)
                    val targetHeight = (transformed.height * scale).toInt().coerceAtLeast(1)
                    val capped = Bitmap.createScaledBitmap(transformed, targetWidth, targetHeight, true)
                    if (capped != transformed) transformed.recycle()
                    capped
                } else {
                    transformed
                }
            } else {
                transformed
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractMetadata(context: Context, uri: Uri, activeZoomRatio: Float = 1.0f): PhotoMetadata {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractMetadataFromStream(stream, activeZoomRatio)
            } ?: createDefaultMetadata(activeZoomRatio)
        } catch (_: Exception) {
            createDefaultMetadata(activeZoomRatio)
        }
    }

    private fun extractMetadataFromStream(stream: InputStream, activeZoomRatio: Float): PhotoMetadata {
        val exif = ExifInterface(stream)
        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: Build.MODEL ?: "FRAMED CAMERA"
        val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: Build.MANUFACTURER ?: ""
        val fullDeviceName = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()

        // Requirement 5: Read TAG_FOCAL_LENGTH_IN_35MM_FILM first; if missing, map from S25 Ultra quad-lens zoom
        val focal35mm = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        val focalLengthStr = when {
            focal35mm > 0 -> "${focal35mm}mm eq."
            activeZoomRatio <= 0.8f -> "13mm eq."
            activeZoomRatio <= 1.8f -> "23mm eq."
            activeZoomRatio <= 4.0f -> "69mm eq."
            else -> "115mm eq."
        }

        val apertureVal = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val apertureStr = when {
            apertureVal > 0.0 -> String.format(Locale.US, "f/%.1f", apertureVal)
            activeZoomRatio <= 0.8f -> "f/1.9"
            activeZoomRatio <= 1.8f -> "f/1.7"
            activeZoomRatio <= 4.0f -> "f/2.4"
            else -> "f/3.4"
        }

        val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val shutterSpeedStr = if (expTime > 0.0) {
            if (expTime >= 1.0) String.format(Locale.US, "%.1fs", expTime) else "1/${Math.round(1.0 / expTime)}s"
        } else {
            "1/250s"
        }

        val isoVal = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
        val isoStr = if (isoVal > 0) "ISO $isoVal" else "ISO 100"

        val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME)
        val lcdStamp = formatLcdDate(rawDate)

        return PhotoMetadata(
            deviceModel = fullDeviceName.uppercase(Locale.US),
            focalLength = focalLengthStr,
            aperture = apertureStr,
            shutterSpeed = shutterSpeedStr,
            iso = isoStr,
            captureDate = rawDate ?: "",
            lcdDateStamp = lcdStamp
        )
    }

    private fun formatLcdDate(rawDate: String?): String {
        return try {
            val date = if (rawDate != null) {
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(rawDate)
            } else {
                Date()
            }
            val cal = java.util.Calendar.getInstance().apply { time = date ?: Date() }
            val year = cal.get(java.util.Calendar.YEAR) % 100
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            String.format(Locale.US, "'%02d %02d %02d", year, month, day)
        } catch (_: Exception) {
            "'98 11 24"
        }
    }

    private fun createDefaultMetadata(activeZoomRatio: Float): PhotoMetadata {
        val model = Build.MODEL ?: "FRAMED CAMERA"
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR) % 100
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val lcdStamp = String.format(Locale.US, "'%02d %02d %02d", year, month, day)

        val focalLengthStr = when {
            activeZoomRatio <= 0.8f -> "13mm eq."
            activeZoomRatio <= 1.8f -> "23mm eq."
            activeZoomRatio <= 4.0f -> "69mm eq."
            else -> "115mm eq."
        }

        return PhotoMetadata(
            deviceModel = model.uppercase(Locale.US),
            focalLength = focalLengthStr,
            aperture = "f/1.7",
            shutterSpeed = "1/250s",
            iso = "ISO 100",
            captureDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()),
            lcdDateStamp = lcdStamp
        )
    }

    fun copyExifTags(sourceStream: InputStream, targetFile: File) {
        try {
            val sourceExif = ExifInterface(sourceStream)
            val destExif = ExifInterface(targetFile.absolutePath)

            val tagsToCopy = arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_LENS_MAKE,
                ExifInterface.TAG_LENS_MODEL
            )

            for (tag in tagsToCopy) {
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }
            destExif.setAttribute(ExifInterface.TAG_SOFTWARE, "Framed Android 2.0")
            destExif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
