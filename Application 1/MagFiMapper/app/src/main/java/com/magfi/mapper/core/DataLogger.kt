package com.magfi.mapper.core

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DataLogger — Dual-trigger CSV row logger.
 * Maintains an in-memory CSV buffer.
 * Adds a row on step detection OR every 1 second (whichever comes first)
 * to prevent data loss during pauses.
 */
class DataLogger {

    companion object {
        const val CSV_HEADER = "timestamp,step_count,pos_x,pos_y,heading,mag_x,mag_y,mag_z,wifi_payload"
        const val TIMER_INTERVAL_MS = 1000L
    }

    private val rows = mutableListOf<String>()
    private var timerJob: Job? = null
    @Volatile private var isLogging = false

    // State snapshot — updated externally before each logRow()
    var stepCount = 0
    var posX = 0f
    var posY = 0f
    var heading = 0f
    var magX = 0f
    var magY = 0f
    var magZ = 0f
    var wifiPayload = "{}"

    fun startLogging(scope: CoroutineScope) {
        isLogging = true
        rows.clear()
        rows.add(CSV_HEADER)  // First row is the header

        timerJob = scope.launch {
            while (isActive && isLogging) {
                delay(TIMER_INTERVAL_MS)
                if (isLogging) logRow()  // Time-based fallback trigger
            }
        }
    }

    fun stopLogging() {
        isLogging = false
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Called by SensorViewModel when StepDetector fires.
     */
    fun onStep() {
        if (!isLogging) return
        logRow()
    }

    /**
     * Builds and appends a CSV row to the in-memory buffer.
     */
    fun logRow() {
        if (!isLogging) return
        val ts = System.currentTimeMillis()
        // Escape commas in JSON payload by replacing with semicolons
        val wifiSafe = wifiPayload.replace(",", ";")
        val row = "$ts,$stepCount,${posX.f2()},${posY.f2()},${heading.f1()}," +
                "${magX.f2()},${magY.f2()},${magZ.f2()},\"$wifiSafe\""
        synchronized(rows) { rows.add(row) }
    }

    fun getRows(): List<String> = synchronized(rows) { rows.toList() }

    fun getRowCount(): Int = synchronized(rows) { rows.size }

    fun clear() {
        synchronized(rows) { rows.clear() }
    }

    /**
     * Exports all buffered rows to a timestamped CSV file in external Documents directory.
     */
    fun exportCsv(
        context: Context,
        mapperName: String,
        buildingName: String,
        floorName: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "raw_mapping_${buildingName}_${floorName}_$timestamp.csv"

        // Android 10+ storage — use app-specific external directory
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir  // Fallback to internal storage
        dir.mkdirs()

        val file = File(dir, fileName)

        // Write metadata header + data rows
        val meta = listOf(
            "# MAG-FI RAW MAPPING DATA",
            "# Mapper: $mapperName",
            "# Building: $buildingName",
            "# Floor: $floorName",
            "# Exported: $timestamp",
            "# Total rows: ${synchronized(rows) { rows.size }}",
            ""
        )

        file.bufferedWriter().use { writer ->
            meta.forEach { writer.write(it); writer.newLine() }
            synchronized(rows) {
                rows.forEach { writer.write(it); writer.newLine() }
            }
        }

        return file
    }

    // Extension helpers for float formatting
    private fun Float.f2(): String = String.format("%.2f", this)
    private fun Float.f1(): String = String.format("%.1f", this)
}
