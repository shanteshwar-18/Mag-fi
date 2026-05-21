package com.magfi.navigator.core

import android.util.Log
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * FingerprintMatcher — the fine localization engine.
 *
 * Implements the 3-layer matching pipeline on every step event:
 *   Layer 1: Wi-Fi Coarse Filter    — restrict candidates to current Wi-Fi zone
 *   Layer 2: Magnetic SSD Scoring   — compute euclidean distance in (Bx,By,Bz) space
 *   Layer 3: KNN Position Estimate  — average top K=3 matched positions
 *
 * SNAP fires only when best candidate SSD < SNAP_THRESHOLD_UT.
 * Duplicate SSID keys in wifi payloads are handled by JSONObject (last-wins).
 */
class FingerprintMatcher {

    companion object {
        private const val TAG               = "FingerprintMatcher"
        const val K                         = 3
        const val SNAP_THRESHOLD_UT         = 8.0f    // µT — snap only below this SSD
    }

    /**
     * Full 3-layer matching pipeline.
     *
     * @param allFingerprints   in-memory fingerprint list from MapDatabaseHelper
     * @param liveBx, liveBy, liveBz   current filtered magnetometer readings (µT)
     * @param strongestRouter   Pair(SSID, RSSI) from WifiScanner, or null if no Wi-Fi
     *
     * @return Pair(avgX, avgY) if a snap should fire, null otherwise.
     */
    fun matchPosition(
        allFingerprints: List<Fingerprint>,
        liveBx: Float,
        liveBy: Float,
        liveBz: Float,
        strongestRouter: Pair<String, Int>?
    ): Pair<Float, Float>? {

        if (allFingerprints.isEmpty()) return null

        // ── LAYER 1: Wi-Fi Coarse Filter ──────────────────────────────────────
        // Keep only fingerprints whose wifiPayload JSON contains the current SSID.
        val candidates: List<Fingerprint> = if (strongestRouter == null) {
            // No Wi-Fi → use all fingerprints (PDR-only fallback)
            allFingerprints
        } else {
            val ssid = strongestRouter.first
            val filtered = allFingerprints.filter { fp ->
                try {
                    JSONObject(fp.wifiPayload).has(ssid)
                } catch (e: Exception) {
                    false
                }
            }
            // If no fingerprints match this router, fall back to all
            if (filtered.isEmpty()) {
                Log.d(TAG, "Wi-Fi filter found no match for '$ssid' — using all FPs")
                allFingerprints
            } else {
                Log.d(TAG, "Wi-Fi filter: ${filtered.size}/${allFingerprints.size} candidates for '$ssid'")
                filtered
            }
        }

        // ── LAYER 2: Magnetic SSD Scoring ─────────────────────────────────────
        // Compute euclidean distance in 3D magnetic field space for each candidate.
        data class Scored(val fp: Fingerprint, val ssd: Float)

        val scored = candidates.map { fp ->
            val ssd = sqrt(
                (liveBx - fp.magX) * (liveBx - fp.magX) +
                (liveBy - fp.magY) * (liveBy - fp.magY) +
                (liveBz - fp.magZ) * (liveBz - fp.magZ)
            )
            Scored(fp, ssd)
        }.sortedBy { it.ssd }

        // ── LAYER 3: KNN Position Estimate ────────────────────────────────────
        val topK = scored.take(minOf(K, scored.size))

        if (topK.isEmpty()) return null

        val bestSsd = topK[0].ssd

        // Snap threshold check — reject if magnetic mismatch too large
        if (bestSsd >= SNAP_THRESHOLD_UT) {
            Log.d(TAG, "No snap — best SSD=${"%.2f".format(bestSsd)}µT >= threshold=$SNAP_THRESHOLD_UT")
            return null
        }

        // Average K nearest positions
        val avgX = topK.sumOf { it.fp.posX.toDouble() }.toFloat() / topK.size
        val avgY = topK.sumOf { it.fp.posY.toDouble() }.toFloat() / topK.size

        Log.d(TAG, "SNAP: bestSSD=${"%.2f".format(bestSsd)}µT → pos=(${"%.2f".format(avgX)}, ${"%.2f".format(avgY)})")
        return Pair(avgX, avgY)
    }
}
