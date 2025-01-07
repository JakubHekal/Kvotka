package com.jakubhekal.kvotka.managers

import kotlin.math.min
import kotlin.math.roundToLong

class FormattedBytes private constructor(val bytes: Long) {

    enum class FormattedUnit { B, KB, MB, GB, TB, PRETTY }

    data class BytePair(val value: Double, val unit: FormattedUnit) {
        val valueString = "%.1f".format(value)
        val unitString = unit.name

        override fun toString(): String {
            return "$valueString $unitString"
        }
    }

    companion object {
        fun from(value: Double, unit: FormattedUnit = FormattedUnit.B): FormattedBytes {
            val units = FormattedUnit.entries.toList()
            var out = value.coerceAtLeast(0.0)
            var index = units.indexOf(unit)
            while (index > 0) {
                index--
                out *= 1000
            }
            return FormattedBytes(out.roundToLong())
        }

        fun from(bytes: Long): FormattedBytes {
            return FormattedBytes(bytes.coerceAtLeast(0L))
        }
    }

    fun to(unit: FormattedUnit = FormattedUnit.PRETTY): BytePair {
        val units = FormattedUnit.entries.toList()
        var index = 0
        var value = bytes.toDouble()
        while (value >= 1000 && index < min(units.indexOf(unit), units.count() - 2)) {
            index++
            value /= 1000
        }
        return BytePair(value, units[index])
    }

    val pretty : BytePair
        get() { return to() }

    operator fun plus(other: FormattedBytes): FormattedBytes {
        return from(bytes + other.bytes)
    }

    operator fun minus(other: FormattedBytes): FormattedBytes {
        return from(bytes - other.bytes)
    }

    operator fun div(other: Long): FormattedBytes {
        return from(bytes / other)
    }
}
