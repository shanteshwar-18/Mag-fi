package com.magfi.mapper.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.magfi.mapper.MappingForegroundService
import com.magfi.mapper.core.DataLogger
import com.magfi.mapper.core.HeadingEstimator
import com.magfi.mapper.core.PdrTracker
import com.magfi.mapper.core.SensorEngine
import com.magfi.mapper.core.StepDetector
import com.magfi.mapper.core.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * SensorViewModel — LiveData bridge between sensor stack and UI.
 * Owns SensorEngine, StepDetector, HeadingEstimator, PdrTracker, WifiScanner, DataLogger.
 * Survives configuration changes (activityViewModels pattern).
 */
class SensorViewModel(application: Application) : AndroidViewModel(application) {

    // ── LIVE DATA ──────────────────────────────────────────────────────────
    val accelData: MutableLiveData<Triple<Float, Float, Float>> = MutableLiveData()
    val magData: MutableLiveData<Triple<Float, Float, Float>> = MutableLiveData()
    val gyroData: MutableLiveData<Triple<Float, Float, Float>> = MutableLiveData()
    val stepCount: MutableLiveData<Int> = MutableLiveData(0)
    val heading: MutableLiveData<Float> = MutableLiveData(0f)
    val posX: MutableLiveData<Float> = MutableLiveData(0f)
    val posY: MutableLiveData<Float> = MutableLiveData(0f)
    val wifiPayload: MutableLiveData<String> = MutableLiveData("{}")
    val rowCount: MutableLiveData<Int> = MutableLiveData(0)
    val isRecording: MutableLiveData<Boolean> = MutableLiveData(false)
    val calibrationState: MutableLiveData<CalibrationState> = MutableLiveData(CalibrationState.Idle)

    // Calibration states
    sealed class CalibrationState {
        object Idle : CalibrationState()
        data class Counting(val secondsLeft: Int) : CalibrationState()
        object Complete : CalibrationState()
    }

    // ── CORE COMPONENTS ───────────────────────────────────────────────────
    private val engine = SensorEngine(application)
    private val stepDetector = StepDetector()
    private val headingEstimator = HeadingEstimator()
    private val pdrTracker = PdrTracker()
    private val wifiScanner = WifiScanner(application)
    private val dataLogger = DataLogger()

    // Latest mag values for calibration
    private var latestBx = 0f
    private var latestBy = 0f

    // Session metadata
    private var mapperName = ""
    private var buildingName = ""
    private var floorName = ""
    private var startLandmark = ""

    private var engineStarted = false

    // BUG FIX #2: dedicated job to poll rowCount every second so the UI always reflects
    // rows written by the timer-based trigger in DataLogger (not just step-based rows)
    private var rowPollJob: Job? = null

    // ── ENGINE MANAGEMENT ─────────────────────────────────────────────────
    fun startEngine() {
        if (engineStarted) return
        engineStarted = true

        engine.start(object : SensorEngine.Listener {
            override fun onAccelUpdate(ax: Float, ay: Float, az: Float) {
                accelData.postValue(Triple(ax, ay, az))

                // Feed step detector — only update UI/logger when recording is active
                stepDetector.processSample(ax, ay, az, object : StepDetector.Listener {
                    override fun onStepDetected(count: Int) {
                        // Only count steps while recording is active
                        if (isRecording.value != true) return

                        // Update position on step
                        pdrTracker.onStep(headingEstimator.getCurrentHeading())
                        val pos = pdrTracker.getPosition()

                        stepCount.postValue(count)
                        posX.postValue(pos.x)
                        posY.postValue(pos.y)

                        // Sync state into DataLogger and trigger a step-based row
                        dataLogger.stepCount = count
                        dataLogger.posX = pos.x
                        dataLogger.posY = pos.y
                        dataLogger.heading = headingEstimator.getCurrentHeading()
                        dataLogger.onStep()
                        // rowCount UI update handled by rowPollJob — no need to post here
                    }
                })
            }

            override fun onMagUpdate(bx: Float, by: Float, bz: Float) {
                magData.postValue(Triple(bx, by, bz))
                latestBx = bx
                latestBy = by

                // Always process heading (needed before recording for display)
                headingEstimator.processMag(bx, by, object : HeadingEstimator.Listener {
                    override fun onHeadingUpdate(degrees: Float) {
                        heading.postValue(degrees)
                        dataLogger.heading = degrees
                    }
                })

                // Keep mag values in logger updated
                dataLogger.magX = bx
                dataLogger.magY = by
                dataLogger.magZ = bz
            }

            override fun onGyroUpdate(gx: Float, gy: Float, gz: Float, timestampNs: Long) {
                gyroData.postValue(Triple(gx, gy, gz))
                headingEstimator.processGyro(gz, timestampNs)
            }
        })

        // Start Wi-Fi scanner
        wifiScanner.start(object : WifiScanner.Listener {
            override fun onWifiPayload(jsonPayload: String) {
                wifiPayload.postValue(jsonPayload)
                dataLogger.wifiPayload = jsonPayload
            }
        })
    }

