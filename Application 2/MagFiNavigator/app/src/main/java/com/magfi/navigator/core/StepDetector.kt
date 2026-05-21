package com.magfi.navigator.core

import kotlin.math.sqrt

/**
 * StepDetector — ported identically from App 1 (MagFiMapper).
 *
 * Peak detection from accelerometer magnitude.
 * Computes acceleration magnitude, applies a moving average baseline,
 * detects peaks above (baseline + THRESHOLD_OFFSET), enforces 350ms gate.
 */
class StepDetector {

    interface Listener {
        fun onStepDetected(stepCount: Int)
    }

    companion object {
        const val THRESHOLD_OFFSET = 1.0f    // m/s² above rolling mean
        const val MIN_STEP_GAP_MS  = 350L    // minimum ms between steps
        const val MAG_WINDOW       = 5       // moving average window for magnitude
    }

    private var stepCount    = 0
    private var lastStepTime = 0L
    private val magBuffer    = ArrayDeque<Float>()
    private var prevMag      = 0f

    fun processSample(ax: Float, ay: Float, az: Float, listener: Listener) {
        val raw = sqrt(ax * ax + ay * ay + az * az)

        // Compute rolling mean BEFORE adding the new value (pre-sample baseline)
        val rollingMean = if (magBuffer.isNotEmpty()) magBuffer.sum() / magBuffer.size else raw

        movingAvg(magBuffer, raw, MAG_WINDOW)

        // Peak: raw spike must exceed baseline AND be rising (avoid falling-edge fires)
        val isPeak = raw > (rollingMean + THRESHOLD_OFFSET) && raw > prevMag
        prevMag = raw

        if (!isPeak) return

        val now = System.currentTimeMillis()
        if (now - lastStepTime < MIN_STEP_GAP_MS) return

        stepCount++
        lastStepTime = now
        listener.onStepDetected(stepCount)
    }

    fun reset() {
        stepCount    = 0
        lastStepTime = 0L
        prevMag      = -1f   // sentinel: first sample is always "rising"
        magBuffer.clear()
    }

    fun getStepCount() = stepCount

    private fun movingAvg(buffer: ArrayDeque<Float>, newVal: Float, window: Int): Float {
        if (buffer.size >= window) buffer.removeFirst()
        buffer.addLast(newVal)
        return buffer.sum() / buffer.size
    }
}
