package com.jakubhekal.kvotka

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class AutoStartReceiver : BroadcastReceiver() {

    val validActions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in validActions) {
            ForegroundService.start(context)
        }
    }
}