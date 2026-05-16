package com.magfi.mapper.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log

/**
 * SensorEngine — core listener class.
 * Registers accelerometer, magnetometer, and gyroscope on a dedicated HandlerThread.
 * Applies a moving average filter (window 5) to each axis.
 * Broadcasts filtered values via Listener callback on the MAIN thread.
 */
class SensorEngine(private val context: Context) : SensorEventListener {

    interface Listener {
        fun onAccelUpdate(ax: Float, ay: Float, az: Float)
        fun onMagUpdate(bx: Float, by: Float, bz: Float)
        fun onGyroUpdate(gx: Float, gy: Float, gz: Float, timestampNs: Long)
    }

    companion object {
        private const val TAG = "SensorEngine"
        private const val WINDOW = 5
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var sensorHandler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isRunning = false
    private var listener: Listener? = null

    // Moving average buffers for each axis (9 total)
    private val accelBufferX = ArrayDeque<Float>()
    private val accelBufferY = ArrayDeque<Float>()
    private val accelBufferZ = ArrayDeque<Float>()
    private val magBufferX = ArrayDeque<Float>()
    private val magBufferY = ArrayDeque<Float>()
    private val magBufferZ = ArrayDeque<Float>()
    private val gyroBufferX = ArrayDeque<Float>()
    private val gyroBufferY = ArrayDeque<Float>()
    private val gyroBufferZ = ArrayDeque<Float>()

    fun start(listener: Listener) {
        this.listener = listener
        isRunning = true

        handlerThread = HandlerThread("SensorThread").also { it.start() }
        sensorHandler = Handler(handlerThread.looper)

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Log.w(TAG, "No accelerometer sensor available")
        } else {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
        }

        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (mag == null) {
            Log.w(TAG, "No magnetometer sensor available")
        } else {
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
        }

        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            Log.w(TAG, "No gyroscope sensor available — will continue without it")
        } else {
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
        }

        Log.d(TAG, "SensorEngine started on HandlerThread")
    }

    fun stop() {
        isRunning = false
        listener = null
        sensorManager.unregisterListener(this)
        if (::handlerThread.isInitialized) {
            handlerThread.quitSafely()
        }
        clearBuffers()
        Log.d(TAG, "SensorEngine stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRunning || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = movingAvg(accelBufferX, event.values[0], WINDOW)
                val ay = movingAvg(accelBufferY, event.values[1], WINDOW)
                val az = movingAvg(accelBufferZ, event.values[2], WINDOW)
                mainHandler.post { listener?.onAccelUpdate(ax, ay, az) }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val bx = movingAvg(magBufferX, event.values[0], WINDOW)
                val by = movingAvg(magBufferY, event.values[1], WINDOW)
                val bz = movingAvg(magBufferZ, event.values[2], WINDOW)
                mainHandler.post { listener?.onMagUpdate(bx, by, bz) }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = movingAvg(gyroBufferX, event.values[0], WINDOW)
                val gy = movingAvg(gyroBufferY, event.values[1], WINDOW)
                val gz = movingAvg(gyroBufferZ, event.values[2], WINDOW)
                val ts = event.timestamp
                mainHandler.post { listener?.onGyroUpdate(gx, gy, gz, ts) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun movingAvg(buffer: ArrayDeque<Float>, newVal: Float, window: Int): Float {
        if (buffer.size >= window) buffer.removeFirst()
        buffer.addLast(newVal)
        return buffer.sum() / buffer.size
    }

    private fun clearBuffers() {
        accelBufferX.clear(); accelBufferY.clear(); accelBufferZ.clear()
        magBufferX.clear(); magBufferY.clear(); magBufferZ.clear()
        gyroBufferX.clear(); gyroBufferY.clear(); gyroBufferZ.clear()
    }
}
