package com.aesthetic.framed.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.aesthetic.framed.data.model.DarkroomAdjustments
import com.aesthetic.framed.data.model.ExportOptions
import com.aesthetic.framed.domain.gl.FilmShaderRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoExporter(private val context: Context) {

    private val filmShaderRenderer = FilmShaderRenderer()

    /**
     * Requirement 5:
     * Full-resolution lossless export pipeline that:
     * 1. Applies unified OpenGL ES 3.0 shader.
     * 2. Composites gallery frames and date stamps.
     * 3. Compresses to JPEG.
     * 4. Copies original EXIF tags to the newly compressed JPEG.
     * 5. Immediately recycles intermediate bitmaps to prevent OOM.
     */
    suspend fun exportPhoto(
        sourceBitmap: Bitmap,
        sourceUri: Uri?,
        adjustments: DarkroomAdjustments,
        options: ExportOptions
    ): Result<Uri> = withContext(Dispatchers.IO) {
        var shaderRendered: Bitmap? = null
        var framedComposite: Bitmap? = null

        try {
            // 1. Offscreen OpenGL ES 3.0 Shader Pipeline
            shaderRendered = filmShaderRenderer.renderOffscreen(sourceBitmap, adjustments)

            // 2. Extract Metadata for Frame & Stamp
            val metadata = if (sourceUri != null) {
                ExifHelper.extractMetadata(context, sourceUri)
            } else {
                ExifHelper.extractMetadata(context, Uri.EMPTY)
            }

            // 3. Composite Frame Matting & Date Stamp
            framedComposite = FrameCompositor.compositeFrame(shaderRendered, options, metadata)

            // Requirement 5: Recycle intermediate shader-rendered bitmap immediately
            shaderRendered.recycle()
            shaderRendered = null

            // 4. Create temporary cache file for EXIF preservation
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempFile = File(context.cacheDir, "framed_export_$timeStamp.jpg")
            FileOutputStream(tempFile).use { out ->
                framedComposite.compress(Bitmap.CompressFormat.JPEG, 98, out)
            }

            // Requirement 5: Copy original EXIF tags to exported file
            if (sourceUri != null) {
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { srcStream ->
                        ExifHelper.copyExifTags(srcStream, tempFile)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 5. Save to MediaStore (DCIM/FramedClone)
            val fileName = "FRAMED_EDIT_$timeStamp.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/FramedClone")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val mediaUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create MediaStore entry")

            context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(mediaUri, updateValues, null, null)
            }

            // Cleanup temp file
            tempFile.delete()

            // Requirement 5: Recycle final composite bitmap
            framedComposite.recycle()
            framedComposite = null

            Result.success(mediaUri)
        } catch (e: Exception) {
            shaderRendered?.recycle()
            framedComposite?.recycle()
            Result.failure(e)
        }
    }
}
