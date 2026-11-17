package com.jakubhekal.kvotka.managers.netstats

import kotlin.math.min
import kotlin.math.roundToLong

data class SimBucket(
    val subId: Int,
    val startMillis: Long,
    val durationMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val uid: Int? = null
)

data class NetstatsData(
    val buckets: List<SimBucket>,
    val uidBuckets: List<SimBucket>,
    val subIds: Set<Int>
)

object NetstatsParser {

    private const val DEFAULT_BUCKET_DURATION = 3_600_000L
    private const val SECONDS_THRESHOLD = 100_000L

    private val identityRegex = Regex("""\[([^\[\]]*)]""")
    private val numberRegex = Regex("""(\w+)=(-?\d+)""")
    private val bucketDurationRegex = Regex("""bucketDuration=(\d+)""")
    private val uidRegex = Regex("""(?:^|\s)uid=(-?\d+)""")
    private val sectionStarts = setOf("Dev stats:", "Xt stats:", "UID stats:")

    /**
     * Extracts mobile buckets with a resolved subscription id. Device totals come from
     * "Xt stats:" (falling back to "Dev stats:"), per-UID buckets from "UID stats:".
     * Identity lines with zero or several mobile subIds are skipped because they cannot be attributed.
     */
    fun parseData(lines: List<String>): NetstatsData {
        val devBuckets = mutableListOf<SimBucket>()
        val xtBuckets = mutableListOf<SimBucket>()
        val uidBuckets = mutableListOf<SimBucket>()
        val subIds = mutableSetOf<Int>()

        var section: String? = null
        var currentSubId: Int? = null
        var currentUid: Int? = null
        var bucketDuration = DEFAULT_BUCKET_DURATION

        for (rawLine in lines) {
            val line = rawLine.trim()
            val isHeader = line.endsWith(":") && rawLine.isNotEmpty() && !rawLine.first().isWhitespace()

            if (isHeader) {
                section = line.takeIf { it in sectionStarts }
                currentSubId = null
                currentUid = null
                continue
            }
            if (section == null) continue

            if (line.startsWith("ident=")) {
                currentSubId = parseSubId(line)?.also { subIds.add(it) }
                currentUid = if (section == "UID stats:") {
                    uidRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 0 }
                } else {
                    null
                }
                bucketDuration = DEFAULT_BUCKET_DURATION
                continue
            }

            bucketDurationRegex.find(line)?.let { match ->
                val value = match.groupValues[1].toLongOrNull()
                if (value != null && value > 0) bucketDuration = durationMillis(value)
            }

            if (!line.startsWith("bucketStart=") && !line.startsWith("st=")) continue
            val subId = currentSubId ?: continue

            val values = numberRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toLong() }
            val rawStart = values["bucketStart"] ?: values["st"] ?: continue
            val startMillis = timestampMillis(rawStart)
            val rx = values["rxBytes"] ?: values["rb"] ?: 0L
            val tx = values["txBytes"] ?: values["tb"] ?: 0L
            val duration = values["bucketDuration"]?.let(::durationMillis) ?: bucketDuration

            val bucket = SimBucket(subId, startMillis, duration, rx, tx, currentUid)
            when (section) {
                "Dev stats:" -> devBuckets.add(bucket)
                "Xt stats:" -> xtBuckets.add(bucket)
                "UID stats:" -> uidBuckets.add(bucket)
            }
        }

        return NetstatsData(
            buckets = xtBuckets.ifEmpty { devBuckets },
            uidBuckets = uidBuckets,
            subIds = subIds
        )
    }

    fun parse(lines: List<String>): List<SimBucket> = parseData(lines).buckets

    /** Sums rx/tx per subId, scaling buckets that only partly overlap the requested range. */
    fun sumInRange(buckets: List<SimBucket>, startMillis: Long, endMillis: Long): Map<Int, Pair<Long, Long>> {
        val totals = mutableMapOf<Int, LongArray>()

        buckets.forEach { bucket ->
            val scale = overlapScale(bucket, startMillis, endMillis) ?: return@forEach
            val entry = totals.getOrPut(bucket.subId) { LongArray(2) }
            entry[0] += (bucket.rxBytes * scale).roundToLong()
            entry[1] += (bucket.txBytes * scale).roundToLong()
        }

        return totals.mapValues { (_, value) -> value[0] to value[1] }
    }

    /** Sums rx/tx per subId and uid, scaling buckets that only partly overlap the requested range. */
    fun sumByUidInRange(
        buckets: List<SimBucket>,
        startMillis: Long,
        endMillis: Long
    ): Map<Int, Map<Int, Pair<Long, Long>>> {
        val totals = mutableMapOf<Int, MutableMap<Int, LongArray>>()

        buckets.forEach { bucket ->
            val uid = bucket.uid ?: return@forEach
            val scale = overlapScale(bucket, startMillis, endMillis) ?: return@forEach
            val entry = totals.getOrPut(bucket.subId) { mutableMapOf() }
                .getOrPut(uid) { LongArray(2) }
            entry[0] += (bucket.rxBytes * scale).roundToLong()
            entry[1] += (bucket.txBytes * scale).roundToLong()
        }

        return totals.mapValues { (_, byUid) ->
            byUid.mapValues { (_, value) -> value[0] to value[1] }
        }
    }

    private fun overlapScale(bucket: SimBucket, startMillis: Long, endMillis: Long): Double? {
        val bucketEnd = bucket.startMillis + bucket.durationMillis
        val overlapStart = maxOf(bucket.startMillis, startMillis)
        val overlapEnd = min(bucketEnd, endMillis)
        if (overlapEnd <= overlapStart) return null

        return if (bucket.durationMillis > 0) {
            min(1.0, (overlapEnd - overlapStart).toDouble() / bucket.durationMillis)
        } else {
            1.0
        }
    }

    // Dumps print bucketDuration in seconds; timestamps use seconds in legacy output and millis in modern output.
    private fun durationMillis(value: Long): Long =
        if (value in 1 until SECONDS_THRESHOLD) value * 1000 else value

    private fun timestampMillis(value: Long): Long =
        if (value < 100_000_000_000L) value * 1000 else value

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