    fun stopEngine() {
        if (!engineStarted) return
        engineStarted = false
        engine.stop()
        wifiScanner.stop()
    }

    // ── SESSION INIT ───────────────────────────────────────────────────────
    fun initSession(mapper: String, building: String, floor: String, landmark: String) {
        mapperName = mapper
        buildingName = building
        floorName = floor
        startLandmark = landmark
    }

    // ── RECORDING CONTROL ─────────────────────────────────────────────────
    fun startRecording(scope: CoroutineScope) {
        scope.launch {
            try {
                // Phase 1: Calibration countdown
                calibrationState.postValue(CalibrationState.Counting(2))
                delay(1000L)
                calibrationState.postValue(CalibrationState.Counting(1))
                delay(1000L)

                // Freeze the latest heading as reference calibration point
                headingEstimator.reset()
                repeat(10) {
                    headingEstimator.calibrate(latestBx, latestBy)
                }

                calibrationState.postValue(CalibrationState.Complete)

                // Phase 2: Reset all trackers and start recording
                pdrTracker.reset()
                stepDetector.reset()
                dataLogger.clear()
                dataLogger.startLogging(scope)

                // Reset UI counters
                stepCount.postValue(0)
                posX.postValue(0f)
                posY.postValue(0f)
                rowCount.postValue(0)
                isRecording.postValue(true)

                // BUG FIX #2: poll rowCount every second so UI always reflects timer-logged rows
                rowPollJob?.cancel()
                rowPollJob = scope.launch {
                    while (isActive && isRecording.value == true) {
                        delay(1000L)
                        if (isRecording.value == true) {
                            rowCount.postValue(dataLogger.getRowCount())
                        }
                    }
                }

                // Start foreground service
                val intent = Intent(getApplication(), MappingForegroundService::class.java).apply {
                    action = MappingForegroundService.ACTION_START
                }
                ContextCompat.startForegroundService(getApplication(), intent)

            } catch (e: Exception) {
                // Ensure overlay is always dismissed even on error
                calibrationState.postValue(CalibrationState.Complete)
            }
        }
    }

    fun stopRecording() {
        rowPollJob?.cancel()
        rowPollJob = null

        dataLogger.stopLogging()
        isRecording.postValue(false)
        calibrationState.postValue(CalibrationState.Idle)

        // Final row count after stop (includes all timer-based rows)
        rowCount.postValue(dataLogger.getRowCount())

        // Stop foreground service
        val intent = Intent(getApplication(), MappingForegroundService::class.java).apply {
            action = MappingForegroundService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun exportCsv(context: Context): File {
        return dataLogger.exportCsv(context, mapperName, buildingName, floorName)
    }

    fun getDataLogger() = dataLogger

    // ── LIFECYCLE ──────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        rowPollJob?.cancel()
        stopRecording()
        stopEngine()
    }
}
