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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jakubhekal.kvotka.R
import com.jakubhekal.kvotka.components.HistoryChart
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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.screen_name_data_usage)) },
                actions = {
                    when (viewModel.state) {
                        GenericViewModel.State.LOADING, GenericViewModel.State.UPDATING -> {}
                        else -> {
                            IconButton(onClick = {
                                viewModel.getState()
                            }) {
                                Icon(Icons.Outlined.Refresh, stringResource(R.string.content_description_refresh))
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
                    MainOverview(viewModel.model!!.period)
                    HorizontalDivider()
                    DailyOverview(viewModel.model!!.daily)
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
fun MainOverview(values: DashboardViewModel.UsageValues) {

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

@Composable
fun DailyOverview(values: List<DashboardViewModel.UsageValues>) {

    if (values.isEmpty()) return

    var dayIndex by remember(values.size) { mutableIntStateOf(values.lastIndex) }
    val index = dayIndex.coerceIn(0, values.lastIndex)

    Column(
        modifier = Modifier.padding(16.dp, 24.dp),
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
            modifier = Modifier.fillMaxSize(),
            values = values.map { it.used.bytes },
            labels = values.map {
                it.date.toInstant().atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM"))
            },
            limits = values.map { it.limit.bytes }) { index ->
            dayIndex = index
        }
    }

}