package com.jakubhekal.kvotka

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.jakubhekal.kvotka.managers.NetworkUsageManager
import com.jakubhekal.kvotka.managers.PermissionManager
import com.jakubhekal.kvotka.managers.ShizukuManager
import com.jakubhekal.kvotka.theme.DataUsageTheme
import com.jakubhekal.kvotka.viewmodels.AppListViewModel
import com.jakubhekal.kvotka.viewmodels.DashboardViewModel
import com.jakubhekal.kvotka.viewmodels.SettingsViewModel
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.destinations.AppListScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DashboardScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.generated.navgraphs.RootNavGraph
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.utils.currentDestinationAsState

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class MainActivity : ComponentActivity() {

    enum class BottomBarDestination(
        val direction: DirectionDestinationSpec,
        val icon: ImageVector,
        @StringRes val labelRes: Int
    ) {
        Dashboard(DashboardScreenDestination, Icons.Outlined.Dashboard, R.string.label_dashboard),
        AppList(AppListScreenDestination, Icons.Outlined.Apps, R.string.screen_name_apps),
        Settings(SettingsScreenDestination, Icons.Outlined.Settings, R.string.screen_name_settings),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ForegroundService.start(this)

        setContent {
            DataUsageTheme {
                val navController = rememberNavController()
                val currentDestination = navController.currentDestinationAsState().value

                val context = this as Context
                val permissionManager = PermissionManager(context)
                val networkUsageManager = NetworkUsageManager(context)
                val shizukuManager = ShizukuManager()
                val packageManager = context.packageManager

                val dashboardViewModel = viewModel { DashboardViewModel(context.dataStore, networkUsageManager, shizukuManager) }
                val appListViewModel = viewModel { AppListViewModel(application,context.dataStore, networkUsageManager, packageManager) }
                val settingsViewModel = viewModel { SettingsViewModel(context.dataStore, permissionManager) }

                var hasUsagePermission by remember { mutableStateOf(permissionManager.hasUsageStatsPermission()) }
                var hasNotificationsPermission by remember { mutableStateOf(permissionManager.hasPostNotificationsPermission()) }

                LifecycleResumeEffect(Unit) {
                    hasUsagePermission = permissionManager.hasUsageStatsPermission()
                    hasNotificationsPermission = permissionManager.hasPostNotificationsPermission()
                    onPauseOrDispose {  }
                }

                if(!hasUsagePermission || !hasNotificationsPermission) {
                    AlertDialog(
                        title = {
                            Text(stringResource(if (!hasUsagePermission) R.string.setting_title_usage_data_permission else R.string.setting_title_post_notifications_permission))
                        },
                        text = {
                            Text(stringResource(if (!hasUsagePermission) R.string.setting_description_usage_data_permission else R.string.setting_description_post_notifications_permission))
                        },
                        onDismissRequest = { },
                        confirmButton = {
                            TextButton(onClick = {
                                if (!hasUsagePermission) {
                                    permissionManager.requestUsageStatsPermission()
                                } else {
                                    permissionManager.requestPostNotificationsPermission()
                                }
                            }) {
                                Text(stringResource(R.string.action_grant))
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
                                    icon = { Icon(it.icon, contentDescription = stringResource(it.labelRes)) },
                                    label = { Text(stringResource(it.labelRes)) },
                                    selected = currentDestination == it.direction,
                                    onClick = {
                                        navController.navigate(it.direction.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    })
                            }
                        }
                    }
                ) { innerPadding ->
                    DestinationsNavHost(
                        navGraph = RootNavGraph,
                        navController = navController,
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        dependenciesContainerBuilder = {
                            dependency(dashboardViewModel)
                            dependency(appListViewModel)
                            dependency(settingsViewModel)
                        }
                    )
                }
            }
        }
    }
}
