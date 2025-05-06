package com.jakubhekal.kvotka

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UsageNotificationService.start(context)
        }
    }
}