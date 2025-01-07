package com.jakubhekal.kvotka.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    AppList((appListViewModel.uiState as AppListUiState.Success).appList)
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
fun AppList(appList: List<AppListViewModel.AppValues>) {
    LazyColumn {
        items(appList) { app ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {}
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
                        bitmap = app.icon,
                        contentDescription = app.name
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            style = MaterialTheme.typography.bodyLarge,
                            text = app.name
                        )
                        Text(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            text = app.bytes.pretty.toString()
                        )
                    }
                }

            }
        }
    }
}