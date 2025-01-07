package com.jakubhekal.kvotka.managers

import android.annotation.SuppressLint
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

class NetworkUsageManager(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    enum class TransmissionType { BYTES_RX, BYTES_TX, BYTES_ALL }

    private fun getBytesFromBucket(bucket: NetworkStats.Bucket, transmissionType: TransmissionType): Long {
        return when (transmissionType) {
            TransmissionType.BYTES_RX -> bucket.rxBytes
            TransmissionType.BYTES_TX -> bucket.txBytes
            TransmissionType.BYTES_ALL -> bucket.rxBytes + bucket.txBytes
        }
    }

    private val subscriberId: String?
        @SuppressLint("HardwareIds")
        get() {
            return try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.subscriberId
            } catch (e: Exception) {
                null
            }

    }

    fun getDeviceBytesCellular(startTime: Long, endTime: Long, transmissionType: TransmissionType = TransmissionType.BYTES_ALL): FormattedBytes {
        return try {
            val bucket: NetworkStats.Bucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                null, startTime, endTime
            )
            FormattedBytes.from(getBytesFromBucket(bucket, transmissionType))
        } catch (e: Exception) {
            e.printStackTrace()
            FormattedBytes.from(0)
        }
    }

    fun getPackageBytesCellular(uid: Int, startTime: Long, endTime: Long, transmissionType: TransmissionType = TransmissionType.BYTES_ALL): FormattedBytes {
        return try {
            val networkStats: NetworkStats = networkStatsManager.queryDetailsForUid(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                subscriberId, startTime, endTime, uid
            )
            var totalBytes: Long = 0
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                totalBytes += getBytesFromBucket(bucket, transmissionType)
            }
            networkStats.close()
            FormattedBytes.from(totalBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            FormattedBytes.from(0)
        }
    }
}

