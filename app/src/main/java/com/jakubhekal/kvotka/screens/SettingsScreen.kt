package com.jakubhekal.kvotka.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jakubhekal.kvotka.R
import com.jakubhekal.kvotka.components.BytesTextField
import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.utils.LimitsPreferences
import com.jakubhekal.kvotka.utils.bytes.FormattedBytes
import com.jakubhekal.kvotka.utils.findActivity
import com.jakubhekal.kvotka.utils.simLabel
import com.jakubhekal.kvotka.viewmodels.GenericViewModel
import com.jakubhekal.kvotka.viewmodels.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import rikka.shizuku.Shizuku
import java.util.Locale.getDefault

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshSims()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.screen_name_settings)) },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), bottom = 0.dp)
        ) {
            when (viewModel.state) {
                GenericViewModel.State.LOADING -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                GenericViewModel.State.SUCCESS, GenericViewModel.State.UPDATING -> {
                    SettingsList(
                        model = viewModel.model!!,
                        simIds = viewModel.simIds,
                        onUpdate = { subId, plan -> viewModel.setState(subId, plan) },
                        onRemove = { viewModel.clearPlan(it) }
                    )
                }
                GenericViewModel.State.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            style = MaterialTheme.typography.titleLarge,
                            text = stringResource(R.string.error_something_went_wrong)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.getState() }) {
                            Text(text = stringResource(R.string.action_try_again))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsList(
    model: SettingsViewModel.Model,
    simIds: List<Int>,
    onUpdate: (Int, LimitsPreferences) -> Unit,
    onRemove: (Int) -> Unit
) {
    val context = LocalContext.current
    val shizukuManager = remember { ShizukuManager() }
    var shizukuAvailable by remember { mutableStateOf(shizukuManager.isAvailable()) }
    var shizukuPermission by remember { mutableStateOf(shizukuManager.hasPermission()) }
    var permissionTick by remember { mutableIntStateOf(0) }

    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermission = grantResult == PackageManager.PERMISSION_GRANTED
        }
    }

    DisposableEffect(Unit) {
        shizukuManager.addPermissionListener(permissionListener)
        onDispose { shizukuManager.removePermissionListener(permissionListener) }
    }

    LifecycleResumeEffect(Unit) {
        permissionTick++
        shizukuAvailable = shizukuManager.isAvailable()
        shizukuPermission = shizukuManager.hasPermission()
        onPauseOrDispose { }
    }

    val hasUsagePermission = remember(permissionTick) { model.permissionManager.hasUsageStatsPermission() }
    val hasNotificationsPermission = remember(permissionTick) { model.permissionManager.hasPostNotificationsPermission() }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            modifier = Modifier.padding(16.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            text = stringResource(R.string.settings_category_permissions)
        )
        SettingRow(
            title = stringResource(R.string.setting_title_usage_data_permission),
            description = stringResource(R.string.setting_description_usage_data_permission)
        ) {
            Switch(
                checked = hasUsagePermission,
                onCheckedChange = {
                    if (it)
                        model.permissionManager.requestUsageStatsPermission()
                    else
                        model.permissionManager.settingsUsageStatsPermission()
            })
        }
        SettingRow(
            title = stringResource(R.string.setting_title_post_notifications_permission),
            description = stringResource(R.string.setting_description_post_notifications_permission),
            // A denied runtime permission cannot be requested again from here, so send the user to settings.
            onClick = { model.permissionManager.settingsPostNotificationsPermission() }
        ) {
            Switch(
                checked = hasNotificationsPermission,
                onCheckedChange = { model.permissionManager.settingsPostNotificationsPermission() })
        }
        SettingRow(
            title = stringResource(R.string.setting_title_per_sim_usage),
            description = stringResource(
                when {
                    !shizukuAvailable -> R.string.shizuku_status_not_running
                    !shizukuPermission -> R.string.shizuku_status_permission_required
                    else -> R.string.shizuku_status_active
                }
            )
        ) {
            when {
                !shizukuAvailable -> TextButton(onClick = {
                    try {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            ?.let { context.startActivity(it) }
                    } catch (_: Exception) {
                    }
                }) {
                    Text(stringResource(R.string.action_open))
                }

                !shizukuPermission -> TextButton(onClick = {
                    context.findActivity()?.let { shizukuManager.requestPermission(it) }
                }) {
                    Text(stringResource(R.string.action_grant))
                }
            }
        }
        Text(
            modifier = Modifier.padding(16.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            text = stringResource(R.string.settings_category_limits)
        )

        var selectedPlanId by remember { mutableIntStateOf(LimitsPreferences.UNASSIGNED_SIM) }
        val planIds = (simIds + model.plans.keys).distinct().sorted()
        val activePlanId = selectedPlanId
            .takeIf { it in planIds }
            ?: planIds.firstOrNull()
            ?: LimitsPreferences.UNASSIGNED_SIM
        val plan = model.plans[activePlanId] ?: LimitsPreferences(0L, 1, false, 0L)
        val periodLimit = FormattedBytes.from(plan.periodLimit)
        val dailyLimit = FormattedBytes.from(plan.dailyLimit)

        @Composable
        fun planLabel(subId: Int): String = if (subId == LimitsPreferences.UNASSIGNED_SIM) {
            stringResource(R.string.label_plan_unassigned)
        } else {
            simLabel(subId)
        }

        var planMenuExpanded by remember { mutableStateOf(false) }

        SettingRow(
            title = stringResource(R.string.setting_title_plan),
            description = if (activePlanId == LimitsPreferences.UNASSIGNED_SIM) {
                stringResource(R.string.setting_description_plan_unassigned)
            } else {
                null
            }
        ) {
            if (planIds.size > 1) {
                Box {
                    TextButton(onClick = { planMenuExpanded = true }) {
                        Text(planLabel(activePlanId))
                    }
                    DropdownMenu(
                        expanded = planMenuExpanded,
                        onDismissRequest = { planMenuExpanded = false }
                    ) {
                        planIds.forEach { subId ->
                            DropdownMenuItem(
                                text = { Text(planLabel(subId)) },
                                onClick = {
                                    selectedPlanId = subId
                                    planMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(planLabel(activePlanId))
            }
        }

        var monthlyLimitDialog by remember { mutableStateOf(false) }
        var monthlyLimitValue by remember(activePlanId, plan.periodLimit) { mutableStateOf(periodLimit) }

        InputDialog(
            show = monthlyLimitDialog,
            onClose = { monthlyLimitDialog = false },
            onSave = { onUpdate(activePlanId, plan.copy(periodLimit = monthlyLimitValue.bytes)) },
            title = stringResource(R.string.setting_title_monthly_limit),
            description = stringResource(R.string.setting_description_monthly_limit)
        ) {
            BytesTextField(
                defaultValue = periodLimit.pretty.valueString,
                defaultUnit = periodLimit.pretty.unit,
                onValueChange = { v, u ->
                    monthlyLimitValue = FormattedBytes.from(v, u)
                }
            )
        }

        SettingRow(
            title = stringResource(R.string.setting_title_monthly_limit),
            description = periodLimit.pretty.toString(),
            onClick = { monthlyLimitDialog = true }
        )

        var monthlyResetDateDialog by remember { mutableStateOf(false) }
        var monthlyResetDateValue by remember(activePlanId, plan.periodResetDate) {
            mutableStateOf(TextFieldValue(plan.periodResetDate.toString()))
        }

        InputDialog(
            show = monthlyResetDateDialog,
            onClose = { monthlyResetDateDialog = false },
            onSave = {
                onUpdate(
                    activePlanId,
                    plan.copy(periodResetDate = monthlyResetDateValue.text.toIntOrNull() ?: plan.periodResetDate)
                )
            },
            title = stringResource(R.string.setting_title_month_reset_date),
            description = stringResource(R.string.setting_description_month_reset_date)
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
            title = stringResource(R.string.setting_title_month_reset_date),
            description = plan.periodResetDate.toString(),
            onClick = { monthlyResetDateDialog = true }
        )
        SettingRow(
            title = stringResource(R.string.setting_title_dynamic_daily_limit),
            description = stringResource(R.string.setting_description_dynamic_daily_limit)
        ) {
            Switch(
                checked = plan.dailyLimitDynamic,
                onCheckedChange = { onUpdate(activePlanId, plan.copy(dailyLimitDynamic = it)) }
            )
        }

        if (!plan.dailyLimitDynamic) {

            var dailyLimitDialog by remember { mutableStateOf(false) }
            var dailyLimitValue by remember(activePlanId, plan.dailyLimit) { mutableStateOf(dailyLimit) }

            InputDialog(
                show = dailyLimitDialog,
                onClose = { dailyLimitDialog = false },
                onSave = { onUpdate(activePlanId, plan.copy(dailyLimit = dailyLimitValue.bytes)) },
                title = stringResource(R.string.setting_title_daily_limit),
                description = stringResource(R.string.setting_description_daily_limit)
            ) {
                BytesTextField(
                    defaultValue = dailyLimit.pretty.valueString,
                    defaultUnit = dailyLimit.pretty.unit,
                    onValueChange = { v, u ->
                        dailyLimitValue = FormattedBytes.from(v, u)
                    }
                )
            }

            SettingRow(
                title = stringResource(R.string.setting_title_daily_limit),
                description = dailyLimit.pretty.toString(),
                onClick = { dailyLimitDialog = true }
            )
        }

        if (model.plans.containsKey(activePlanId)) {
            SettingRow(
                title = stringResource(R.string.action_remove_plan),
                onClick = { onRemove(activePlanId) }
            )
        }

        val currentLocale = context.resources.configuration.locales[0]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                modifier = Modifier.padding(16.dp, 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                text = stringResource(R.string.settings_category_other)
            )
            SettingRow(
                title = stringResource(R.string.setting_title_language),
                description = currentLocale.getDisplayName(currentLocale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                    intent.data = Uri.parse("package:" + context.packageName)
                    context.startActivity(intent)
                }
            )
        }
    }
}


@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable() (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
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
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = {
                                onSave()
                                onClose()
                            }
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }

                }
            }
        }
    }
}
