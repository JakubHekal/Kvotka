package com.jakubhekal.kvotka.utils.bytes

class FormattedBytesPair private constructor(val rx: FormattedBytes, val tx: FormattedBytes) {
    val total = rx + tx

    companion object {
        fun from(rxBytes: Long, txBytes: Long): FormattedBytesPair {
            return FormattedBytesPair(FormattedBytes.from(rxBytes), FormattedBytes.from(txBytes))
        }

        val ZERO = FormattedBytesPair(FormattedBytes.ZERO, FormattedBytes.ZERO)
    }

    operator fun plus(other: FormattedBytesPair): FormattedBytesPair {
        return FormattedBytesPair(rx + other.rx, tx + other.tx)
    }

}