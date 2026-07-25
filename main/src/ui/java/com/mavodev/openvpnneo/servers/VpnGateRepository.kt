/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.servers

import android.content.Context
import android.net.TrafficStats
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Immutable description of a single free VPNGate server. */
data class VpnGateServer(
    val hostName: String,
    val ip: String,
    val score: Long,
    val ping: Int,          // round-trip in ms, -1 if unknown
    val speedBps: Long,     // measured throughput in bits/s
    val countryLong: String,
    val countryShort: String, // 2-letter ISO code (maps to flag_<cc> drawables)
    val sessions: Int,
    val uptimeMs: Long,
    private val configBase64: String,
) {
    /** Decoded .ovpn text, ready to hand to ConfigParser. */
    fun decodeConfig(): String =
        String(Base64.decode(configBase64, Base64.DEFAULT))
}

/**
 * Fetches and parses the public VPNGate server list (https://www.vpngate.net/api/iphone/).
 *
 * The endpoint returns CSV. Each data row embeds a complete OpenVPN configuration in the
 * last column (base64), so "OpenVPN only" is enforced simply by discarding rows whose
 * config column is empty. No other protocols are exposed by this app.
 *
 * Networking mirrors the conventions in [com.mavodev.openvpnneo.country.CountryInfoRepository]
 * (OkHttp with a hard call timeout, TrafficStats tagging to keep StrictMode quiet).
 */
class VpnGateRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Hard cap for the whole call, including DNS (not covered by connect/read timeouts).
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Result of a fetch: either the parsed server list or a failure. Kept explicit so the
     * UI can distinguish "no network / error" from "network fine but zero servers".
     * [fromCache] tells the UI whether the data came from the on-disk cache.
     */
    sealed class Result {
        data class Success(val servers: List<VpnGateServer>, val fromCache: Boolean) : Result()
        object Failure : Result()
    }

    /**
     * Returns the server list, using the on-disk cache when it is younger than
     * [CACHE_TTL_MS] unless [forceRefresh] is set. A network fetch that succeeds
     * refreshes the cache; if a network fetch fails, any (even stale) cache is used
     * as a fallback so the user still sees something.
     */
    suspend fun fetchServers(context: Context, forceRefresh: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            val cacheFile = cacheFile(context)

            if (!forceRefresh) {
                val cached = readFreshCache(cacheFile)
                if (cached != null) return@withContext Result.Success(cached, fromCache = true)
            }

            try {
                TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG)
                val request = Request.Builder()
                    .url(API_URL)
                    .header("User-Agent", USER_AGENT)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "VPNGate API returned ${response.code}")
                        return@withContext fallbackToStaleCache(cacheFile)
                    }
                    val body = response.body?.string()
                        ?: return@withContext fallbackToStaleCache(cacheFile)
                    writeCache(cacheFile, body)
                    Result.Success(parseCsv(body), fromCache = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "VPNGate request failed", e)
                fallbackToStaleCache(cacheFile)
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }

    private fun cacheFile(context: Context) = File(context.cacheDir, CACHE_FILE_NAME)

    /** Parsed cache contents if the file exists and is younger than the TTL, else null. */
    private fun readFreshCache(cacheFile: File): List<VpnGateServer>? {
        if (!cacheFile.exists()) return null
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        if (age > CACHE_TTL_MS) return null
        return try {
            parseCsv(cacheFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Reading VPNGate cache failed", e)
            null
        }
    }

    /** Uses any cache (even expired) when the network is unavailable, else Failure. */
    private fun fallbackToStaleCache(cacheFile: File): Result {
        if (!cacheFile.exists()) return Result.Failure
        return try {
            Result.Success(parseCsv(cacheFile.readText()), fromCache = true)
        } catch (e: Exception) {
            Result.Failure
        }
    }

    private fun writeCache(cacheFile: File, body: String) {
        try {
            cacheFile.writeText(body)
        } catch (e: Exception) {
            Log.w(TAG, "Writing VPNGate cache failed", e)
        }
    }

    private fun parseCsv(csv: String): List<VpnGateServer> {
        val out = ArrayList<VpnGateServer>()
        csv.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            // Skip the "*vpn_servers" / trailing "*" markers and the "#" header row.
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("#")) return@forEach
            val f = line.split(",")
            if (f.size < COLUMN_COUNT) return@forEach
            val config = f[COL_CONFIG]
            // OpenVPN-only: rows without an embedded OpenVPN config are useless to us.
            if (config.isBlank()) return@forEach
            val countryShort = f[COL_COUNTRY_SHORT].trim()
            if (countryShort.isBlank()) return@forEach
            out += VpnGateServer(
                hostName = f[COL_HOSTNAME],
                ip = f[COL_IP],
                score = f[COL_SCORE].toLongOrNull() ?: 0L,
                ping = f[COL_PING].toIntOrNull() ?: -1,
                speedBps = f[COL_SPEED].toLongOrNull() ?: 0L,
                countryLong = f[COL_COUNTRY_LONG],
                countryShort = countryShort,
                sessions = f[COL_SESSIONS].toIntOrNull() ?: 0,
                uptimeMs = f[COL_UPTIME].toLongOrNull() ?: 0L,
                configBase64 = config,
            )
        }
        return out
    }

    companion object {
        private const val TAG = "VpnGateRepository"
        private const val API_URL = "https://www.vpngate.net/api/iphone/"
        private const val USER_AGENT = "OpenVPN-Neo/1.0"
        private const val TRAFFIC_STATS_TAG = 0x564E4701 // "VNG" fetch

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val CALL_TIMEOUT_SECONDS = 45L

        private const val CACHE_FILE_NAME = "vpngate_servers.csv"
        private const val CACHE_TTL_MS = 60L * 60L * 1000L // 60 minutes

        // CSV column layout of the VPNGate iPhone API.
        private const val COL_HOSTNAME = 0
        private const val COL_IP = 1
        private const val COL_SCORE = 2
        private const val COL_PING = 3
        private const val COL_SPEED = 4
        private const val COL_COUNTRY_LONG = 5
        private const val COL_COUNTRY_SHORT = 6
        private const val COL_SESSIONS = 7
        private const val COL_UPTIME = 8
        private const val COL_CONFIG = 14
        private const val COLUMN_COUNT = 15
    }
}
