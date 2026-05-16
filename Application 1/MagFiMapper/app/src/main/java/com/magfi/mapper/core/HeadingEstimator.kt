package com.magfi.mapper.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * HeadingEstimator — Magnetometer + Gyroscope fusion for heading.
 * Computes absolute heading using atan2(By, Bx) from filtered magnetometer data.
 * Uses gyroscope only for short-term smoothing between mag updates.
 * Outputs stable 0–360° heading values.
 */
class HeadingEstimator {

    interface Listener {
        fun onHeadingUpdate(degrees: Float)
    }

    private var theta0: Float = 0f
    private var isCalibrated = false
    private var lastHeadingDeg = 0f
    private val headingBuffer = ArrayDeque<Float>()
    private var lastGyroTimestamp = 0L
    private var gyroIntegral = 0f

    // For calibration — collect samples
    private val calibSamplesX = mutableListOf<Float>()
    private val calibSamplesY = mutableListOf<Float>()

    fun calibrate(bx: Float, by: Float) {
        calibSamplesX.add(bx)
        calibSamplesY.add(by)

        if (calibSamplesX.size >= 10) {
            val avgBx = calibSamplesX.average().toFloat()
            val avgBy = calibSamplesY.average().toFloat()
            theta0 = atan2(avgBy.toDouble(), avgBx.toDouble()).toFloat()
            isCalibrated = true
            calibSamplesX.clear()
            calibSamplesY.clear()
        }
    }

    fun processMag(bx: Float, by: Float, listener: Listener) {
        // Step 1: Compute raw heading (degrees, 0–360)
        var rawDeg = Math.toDegrees(atan2(by.toDouble(), bx.toDouble())).toFloat()
        if (rawDeg < 0) rawDeg += 360f

        // Step 2: Apply moving average using unit circle to handle 0/360 wrap-around
        val heading = circularMovingAvg(headingBuffer, rawDeg, 5)

        // Step 3: Blend with gyro integral for short-term stability
        val blend = 0.85f * heading + 0.15f * (lastHeadingDeg + gyroIntegral)
        gyroIntegral = 0f  // reset after each mag update

        // Step 4: Normalize to 0-360
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
        isCalibrated = false
        gyroIntegral = 0f
        headingBuffer.clear()
        calibSamplesX.clear()
        calibSamplesY.clear()
        lastGyroTimestamp = 0L
        lastHeadingDeg = 0f
    }

    /**
     * Circular moving average to correctly handle 0/360 wrap-around.
     * Averages on the unit circle using sin/cos then converts back.
     */
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
