package com.jakubhekal.kvotka.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.jakubhekal.kvotka.managers.FormattedBytes
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

@Composable
fun BytesTextField(
    modifier: Modifier = Modifier,
    label: @Composable() (() -> Unit)? = null,
    defaultValue: String? = null,
    defaultUnit: FormattedBytes.FormattedUnit = FormattedBytes.FormattedUnit.GB,
    onValueChange: (value: Double, unit: FormattedBytes.FormattedUnit) -> Unit
) {

    var value by remember { mutableStateOf(TextFieldValue(defaultValue ?: "")) }
    var currentUnit by remember { mutableStateOf(defaultUnit) }
    var unitDropdown by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        value = value,
        onValueChange = {
            if(it.text.isEmpty() || (it.text.isNumeric() && it.text.toDoubleLocaleOrNull() != null)) {
                value = it
                onValueChange(it.text.toDoubleLocaleOrNull() ?: 0.0, currentUnit)
            }
        },
        trailingIcon = {
            Text(
                modifier = Modifier
                    .clickable { unitDropdown = true },
                text = currentUnit.name,
            )

            DropdownMenu(expanded = unitDropdown, onDismissRequest = { unitDropdown = false }) {
                FormattedBytes.FormattedUnit.entries.dropLast(1).forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit.name) },
                        onClick = {
                            currentUnit = unit
                            onValueChange(value.text.toDoubleLocaleOrNull() ?: 0.0, unit)
                            unitDropdown = false
                        }
                    )
                }
            }

        }
    )
}