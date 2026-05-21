package com.magfi.navigator.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * HeadingEstimator — ported identically from App 1 (MagFiMapper).
 *
 * Computes absolute heading using atan2(By, Bx) from filtered magnetometer data.
 * Blends with gyroscope integral (15% weight) for short-term stability.
 * Handles 0/360° wrap-around using circular moving average on unit circle.
 * Outputs stable 0–360° heading values.
 */
class HeadingEstimator {

    interface Listener {
        fun onHeadingUpdate(degrees: Float)
    }

    private var lastHeadingDeg   = 0f
    private val headingBuffer    = ArrayDeque<Float>()
    private var lastGyroTimestamp = 0L
    private var gyroIntegral     = 0f
    private val calibSamplesX   = mutableListOf<Float>()
    private val calibSamplesY   = mutableListOf<Float>()

    fun calibrate(bx: Float, by: Float) {
        calibSamplesX.add(bx)
        calibSamplesY.add(by)
        if (calibSamplesX.size >= 10) {
            calibSamplesX.clear()
            calibSamplesY.clear()
        }
    }

    fun processMag(bx: Float, by: Float, listener: Listener) {
        var rawDeg = Math.toDegrees(atan2(by.toDouble(), bx.toDouble())).toFloat()
        if (rawDeg < 0) rawDeg += 360f

        val heading = circularMovingAvg(headingBuffer, rawDeg, 5)
        val blend   = 0.85f * heading + 0.15f * (lastHeadingDeg + gyroIntegral)
        gyroIntegral = 0f

        var finalHeading = blend % 360f
        if (finalHeading < 0) finalHeading += 360f

        lastHeadingDeg = finalHeading
        listener.onHeadingUpdate(finalHeading)
    }

    fun processGyro(gz: Float, timestampNs: Long) {
        if (lastGyroTimestamp == 0L) {
            lastGyroTimestamp = timestampNs
            return
        }
        val dt = (timestampNs - lastGyroTimestamp) / 1_000_000_000.0
        gyroIntegral += Math.toDegrees(gz.toDouble()).toFloat() * dt.toFloat()
        lastGyroTimestamp = timestampNs
    }

    fun getCurrentHeading() = lastHeadingDeg

    fun reset() {
        gyroIntegral     = 0f
        lastGyroTimestamp = 0L
        lastHeadingDeg   = 0f
        headingBuffer.clear()
        calibSamplesX.clear()
        calibSamplesY.clear()
    }

    /** Circular mean avoids 0/360° discontinuity by working on unit circle. */
    private fun circularMovingAvg(buffer: ArrayDeque<Float>, newDeg: Float, window: Int): Float {
        if (buffer.size >= window) buffer.removeFirst()
        buffer.addLast(newDeg)
        val avgSin = buffer.map { sin(Math.toRadians(it.toDouble())) }.average()
        val avgCos = buffer.map { cos(Math.toRadians(it.toDouble())) }.average()
        var result = Math.toDegrees(atan2(avgSin, avgCos)).toFloat()
        if (result < 0) result += 360f
        return result
    }
}
