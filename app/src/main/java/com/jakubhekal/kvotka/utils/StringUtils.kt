package com.jakubhekal.kvotka.utils

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

fun String.toDoubleLocaleOrNull() : Double? {
    return try {
        val numberFormat : NumberFormat = NumberFormat.getInstance(Locale.getDefault())
        numberFormat.parse(this)?.toDouble()
    } catch (e: Exception) {
        null
    }
}

fun String.isNumeric() : Boolean {
    return this.all { c -> c.isDigit() || c == DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator}
}