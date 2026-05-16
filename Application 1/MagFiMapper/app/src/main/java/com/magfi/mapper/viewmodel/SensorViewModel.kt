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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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

    // ── ENGINE MANAGEMENT ─────────────────────────────────────────────────
    fun startEngine() {
        if (engineStarted) return
        engineStarted = true

        engine.start(object : SensorEngine.Listener {
            override fun onAccelUpdate(ax: Float, ay: Float, az: Float) {
                accelData.postValue(Triple(ax, ay, az))

                // Update step detector
                stepDetector.processSample(ax, ay, az, object : StepDetector.Listener {
                    override fun onStepDetected(count: Int) {
                        // Update position on step
                        pdrTracker.onStep(headingEstimator.getCurrentHeading())
                        val pos = pdrTracker.getPosition()

                        stepCount.postValue(count)
                        posX.postValue(pos.x + 0f)  // normalize -0f
                        posY.postValue(pos.y + 0f)

                        // Update data logger state and trigger step-based row
                        dataLogger.stepCount = count
                        dataLogger.posX = pos.x + 0f
                        dataLogger.posY = pos.y + 0f
                        dataLogger.heading = headingEstimator.getCurrentHeading()
                        dataLogger.onStep()
                        rowCount.postValue(dataLogger.getRowCount())
                    }
                })
            }

            override fun onMagUpdate(bx: Float, by: Float, bz: Float) {
                magData.postValue(Triple(bx, by, bz))
                latestBx = bx
                latestBy = by

                // Calibration sampling
                headingEstimator.calibrate(bx, by)

                // Update heading
                headingEstimator.processMag(bx, by, object : HeadingEstimator.Listener {
                    override fun onHeadingUpdate(degrees: Float) {
                        heading.postValue(degrees)
                        dataLogger.heading = degrees
                    }
                })

                // Update mag in data logger
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

                // Trigger heading calibration with current mag readings
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

                stepCount.postValue(0)
                posX.postValue(0f)
                posY.postValue(0f)
                rowCount.postValue(0)
                isRecording.postValue(true)

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
        dataLogger.stopLogging()
        isRecording.postValue(false)
        calibrationState.postValue(CalibrationState.Idle)

        // Stop foreground service
        val intent = Intent(getApplication(), MappingForegroundService::class.java).apply {
            action = MappingForegroundService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun exportCsv(context: Context): File {
        return dataLogger.exportCsv(context, mapperName, buildingName, floorName)
    }

    // ── LIFECYCLE ──────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        stopRecording()
        stopEngine()
    }
}
