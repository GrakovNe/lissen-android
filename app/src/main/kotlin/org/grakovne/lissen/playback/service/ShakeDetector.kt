package org.grakovne.lissen.playback.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@UnstableApi
class ShakeDetector
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onShake: (() -> Unit)? = null

    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f

    // Configurable Thresholds
    private val threshold = 12.0f // Accelerometer sensitivity
    private val timeThreshold = 1000L // Minimum time between shakes in ms
    private val debounceTime = 500L // Time buffer to avoid duplicate triggers

    fun start(onShake: () -> Unit) {
      this.onShake = onShake
      if (accelerometer != null) {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
      } else {
        Timber.w("Accelerometer not supported on this device.")
      }
    }

    fun stop() {
      sensorManager.unregisterListener(this)
      onShake = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
      event ?: return

      if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
        val curTime = System.currentTimeMillis()

        // Only process if enough time has passed
        if ((curTime - lastUpdate) > 100) {
          val diffTime = (curTime - lastUpdate)
          lastUpdate = curTime

          val x = event.values[0]
          val y = event.values[1]
          val z = event.values[2]

          // Simple shake detection logic based on g-force change
          // We're looking for significant acceleration in any direction
          val gX = x / SensorManager.GRAVITY_EARTH
          val gY = y / SensorManager.GRAVITY_EARTH
          val gZ = z / SensorManager.GRAVITY_EARTH

          // Calculate g-force
          val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

          // 1.0 is minimal gravity. Anything significantly above suggests movement.
          // Using raw values might be simpler if gForce logic is too sensitive or not enough.
          // Let's stick to the classic implementation:

          val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

          if (speed > SHAKE_THRESHOLD) { // Using a constant threshold
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp > timeThreshold) {
              lastShakeTimestamp = now
              Timber.d("Shake detected! Speed: $speed")
              onShake?.invoke()
            }
          }

          lastX = x
          lastY = y
          lastZ = z
        }
      }
    }

    private var lastShakeTimestamp: Long = 0

    companion object {
      private const val SHAKE_THRESHOLD = 800
    }

    override fun onAccuracyChanged(
      sensor: Sensor?,
      accuracy: Int,
    ) {
      // No-op
    }
  }
