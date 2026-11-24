package com.aesthetic.framed.domain.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aesthetic.framed.data.model.HorizonState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class HorizonSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _horizonState = MutableStateFlow(HorizonState())
    val horizonState: StateFlow<HorizonState> = _horizonState.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Exponential smoothing factor (0.0 < alpha <= 1.0)
    private val alpha = 0.25f
    private var smoothedRoll = 0f
    private var smoothedPitch = 0f
    private var isFirstSample = true

    fun startListening() {
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        isFirstSample = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[1] = pitch (radians, [-pi, pi])
        // orientationAngles[2] = roll (radians, [-pi/2, pi/2])
        val rawPitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        if (isFirstSample) {
            smoothedPitch = rawPitchDegrees
            smoothedRoll = rawRollDegrees
            isFirstSample = false
        } else {
            smoothedPitch += alpha * (rawPitchDegrees - smoothedPitch)
            smoothedRoll += alpha * (rawRollDegrees - smoothedRoll)
        }

        // Snapping within +/- 0.5 degrees is considered perfectly level
        val isLevel = abs(smoothedRoll) <= 0.5f

        _horizonState.value = HorizonState(
            rollDegrees = smoothedRoll,
            pitchDegrees = smoothedPitch,
            isLevel = isLevel
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
