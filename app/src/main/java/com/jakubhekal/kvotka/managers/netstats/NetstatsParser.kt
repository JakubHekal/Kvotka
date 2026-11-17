package com.jakubhekal.kvotka.managers.netstats

import kotlin.math.min
import kotlin.math.roundToLong

data class SimBucket(
    val subId: Int,
    val startMillis: Long,
    val durationMillis: Long,
    val rxBytes: Long,
    val txBytes: Long
)

object NetstatsParser {

    private val sectionEnds = setOf("Active interfaces:", "Xt stats:", "UID stats:", "Tag stats:")
    private val identityRegex = Regex("""\[([^\[\]]*)]""")
    private val numberRegex = Regex("""(\w+)=(-?\d+)""")

    /**
     * Extracts mobile buckets with a resolved subscription id from the "Dev stats:" section.
     * Identity lines with zero or several mobile subIds are skipped because they cannot be attributed.
     */
    fun parse(lines: List<String>): List<SimBucket> {
        val buckets = mutableListOf<SimBucket>()
        var inDevStats = false
        var currentSubId: Int? = null
        var bucketDuration = 3_600_000L

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (!inDevStats) {
                if (line == "Dev stats:") inDevStats = true
                continue
            }
            if (line in sectionEnds) break

            if (line.startsWith("ident=")) {
                currentSubId = parseSubId(line)
                bucketDuration = 3_600_000L
                continue
            }

            if (line.startsWith("bucketDuration=")) {
                val value = line.substringAfter('=').toLongOrNull()
                if (value != null && value > 0) bucketDuration = value
                continue
            }

            if (!line.startsWith("bucketStart=") && !line.startsWith("st=")) continue
            val subId = currentSubId ?: continue

            val values = numberRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toLong() }
            val rawStart = values["bucketStart"] ?: values["st"] ?: continue
            val startMillis = if (rawStart < 100_000_000_000L) rawStart * 1000 else rawStart
            val rx = values["rxBytes"] ?: values["rb"] ?: 0L
            val tx = values["txBytes"] ?: values["tb"] ?: 0L
            val duration = values["bucketDuration"] ?: bucketDuration

            buckets.add(SimBucket(subId, startMillis, duration, rx, tx))
        }

        return buckets
    }

    /** Sums rx/tx per subId, scaling buckets that only partly overlap the requested range. */
    fun sumInRange(buckets: List<SimBucket>, startMillis: Long, endMillis: Long): Map<Int, Pair<Long, Long>> {
        val totals = mutableMapOf<Int, LongArray>()

        buckets.forEach { bucket ->
            val bucketEnd = bucket.startMillis + bucket.durationMillis
            val overlapStart = maxOf(bucket.startMillis, startMillis)
            val overlapEnd = min(bucketEnd, endMillis)
            if (overlapEnd <= overlapStart) return@forEach

            val scale = if (bucket.durationMillis > 0) {
                min(1.0, (overlapEnd - overlapStart).toDouble() / bucket.durationMillis)
            } else {
                1.0
            }

            val entry = totals.getOrPut(bucket.subId) { LongArray(2) }
            entry[0] += (bucket.rxBytes * scale).roundToLong()
            entry[1] += (bucket.txBytes * scale).roundToLong()
        }

        return totals.mapValues { (_, value) -> value[0] to value[1] }
    }

    private fun parseSubId(line: String): Int? {
        val mobileSubIds = identityRegex.findAll(line)
            .map { it.groupValues[1] }
            .filter { identity -> isMobile(identity) }
            .mapNotNull { identity ->
                numberRegex.findAll(identity)
                    .firstOrNull { it.groupValues[1] == "subId" }
                    ?.groupValues?.get(2)?.toIntOrNull()
            }
            .toSet()

        if (mobileSubIds.size != 1) return null
        return mobileSubIds.first().takeIf { it >= 0 }
    }

    private fun isMobile(identity: String): Boolean {
        if (identity.contains("type=MOBILE")) return true
        val type = numberRegex.findAll(identity)
            .firstOrNull { it.groupValues[1] == "type" }
            ?.groupValues?.get(2)
            ?: return false
        return type == "0"
    }
}
