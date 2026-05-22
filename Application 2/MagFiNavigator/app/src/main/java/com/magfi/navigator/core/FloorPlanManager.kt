package com.magfi.navigator.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * FloorPlanManager — loads floor_plan.png from assets and provides the
 * authoritative pixel ↔ meter coordinate conversion.
 *
 * Block C · Lab Wing · Floor 3
 * ─────────────────────────────────────────────────────────────────────────
 * Image: 1024 × 1536 px (not square → DIFFERENT px/m on X and Y axes).
 * Use SCALE_X_PX_PER_M and SCALE_Y_PX_PER_M separately.
 *
 * Coordinate system:
 *   PDR (x=0, y=0)  = Entrance center at bottom of corridor
 *   +Y  = North (walking toward staircase / up the map)
 *   +X  = East  (into the lab rooms, right side of image)
 *
 * Image coordinate system (top-left = 0,0):
 *   image_px increases rightward
 *   image_py increases DOWNWARD  ← opposite to PDR +Y
 */
class FloorPlanManager(private val context: Context) {

    companion object {
        private const val TAG = "FloorPlanManager"
        private const val ASSET_NAME = "floor_plan.png"

        // ── Image dimensions (do NOT change unless you resize the PNG) ──────
        const val IMAGE_WIDTH_PX  = 1024f
        const val IMAGE_HEIGHT_PX = 1536f

        // ── Physical building dimensions ─────────────────────────────────────
        const val CORRIDOR_LENGTH_M = 40.0f   // Y-axis: entrance → staircase
        const val BUILDING_WIDTH_M  =  7.8f   // X-axis: left wall → right wall

        // ── Key pixel coordinates IN THE IMAGE (top-left = 0,0) ─────────────
        // Entrance center (physical origin 0,0):
        const val ORIGIN_PX_X = 310f   // horizontal pixel of entrance centre
        const val ORIGIN_PX_Y = 1370f  // vertical pixel of entrance centre

        // Staircase top (40 m North of entrance — directly above entrance):
        const val STAIR_PX_X = 310f
        const val STAIR_PX_Y =  95f

        // Building wall pixels for X-axis scale:
        const val LEFT_WALL_PX  = 235f
        const val RIGHT_WALL_PX = 620f

        // ── Derived scale factors ────────────────────────────────────────────
        // Y: (entrance pixel − staircase pixel) / corridor length in metres
        //    = (1370 − 95) / 40.0 = 31.875 px/m
        const val SCALE_Y_PX_PER_M =
            (ORIGIN_PX_Y - STAIR_PX_Y) / CORRIDOR_LENGTH_M  // 31.875f

        // X: building wall span in px / 7.8 m = 385 / 7.8 ≈ 49.36 px/m
        const val SCALE_X_PX_PER_M =
            (RIGHT_WALL_PX - LEFT_WALL_PX) / BUILDING_WIDTH_M  // ~49.36f
    }

    // ── Public properties set after loadFloorPlan() ──────────────────────────
    var bitmap: Bitmap? = null
        private set

    // Retained for MapCanvasView backward compatibility — set from companion.
    var originPixelX: Float = ORIGIN_PX_X
    var originPixelY: Float = ORIGIN_PX_Y
    var scalePxPerMeter: Float = SCALE_Y_PX_PER_M   // legacy — use two-axis API

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Load floor_plan.png from assets.
     * Uses inSampleSize=2 to halve memory footprint (large image).
     * Returns true on success.
     */
    fun loadFloorPlan(): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                // First pass: just decode bounds
                inJustDecodeBounds = true
                context.assets.open(ASSET_NAME).use {
                    BitmapFactory.decodeStream(it, null, this)
                }
                // Decide sample size to avoid OOM on high-res PNG
                inSampleSize = calculateInSampleSize(outWidth, outHeight, 1024, 1536)
                inJustDecodeBounds = false
            }
            context.assets.open(ASSET_NAME).use { stream ->
                bitmap = BitmapFactory.decodeStream(stream, null, options)
            }
            Log.d(TAG, "Floor plan loaded: ${bitmap?.width}×${bitmap?.height}px " +
                    "(scaleY=${SCALE_Y_PX_PER_M}px/m, scaleX=${SCALE_X_PX_PER_M}px/m)")
            bitmap != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $ASSET_NAME: ${e.message}")
            false
        }
    }

    // ── Coordinate conversion ────────────────────────────────────────────────

    /**
     * Convert PDR meter coordinates → image pixel coordinates.
     *
     * PDR (0,0) = entrance bottom-centre.
     * +Y North  → image_py DECREASES (upward in image).
     * +X East   → image_px INCREASES (rightward in image).
     */
    fun metersToPixels(xMeters: Float, yMeters: Float): Pair<Float, Float> {
        val imagePx = ORIGIN_PX_X + (xMeters * SCALE_X_PX_PER_M)
        val imagePy = ORIGIN_PX_Y - (yMeters * SCALE_Y_PX_PER_M)
        return Pair(imagePx, imagePy)
    }

    /**
     * Inverse: image pixel (px, py) → PDR meter coordinates.
     */
    fun pixelsToMeters(px: Float, py: Float): Pair<Float, Float> {
        val xMeters = (px - ORIGIN_PX_X) / SCALE_X_PX_PER_M
        val yMeters = (ORIGIN_PX_Y - py) / SCALE_Y_PX_PER_M
        return Pair(xMeters, yMeters)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun calculateInSampleSize(
        width: Int, height: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth  = width  / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                   halfWidth  / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ── Legacy two-point calibration (kept for API compatibility) ─────────────
    @Deprecated("Use the companion object constants directly — two-axis calibration.")
    fun setScaleFromTwoPoints(
        px1: Float, py1: Float, realMeters1X: Float, realMeters1Y: Float,
        px2: Float, py2: Float, realMeters2X: Float, realMeters2Y: Float
    ) {
        // No-op: scale is now derived from companion constants.
        Log.w(TAG, "setScaleFromTwoPoints() is deprecated — using companion constants.")
    }
}
