package com.jakubhekal.kvotka.utils

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jakubhekal.kvotka.R

/** Best-effort carrier name for a subscription. Some devices throw without READ_PHONE_STATE. */
fun Context.simCarrierName(subId: Int): String? = try {
    val sim = getSystemService(TelephonyManager::class.java).createForSubscriptionId(subId)
    val carrierIdName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        sim.simCarrierIdName?.toString()
    } else {
        null
    }
    carrierIdName?.trim()?.takeIf { it.isNotEmpty() }
        ?: sim.simOperatorName?.trim()?.takeIf { it.isNotEmpty() }
        ?: sim.networkOperatorName?.trim()?.takeIf { it.isNotEmpty() }
} catch (_: Throwable) {
    null
}

@Composable
fun simLabel(subId: Int): String {
    val context = LocalContext.current
    val carrier = remember(subId) { context.simCarrierName(subId) }
    return if (carrier != null) {
        stringResource(R.string.label_sim_carrier, carrier, subId)
    } else {
        stringResource(R.string.label_sim, subId)
    }
}
