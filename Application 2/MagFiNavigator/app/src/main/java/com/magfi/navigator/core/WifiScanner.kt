package com.magfi.navigator.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * WifiScanner — ported from App 1 with getStrongestRouter() added for App 2.
 *
 * App 1 purpose: build JSON payload for CSV logging.
 * App 2 purpose: identify current Wi-Fi zone for coarse localization filter.
 *
 * Retained: BroadcastReceiver, 2-second scan interval, top-5 RSSI,
 *           rolling 3-sample average per BSSID.
 *
 * Added: getStrongestRouter() — returns SSID+RSSI of the single strongest AP.
 *        Used by FingerprintMatcher as the coarse zone key.
 */
class WifiScanner(private val context: Context) {

    interface Listener {
        fun onWifiPayload(jsonPayload: String)
    }

    companion object {
        private const val TAG              = "WifiScanner"
        const val SCAN_INTERVAL_MS         = 2000L
        const val TOP_N                    = 5
        const val RSSI_AVG_WINDOW          = 3
    }

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val handler      = Handler(Looper.getMainLooper())
    private val rssiHistory  = mutableMapOf<String, ArrayDeque<Int>>()
    private var lastPayload  = "{}"
    private var listener: Listener? = null
    private var isScanning   = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            processScanResults()
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            try { wifiManager.startScan() } catch (e: Exception) {
                Log.w(TAG, "startScan failed: ${e.message}")
            }
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    fun start(listener: Listener) {
        this.listener = listener
        isScanning    = true
        rssiHistory.clear()
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
        handler.post(scanRunnable)
        Log.d(TAG, "WifiScanner started")
    }

    fun stop() {
        isScanning = false
        handler.removeCallbacksAndMessages(null)
        try { context.unregisterReceiver(scanReceiver) }
        catch (e: IllegalArgumentException) { Log.w(TAG, "Receiver not registered") }
        listener = null
        Log.d(TAG, "WifiScanner stopped")
    }

    fun getLastPayload(): String = lastPayload

    /**
     * App 2 addition: returns SSID+RSSI of the strongest access point
     * seen in the most recent rolling average window.
     * Returns null if no scan results are available.
     */
    fun getStrongestRouter(): Pair<String, Int>? {
        if (rssiHistory.isEmpty()) return null
        val best = rssiHistory.entries.maxByOrNull { it.value.average() } ?: return null
        return Pair(best.key, best.value.average().toInt())
    }

    private fun processScanResults() {
        if (!isScanning) return
        val results = try {
            wifiManager.scanResults
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get scan results: ${e.message}")
            emptyList()
        }

        val topResults = results.sortedByDescending { it.level }.take(TOP_N)

        for (result in topResults) {
            val key = if (result.SSID.isNullOrEmpty() || result.SSID == "<unknown ssid>") {
                result.BSSID ?: continue
            } else result.SSID

            val deque = rssiHistory.getOrPut(key) { ArrayDeque(RSSI_AVG_WINDOW) }
            if (deque.size >= RSSI_AVG_WINDOW) deque.removeFirst()
            deque.addLast(result.level)
        }

        // Build JSON payload (consistent with App 1 format)
        val sb = StringBuilder("{")
        val entries = topResults.mapNotNull { result ->
            val key = if (result.SSID.isNullOrEmpty() || result.SSID == "<unknown ssid>") {
                result.BSSID ?: return@mapNotNull null
            } else result.SSID
            val deque   = rssiHistory[key] ?: return@mapNotNull null
            val avgRssi = deque.sum() / deque.size
            val safeKey = key.take(20).replace("\"", "\\\"")
            "\"$safeKey\": $avgRssi"
        }
        sb.append(entries.joinToString(", ")).append("}")

        lastPayload = sb.toString()
        Log.d(TAG, "WIFI_PAYLOAD: $lastPayload")
        listener?.onWifiPayload(lastPayload)
    }
}
