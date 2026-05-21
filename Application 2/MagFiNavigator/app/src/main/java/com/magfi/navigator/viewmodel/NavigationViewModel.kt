package com.magfi.navigator.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.magfi.navigator.core.FingerprintMatcher
import com.magfi.navigator.core.Fingerprint
import com.magfi.navigator.core.FloorPlanManager
import com.magfi.navigator.core.GraphNode
import com.magfi.navigator.core.HeadingEstimator
import com.magfi.navigator.core.MapDatabaseHelper
import com.magfi.navigator.core.PdrTracker
import com.magfi.navigator.core.RoutingEngine
import com.magfi.navigator.core.SensorEngine
import com.magfi.navigator.core.StepDetector
import com.magfi.navigator.core.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Localization state sealed class.
 * Reflects the current quality of the position estimate.
 */
sealed class LocalizationState {
    object WaitingForStep : LocalizationState()
    object PdrOnly        : LocalizationState()   // no Wi-Fi/mag match for 10+ steps
    data class Fused(val ssd: Float) : LocalizationState()   // snap fired
}

/**
 * NavigationViewModel — LiveData hub for all navigation state.
 *
 * Orchestrates the full localization pipeline on every step:
 *   1. PDR update  (heading + step length → new x,y)
 *   2. Wi-Fi filter + Magnetic KNN  (FingerprintMatcher)
 *   3. SNAP  (PdrTracker.snap() if match is strong enough)
 *   4. Publish to LiveData  (UI observes and redraws)
 *
 * Also manages:
 *   - Floor plan loading  (FloorPlanManager)
 *   - Routing  (RoutingEngine → Dijkstra path)
 *   - Fail-safe states  (sensor unavailable, empty DB, PDR-only mode)
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NavigationViewModel"

        // Hardcoded floor plan calibration
        // Update pixel values to match your actual floor_plan.png dimensions.
        // Example: main entrance door at pixel (100,400) on the unscaled bitmap,
        // corridor endpoint at pixel (100,1400) = 20m North.
        private const val ORIGIN_PX_X     = 100f
        private const val ORIGIN_PX_Y     = 400f
        private const val CALIB_PT2_PX_X  = 100f
        private const val CALIB_PT2_PX_Y  = 1400f
        private const val CALIB_PT2_REAL_Y = 20f   // 20 meters North of origin
    }

    // ── LiveData ──────────────────────────────────────────────────────────────
    val stepCount         : MutableLiveData<Int>                    = MutableLiveData(0)
    val heading           : MutableLiveData<Float>                  = MutableLiveData(0f)
    val magData           : MutableLiveData<Triple<Float,Float,Float>> = MutableLiveData()
    val posX              : MutableLiveData<Float>                  = MutableLiveData(0f)
    val posY              : MutableLiveData<Float>                  = MutableLiveData(0f)
    val fingerprintCount  : MutableLiveData<Int>                    = MutableLiveData(0)
    val strongestRouter   : MutableLiveData<Pair<String,Int>?>      = MutableLiveData(null)
    val currentRoute      : MutableLiveData<List<GraphNode>?>       = MutableLiveData(null)
    val destinationName   : MutableLiveData<String>                 = MutableLiveData("")
    val routeError        : MutableLiveData<String?>                = MutableLiveData(null)
    val localizationState : MutableLiveData<LocalizationState>      = MutableLiveData(LocalizationState.WaitingForStep)
    val sensorError       : MutableLiveData<String?>                = MutableLiveData(null)

    // ── Core components ───────────────────────────────────────────────────────
    val  floorPlanManager  = FloorPlanManager(application)
    private val dbHelper   = MapDatabaseHelper(application)
    private val engine     = SensorEngine(application)
    private val stepDetector      = StepDetector()
    private val headingEstimator  = HeadingEstimator()
    private val pdrTracker        = PdrTracker()
    private val wifiScanner       = WifiScanner(application)
    private val matcher           = FingerprintMatcher()
    private val routingEngine     = RoutingEngine()

    // ── In-memory fingerprint store ────────────────────────────────────────────
    var allFingerprints: List<Fingerprint> = emptyList()
        private set

    // ── Fail-safe tracking ────────────────────────────────────────────────────
    private var pdrOnlySteps = 0
    private var lastSsd      = Float.MAX_VALUE

    // ── Latest mag readings (accessed from step callback) ─────────────────────
    private val latestBx get() = magData.value?.first  ?: 0f
    private val latestBy get() = magData.value?.second ?: 0f
    private val latestBz get() = magData.value?.third  ?: 0f

    init {
        loadDatabaseAndFloorPlan()
        buildRoutingGraph()
    }

    // ── Initialization ────────────────────────────────────────────────────────

    private fun loadDatabaseAndFloorPlan() {
        viewModelScope.launch(Dispatchers.IO) {
            // Load fingerprints
            try {
                allFingerprints = dbHelper.loadAllFingerprints()
                fingerprintCount.postValue(allFingerprints.size)
                Log.d(TAG, "Loaded ${allFingerprints.size} fingerprints")

                if (allFingerprints.isEmpty()) {
                    sensorError.postValue(
                        "Map database is empty — magnetic matching disabled. " +
                        "Please re-run the Python Bridge pipeline."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB load failed: ${e.message}")
                sensorError.postValue("Failed to load map database: ${e.message}")
            }

            // Load floor plan
            val ok = floorPlanManager.loadFloorPlan()
            if (ok) {
                floorPlanManager.originPixelX = ORIGIN_PX_X
                floorPlanManager.originPixelY = ORIGIN_PX_Y
                floorPlanManager.setScaleFromTwoPoints(
                    px1 = ORIGIN_PX_X,    py1 = ORIGIN_PX_Y,
                    realMeters1X = 0f,    realMeters1Y = 0f,
                    px2 = CALIB_PT2_PX_X, py2 = CALIB_PT2_PX_Y,
                    realMeters2X = 0f,    realMeters2Y = CALIB_PT2_REAL_Y
                )
                Log.d(TAG, "Floor plan ready — scale=${floorPlanManager.scalePxPerMeter}px/m")
            } else {
                Log.w(TAG, "floor_plan.png not found in assets — using grid placeholder")
            }
        }
    }

    /**
     * Build the hardcoded routing graph.
     * Coordinates are in PDR meters (origin = main entrance).
     * Adapt node coordinates to match your actual building layout and floor_plan.png scale.
     */
    private fun buildRoutingGraph() {
        // ── Nodes ──────────────────────────────────────────────────────────────
        routingEngine.addNode("MainEntrance",     0f,   0f)
        routingEngine.addNode("Corridor_A",       0f,   5f)
        routingEngine.addNode("Corridor_B",       0f,  10f)
        routingEngine.addNode("CorridorJunction", 4f,  10f)
        routingEngine.addNode("Lab301",           8f,  10f)
        routingEngine.addNode("Lab302",           8f,  14f)
        routingEngine.addNode("Lab303",           8f,  18f)
        routingEngine.addNode("Library",         -4f,  10f)
        routingEngine.addNode("Cafeteria",       -4f,   5f)
        routingEngine.addNode("StairsNorth",      0f,  20f)
        routingEngine.addNode("StairsSouth",      0f,  -2f)
        routingEngine.addNode("FacultyOffice",    4f,   5f)
        routingEngine.addNode("SeminarHall",      8f,   5f)
        routingEngine.addNode("MainExit",         0f,  -4f)

        // ── Edges (bidirectional, auto-weighted) ───────────────────────────────
        routingEngine.addEdge("MainEntrance",     "Corridor_A")
        routingEngine.addEdge("Corridor_A",       "Corridor_B")
        routingEngine.addEdge("Corridor_A",       "Cafeteria")
        routingEngine.addEdge("Corridor_A",       "FacultyOffice")
        routingEngine.addEdge("FacultyOffice",    "SeminarHall")
        routingEngine.addEdge("Corridor_B",       "CorridorJunction")
        routingEngine.addEdge("CorridorJunction", "Lab301")
        routingEngine.addEdge("CorridorJunction", "Library")
        routingEngine.addEdge("Lab301",           "Lab302")
        routingEngine.addEdge("Lab302",           "Lab303")
        routingEngine.addEdge("Corridor_B",       "StairsNorth")
        routingEngine.addEdge("MainEntrance",     "StairsSouth")
        routingEngine.addEdge("StairsSouth",      "MainExit")

        Log.d(TAG, "Routing graph built: ${routingEngine.getAllNodeIds().size} nodes")
    }

    // ── Sensor lifecycle ──────────────────────────────────────────────────────

    fun startSensors() {
        engine.start(object : SensorEngine.Listener {
            override fun onAccelUpdate(ax: Float, ay: Float, az: Float) {
                stepDetector.processSample(ax, ay, az, object : StepDetector.Listener {
                    override fun onStepDetected(count: Int) {
                        onStep(count)
                    }
                })
            }

            override fun onMagUpdate(bx: Float, by: Float, bz: Float) {
                magData.postValue(Triple(bx, by, bz))
                headingEstimator.processMag(bx, by, object : HeadingEstimator.Listener {
                    override fun onHeadingUpdate(degrees: Float) {
                        heading.postValue(degrees)
                    }
                })
            }

            override fun onGyroUpdate(gx: Float, gy: Float, gz: Float, timestampNs: Long) {
                headingEstimator.processGyro(gz, timestampNs)
            }
        })

        // Start Wi-Fi scanning
        wifiScanner.start(object : WifiScanner.Listener {
            override fun onWifiPayload(jsonPayload: String) {
                strongestRouter.postValue(wifiScanner.getStrongestRouter())
            }
        })

        // Report sensor errors for fail-safe UX
        if (!engine.hasAccelerometer) {
            sensorError.postValue("Accelerometer not available — step detection disabled.")
        } else if (!engine.hasMagnetometer) {
            sensorError.postValue(
                "Magnetometer not available — magnetic matching disabled, running PDR only."
            )
        }
    }

    fun stopSensors() {
        engine.stop()
        wifiScanner.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopSensors()
    }

    // ── Fusion pipeline — runs on every step ──────────────────────────────────

    private fun onStep(count: Int) {
        stepCount.postValue(count)

        // 1. PDR update
        pdrTracker.onStep(headingEstimator.getCurrentHeading())

        // 2. Wi-Fi coarse filter + Magnetic KNN
        val corrected = matcher.matchPosition(
            allFingerprints,
            latestBx, latestBy, latestBz,
            strongestRouter.value
        )

        // 3. SNAP (if strong match found)
        if (corrected != null) {
            pdrTracker.snap(corrected.first, corrected.second)
            pdrOnlySteps = 0
            localizationState.postValue(LocalizationState.Fused(0f))   // ssd available in matcher
        } else {
            pdrOnlySteps++
            val state = if (pdrOnlySteps > 10) LocalizationState.PdrOnly
                        else LocalizationState.WaitingForStep
            localizationState.postValue(state)
        }

        // 4. Publish updated position
        val pos = pdrTracker.getPosition()
        posX.postValue(pos.x)
        posY.postValue(pos.y)
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /**
     * Set the navigation destination by name.
     * Finds the nearest matching node, runs Dijkstra from current PDR position,
     * and publishes the path as currentRoute LiveData.
     */
    fun setDestination(name: String) {
        if (name.isBlank()) return
        destinationName.postValue(name)

        // Find target node (case-insensitive substring match)
        val targetId = routingEngine.getAllNodeIds()
            .firstOrNull { it.contains(name.trim().replace(" ", ""), ignoreCase = true) }
            ?: routingEngine.getAllNodeIds()
            .firstOrNull { it.contains(name.trim(), ignoreCase = true) }

        if (targetId == null) {
            routeError.postValue("Destination not found: $name")
            return
        }

        // Find nearest graph node to current PDR position as the start
        val startId = findNearestNode(
            pdrTracker.getPosition().x,
            pdrTracker.getPosition().y
        )

        val path = routingEngine.findPath(startId, targetId)
        if (path == null) {
            routeError.postValue("No route found to $name")
        } else {
            currentRoute.postValue(path)
        }
    }

    /** Re-route from current position to the last known destination. */
    fun reRoute() {
        val dest = destinationName.value ?: return
        setDestination(dest)
    }

    private fun findNearestNode(x: Float, y: Float): String {
        return routingEngine.getAllNodeIds().minByOrNull { id ->
            val n  = routingEngine.getNode(id) ?: return@minByOrNull Float.MAX_VALUE
            val dx = n.x - x
            val dy = n.y - y
            dx * dx + dy * dy
        } ?: routingEngine.getAllNodeIds().first()
    }
}
