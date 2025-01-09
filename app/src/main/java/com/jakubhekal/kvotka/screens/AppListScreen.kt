package com.jakubhekal.kvotka.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.jakubhekal.kvotka.viewmodels.AppListUiState
import com.jakubhekal.kvotka.viewmodels.AppListViewModel
import com.ramcosta.composedestinations.annotation.Destination


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Destination
@Composable
fun AppListScreen(appListViewModel: AppListViewModel) {

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Apps") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        appListViewModel.getState()
                    }) {
                        Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                }

            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            when(appListViewModel.uiState) {
                is AppListUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AppListUiState.Success -> {

                    var currentAppIndex by remember { mutableStateOf(0) }
                    var showBottomSheet by remember { mutableStateOf(false) }

                    AppList(
                        (appListViewModel.uiState as AppListUiState.Success).appList,
                        onClickItem = { index ->
                            currentAppIndex = index
                            showBottomSheet = true
                        }
                    )

                    if (showBottomSheet) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                showBottomSheet = false
                            }
                        ) {
                            val app by remember { mutableStateOf((appListViewModel.uiState as AppListUiState.Success).appList[currentAppIndex]) }
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
                                            text = "received"
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
                                            text = "sent"
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
                                            text = "Total ${app.used.total.pretty}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is AppListUiState.Error -> {
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
                        Button(onClick = { appListViewModel.getState() }) {
                            Text(text = "Try again")
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            style = MaterialTheme.typography.bodyLarge,
                            text = app.name
                        )
                        Text(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            text = app.used.total.pretty.toString()
                        )
                    }
                }

            }
        }
    }
}