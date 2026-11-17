package com.jakubhekal.kvotka.managers.netstats

import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SimUsageProvider(private val shizukuManager: ShizukuManager) {

    private companion object {
        const val CACHE_MILLIS = 60_000L
    }

    private val mutex = Mutex()
    private var cachedAt = 0L
    @Volatile
    private var cached: NetstatsData? = null

    /** Last parsed dump, or null when nothing has loaded yet. Never blocks. */
    fun cached(): NetstatsData? = cached

    /**
     * Parsed netstats, or null when Shizuku is unavailable.
     * Uses --full so rotated files older than the current boot are included; the system keeps ~90 days.
     */
    suspend fun data(force: Boolean = false): NetstatsData? {
        if (!shizukuManager.hasPermission()) return null

        return mutex.withLock {
            val now = System.currentTimeMillis()
            cached?.let { value ->
                if (!force && now - cachedAt < CACHE_MILLIS) return@withLock value
            }

            shizukuManager.run("dumpsys netstats --poll")
            val lines = shizukuManager.run("dumpsys netstats --full --uid") ?: return@withLock null
            NetstatsParser.parseData(lines).also {
                cached = it
                cachedAt = now
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
