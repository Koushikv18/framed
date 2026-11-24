package com.aesthetic.framed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aesthetic.framed.domain.camera.CameraEngine
import com.aesthetic.framed.domain.export.ExifHelper
import com.aesthetic.framed.domain.export.PhotoExporter
import com.aesthetic.framed.domain.recipe.EditRecipe
import com.aesthetic.framed.domain.recipe.EditRecipeStore
import com.aesthetic.framed.domain.sensor.HorizonSensorManager
import com.aesthetic.framed.ui.camera.CameraScreen
import com.aesthetic.framed.ui.editor.DarkroomScreen
import com.aesthetic.framed.ui.theme.FramedTheme
import com.aesthetic.framed.ui.theme.StudioBlack
import com.aesthetic.framed.ui.theme.StudioText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var cameraEngine: CameraEngine
    private lateinit var horizonSensorManager: HorizonSensorManager
    private lateinit var photoExporter: PhotoExporter
    private lateinit var editRecipeStore: EditRecipeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        cameraEngine = CameraEngine(applicationContext)
        horizonSensorManager = HorizonSensorManager(applicationContext)
        photoExporter = PhotoExporter(applicationContext)
        editRecipeStore = EditRecipeStore(applicationContext)

        setContent {
            FramedTheme {
                val coroutineScope = rememberCoroutineScope()

                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var currentSourceUri by remember { mutableStateOf<Uri?>(null) }

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasCameraPermission = isGranted
                    }
                )

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(StudioBlack)
                        .safeDrawingPadding()
                ) {
                    if (!hasCameraPermission) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CAMERA PERMISSION REQUIRED",
                                color = StudioText,
                                fontSize = 13.sp
                            )
                        }
                    } else if (editingBitmap != null) {
                        DarkroomScreen(
                            sourceBitmap = editingBitmap!!,
                            onBack = {
                                editingBitmap?.recycle()
                                editingBitmap = null
                            },
                            onExportRequested = { previewBitmap, adjustments, options ->
                                coroutineScope.launch {
                                    Toast.makeText(this@MainActivity, "Rendering full-res FBO export...", Toast.LENGTH_SHORT).show()

                                    // Full-resolution original bitmap decoded with exact EXIF rotation
                                    val fullResBitmap = currentSourceUri?.let { uri ->
                                        ExifHelper.decodeRotatedBitmap(applicationContext, uri, maxDimension = null)
                                    } ?: previewBitmap

                                    val result = photoExporter.exportPhoto(fullResBitmap, currentSourceUri, adjustments, options)

                                    if (fullResBitmap != previewBitmap) {
                                        fullResBitmap.recycle()
                                    }

                                    result.onSuccess { uri ->
                                        // Save non-destructive edit recipe to app-private storage
                                        val recipe = EditRecipe(
                                            id = UUID.randomUUID().toString(),
                                            sourceUriString = currentSourceUri.toString(),
                                            timestamp = System.currentTimeMillis(),
                                            adjustments = adjustments,
                                            exportOptions = options
                                        )
                                        editRecipeStore.saveRecipe(recipe)

                                        Toast.makeText(this@MainActivity, "Saved to DCIM/FramedClone", Toast.LENGTH_LONG).show()
                                        editingBitmap?.recycle()
                                        editingBitmap = null
                                    }.onFailure { e ->
                                        Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    } else {
                        CameraScreen(
                            cameraEngine = cameraEngine,
                            horizonSensorManager = horizonSensorManager,
                            onPhotoCaptured = { uri ->
                                currentSourceUri = uri
                                // Save a copy to app-private storage for non-destructive library
                                saveOriginalToPrivateStorage(uri)

                                // Downscale interactive preview to max 2048px on long edge for locked 120fps scrubbing
                                val previewBmp = ExifHelper.decodeRotatedBitmap(applicationContext, uri, maxDimension = 2048)
                                if (previewBmp != null) {
                                    editingBitmap = previewBmp
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun saveOriginalToPrivateStorage(uri: Uri) {
        try {
            val originalsDir = File(filesDir, "originals").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(originalsDir, "ORIGINAL_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
