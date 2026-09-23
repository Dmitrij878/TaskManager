package com.rk.taskmanager.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rk.bridge.bridge
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.components.ProcessSearchBar
import com.rk.taskmanager.daemon.isConnected
import com.rk.taskmanager.daemon.startDaemon
import com.rk.taskmanager.screens.gpu.GpuViewModel
import com.rk.commons.settings.Settings
import com.rk.commons.strings
import com.rk.taskmanager.settings.SettingsRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.rk.taskmanager.screens.battery.Battery
import com.rk.taskmanager.screens.battery.updateBatteryStatsOnly
import com.rk.taskmanager.screens.network.Network
import com.rk.taskmanager.screens.network.updateNetworkStatsOnly




var selectedscreen = mutableIntStateOf(if (Settings.defaultToProcessScreen) 1 else 0)
var showFilter = mutableStateOf(false)
var showSort = mutableStateOf(false)

fun Context.openAppSettings() {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, navController: NavController, viewModel: ProcessViewModel, gpuViewModel: GpuViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var scanCounter by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            if (isConnected) {
                launch(Dispatchers.IO) {
                    // 1. Графики обновляем моментально каждый тик (хоть каждые 150 мс)
                    com.rk.taskmanager.screens.network.updateNetworkStatsOnly()
                    com.rk.taskmanager.screens.battery.updateBatteryStatsOnly(context)

                    // 2. Тяжелый топ процессов вызываем строго раз в 5 тиков
                    if (scanCounter >= 5) {
                        com.rk.taskmanager.screens.network.calculateTopApps(context)
                        com.rk.taskmanager.screens.battery.calculateTopBatteryApps(context)
                        scanCounter = 0
                    } else {
                        scanCounter++
                    }
                }
            }
            delay(com.rk.commons.settings.Settings.updateFrequency.toLong())
        }
    }
    if (isConnected) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                if (selectedscreen.intValue == 0 || selectedscreen.intValue == 3 || selectedscreen.intValue == 4) {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    when (selectedscreen.intValue) {
                                        3 -> "Мониторинг сети"
                                        4 -> "Состояние батареи"
                                        else -> stringResource(strings.app_name)
                                    }
                                )
                            },
                            actions = {
                                IconButton(
                                    enabled = bridge != null,
                                    modifier = Modifier.padding(8.dp),
                                    onClick = {
                                        fun Activity.requestNotificationPermissionOrOpenSettings() {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

                                            if (ContextCompat.checkSelfPermission(
                                                    this,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                return
                                            }

                                            val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
                                            val requestedBefore = prefs.getBoolean("notification_requested", false)

                                            if (!requestedBefore) {
                                                prefs.edit {
                                                    putBoolean("notification_requested", true)
                                                }
                                                MainActivity.instance?.notificationPermissionLauncher?.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            } else {
                                                Toast.makeText(context, "Please grant notification permission to use this feature", Toast.LENGTH_LONG).show()
                                                openAppSettings()
                                            }
                                        }

                                        (context as? Activity)?.requestNotificationPermissionOrOpenSettings()

                                        // ВЫРЕЗАЕМ ПРОВЕРКУ ПРЕМИУМА ДЛЯ УВЕДОМЛЕНИЙ
                                        if (bridge != null) {
                                            if (bridge!!.isNotificationServiceRunning().value) {
                                                bridge?.stopNotificationService(context)
                                            } else {
                                                bridge?.launchNotificationService(context)
                                            }
                                        }
                                    }) {
                                    Icon(
                                        imageVector = if (bridge == null) {
                                            Icons.Filled.Notifications
                                        } else {
                                            if (bridge!!.isNotificationServiceRunning().value) {
                                                Icons.Filled.NotificationsOff
                                            } else {
                                                Icons.Filled.Notifications
                                            }
                                        },
                                        contentDescription = null
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.padding(8.dp),
                                    onClick = {
                                        navController.navigate(SettingsRoutes.Settings.route)
                                    }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                } else if (selectedscreen.intValue == 1) {
                    ProcessSearchBar(viewModel = viewModel, navController = navController)
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedscreen.intValue == 0,
                        onClick = { selectedscreen.intValue = 0 },
                        icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Ресурсы") }
                    )
                    NavigationBarItem(
                        selected = selectedscreen.intValue == 1,
                        onClick = { selectedscreen.intValue = 1 },
                        icon = { Icon(imageVector = Icons.Filled.DeveloperBoard, contentDescription = null) },
                        label = { Text("Процессы") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedscreen.intValue) {
                    0 -> ResourceHostScreen(viewModel = viewModel, gpuViewModel = gpuViewModel)
                    1 -> Processes(viewModel = viewModel, navController = navController)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            var loading by remember { mutableStateOf(false) }
            if (loading) {
                LinearProgressIndicator()
            } else {
                IconButton(onClick = {
                    loading = true
                    coroutineScope.launch {
                        startDaemon(context, mode = 0)
                        loading = false
                    }
                }) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start Daemon")
                }
            }
        }
    }
}
