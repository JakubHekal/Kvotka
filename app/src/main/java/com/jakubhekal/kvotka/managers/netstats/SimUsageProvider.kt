package com.jakubhekal.kvotka.managers.netstats

import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SimUsageProvider(private val shizukuManager: ShizukuManager) {

    private companion object {
        const val CACHE_MILLIS = 60_000L
    }

    private val mutex = Mutex()
    private var cachedAt = 0L
    private var cached: NetstatsData? = null

    /**
     * Parsed netstats, or null when Shizuku is unavailable.
     * Uses --full so rotated files older than the current boot are included; the system keeps ~90 days.
     */
    fun isAvailable(): Boolean = shizukuManager.isAvailable()

    fun hasPermission(): Boolean = shizukuManager.hasPermission()

    suspend fun data(force: Boolean = false): NetstatsData? {
        val now = System.currentTimeMillis()
        if (!force) {
            cached?.let { value ->
                if (now - cachedAt < CACHE_MILLIS) return value
            }
        }

        if (shizukuManager.isAvailable()) {
            // Permission state can settle a moment after the binder connects.
            withTimeoutOrNull(5_000) {
                while (!shizukuManager.hasPermission()) delay(100)
            }
        }
        if (!shizukuManager.hasPermission()) return null

        return mutex.withLock {
            if (!force) {
                cached?.let { value ->
                    if (System.currentTimeMillis() - cachedAt < CACHE_MILLIS) return@withLock value
                }
            }

            shizukuManager.run("dumpsys netstats --poll")
            val lines = shizukuManager.run("dumpsys netstats --full --uid") ?: return@withLock null
            withContext(Dispatchers.Default) {
                NetstatsParser.parseData(lines)
            }.also {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
        }
    }

    fun usage(data: NetstatsData, startMillis: Long, endMillis: Long): Map<Int, FormattedBytesPair> =
        NetstatsParser.sumInRange(data.buckets, startMillis, endMillis)
            .mapValues { (_, bytes) -> FormattedBytesPair.from(bytes.first, bytes.second) }

    fun uidUsage(
        data: NetstatsData,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, Map<Int, FormattedBytesPair>> =
        NetstatsParser.sumByUidInRange(data.uidBuckets, startMillis, endMillis)
            .mapValues { (_, byUid) ->
                byUid.mapValues { (_, bytes) -> FormattedBytesPair.from(bytes.first, bytes.second) }
            }
}
