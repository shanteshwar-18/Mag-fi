package com.magfi.navigator.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MapCanvasView — custom View that renders the indoor navigation map.
 *
 * Four rendering layers (drawn in order):
 *   1. Floor plan bitmap  — scaled to fill the view
 *   2. Route polyline     — dashed teal line through GraphNodes
 *   3. Graph node circles — amber waypoints + teal destination
 *   4. Blue Dot           — animated pulse rings at user position
 *
 * Also supports:
 *   - Pinch-to-zoom (ScaleGestureDetector)
 *   - Pan (GestureDetector.onScroll)
 *   - Follow mode (auto-center on Blue Dot)
 *   - Re-center via public API
 */
class MapCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Public properties — set from NavigationFragment ───────────────────────
    var floorBitmap: Bitmap? = null
    /** Inject FloorPlanManager so metersToCanvas() uses two-axis calibration. */
    var floorPlanManager: FloorPlanManager? = null
    // Legacy single-axis properties retained for backward compatibility:
    var scalePxPerMeter: Float = FloorPlanManager.SCALE_Y_PX_PER_M
    var originPixelX: Float = FloorPlanManager.ORIGIN_PX_X
    var originPixelY: Float = FloorPlanManager.ORIGIN_PX_Y
    var userPosX: Float = 0f    // in meters
    var userPosY: Float = 0f    // in meters
    var routeNodes: List<GraphNode> = emptyList()
    var showNodeLabels: Boolean = true

    // ── Zoom / Pan state ─────────────────────────────────────────────────────
    private var scaleFactor = 1.5f
    private var translateX  = 0f
    private var translateY  = 0f
    private val MIN_SCALE   = 0.8f
    private val MAX_SCALE   = 5.0f
    private var pivotX      = 0f
    private var pivotY      = 0f

    /** When true, the view auto-centers on the Blue Dot after each position update. */
    var followMode: Boolean = true

    // ── Blue Dot pulse animation ──────────────────────────────────────────────
    private var pulseRadius = 22f
    private val pulseAnimator: ValueAnimator = ValueAnimator.ofFloat(22f, 34f).apply {
        duration       = 1200
        repeatCount    = ValueAnimator.INFINITE
        repeatMode     = ValueAnimator.REVERSE
        interpolator   = LinearInterpolator()
        addUpdateListener { pulseRadius = it.animatedValue as Float; invalidate() }
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#14FFEC")
        strokeWidth = 8f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        maskFilter  = BlurMaskFilter(6f, BlurMaskFilter.Blur.SOLID)
    }
    private val nodeAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.SOLID)
    }
    private val nodeDestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14FFEC")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.SOLID)
    }
    private val nodeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#E0F7FA")
        textSize  = 28f
        textAlign = Paint.Align.CENTER
    }
    private val dotPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#663B82F6") // neon blue translucent
        style = Paint.Style.FILL
    }
    private val dotMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID)
    }
    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#2214FFEC")
        strokeWidth = 1f
        style       = Paint.Style.STROKE
    }

    // ── Gesture detectors ─────────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pivotX = detector.focusX
                pivotY = detector.focusY
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (scaleFactor * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                // Adjust translation to zoom toward pinch center
                translateX -= (pivotX - translateX) * (newScale / scaleFactor - 1f)
                translateY -= (pivotY - translateY) * (newScale / scaleFactor - 1f)
                scaleFactor = newScale
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                translateX -= distanceX
                translateY -= distanceY
                followMode = false   // user panned — disable auto-follow
                invalidate()
                return true
            }
        })

    init {
        pulseAnimator.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!pulseAnimator.isRunning) pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel()
    }

    // ── Public update methods ─────────────────────────────────────────────────

    fun updatePosition(x: Float, y: Float) {
        userPosX = x
        userPosY = y
        if (followMode) centerViewOnDot()
        invalidate()
    }

    fun updateRoute(nodes: List<GraphNode>) {
        routeNodes = nodes
        followMode = true   // reset follow mode when a new route is set
        invalidate()
    }

    /** Programmatically re-center view on the Blue Dot. */
    fun recenter(zoom: Float = 2f) {
        scaleFactor = zoom.coerceIn(MIN_SCALE, MAX_SCALE)
        followMode  = true
        centerViewOnDot()
        invalidate()
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var consumed = scaleDetector.onTouchEvent(event)
        consumed = gestureDetector.onTouchEvent(event) || consumed
        return consumed || super.onTouchEvent(event)
    }

    // ── Canvas rendering ──────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor, pivotX - translateX, pivotY - translateY)

        drawFloorPlan(canvas)
        drawRouteLine(canvas)
        drawGraphNodes(canvas)
        drawBlueDot(canvas)

        canvas.restore()
    }

    private fun drawFloorPlan(canvas: Canvas) {
        val bmp = floorBitmap ?: run {
            // No bitmap — draw placeholder dark background with grid
            canvas.drawColor(Color.parseColor("#0D2137"))
            val step = 50f
            var x = 0f
            while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
            var y = 0f
            while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
            return
        }
        // Draw bitmap stretched to fill the entire canvas area (pre-scale transform applied by onDraw)
        val canvasW = width.toFloat()  / scaleFactor
        val canvasH = height.toFloat() / scaleFactor
        val rect = RectF(0f, 0f, canvasW, canvasH)
        canvas.drawBitmap(bmp, null, rect, null)
    }

    private fun drawRouteLine(canvas: Canvas) {
        if (routeNodes.size < 2) return
        val path = Path()
        val (fx, fy) = metersToCanvas(routeNodes[0].x, routeNodes[0].y)
        path.moveTo(fx, fy)
        for (i in 1 until routeNodes.size) {
            val (nx, ny) = metersToCanvas(routeNodes[i].x, routeNodes[i].y)
            path.lineTo(nx, ny)
        }
        canvas.drawPath(path, routePaint)
    }

    private fun drawGraphNodes(canvas: Canvas) {
        for ((index, node) in routeNodes.withIndex()) {
            val (cx, cy) = metersToCanvas(node.x, node.y)
            val isDestination = index == routeNodes.lastIndex
            val paint  = if (isDestination) nodeDestPaint else nodeAmberPaint
            val radius = if (isDestination) 18f else 12f
            canvas.drawCircle(cx, cy, radius, paint)
            if (showNodeLabels) {
                canvas.drawText(node.id.replace("_", " "), cx, cy - radius - 6f, nodeLabelPaint)
            }
        }
    }

    private fun drawBlueDot(canvas: Canvas) {
        val (dotX, dotY) = metersToCanvas(userPosX, userPosY)

        // Outer animated pulse ring
        canvas.drawCircle(dotX, dotY, pulseRadius, dotPulsePaint)
        // Main dot
        canvas.drawCircle(dotX, dotY, 18f, dotMainPaint)
        // Inner white core
        canvas.drawCircle(dotX, dotY, 7f, dotCorePaint)
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    /**
     * Convert PDR meter coordinates → canvas pixel coordinates.
     *
     * Uses FloorPlanManager.metersToPixels() for the correct two-axis
     * (SCALE_X_PX_PER_M ≠ SCALE_Y_PX_PER_M) conversion, then scales
     * those image pixels to the actual View size.
     */
    private fun metersToCanvas(xMeters: Float, yMeters: Float): Pair<Float, Float> {
        val bmp = floorBitmap
        val fm  = floorPlanManager

        val (imagePx, imagePy) = if (fm != null) {
            // Two-axis: use calibrated manager
            fm.metersToPixels(xMeters, yMeters)
        } else {
            // Fallback: single-axis legacy formula
            val px = originPixelX + xMeters * scalePxPerMeter
            val py = originPixelY - yMeters * scalePxPerMeter
            Pair(px, py)
        }

        // Scale image pixels → canvas pixels (bitmap is stretched to fill the view)
        val imgW = bmp?.width?.toFloat()  ?: FloorPlanManager.IMAGE_WIDTH_PX
        val imgH = bmp?.height?.toFloat() ?: FloorPlanManager.IMAGE_HEIGHT_PX
        val canvasW = width.toFloat()  / scaleFactor
        val canvasH = height.toFloat() / scaleFactor
        val canvasX = imagePx * (canvasW / imgW)
        val canvasY = imagePy * (canvasH / imgH)
        return Pair(canvasX, canvasY)
    }

    /** Compute translation so Blue Dot is centered in the view. */
    private fun centerViewOnDot() {
        val (dotCanvasX, dotCanvasY) = metersToCanvas(userPosX, userPosY)
        translateX = width  / 2f - dotCanvasX * scaleFactor
        translateY = height / 2f - dotCanvasY * scaleFactor
    }
}
