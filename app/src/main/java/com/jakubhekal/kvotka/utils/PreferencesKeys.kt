package com.jakubhekal.kvotka.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

object PreferencesKeys {
    object Settings {
        val PERIOD_LIMIT = longPreferencesKey("period_limit")
        val PERIOD_RESET_DATE = intPreferencesKey("period_reset_date")
        val DAILY_LIMIT_DYNAMIC = booleanPreferencesKey("daily_limit_dynamic")
        val DAILY_LIMIT = longPreferencesKey("daily_limit")
    }

    object Filter {
        val PERIOD = intPreferencesKey("period")
        val ORDER_BY = intPreferencesKey("order_by")
        val ORDER_ASC = booleanPreferencesKey("order_asc")
    }
}