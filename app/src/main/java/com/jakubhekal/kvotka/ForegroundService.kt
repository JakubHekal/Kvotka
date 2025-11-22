package com.jakubhekal.kvotka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.viewmodels.ForegroundServiceViewModel
import com.jakubhekal.kvotka.viewmodels.GenericViewModel
import java.util.Timer
import java.util.TimerTask

class ForegroundService: LifecycleService() {

    companion object {

        private fun getIntent(context: Context): Intent {
            return Intent(context, ForegroundService::class.java)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel("CHANNEL_ID", "Foreground service", NotificationManager.IMPORTANCE_LOW)
        notificationChannel.setShowBadge(false)
        notificationManager.createNotificationChannel(notificationChannel)

        val notificationBuilder = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
            .setOngoing(true)
            .setContentIntent(Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            })

        val foregroundServiceViewModel by mutableStateOf(ForegroundServiceViewModel(this.dataStore, NetworkUsageManager(this)))

        startForeground(notificationBuilder)

        timer.schedule(object : TimerTask() {
            override fun run() {
                foregroundServiceViewModel.getState()
                notificationBuilder.apply {
                    when (foregroundServiceViewModel.state) {
                        GenericViewModel.State.LOADING -> {
                            setContentTitle("Loading...")
                            setContentText("Please wait")
                        }

                        GenericViewModel.State.SUCCESS, GenericViewModel.State.UPDATING -> {
                            setContentTitle("Used: ${foregroundServiceViewModel.model!!.used.pretty}")
                            setContentText("Remaining: ${foregroundServiceViewModel.model!!.remaining.pretty} • Limit: ${foregroundServiceViewModel.model!!.limit.pretty}")
                        }

                        GenericViewModel.State.ERROR -> {
                            setContentTitle("Error")
                            setContentText("Failed to get usage data")
                        }

                    }
                }

                notificationManager.notify(100, notificationBuilder.build())
            }
        }, 0, 5000)


        return super.onStartCommand(intent, flags, startId)

    }

    private fun startForeground(notificationBuilder: NotificationCompat.Builder) {
        try {
            ServiceCompat.startForeground(
            this, 100, notificationBuilder.build(),
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