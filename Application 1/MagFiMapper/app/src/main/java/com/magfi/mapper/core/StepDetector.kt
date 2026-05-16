package com.magfi.mapper.core

import kotlin.math.sqrt

/**
 * StepDetector — Peak detection from accelerometer magnitude.
 * Computes acceleration magnitude, applies moving average,
 * detects peaks above threshold, and enforces 350ms minimum gap between steps.
 */
class StepDetector {

    interface Listener {
        fun onStepDetected(stepCount: Int)
    }

    companion object {
        const val THRESHOLD_OFFSET = 1.0f    // m/s² above rolling mean
        const val MIN_STEP_GAP_MS = 350L     // minimum ms between steps
        const val MAG_WINDOW = 5             // moving average window for magnitude
    }

    private var stepCount = 0
    private var lastStepTime = 0L
    private val magBuffer = ArrayDeque<Float>()
    private var prevMag = 0f                 // previous magnitude (for rising edge detection)

    fun processSample(ax: Float, ay: Float, az: Float, listener: Listener) {
        // Step 1: Compute raw magnitude
        val raw = sqrt(ax * ax + ay * ay + az * az)

        // Step 2: Apply moving average to magnitude
        val mag = movingAvg(magBuffer, raw, MAG_WINDOW)

        // Step 3: Compute rolling mean from buffer
        val rollingMean = if (magBuffer.isNotEmpty()) magBuffer.sum() / magBuffer.size else mag

        // Step 4: Detect peak — must be RISING and above threshold
        val isPeak = mag > (rollingMean + THRESHOLD_OFFSET) && mag > prevMag

        prevMag = mag

        if (!isPeak) return

        // Step 5: Enforce timing gate
        val now = System.currentTimeMillis()
        if (now - lastStepTime < MIN_STEP_GAP_MS) return

        // Step 6: Valid step
        stepCount++
        lastStepTime = now
        listener.onStepDetected(stepCount)
    }

    fun reset() {
        stepCount = 0
        lastStepTime = 0L
        prevMag = 0f
        magBuffer.clear()
    }

    fun getStepCount() = stepCount

    private fun movingAvg(buffer: ArrayDeque<Float>, newVal: Float, window: Int): Float {
        if (buffer.size >= window) buffer.removeFirst()
        buffer.addLast(newVal)
        return buffer.sum() / buffer.size
    }
}
