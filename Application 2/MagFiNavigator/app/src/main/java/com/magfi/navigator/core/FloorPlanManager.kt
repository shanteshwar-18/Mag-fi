package com.magfi.navigator.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FloorPlanManager — loads the floor plan bitmap from assets and provides
 * coordinate conversion between PDR meter space and canvas pixel space.
 *
 * Calibration: two real-world points are used to derive scalePxPerMeter.
 * The PDR origin (0,0) maps to (originPixelX, originPixelY) on the bitmap.
 *
 * Coordinate conventions:
 *   PDR:    X = East (+), Y = North (+)
 *   Canvas: X = right (+), Y = down (+)   → Y-axis is FLIPPED
 *
 * metersToPixels: py = originPixelY - (yMeters * scale)   [Y inverted]
 */
class FloorPlanManager(private val context: Context) {

    companion object {
        private const val TAG = "FloorPlanManager"
        private const val FLOOR_PLAN_ASSET = "floor_plan.png"
    }

    var bitmap: Bitmap? = null
        private set

    /** pixels per meter — set via setScaleFromTwoPoints() */
    var scalePxPerMeter: Float = 50f
        private set

    /** pixel coordinates of the PDR origin (0,0) on the bitmap */
    var originPixelX: Float = 0f
    var originPixelY: Float = 0f

    /**
     * Load floor_plan.png from assets.
     * Uses inSampleSize=2 to halve dimensions (OOM protection on low-RAM devices).
     * Returns true on success, false if asset not found.
     */
    fun loadFloorPlan(): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2    // halves each dimension → ¼ RAM usage
            }
            val stream = context.assets.open(FLOOR_PLAN_ASSET)
            bitmap = BitmapFactory.decodeStream(stream, null, opts)
            stream.close()
            Log.d(TAG, "Floor plan loaded: ${bitmap?.width}×${bitmap?.height}px")
            bitmap != null
        } catch (e: Exception) {
            Log.e(TAG, "Floor plan load failed: ${e.message}")
            false
        }
    }

    /**
     * Compute scalePxPerMeter from two calibration landmark points.
     *
     * @param px1, py1   pixel coordinates of landmark 1
     * @param realMeters1X, realMeters1Y   PDR meter coordinates of landmark 1
     * @param px2, py2   pixel coordinates of landmark 2
     * @param realMeters2X, realMeters2Y   PDR meter coordinates of landmark 2
     */
    fun setScaleFromTwoPoints(
        px1: Float, py1: Float, realMeters1X: Float, realMeters1Y: Float,
        px2: Float, py2: Float, realMeters2X: Float, realMeters2Y: Float
    ) {
        val pixDist  = sqrt((px2 - px1).pow(2) + (py2 - py1).pow(2))
        val realDist = sqrt((realMeters2X - realMeters1X).pow(2) + (realMeters2Y - realMeters1Y).pow(2))
        if (realDist > 0f) {
            scalePxPerMeter = pixDist / realDist
            Log.d(TAG, "Scale set: ${scalePxPerMeter}px/m (pixDist=$pixDist, realDist=$realDist)")
        }
    }

    /**
     * Convert PDR meter coordinates → bitmap pixel coordinates.
     * Note: Y is inverted (PDR north = canvas up = decreasing pixel Y).
     */
    fun metersToPixels(xMeters: Float, yMeters: Float): Pair<Float, Float> {
        val px = originPixelX + (xMeters * scalePxPerMeter)
        val py = originPixelY - (yMeters * scalePxPerMeter)    // Y flipped
        return Pair(px, py)
    }

    /**
     * Convert bitmap pixel coordinates → PDR meter coordinates.
     */
    fun pixelsToMeters(px: Float, py: Float): Pair<Float, Float> {
        val xMeters = (px - originPixelX) / scalePxPerMeter
        val yMeters = (originPixelY - py) / scalePxPerMeter    // Y flipped
        return Pair(xMeters, yMeters)
    }
}
