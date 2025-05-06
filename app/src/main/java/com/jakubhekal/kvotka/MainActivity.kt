package com.jakubhekal.kvotka

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.PermissionManager
import com.jakubhekal.kvotka.screens.NavGraphs
import com.jakubhekal.kvotka.screens.appCurrentDestinationAsState
import com.jakubhekal.kvotka.screens.destinations.AppListScreenDestination
import com.jakubhekal.kvotka.screens.destinations.DashboardScreenDestination
import com.jakubhekal.kvotka.screens.destinations.SettingsScreenDestination
import com.jakubhekal.kvotka.theme.DataUsageTheme
import com.jakubhekal.kvotka.viewmodels.AppListViewModel
import com.jakubhekal.kvotka.viewmodels.DashboardViewModel
import com.jakubhekal.kvotka.viewmodels.SettingsViewModel
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class MainActivity : ComponentActivity() {

    enum class BottomBarDestination(
        val direction: DirectionDestinationSpec,
        val icon: ImageVector,
        val label: String
    ) {
        Dashboard(DashboardScreenDestination, Icons.Outlined.Dashboard, "Dashboard"),
        AppList(AppListScreenDestination, Icons.Outlined.Apps, "Apps"),
        Settings(SettingsScreenDestination, Icons.Outlined.Settings, "Settings"),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, UsageNotificationService::class.java)
        this.startForegroundService(intent)

        setContent {
            DataUsageTheme {
                val navController = rememberNavController()
                val destinationNavigator = navController.rememberDestinationsNavigator()
                val currentDestination = navController.appCurrentDestinationAsState().value

                val context = this as Context
                val permissionManager = PermissionManager(context)
                val networkUsageManager = NetworkUsageManager(context)
                val packageManager = context.packageManager

                val dashboardViewModel = viewModel { DashboardViewModel(context.dataStore, networkUsageManager) }
                val appListViewModel = viewModel { AppListViewModel(context.dataStore, networkUsageManager, packageManager) }
                val settingsViewModel = viewModel { SettingsViewModel(context.dataStore, permissionManager) }

                var state by remember { mutableStateOf(permissionManager.hasUsageStatsPermission() && permissionManager.hasPostNotificationsPermission()) }

                LifecycleResumeEffect(Unit) {
                    state = permissionManager.hasUsageStatsPermission() && permissionManager.hasPostNotificationsPermission()
                    onPauseOrDispose {  }
                }

                if(!state) {
                    AlertDialog(
                        title = { Text("Missing permission") },
                        text = { Text("Grant this application access to read usage stats and post notifications. We required this permissions to read your phone data usage and display your data usage.") },
                        onDismissRequest = { },
                        confirmButton = {
                            TextButton(onClick = {
                                permissionManager.requestUsageStatsPermission()
                                permissionManager.requestPostNotificationsPermission()
                            }) {
                                Text("Grant")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            BottomBarDestination.entries.forEach {
                                NavigationBarItem(
                                    icon = { Icon(it.icon, contentDescription = it.label) },
                                    label = { Text(it.label) },
                                    selected = currentDestination == it.direction,
                                    onClick = {
                                        destinationNavigator.navigate(it.direction)
                                    })
                            }
                        }
                    }
                ) { innerPadding ->
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                        dependenciesContainerBuilder = {
                            dependency(DashboardScreenDestination) { dashboardViewModel }
                            dependency(AppListScreenDestination) { appListViewModel }
                            dependency(SettingsScreenDestination) { settingsViewModel }
                        }
                    )
                }
            }
        }
    }
}