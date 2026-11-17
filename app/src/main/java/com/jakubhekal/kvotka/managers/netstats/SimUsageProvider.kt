package com.jakubhekal.kvotka.managers.netstats

import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair

class SimUsageProvider(private val shizukuManager: ShizukuManager) {

    private companion object {
        const val CACHE_MILLIS = 60_000L
    }

    private var cachedAt = 0L
    private var cachedPeriodStart = 0L
    private var cached: Map<Int, FormattedBytesPair>? = null

    /**
     * Per-SIM cellular usage for [startMillis]..[endMillis], or null when Shizuku is unavailable.
     * Data comes from `dumpsys netstats`; the history it keeps is limited by the device.
     */
    suspend fun usage(startMillis: Long, endMillis: Long, force: Boolean = false): Map<Int, FormattedBytesPair>? {
        if (!shizukuManager.hasPermission()) return null

        val now = System.currentTimeMillis()
        val cachedValue = cached
        if (!force && cachedValue != null && cachedPeriodStart == startMillis && now - cachedAt < CACHE_MILLIS) {
            return cachedValue
        }

        shizukuManager.run("dumpsys netstats --poll")
        val lines = shizukuManager.run("dumpsys netstats") ?: return null

        val usage = NetstatsParser.sumInRange(NetstatsParser.parse(lines), startMillis, endMillis)
            .mapValues { (_, bytes) -> FormattedBytesPair.from(bytes.first, bytes.second) }

        cached = usage
        cachedPeriodStart = startMillis
        cachedAt = now
        return usage
    }
}
