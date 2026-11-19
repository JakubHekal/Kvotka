package com.jakubhekal.kvotka.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jakubhekal.kvotka.R
import com.jakubhekal.kvotka.components.HistoryChart
import com.jakubhekal.kvotka.utils.simLabel
import com.jakubhekal.kvotka.viewmodels.DashboardViewModel
import com.jakubhekal.kvotka.viewmodels.GenericViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Destination<RootGraph>(
    start = true
)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    val simIds = (viewModel.simIds + viewModel.simUsage.orEmpty().keys).distinct().sorted()
    var simMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.screen_name_data_usage)) },
                actions = {
                    if (simIds.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { simMenuExpanded = true }) {
                                Icon(Icons.Outlined.SimCard, null)
                                Text(
                                    viewModel.selectedSim?.let { simLabel(it) }
                                        ?: stringResource(R.string.label_all_sims)
                                )
                            }
                            DropdownMenu(
                                expanded = simMenuExpanded,
                                onDismissRequest = { simMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.label_all_sims)) },
                                    onClick = {
                                        viewModel.selectSim(null)
                                        simMenuExpanded = false
                                    }
                                )
                                simIds.forEach { subId ->
                                    DropdownMenuItem(
                                        text = { Text(simLabel(subId)) },
                                        onClick = {
                                            viewModel.selectSim(subId)
                                            simMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    when (viewModel.state) {
                        GenericViewModel.State.LOADING, GenericViewModel.State.UPDATING -> {}
                        else -> {
                            IconButton(onClick = {
                                viewModel.getState()
                            }) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    stringResource(R.string.content_description_refresh)
                                )
                            }
                        }
                    }

                }
            )
        },
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
                    MainOverview(viewModel.model!!.period, viewModel.model!!.hasPlan)
                    HorizontalDivider()
                    DailyOverview(
                        modifier = Modifier.weight(1f),
                        values = viewModel.model!!.daily,
                        showLimits = viewModel.model!!.hasPlan
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
fun MainOverview(values: DashboardViewModel.UsageValues, showLimits: Boolean) {

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                text = stringResource(R.string.label_used).lowercase()
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge,
                text = values.used.pretty.valueString
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                text = values.used.pretty.unitString
            )

            if (!showLimits) return@Row

            Spacer(modifier = Modifier.weight(1f))

            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                text = stringResource(R.string.label_remaining).lowercase()
            )

            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.headlineLarge,
                text = values.remaining.pretty.valueString
            )

            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                text = values.remaining.pretty.unitString
            )
        }

        if (showLimits) {
            LinearProgressIndicator(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth(),
                progress = { values.used.bytes.toFloat() / (values.limit.bytes.toFloat() + 1) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(R.string.label_value_resets_on,
                        values.date.toInstant().atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d.M"))
                    ),
                )

                Text(
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(R.string.label_value_limit, values.limit.pretty),
                )
            }
        }
    }
}

@Composable
fun DailyOverview(
    modifier: Modifier = Modifier,
    values: List<DashboardViewModel.UsageValues>,
    showLimits: Boolean
) {

    if (values.isEmpty()) return

    var dayIndex by remember(values.size) { mutableIntStateOf(values.lastIndex) }
    val index = dayIndex.coerceIn(0, values.lastIndex)

    Column(
        modifier = modifier.padding(16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                style = MaterialTheme.typography.headlineSmall,
                text = when (index) {
                    values.lastIndex -> stringResource(R.string.label_today)
                    values.lastIndex - 1 -> stringResource(R.string.label_yesterday)
                    else -> values[index].date.toInstant().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("E dd.MM"))
                },
            )

            if (showLimits) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                    )
                    Text(
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                        text = stringResource(R.string.label_value_limit,values[index].limit.pretty),
                    )
                }
            }

        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                text = stringResource(R.string.label_used).lowercase()
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
                text = values[index].used.pretty.valueString
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                text = values[index].used.pretty.unitString
            )

            if (!showLimits) return@Row

            Spacer(modifier = Modifier.weight(1f))

            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                text = stringResource(R.string.label_remaining).lowercase()
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.headlineMedium,
                text = values[index].remaining.pretty.valueString
            )
            Text(
                modifier = Modifier.alignByBaseline(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                text = values[index].remaining.pretty.unitString
            )
        }

        HistoryChart(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp),
            values = values.map { it.used.bytes },
            labels = values.map {
                it.date.toInstant().atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM"))
            },
            limits = if (showLimits) values.map { it.limit.bytes } else null) { index ->
            dayIndex = index
        }
    }

}