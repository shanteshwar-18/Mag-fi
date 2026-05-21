package com.magfi.navigator.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * PdrTracker — ported from App 1 with the SNAP extension for App 2.
 *
 * Pedestrian Dead Reckoning: updates (x, y) on each step event using
 * heading angle and fixed step length of 0.72m.
 *
 * App 2 addition: snap(correctedX, correctedY) — called by FingerprintMatcher
 * when a strong KNN match is found. Silently overrides PDR coordinates,
 * eliminating accumulated drift without any user-visible jump.
 */
class PdrTracker {

    data class Position(val x: Float, val y: Float)

    companion object {
        const val STEP_LENGTH_M = 0.72f   // fixed step length in meters
    }

    private var posX = 0f
    private var posY = 0f

    /**
     * Called on each detected step.
     * X = East (cos), Y = North (sin). Heading 0° = East (atan2 convention).
     */
    fun onStep(headingDeg: Float) {
        val rad  = Math.toRadians(headingDeg.toDouble())
        posX += (STEP_LENGTH_M * cos(rad)).toFloat()
        posY += (STEP_LENGTH_M * sin(rad)).toFloat()
    }

    fun getPosition() = Position(posX, posY)

    fun reset() {
        posX = 0f
        posY = 0f
    }

    /**
     * SNAP — App 2 addition.
     * Overrides current PDR position with a corrected KNN-matched position.
     * This erases accumulated drift; the Blue Dot re-renders smoothly on next step.
     */
    fun snap(correctedX: Float, correctedY: Float) {
        posX = correctedX
        posY = correctedY
    }
}
