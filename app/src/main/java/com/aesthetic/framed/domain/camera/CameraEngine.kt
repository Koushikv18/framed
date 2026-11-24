package com.aesthetic.framed.domain.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aesthetic.framed.data.model.CameraAspectRatio
import com.aesthetic.framed.data.model.FlashMode
import com.aesthetic.framed.data.model.ManualExposureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
class CameraEngine(private val context: Context) {

    companion object {
        private const val TAG = "CameraEngine"
        val OUTPUT_FORMAT_JPEG = ImageCapture.OUTPUT_FORMAT_JPEG
        val OUTPUT_FORMAT_RAW = ImageCapture.OUTPUT_FORMAT_RAW
        val OUTPUT_FORMAT_RAW_JPEG = ImageCapture.OUTPUT_FORMAT_RAW_JPEG
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val compositionAnalyzer = com.aesthetic.framed.domain.coach.CompositionAnalyzer(context)

    private val _exposureState = MutableStateFlow(ManualExposureState())
    val exposureState: StateFlow<ManualExposureState> = _exposureState.asStateFlow()

    private val _currentAspectRatio = MutableStateFlow(CameraAspectRatio.RATIO_4_3)
    val currentAspectRatio: StateFlow<CameraAspectRatio> = _currentAspectRatio.asStateFlow()

    /**
     * Fix the dead cameraExecutor bug: Ensure cameraExecutor is active
     * before binding use cases or running analysis.
     */
    fun ensureExecutorActive() {
        if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    fun initialize(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onInitialized: () -> Unit = {}
    ) {
        ensureExecutorActive()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner, previewView)
            checkRawCapabilities()
            onInitialized()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Inspect native supported output formats for DNG RAW.
     */
    private fun checkRawCapabilities() {
        val currentCamera = camera ?: return
        try {
            val capabilities = ImageCapture.getImageCaptureCapabilities(currentCamera.cameraInfo)
            val supportedFormats = capabilities.supportedOutputFormats
            val hasRaw = supportedFormats.contains(OUTPUT_FORMAT_RAW_JPEG) ||
                    supportedFormats.contains(OUTPUT_FORMAT_RAW)

            _exposureState.value = _exposureState.value.copy(
                isRawSupported = hasRaw
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read camera RAW capabilities", e)
            _exposureState.value = _exposureState.value.copy(
                isRawSupported = false
            )
        }
    }

    fun setAspectRatio(ratio: CameraAspectRatio, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        _currentAspectRatio.value = ratio
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    /**
     * S25 Ultra Hardware Guardrail:
     * Quad-lens switching via CameraX zoomRatio (0.6f, 1.0f, 3.0f, 5.0f).
     * Restricts DNG RAW capture to 0.6x and 1.0x lenses only to avoid Samsung's
     * third-party telephoto RAW corruption bug on 3x and 5x sensors.
     */
    fun setZoomRatio(ratio: Float, lifecycleOwner: LifecycleOwner? = null, previewView: PreviewView? = null) {
        val currentCamera = camera
        val zoomState = currentCamera?.cameraInfo?.zoomState?.value
        val minRatio = zoomState?.minZoomRatio ?: 0.6f
        val maxRatio = zoomState?.maxZoomRatio ?: 10.0f
        val clampedRatio = ratio.coerceIn(minRatio, maxRatio)

        val isTelephoto = clampedRatio >= 2.5f
        val shouldRebind = isTelephoto && _exposureState.value.isRawEnabled

        _exposureState.value = _exposureState.value.copy(
            zoomRatio = clampedRatio,
            isTelephotoRawRestricted = isTelephoto,
            isRawEnabled = if (isTelephoto) false else _exposureState.value.isRawEnabled
        )

        currentCamera?.cameraControl?.setZoomRatio(clampedRatio)
        compositionAnalyzer.updateCurrentZoom(clampedRatio)

        if (shouldRebind && lifecycleOwner != null && previewView != null) {
            bindCameraUseCases(lifecycleOwner, previewView)
        }
    }

    fun setRawCaptureEnabled(enabled: Boolean, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        // Enforce S25 Ultra guardrail: restrict DNG RAW on telephoto lenses (3x / 5x)
        if (_exposureState.value.zoomRatio >= 2.5f && enabled) {
            return
        }
        if (!_exposureState.value.isRawSupported && enabled) return
        _exposureState.value = _exposureState.value.copy(isRawEnabled = enabled)
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun pauseCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        ensureExecutorActive()
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        ensureExecutorActive()
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        // Restrict RAW if telephoto is active (>= 2.5x)
        val rawEnabled = _exposureState.value.isRawEnabled &&
                _exposureState.value.isRawSupported &&
                _exposureState.value.zoomRatio < 2.5f

        val captureFormat = if (rawEnabled) {
            OUTPUT_FORMAT_RAW_JPEG
        } else {
            OUTPUT_FORMAT_JPEG
        }

        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)

        try {
            captureBuilder.setOutputFormat(captureFormat)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set capture format: $captureFormat, falling back to JPEG", e)
            captureBuilder.setOutputFormat(OUTPUT_FORMAT_JPEG)
        }

        imageCapture = captureBuilder.build()

        imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, compositionAnalyzer)
            }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
            camera?.cameraControl?.setZoomRatio(_exposureState.value.zoomRatio)
            applyExposureParameters()
            applyFlashParameters()
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    fun updateManualMode(isManual: Boolean) {
        _exposureState.value = _exposureState.value.copy(isManualMode = isManual)
        applyExposureParameters()
    }

    fun updateIso(iso: Int) {
        _exposureState.value = _exposureState.value.copy(iso = iso)
        if (_exposureState.value.isManualMode) {
            applyExposureParameters()
        }
    }

    fun updateShutterSpeed(shutterSpeedNanos: Long) {
        _exposureState.value = _exposureState.value.copy(shutterSpeedNanos = shutterSpeedNanos)
        if (_exposureState.value.isManualMode) {
            applyExposureParameters()
        }
    }

    fun updateExposureCompensation(index: Int) {
        _exposureState.value = _exposureState.value.copy(exposureCompensationIndex = index)
        if (!_exposureState.value.isManualMode) {
            applyExposureParameters()
        }
    }

    fun cycleFlashMode() {
        val nextMode = when (_exposureState.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        _exposureState.value = _exposureState.value.copy(flashMode = nextMode)
        applyFlashParameters()
    }

    private fun applyExposureParameters() {
        val activeCamera = camera ?: return
        val state = _exposureState.value
        val camera2Control = Camera2CameraControl.from(activeCamera.cameraControl)

        val requestOptions = CaptureRequestOptions.Builder()

        try {
            if (state.isManualMode) {
                requestOptions.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                requestOptions.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    state.iso
                )
                requestOptions.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    state.shutterSpeedNanos
                )
            } else {
                requestOptions.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                try {
                    activeCamera.cameraControl.setExposureCompensationIndex(state.exposureCompensationIndex)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            camera2Control.setCaptureRequestOptions(requestOptions.build())
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackOptions = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    .build()
                camera2Control.setCaptureRequestOptions(fallbackOptions)
                _exposureState.value = _exposureState.value.copy(isManualMode = false)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to reset camera to auto exposure", fallbackEx)
            }
        }
    }

    private fun applyFlashParameters() {
        val activeCamera = camera ?: return
        val currentImageCapture = imageCapture ?: return
        val state = _exposureState.value

        if (state.flashMode == FlashMode.TORCH) {
            activeCamera.cameraControl.enableTorch(true)
            currentImageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
        } else {
            activeCamera.cameraControl.enableTorch(false)
            currentImageCapture.flashMode = when (state.flashMode) {
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    /**
     * Take picture with S25 Ultra hardware protection:
     * - Restricts RAW to 0.6x and 1.0x wide lenses.
     * - When OUTPUT_FORMAT_RAW_JPEG is active, supplies dual OutputFileOptions (rawOptions & jpegOptions)
     *   and copies both .dng and .jpg to context.filesDir/originals for non-destructive editing.
     */
    fun takePhoto(
        onSuccess: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        ensureExecutorActive()
        val capture = imageCapture ?: return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val isTelephoto = _exposureState.value.zoomRatio >= 2.5f
        val isDualRawActive = _exposureState.value.isRawEnabled && _exposureState.value.isRawSupported && !isTelephoto

        val originalsDir = File(context.filesDir, "originals").apply {
            if (!exists()) mkdirs()
        }
        val privateJpegFile = File(originalsDir, "ORIGINAL_${timeStamp}.jpg")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "FRAMED_${timeStamp}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/FramedClone")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val jpegOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        if (isDualRawActive) {
            // Dual RAW + JPEG capture via CameraX 1.7.0
            val privateDngFile = File(originalsDir, "ORIGINAL_${timeStamp}.dng")
            val rawOptions = ImageCapture.OutputFileOptions.Builder(privateDngFile).build()

            capture.takePicture(
                rawOptions,
                jpegOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        if (savedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }
                            context.contentResolver.update(savedUri, updateValues, null, null)
                        }
                        // Copy JPEG into app-private originals
                        if (savedUri != null) {
                            try {
                                context.contentResolver.openInputStream(savedUri)?.use { input ->
                                    FileOutputStream(privateJpegFile).use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to copy original dual RAW JPEG to private storage", e)
                            }
                        }
                        val finalUri = savedUri ?: Uri.fromFile(privateJpegFile)
                        ContextCompat.getMainExecutor(context).execute {
                            onSuccess(finalUri)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        ContextCompat.getMainExecutor(context).execute {
                            onError(exception)
                        }
                    }
                }
            )
        } else {
            // Standard JPEG capture
            capture.takePicture(
                jpegOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        if (savedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            }
                            context.contentResolver.update(savedUri, updateValues, null, null)
                        }
                        if (savedUri != null) {
                            try {
                                context.contentResolver.openInputStream(savedUri)?.use { input ->
                                    FileOutputStream(privateJpegFile).use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to copy original dual RAW JPEG to private storage", e)
                            }
                        }
                        val finalUri = savedUri ?: Uri.fromFile(privateJpegFile)
                        ContextCompat.getMainExecutor(context).execute {
                            onSuccess(finalUri)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        ContextCompat.getMainExecutor(context).execute {
                            onError(exception)
                        }
                    }
                }
            )
        }
    }

    fun release() {
        // Do not permanently kill cameraExecutor during normal navigation
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbind camera provider on release", e)
        }
    }

    fun destroy() {
        cameraExecutor.shutdown()
    }
}
