package com.magfi.mapper.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * PdrTracker — Pedestrian Dead Reckoning position engine.
 * On each step event, updates (x, y) using heading angle and fixed step length of 0.72m.
 * Exposes current position and trajectory history.
 */
class PdrTracker {

    data class Position(val x: Float, val y: Float)

    companion object {
        const val STEP_LENGTH_M = 0.72f   // fixed step length in meters
    }

    private var posX = 0f
    private var posY = 0f
    private val trajectory = mutableListOf<Position>()

    /**
     * Called on each detected step. Updates position using heading.
     * X = East (cos), Y = North (sin). Heading 0° = East (atan2 convention).
     */
    fun onStep(headingDeg: Float) {
        val rad = Math.toRadians(headingDeg.toDouble())
        posX += (STEP_LENGTH_M * cos(rad)).toFloat()
        posY += (STEP_LENGTH_M * sin(rad)).toFloat()
        trajectory.add(Position(posX, posY))
    }

    fun getPosition() = Position(posX, posY)

    fun getTrajectory(): List<Position> = trajectory.toList()

    fun reset() {
        posX = 0f
        posY = 0f
        trajectory.clear()
    }
}
