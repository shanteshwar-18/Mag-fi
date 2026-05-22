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

// ── Destination model ─────────────────────────────────────────────────────────

/**
 * DestinationItem — a single navigable location.
 * [id] MUST exactly match the node ID in RoutingEngine (case-sensitive).
 */
data class DestinationItem(
    val id: String,
    val title: String,
    val category: String,      // "Lab" | "Classroom" | "Office" | "Staff" | "Exit"
    val subtitle: String = ""
)

// ── Localization state ────────────────────────────────────────────────────────

sealed class LocalizationState {
    object WaitingForStep : LocalizationState()
    object PdrOnly        : LocalizationState()
    data class Fused(val ssd: Float) : LocalizationState()
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
 *   - Floor plan loading  (FloorPlanManager — two-axis calibration)
 *   - Routing  (RoutingEngine → Dijkstra path)
 *   - Fail-safe states  (sensor unavailable, empty DB, PDR-only mode)
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NavigationViewModel"
    }

    // ── LiveData ──────────────────────────────────────────────────────────────
    val stepCount         : MutableLiveData<Int>                       = MutableLiveData(0)
    val heading           : MutableLiveData<Float>                     = MutableLiveData(0f)
    val magData           : MutableLiveData<Triple<Float,Float,Float>> = MutableLiveData()
    val posX              : MutableLiveData<Float>                     = MutableLiveData(0f)
    val posY              : MutableLiveData<Float>                     = MutableLiveData(0f)
    val fingerprintCount  : MutableLiveData<Int>                       = MutableLiveData(0)
    val strongestRouter   : MutableLiveData<Pair<String,Int>?>         = MutableLiveData(null)
    val currentRoute      : MutableLiveData<List<GraphNode>?>          = MutableLiveData(null)
    val destinationName   : MutableLiveData<String>                    = MutableLiveData("")
    val routeError        : MutableLiveData<String?>                   = MutableLiveData(null)
    val localizationState : MutableLiveData<LocalizationState>         = MutableLiveData(LocalizationState.WaitingForStep)
    val sensorError       : MutableLiveData<String?>                   = MutableLiveData(null)

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

    private val latestBx get() = magData.value?.first  ?: 0f
    private val latestBy get() = magData.value?.second ?: 0f
    private val latestBz get() = magData.value?.third  ?: 0f

    // ── Destination list — Block C · Lab Wing · Floor 3 ───────────────────────
    val destinations: List<DestinationItem> = listOf(
        // ── LABS ────────────────────────────────────────────────────────────
        DestinationItem("room_1323d",  "1323D",        "Lab",       "Block C · Floor 3"),
        DestinationItem("room_1323bc", "1323 B&C",     "Lab",       "Block C · Floor 3"),
        DestinationItem("room_1323a",  "1323A",        "Lab",       "Block C · Floor 3"),
        DestinationItem("room_1321d",  "1321D",        "Lab",       "Block C · Floor 3"),
        DestinationItem("room_1321bc", "1321 B&C",     "Lab",       "Block C · Floor 3"),
        DestinationItem("room_1321a",  "1321A",        "Lab",       "Block C · Floor 3"),
        // ── CLASSROOM ───────────────────────────────────────────────────────
        DestinationItem("room_1324",   "1324",         "Classroom", "Block C · Floor 3"),
        // ── OFFICES & STAFF ─────────────────────────────────────────────────
        DestinationItem("room_1322",   "1322 HOD Cabin",  "Office", "Block C · Floor 3"),
        DestinationItem("room_1320",   "1320 Staff Room", "Staff",  "Block C · Floor 3"),
        // ── EXITS & STAIRS ───────────────────────────────────────────────────
        DestinationItem("staircase",          "Staircase (Top)",  "Exit", "North end · Floor 3"),
        DestinationItem("corridor_entrance",  "Entrance",         "Exit", "Ground level")
    )

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
                        "Map database is empty — magnetic matching disabled."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB load failed: ${e.message}")
                sensorError.postValue("Failed to load map database: ${e.message}")
            }

            // Load floor plan (two-axis calibration loaded from companion constants)
            val ok = floorPlanManager.loadFloorPlan()
            if (ok) {
                Log.d(TAG, "Floor plan ready — scaleY=${FloorPlanManager.SCALE_Y_PX_PER_M}px/m" +
                           " scaleX=${FloorPlanManager.SCALE_X_PX_PER_M}px/m")
                // Verify calibration via debug logs
                val (px0, py0) = floorPlanManager.metersToPixels(0f, 0f)
                val (px1, py1) = floorPlanManager.metersToPixels(0f, 40f)
                Log.d(TAG, "metersToPixels(0,0)  → (${px0.toInt()}, ${py0.toInt()})  [expect ~310, 1370]")
                Log.d(TAG, "metersToPixels(0,40) → (${px1.toInt()}, ${py1.toInt()})  [expect ~310,  95]")
            } else {
                Log.w(TAG, "floor_plan.png not found in assets — using grid placeholder")
            }
        }
    }

    /**
     * Block C Lab Wing routing graph.
     *
     * Two node types:
     *   corridor_* / wp_* — walkable corridor waypoints along centre-line (x = 0.9 m)
     *   room_*            — destination leaf nodes at room centre (x = 4.8 m)
     *
     * All Y coordinates are metres from the entrance (PDR origin).
     * Edges are bidirectional and auto-weighted by Euclidean distance.
     */
    private fun buildRoutingGraph() {
        // ── CORRIDOR WAYPOINTS (walkable path) ─────────────────────────────────
        routingEngine.addNode("corridor_entrance", 0.9f,  0.0f)
        routingEngine.addNode("wp_1324_door",      0.9f,  7.5f)
        routingEngine.addNode("wp_1323d_door",     0.9f,  9.0f)
        routingEngine.addNode("wp_1323bc",         0.9f, 12.8f)
        routingEngine.addNode("wp_1323a",          0.9f, 14.5f)
        routingEngine.addNode("wp_1322",           0.9f, 18.5f)
        routingEngine.addNode("wp_1321d",          0.9f, 21.0f)
        routingEngine.addNode("wp_1321bc",         0.9f, 24.8f)
        routingEngine.addNode("wp_1321a",          0.9f, 26.5f)
        routingEngine.addNode("wp_1320",           0.9f, 30.5f)
        routingEngine.addNode("staircase",         0.9f, 37.0f)

        // ── ROOM DESTINATION NODES (leaf nodes — enter via spur) ───────────────
        // Room centre X = corridor width (1.8m) + half room depth (3.0m) = 4.8m
        routingEngine.addNode("room_1324",   4.8f,  4.25f)
        routingEngine.addNode("room_1323d",  4.8f, 10.5f)
        routingEngine.addNode("room_1323bc", 4.8f, 13.25f)
        routingEngine.addNode("room_1323a",  4.8f, 16.0f)
        routingEngine.addNode("room_1322",   4.8f, 19.25f)
        routingEngine.addNode("room_1321d",  4.8f, 22.5f)
        routingEngine.addNode("room_1321bc", 4.8f, 25.25f)
        routingEngine.addNode("room_1321a",  4.8f, 28.0f)
        routingEngine.addNode("room_1320",   4.8f, 32.0f)

        // ── CORRIDOR CHAIN EDGES (bidirectional, auto-weighted) ────────────────
        routingEngine.addEdge("corridor_entrance", "wp_1324_door")   // 7.5 m
        routingEngine.addEdge("wp_1324_door",      "wp_1323d_door")  // 1.5 m
        routingEngine.addEdge("wp_1323d_door",     "wp_1323bc")      // 3.8 m
        routingEngine.addEdge("wp_1323bc",         "wp_1323a")       // 1.7 m
        routingEngine.addEdge("wp_1323a",          "wp_1322")        // 4.0 m
        routingEngine.addEdge("wp_1322",           "wp_1321d")       // 2.5 m
        routingEngine.addEdge("wp_1321d",          "wp_1321bc")      // 3.8 m
        routingEngine.addEdge("wp_1321bc",         "wp_1321a")       // 1.7 m
        routingEngine.addEdge("wp_1321a",          "wp_1320")        // 4.0 m
        routingEngine.addEdge("wp_1320",           "staircase")      // 6.5 m

        // ── ROOM SPUR EDGES (corridor waypoint → room, one spur only) ─────────
        routingEngine.addEdge("wp_1324_door",  "room_1324")    // 5.1 m
        routingEngine.addEdge("wp_1323d_door", "room_1323d")   // 4.2 m
        routingEngine.addEdge("wp_1323bc",     "room_1323bc")  // 3.9 m
        routingEngine.addEdge("wp_1323a",      "room_1323a")   // 4.2 m
        routingEngine.addEdge("wp_1322",       "room_1322")    // 4.0 m
        routingEngine.addEdge("wp_1321d",      "room_1321d")   // 4.2 m
        routingEngine.addEdge("wp_1321bc",     "room_1321bc")  // 3.9 m
        routingEngine.addEdge("wp_1321a",      "room_1321a")   // 4.2 m
        routingEngine.addEdge("wp_1320",       "room_1320")    // 4.2 m

        Log.d(TAG, "Routing graph built: ${routingEngine.getAllNodeIds().size} nodes")
    }

    // ── Sensor lifecycle ──────────────────────────────────────────────────────

    fun startSensors() {
        engine.start(object : SensorEngine.Listener {
            override fun onAccelUpdate(ax: Float, ay: Float, az: Float) {
                stepDetector.processSample(ax, ay, az, object : StepDetector.Listener {
                    override fun onStepDetected(count: Int) { onStep(count) }
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

        wifiScanner.start(object : WifiScanner.Listener {
            override fun onWifiPayload(jsonPayload: String) {
                strongestRouter.postValue(wifiScanner.getStrongestRouter())
            }
        })

        if (!engine.hasAccelerometer) {
            sensorError.postValue("Accelerometer not available — step detection disabled.")
        } else if (!engine.hasMagnetometer) {
            sensorError.postValue("Magnetometer not available — running PDR only.")
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

    // ── Fusion pipeline ───────────────────────────────────────────────────────

    private fun onStep(count: Int) {
        stepCount.postValue(count)
        pdrTracker.onStep(headingEstimator.getCurrentHeading())

        val corrected = matcher.matchPosition(
            allFingerprints, latestBx, latestBy, latestBz, strongestRouter.value
        )

        if (corrected != null) {
            pdrTracker.snap(corrected.first, corrected.second)
            pdrOnlySteps = 0
            localizationState.postValue(LocalizationState.Fused(0f))
        } else {
            pdrOnlySteps++
            localizationState.postValue(
                if (pdrOnlySteps > 10) LocalizationState.PdrOnly
                else LocalizationState.WaitingForStep
            )
        }

        val pos = pdrTracker.getPosition()
        posX.postValue(pos.x)
        posY.postValue(pos.y)
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    fun setDestination(name: String) {
        if (name.isBlank()) return
        destinationName.postValue(name)

        // Find destination node — name may come from DestinationItem.id directly
        val targetId = destinations.firstOrNull { it.id == name }?.id
            ?: destinations.firstOrNull {
                it.title.contains(name.trim(), ignoreCase = true)
            }?.id
            ?: routingEngine.getAllNodeIds()
                .firstOrNull { it.contains(name.trim().replace(" ", "_"), ignoreCase = true) }

        if (targetId == null) {
            routeError.postValue("Destination not found: $name")
            return
        }

        val startId = findNearestNode(pdrTracker.getPosition().x, pdrTracker.getPosition().y)
        val path = routingEngine.findPath(startId, targetId)
        if (path == null) {
            routeError.postValue("No route found to $name")
        } else {
            currentRoute.postValue(path)
        }
    }

    fun reRoute() {
        val dest = destinationName.value ?: return
        setDestination(dest)
    }

    private fun findNearestNode(x: Float, y: Float): String {
        return routingEngine.getAllNodeIds().minByOrNull { id ->
            val n = routingEngine.getNode(id) ?: return@minByOrNull Float.MAX_VALUE
            (n.x - x).pow(2) + (n.y - y).pow(2)
        } ?: "corridor_entrance"
    }
}
