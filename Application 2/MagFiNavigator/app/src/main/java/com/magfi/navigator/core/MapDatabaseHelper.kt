package com.magfi.navigator.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import org.json.JSONObject

/**
 * Fingerprint data class — one row from the fingerprints table.
 * Represents a mapped position with magnetic signature and Wi-Fi context.
 */
data class Fingerprint(
    val id: Int,
    val posX: Float,
    val posY: Float,
    val heading: Float,
    val magX: Float,
    val magY: Float,
    val magZ: Float,
    val wifiPayload: String    // JSON string e.g. {"VIT_TPO":-56,"VIT_Staff":-58}
)

/**
 * MapDatabaseHelper — wraps the pre-packaged SQLite database.
 *
 * The database MUST be placed at:
 *   app/src/main/assets/databases/map_database.db
 *
 * SQLiteAssetHelper handles copying from assets to the app's database directory
 * on first run. DATABASE_VERSION must match any future schema changes.
 */
class MapDatabaseHelper(context: Context)
    : SQLiteAssetHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG             = "MapDatabaseHelper"
        const val DATABASE_NAME           = "map_database.db"
        const val DATABASE_VERSION        = 1
        const val TABLE_NAME              = "fingerprints"
    }

    /**
     * Load ALL fingerprint rows into memory in one pass.
     * Call this ONCE on app start and cache the result.
     * Do NOT re-query on every localization step.
     */
    fun loadAllFingerprints(): List<Fingerprint> {
        val db: SQLiteDatabase = readableDatabase
        val list = mutableListOf<Fingerprint>()

        val cursor = db.rawQuery(
            "SELECT id, pos_x, pos_y, heading, mag_x, mag_y, mag_z, wifi_json FROM $TABLE_NAME",
            null
        )

        try {
            while (cursor.moveToNext()) {
                list.add(
                    Fingerprint(
                        id          = cursor.getInt(0),
                        posX        = cursor.getFloat(1),
                        posY        = cursor.getFloat(2),
                        heading     = cursor.getFloat(3),
                        magX        = cursor.getFloat(4),
                        magY        = cursor.getFloat(5),
                        magZ        = cursor.getFloat(6),
                        wifiPayload = cursor.getString(7) ?: "{}"
                    )
                )
            }
        } finally {
            cursor.close()
        }

        Log.d(TAG, "Loaded ${list.size} fingerprints from DB")
        return list
    }

    /**
     * Returns the SSID + averaged RSSI of the strongest access point
     * stored in a fingerprint's wifi payload JSON.
     * Returns null if JSON is empty or malformed.
     */
    fun getStrongestWifiRouter(fingerprint: Fingerprint): Pair<String, Int>? {
        return try {
            val json = JSONObject(fingerprint.wifiPayload)
            var bestSsid: String? = null
            var bestRssi = Int.MIN_VALUE
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rssi = json.getInt(key)
                if (rssi > bestRssi) {
                    bestRssi = rssi
                    bestSsid = key
                }
            }
            if (bestSsid != null) Pair(bestSsid, bestRssi) else null
        } catch (e: Exception) {
            null
        }
    }
}
