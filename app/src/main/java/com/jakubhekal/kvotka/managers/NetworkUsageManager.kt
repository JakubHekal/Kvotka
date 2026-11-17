package com.jakubhekal.kvotka.managers

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import com.jakubhekal.kvotka.utils.bytes.FormattedBytesPair

class NetworkUsageManager(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun getDeviceUsageCellular(startTime: Long, endTime: Long): FormattedBytesPair {
        return try {
            val bucket: NetworkStats.Bucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                null, startTime, endTime
            )
            FormattedBytesPair.from(bucket.rxBytes, bucket.txBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            FormattedBytesPair.ZERO
        }
    }

    fun getUidsUsageCellular(startTime: Long, endTime: Long) : Map<Int, FormattedBytesPair>  {

        val map = mutableMapOf<Int, FormattedBytesPair>()

        try {
            val networkStats: NetworkStats = networkStatsManager.querySummary(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                null, startTime, endTime
            )

            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                val last = map.getOrDefault(bucket.uid, FormattedBytesPair.ZERO)
                map[bucket.uid] = FormattedBytesPair.from(bucket.rxBytes, bucket.txBytes) + last
            }
            networkStats.close()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return map
    }
}

