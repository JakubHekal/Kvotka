package com.jakubhekal.kvotka

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.netstats.SimUsageProvider
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
            // startForegroundService is required outside of a broadcast whitelist on Android 8+.
            ContextCompat.startForegroundService(context, getIntent(context))
        }

        fun stop(context: Context) {
            val intent = getIntent(context)
            context.stopService(intent)
        }

    }

    private var timer: Timer? = null
    private var viewModel: ForegroundServiceViewModel? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel("CHANNEL_ID", getString(R.string.foreground_service_name), NotificationManager.IMPORTANCE_LOW)
        notificationChannel.setShowBadge(false)
        notificationManager.createNotificationChannel(notificationChannel)

        val notificationBuilder = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setSmallIcon(R.drawable.ic_stat_usage)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            })

        if (!startForeground(notificationBuilder)) return START_NOT_STICKY

        if (timer == null) {
            val foregroundServiceViewModel = ForegroundServiceViewModel(
                this.dataStore,
                NetworkUsageManager(this),
                SimUsageProvider.shared
            )
            viewModel = foregroundServiceViewModel
            foregroundServiceViewModel.start()

            val timer = Timer()
            this.timer = timer
            fun loadingText() = getString(R.string.notification_title_loading) to
                    getString(R.string.notification_text_loading)
            timer.schedule(object : TimerTask() {
                override fun run() {
                    foregroundServiceViewModel.getState()
                    // Always update the notification; an early return here left stale text forever.
                    val (title, text) = when (foregroundServiceViewModel.state) {
                        GenericViewModel.State.ERROR -> getString(R.string.notification_title_error) to
                                getString(R.string.notification_text_error)

                        GenericViewModel.State.LOADING -> loadingText()

                        GenericViewModel.State.SUCCESS, GenericViewModel.State.UPDATING -> {
                            val model = foregroundServiceViewModel.model
                            if (model == null) {
                                loadingText()
                            } else {
                                getString(R.string.notification_title_usage, model.used.pretty) to
                                        if (model.hasPlan) {
                                            getString(
                                                R.string.notification_text_usage,
                                                model.remaining.pretty,
                                                model.limit.pretty
                                            )
                                        } else {
                                            getString(R.string.notification_text_no_plan)
                                        }
                            }
                        }
                    }

                    notificationBuilder
                        .setContentTitle(title)
                        .setContentText(text)

                    notificationManager.notify(100, notificationBuilder.build())
                }
            }, 0, 60000)
        }

        return super.onStartCommand(intent, flags, startId)

    }

    private fun startForeground(notificationBuilder: NotificationCompat.Builder): Boolean {
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
            return true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                throw e
            }
            // Without a foreground notification the system kills the service anyway; stop cleanly.
            stopSelf()
            return false
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        viewModel?.stop()
        viewModel = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
