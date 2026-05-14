package com.jakubhekal.kvotka.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jakubhekal.kvotka.R
import com.jakubhekal.kvotka.viewmodels.AppListViewModel
import com.jakubhekal.kvotka.viewmodels.GenericViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Destination<RootGraph>
@Composable
fun AppListScreen(viewModel: AppListViewModel) {

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var showFilterBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.screen_name_apps)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    when (viewModel.state) {
                        GenericViewModel.State.LOADING, GenericViewModel.State.UPDATING -> {}
                        else -> {
                            IconButton(onClick = {
                                viewModel.getState()
                            }) {
                                Icon(Icons.Outlined.Refresh,
                                    stringResource(R.string.content_description_refresh)
                                )
                            }
                        }
                    }
                }

            )
        },
        floatingActionButton = {
            if(viewModel.state != GenericViewModel.State.LOADING) {
                FloatingActionButton(
                    onClick = { showFilterBottomSheet = true },
                ) {
                    Icon(Icons.Outlined.FilterAlt,
                        stringResource(R.string.content_description_filters)
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), bottom = 0.dp)
        ) {
            when(viewModel.state) {
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

                    var currentAppIndex by remember { mutableStateOf(0) }
                    var showAppDetailBottomSheet by remember { mutableStateOf(false) }

                    AppList(
                        appList = viewModel.model!!.apps,
                        onClickItem = { index ->
                            currentAppIndex = index
                            showAppDetailBottomSheet = true
                        }
                    )
                    if (showFilterBottomSheet) {
                        var filters by remember { mutableStateOf(viewModel.model!!.filters) }
                        ModalBottomSheet(
                            onDismissRequest = {
                                showFilterBottomSheet = false
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp, 16.dp, 24.dp, 24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                    text = stringResource(R.string.filter_name_period)
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppListViewModel.Filters.Period.entries.forEach { filter ->
                                        FilterChip(
                                            onClick = { filters = filters.copy(interval = filter) },
                                            label = {
                                                Text(stringResource(filter.titleRes))
                                            },
                                            selected = filters.interval == filter,
                                        )
                                    }
                                }

                                Text(
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                    text = stringResource(R.string.filter_name_order_by)
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppListViewModel.Filters.OrderBy.entries.forEach { filter ->
                                        FilterChip(
                                            onClick = {
                                                filters = if (filters.orderBy == filter) {
                                                    filters.copy(orderBy = filter, orderAsc = !filters.orderAsc)
                                                } else {
                                                    filters.copy(orderBy = filter, orderAsc = false)
                                                }
                                            },
                                            label = {
                                                Text(stringResource(filter.titleRes))
                                            },
                                            leadingIcon = {
                                                if(filters.orderBy == filter) {
                                                    if(filters.orderAsc) {
                                                        Icon(
                                                            modifier = Modifier.size(16.dp),
                                                            imageVector = Icons.Outlined.ArrowUpward,
                                                            contentDescription = stringResource(R.string.content_description_ascending)
                                                        )
                                                    } else {
                                                        Icon(
                                                            modifier = Modifier.size(16.dp),
                                                            imageVector = Icons.Outlined.ArrowDownward,
                                                            contentDescription = stringResource(R.string.content_description_descending)
                                                        )
                                                    }
                                                }
                                            },
                                            selected = filters.orderBy == filter,
                                        )
                                    }
                                }
                                
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        viewModel.setState(viewModel.model!!.copy(filters = filters))
                                        showFilterBottomSheet = false
                                    }
                                ) {
                                    Text(text = stringResource(R.string.action_save))
                                }
                            }
                        }
                    } else if (showAppDetailBottomSheet) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                showAppDetailBottomSheet = false
                            }
                        ) {
                            val app by remember { mutableStateOf(viewModel.model!!.apps[currentAppIndex]) }
                            Column(
                                modifier = Modifier
                                    .padding(24.dp, 16.dp, 24.dp, 24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {

                                if(app.bitmap != null) {
                                    Image(
                                        modifier = Modifier.size(64.dp),
                                        bitmap = app.bitmap!!,
                                        contentDescription = app.name
                                    )
                                }

                                if(app.vector != null){
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            imageVector = app.vector!!,
                                            contentDescription = app.name
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {

                                    Text(
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.headlineMedium,
                                        text = app.name
                                    )

                                    if(app.packageName != null) {
                                        Text(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            text = app.packageName!!
                                        )
                                    }
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.primary,
                                            text = stringResource(R.string.label_received)
                                        )
                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.headlineSmall,
                                            text = app.used.rx.pretty.valueString
                                        )
                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.primary,
                                            text = app.used.rx.pretty.unitString
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.secondary,
                                            text = stringResource(R.string.label_sent)
                                        )

                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.headlineSmall,
                                            text = app.used.tx.pretty.valueString
                                        )

                                        Text(
                                            modifier = Modifier.alignByBaseline(),
                                            color = MaterialTheme.colorScheme.secondary,
                                            text = app.used.tx.pretty.unitString
                                        )
                                    }

                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .fillMaxWidth(),
                                        progress = { app.used.rx.bytes.toFloat() / (app.used.total.bytes.toFloat() + 1) },
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            color = MaterialTheme.colorScheme.outline,
                                            style = MaterialTheme.typography.bodyMedium,
                                            text = stringResource(
                                                R.string.label_value_total,
                                                app.used.total.pretty
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
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
fun AppList(
    appList: List<AppListViewModel.AppValues>,
    onClickItem: (index: Int) -> Unit = { _ -> }
) {
    LazyColumn {
        itemsIndexed(appList) { index, app ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickItem(index) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if(app.bitmap != null) {
                        Image(
                            modifier = Modifier.size(32.dp),
                            bitmap = app.bitmap,
                            contentDescription = app.name
                        )
                    }

                    if(app.vector != null){
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                imageVector = app.vector,
                                contentDescription = app.name
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            style = MaterialTheme.typography.bodyLarge,
                            text = app.name
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                text = app.used.total.pretty.toString()
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = stringResource(R.string.content_description_received),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                text = app.used.rx.pretty.toString()
                            )
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = stringResource(R.string.content_description_sent),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                text = app.used.tx.pretty.toString()
                            )
                        }

                    }
                }
            }
        }
    }
}