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

    fun getRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Reads EXIF orientation from captured or imported photos and applies
     * the exact rotation matrix before feeding to OpenGL ES 3.0 or Compose,
     * ensuring portrait shots never render sideways or distorted.
     * Optionally downsamples to maxDimension for smooth 120fps scrubbing.
     */
    fun decodeRotatedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int? = null
    ): Bitmap? {
        return try {
            val degrees = getRotationDegrees(context, uri)

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

            if (degrees != 0) {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) {
                    decoded.recycle()
                }
                rotated
            } else {
                decoded
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractMetadata(context: Context, uri: Uri): PhotoMetadata {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractMetadataFromStream(stream)
            } ?: createDefaultMetadata()
        } catch (_: Exception) {
            createDefaultMetadata()
        }
    }

    private fun extractMetadataFromStream(stream: InputStream): PhotoMetadata {
        val exif = ExifInterface(stream)
        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: Build.MODEL ?: "FRAMED CAMERA"
        val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: Build.MANUFACTURER ?: ""
        val fullDeviceName = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()

        val focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val focalLengthStr = if (focalLengthMm > 0.0) "${Math.round(focalLengthMm * 5.5)}mm eq." else "24mm eq."

        val apertureVal = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val apertureStr = if (apertureVal > 0.0) String.format(Locale.US, "f/%.1f", apertureVal) else "f/1.8"

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

    private fun createDefaultMetadata(): PhotoMetadata {
        val model = Build.MODEL ?: "FRAMED CAMERA"
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR) % 100
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val lcdStamp = String.format(Locale.US, "'%02d %02d %02d", year, month, day)

        return PhotoMetadata(
            deviceModel = model.uppercase(Locale.US),
            focalLength = "24mm eq.",
            aperture = "f/1.8",
            shutterSpeed = "1/250s",
            iso = "ISO 100",
            captureDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()),
            lcdDateStamp = lcdStamp
        )
    }

    /**
     * Requirement 5:
     * Explicitly copy original EXIF tags onto the exported MediaStore JPEG after Bitmap.compress().
     */
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
            destExif.setAttribute(ExifInterface.TAG_SOFTWARE, "Framed Android 1.0")
            destExif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
