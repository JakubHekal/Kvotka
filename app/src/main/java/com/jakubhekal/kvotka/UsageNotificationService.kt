package com.jakubhekal.kvotka

import AutoStartWorker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.providers.UsageLimitsProvider
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Timer
import java.util.TimerTask

sealed interface UsageNotificationState {
    data class Success(
        val used: FormattedBytes,
        val remaining: FormattedBytes,
        val limit: FormattedBytes,
    ): UsageNotificationState
    object Error: UsageNotificationState
    object Loading: UsageNotificationState
}

class UsageNotificationService: LifecycleService() {

    companion object {

        private fun getIntent(context: Context): Intent {
            return Intent(context, UsageNotificationService::class.java)
        }

        fun start(context: Context) {
            val intent = getIntent(context)
            context.stopService(intent)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = getIntent(context)
            context.stopService(intent)
        }

    }

    private val timer = Timer()
    private var state: UsageNotificationState by mutableStateOf(UsageNotificationState.Loading)

    private fun getState() {
        val networkUsageManager = NetworkUsageManager(this)
        lifecycleScope.launch {
            dataStore.data.collect {
                val usageLimitsProvider = UsageLimitsProvider(it)
                state = UsageNotificationState.Loading
                state = try {
                    val now = ZonedDateTime.now()
                    val dayStart = usageLimitsProvider.todayStart

                    val thisPeriodUsed = networkUsageManager.getDeviceUsageCellular(
                        usageLimitsProvider.currentPeriodStart.toInstant().toEpochMilli(),
                        dayStart.toInstant().toEpochMilli()
                    ).total

                    val thisPeriodRemaining = usageLimitsProvider.periodLimit - thisPeriodUsed


                    val dayUsed = networkUsageManager.getDeviceUsageCellular(
                        dayStart.toInstant().toEpochMilli(),
                        now.toInstant().toEpochMilli()
                    ).total

                    val dayLimit = if (usageLimitsProvider.dynamicDailyLimit) {
                        val daysRemaining = ChronoUnit.DAYS.between(
                            dayStart,
                            usageLimitsProvider.nextPeriodStart
                        )

                        thisPeriodRemaining / daysRemaining
                    } else {
                        usageLimitsProvider.dailyLimit
                    }

                    val dayRemaining = dayLimit - dayUsed

                    UsageNotificationState.Success(dayUsed, dayRemaining, dayLimit)
                } catch (e: Exception) {
                    UsageNotificationState.Error
                }
                startForeground()
            }
        }
    }

    private fun buildNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }).apply {
                when (state) {
                    is UsageNotificationState.Success -> {
                        val (used, remaining, limit) = state as UsageNotificationState.Success
                        setContentTitle("Used: ${used.pretty}")
                        setContentText("Remaining: ${remaining.pretty} • Limit: ${limit.pretty}")
                    }

                    is UsageNotificationState.Error -> {
                        setContentTitle("Error")
                        setContentText("Failed to get usage data")
                    }

                    is UsageNotificationState.Loading -> {
                        setContentText("Loading...")
                    }
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        AutoStartWorker.enqueue(this)

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                getState()
            }
        }, 0, 5000)


        return super.onStartCommand(intent, flags, startId)

    }

    private fun startForeground() {
        val notificationManager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "CHANNEL_ID",
            "Foreground service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)

        try {
             ServiceCompat.startForeground(
                 this, 100, buildNotification().build(),
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                     ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                 } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                     ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                 } else {
                     0
                 },
             )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                throw e
            }
        }
    }

    override fun onDestroy() {
        timer.cancel()
        super.onDestroy()
    }
}