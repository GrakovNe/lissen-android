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
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile
    private var onShake: (() -> Unit)? = null

    @Volatile
    private var lastUpdate: Long = 0

    @Volatile
    private var lastX: Float = 0f

    @Volatile
    private var lastY: Float = 0f

    @Volatile
    private var lastZ: Float = 0f

    @Volatile
    private var lastShakeTimestamp: Long = 0

    // Configurable Thresholds
    private val timeThreshold = 1000L // Minimum time between shakes in ms

    private val lock = Any()

    fun start(onShake: () -> Unit) {
      synchronized(lock) {
        this.onShake = onShake
      }

      if (accelerometer != null) {
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
      } else {
        Timber.w("Accelerometer not supported on this device.")
      }
    }

    fun stop() {
      sensorManager?.unregisterListener(this)

      synchronized(lock) {
        onShake = null
      }
    }

    override fun onSensorChanged(event: SensorEvent?) {
      event ?: return

      if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
        val curTime = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        var diffTime = 0L
        var prevX = 0f
        var prevY = 0f
        var prevZ = 0f
        var shouldProcess = false

        // First synchronized block: Read/Update State
        synchronized(lock) {
          if ((curTime - lastUpdate) > 100) {
            diffTime = (curTime - lastUpdate)
            lastUpdate = curTime

            prevX = lastX
            prevY = lastY
            prevZ = lastZ

            lastX = x
            lastY = y
            lastZ = z
            shouldProcess = true
          }
        }

        if (!shouldProcess) return

        // Calculation outside lock
        val deltaX = x - prevX
        val deltaY = y - prevY
        val deltaZ = z - prevZ

        val speed = Math.sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / diffTime * 10000

        if (speed > SHAKE_THRESHOLD) {
          val now = System.currentTimeMillis()
          var callback: (() -> Unit)? = null

          // Second synchronized block: Throttle & Callback Retrieval
          synchronized(lock) {
            if (now - lastShakeTimestamp > timeThreshold) {
              lastShakeTimestamp = now
              callback = onShake
            }
          }

          // Execution outside lock
          if (callback != null) {
            Timber.d("Shake detected! Speed: $speed")
            mainHandler.post { callback?.invoke() }
          }
        }
      }
    }

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
