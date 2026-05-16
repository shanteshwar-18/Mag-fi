package com.magfi.mapper.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * WifiScanner — Periodic Wi-Fi RSSI scan.
 * Uses WifiManager.startScan() on a 2-second interval.
 * Reads ScanResults, sorts by RSSI descending, takes top 5,
 * averages last 3 scans per BSSID, and produces a JSON string payload.
 *
 * NOTE: startScan() is throttled by Android 9+ (1 scan per 30s per app in background).
 *       Works correctly in foreground / screen-on mode only.
 */
class WifiScanner(private val context: Context) {

    interface Listener {
        fun onWifiPayload(jsonPayload: String)
    }

    companion object {
        private const val TAG = "WifiScanner"
        const val SCAN_INTERVAL_MS = 2000L
        const val TOP_N = 5
        const val RSSI_AVG_WINDOW = 3
    }

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val handler = Handler(Looper.getMainLooper())
    private val rssiHistory = mutableMapOf<String, ArrayDeque<Int>>()
    private var lastPayload = "{}"
    private var listener: Listener? = null
    private var isScanning = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            processScanResults()
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            try {
                wifiManager.startScan()
            } catch (e: Exception) {
                Log.w(TAG, "startScan failed: ${e.message}")
            }
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    fun start(listener: Listener) {
        this.listener = listener
        isScanning = true
        rssiHistory.clear()

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)

        handler.post(scanRunnable)
        Log.d(TAG, "WifiScanner started")
    }

    fun stop() {
        isScanning = false
        handler.removeCallbacksAndMessages(null)
        try {
            context.unregisterReceiver(scanReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered: ${e.message}")
        }
        listener = null
        Log.d(TAG, "WifiScanner stopped")
    }

    fun getLastPayload(): String = lastPayload

    private fun processScanResults() {
        if (!isScanning) return

        val results = try {
            wifiManager.scanResults
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get scan results: ${e.message}")
            emptyList()
        }

        // Sort by RSSI descending, take top N
        val topResults = results.sortedByDescending { it.level }.take(TOP_N)

        for (result in topResults) {
            // Use SSID as key; fall back to BSSID if SSID is unknown
            val key = if (result.SSID.isNullOrEmpty() || result.SSID == "<unknown ssid>") {
                result.BSSID ?: continue
            } else {
                result.SSID
            }

            val deque = rssiHistory.getOrPut(key) { ArrayDeque(RSSI_AVG_WINDOW) }
            if (deque.size >= RSSI_AVG_WINDOW) deque.removeFirst()
            deque.addLast(result.level)
        }

        // Build JSON using StringBuilder (no external library)
        val sb = StringBuilder("{")
        val entries = topResults.mapNotNull { result ->
            val key = if (result.SSID.isNullOrEmpty() || result.SSID == "<unknown ssid>") {
                result.BSSID ?: return@mapNotNull null
            } else {
                result.SSID
            }
            val deque = rssiHistory[key] ?: return@mapNotNull null
            val avgRssi = deque.sum() / deque.size
            val safeKey = key.take(20).replace("\"", "\\\"")
            "\"$safeKey\": $avgRssi"
        }
        sb.append(entries.joinToString(separator = ", "))
        sb.append("}")

        lastPayload = sb.toString()
        Log.d(TAG, "WIFI_PAYLOAD: $lastPayload")
        listener?.onWifiPayload(lastPayload)
    }
}
