package com.aesthetic.framed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.aesthetic.framed.ui.library.FramedLibraryScreen
import com.aesthetic.framed.ui.theme.EmeraldLevel
import com.aesthetic.framed.ui.theme.FramedTheme
import com.aesthetic.framed.ui.theme.StudioBlack
import com.aesthetic.framed.ui.theme.StudioText
import com.aesthetic.framed.ui.theme.StudioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class AppScreen {
    CAMERA,
    DARKROOM,
    LIBRARY
}

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

                var currentScreen by remember { mutableStateOf(AppScreen.CAMERA) }
                var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var currentSourceUri by remember { mutableStateOf<Uri?>(null) }
                var currentRecipe by remember { mutableStateOf<EditRecipe?>(null) }
                var activeZoomRatio by remember { mutableFloatStateOf(1.0f) }
                var isLoading by remember { mutableStateOf(false) }
                var loadingMessage by remember { mutableStateOf("PROCESSING...") }

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
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        when (currentScreen) {
                            AppScreen.CAMERA -> {
                                CameraScreen(
                                    cameraEngine = cameraEngine,
                                    horizonSensorManager = horizonSensorManager,
                                    onPhotoCaptured = { uri ->
                                        activeZoomRatio = cameraEngine.exposureState.value.zoomRatio
                                        isLoading = true
                                        loadingMessage = "LOADING PREVIEW..."
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val savedUri = saveOriginalToPrivateStorage(uri) ?: uri
                                            val previewBmp = ExifHelper.decodeRotatedBitmap(
                                                applicationContext,
                                                savedUri,
                                                maxDimension = 2048
                                            )
                                            val recipe = EditRecipe(
                                                id = UUID.randomUUID().toString(),
                                                sourceUriString = savedUri.toString(),
                                                timestamp = System.currentTimeMillis()
                                            )
                                            editRecipeStore.saveRecipe(recipe)

                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                if (previewBmp != null) {
                                                    editingBitmap?.recycle()
                                                    editingBitmap = previewBmp
                                                    currentSourceUri = savedUri
                                                    currentRecipe = recipe
                                                    currentScreen = AppScreen.DARKROOM
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Failed to load image preview", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onOpenLibrary = {
                                        currentScreen = AppScreen.LIBRARY
                                    }
                                )
                            }

                            AppScreen.LIBRARY -> {
                                BackHandler {
                                    currentScreen = AppScreen.CAMERA
                                }
                                FramedLibraryScreen(
                                    recipeStore = editRecipeStore,
                                    onBack = {
                                        currentScreen = AppScreen.CAMERA
                                    },
                                    onRecipeSelected = { recipe ->
                                        val uri = Uri.parse(recipe.sourceUriString)
                                        isLoading = true
                                        loadingMessage = "OPENING DARKROOM..."
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val previewBmp = ExifHelper.decodeRotatedBitmap(
                                                applicationContext,
                                                uri,
                                                maxDimension = 2048
                                            )
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                if (previewBmp != null) {
                                                    editingBitmap?.recycle()
                                                    editingBitmap = previewBmp
                                                    currentSourceUri = uri
                                                    currentRecipe = recipe
                                                    currentScreen = AppScreen.DARKROOM
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Original file not found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onPhotoImported = { uri, recipe ->
                                        editRecipeStore.saveRecipe(recipe)
                                        isLoading = true
                                        loadingMessage = "IMPORTING PHOTO..."
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val previewBmp = ExifHelper.decodeRotatedBitmap(
                                                applicationContext,
                                                uri,
                                                maxDimension = 2048
                                            )
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                if (previewBmp != null) {
                                                    editingBitmap?.recycle()
                                                    editingBitmap = previewBmp
                                                    currentSourceUri = uri
                                                    currentRecipe = recipe
                                                    currentScreen = AppScreen.DARKROOM
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            AppScreen.DARKROOM -> {
                                BackHandler {
                                    editingBitmap?.recycle()
                                    editingBitmap = null
                                    currentScreen = AppScreen.LIBRARY
                                }
                                editingBitmap?.let { bmp ->
                                    DarkroomScreen(
                                        sourceBitmap = bmp,
                                        initialAdjustments = currentRecipe?.adjustments ?: com.aesthetic.framed.data.model.DarkroomAdjustments(),
                                        initialExportOptions = currentRecipe?.exportOptions ?: com.aesthetic.framed.data.model.ExportOptions(),
                                        onBack = {
                                            editingBitmap?.recycle()
                                            editingBitmap = null
                                            currentScreen = AppScreen.LIBRARY
                                        },
                                        onExportRequested = { previewBitmap, adjustments, options ->
                                            isLoading = true
                                            loadingMessage = "RENDERING FBO EXPORT..."
                                            coroutineScope.launch(Dispatchers.IO) {
                                                // Full-resolution original bitmap decoded with exact EXIF rotation
                                                val fullResBitmap = currentSourceUri?.let { uri ->
                                                    ExifHelper.decodeRotatedBitmap(applicationContext, uri, maxDimension = null)
                                                } ?: previewBitmap

                                                val result = photoExporter.exportPhoto(
                                                    fullResBitmap,
                                                    currentSourceUri,
                                                    adjustments,
                                                    options,
                                                    activeZoomRatio
                                                )

                                                if (fullResBitmap != previewBitmap) {
                                                    fullResBitmap.recycle()
                                                }

                                                withContext(Dispatchers.Main) {
                                                    isLoading = false
                                                    result.onSuccess {
                                                        // Update non-destructive edit recipe
                                                        val updated = (currentRecipe ?: EditRecipe(
                                                            id = UUID.randomUUID().toString(),
                                                            sourceUriString = currentSourceUri.toString(),
                                                            timestamp = System.currentTimeMillis()
                                                        )).copy(
                                                            adjustments = adjustments,
                                                            exportOptions = options
                                                        )
                                                        editRecipeStore.saveRecipe(updated)

                                                        Toast.makeText(this@MainActivity, "Saved to DCIM/Framed", Toast.LENGTH_LONG).show()
                                                        editingBitmap?.recycle()
                                                        editingBitmap = null
                                                        currentScreen = AppScreen.LIBRARY
                                                    }.onFailure { e ->
                                                        Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Studio Loading Overlay
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = EmeraldLevel,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = loadingMessage,
                                    color = StudioTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveOriginalToPrivateStorage(uri: Uri): Uri? {
        return try {
            val originalsDir = File(filesDir, "originals").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(originalsDir, "ORIGINAL_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
