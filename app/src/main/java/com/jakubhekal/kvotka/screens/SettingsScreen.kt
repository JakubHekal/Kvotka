package com.jakubhekal.kvotka.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jakubhekal.kvotka.components.BytesTextField
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.viewmodels.GenericViewModel
import com.jakubhekal.kvotka.viewmodels.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            when (viewModel.state) {
                GenericViewModel.State.LOADING, GenericViewModel.State.UPDATING -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                GenericViewModel.State.SUCCESS -> {
                    SettingsList(viewModel.model!!) { viewModel.setState(it) }
                }
                GenericViewModel.State.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            style = MaterialTheme.typography.titleLarge,
                            text = "Something went wrong"
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.getState() }) {
                            Text(text = "Try again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsList(model: SettingsViewModel.Model, onUpdate: (SettingsViewModel.Model) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            modifier = Modifier.padding(16.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            text = "Permissions"
        )
        SettingRow(
            title = "Usage data permission",
            description = "We use internal device statistics to calculate your data usage."
        ) {
            Switch(checked = model.permissionManager.hasUsageStatsPermission(), onCheckedChange = {
                model.permissionManager.requestUsageStatsPermission()
            })
        }
        SettingRow(
            title = "Post notifications permission",
            description = "We use this to show notifications about your daily usage and warnings about limits."
        ) {
            Switch(
                checked = model.permissionManager.hasPostNotificationsPermission(),
                onCheckedChange = {
                    model.permissionManager.hasPostNotificationsPermission()
                })
        }
        Text(
            modifier = Modifier.padding(16.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            text = "Limits"
        )

        var monthlyLimitDialog by remember { mutableStateOf(false) }
        var monthlyLimitValue by remember { mutableStateOf(model.periodLimit) }

        InputDialog(
            show = monthlyLimitDialog,
            onClose = { monthlyLimitDialog = false },
            onSave = { onUpdate(model.copy(periodLimit = monthlyLimitValue)) },
            title = "Monthly limit",
            description = "Your monthly limit, you can change the unit by clicking on it"
        ) {
            BytesTextField(
                defaultValue = model.periodLimit.pretty.valueString,
                defaultUnit = model.periodLimit.pretty.unit,
                onValueChange = { v, u ->
                    monthlyLimitValue = FormattedBytes.from(v, u)
                }
            )
        }

        SettingRow(
            title = "Monthly limit",
            description = model.periodLimit.pretty.toString(),
            onClick = { monthlyLimitDialog = true }
        )

        var monthlyResetDateDialog by remember { mutableStateOf(false) }
        var monthlyResetDateValue by remember { mutableStateOf(TextFieldValue(model.periodResetDate.toString())) }

        InputDialog(
            show = monthlyResetDateDialog,
            onClose = { monthlyResetDateDialog = false },
            onSave = { onUpdate(model.copy(periodResetDate = monthlyResetDateValue.text.toInt())) },
            title = "Month reset date",
            description = "Specify on which day of the month your limits resets"
        ) {
            OutlinedTextField(
                value = monthlyResetDateValue,
                onValueChange = {
                    if (it.text.isEmpty() || (it.text.toIntOrNull() != null && it.text.toInt() in 1..31)) {
                        monthlyResetDateValue = it
                    }
                }
            )
        }

        SettingRow(
            title = "Month reset date",
            description = model.periodResetDate.toString(),
            onClick = { monthlyResetDateDialog = true }
        )
        SettingRow(
            title = "Dynamic daily limit",
            description = "We calculate your daily limit dynamically based on your usage history and remaining data."
        ) {
            Switch(checked = model.dailyLimitDynamic, onCheckedChange = { onUpdate(model.copy(dailyLimitDynamic = it)) })
        }

        if (!model.dailyLimitDynamic) {

            var dailyLimitDialog by remember { mutableStateOf(false) }
            var dailyLimitValue by remember { mutableStateOf(model.periodLimit) }

            InputDialog(
                show = dailyLimitDialog,
                onClose = { dailyLimitDialog = false },
                onSave = { onUpdate(model.copy(dailyLimit = dailyLimitValue)) },
                title = "Daily limit",
                description = "Your daily limit, you can change the unit by clicking on it"
            ) {
                BytesTextField(
                    defaultValue = model.dailyLimit.pretty.valueString,
                    defaultUnit = model.dailyLimit.pretty.unit,
                    onValueChange = { v, u ->
                        dailyLimitValue = FormattedBytes.from(v, u)
                    }
                )
            }

            SettingRow(
                title = "Daily limit",
                description = model.dailyLimit.pretty.toString(),
                onClick = { dailyLimitDialog = true }
            )
        }
    }
}


@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit = {},
    control: @Composable() (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick != {}) {
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = title
                )
                if(description != null) {
                    Text(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        text = description
                    )
                }
            }

            Column {
                control?.invoke()
            }

        }

    }
}

@Composable
fun InputDialog(
    show: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    title: String,
    description: String,
    content: @Composable() (() -> Unit)
) {
    if(show) {
        Dialog(
            onDismissRequest = { onClose() },
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        style = MaterialTheme.typography.headlineSmall,
                        text = title,
                    )
                    Text(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        text = description,
                    )
                    content.invoke()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onClose() }
                        ) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                onSave()
                                onClose()
                            }
                        ) {
                            Text("Save")
                        }
                    }

                }
            }
        }
    }
}