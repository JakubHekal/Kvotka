package com.jakubhekal.kvotka.utils

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

fun String.toDoubleLocaleOrNull() : Double? {
    return try {
        val numberFormat : NumberFormat = NumberFormat.getInstance(Locale.getDefault())
        val position = ParsePosition(0)
        val value = numberFormat.parse(this, position) ?: return null
        // Accept a trailing decimal separator while the user is still typing.
        val decimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        val consumesAll = position.index == length
        val consumesIncompleteDecimal = position.index == length - 1 && lastOrNull() == decimalSeparator
        if (consumesAll || consumesIncompleteDecimal) value.toDouble() else null
    } catch (e: Exception) {
        null
    }
}

fun String.isNumeric() : Boolean {
    return this.all { c -> c.isDigit() || c == DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator}
}
