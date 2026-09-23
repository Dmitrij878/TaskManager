package com.rk.taskmanager.screens.network

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.utils.FormatUtils
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.settings.SettingsRoutes

val networkGraphHandler = GraphDataHandler(seriesCount = 2)

private var lastRxBytes = 0L
private var lastTxBytes = 0L
private var downloadSpeed by mutableLongStateOf(0L)
private var uploadSpeed by mutableLongStateOf(0L)

data class AppNetInfo(val appName: String, val totalBytes: Long, val currentSpeed: Long)
private var topTotalApps by mutableStateOf<List<AppNetInfo>>(emptyList())
private var topCurrentApps by mutableStateOf<List<AppNetInfo>>(emptyList())
private val lastAppBytesMap = mutableMapOf<Int, Long>()
private var hasUsagePermission by mutableStateOf(true)

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.noteOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

suspend fun updateNetworkStatsOnly() {
    val currentRxBytes = TrafficStats.getTotalRxBytes()
    val currentTxBytes = TrafficStats.getTotalTxBytes()

    if (lastRxBytes != 0L) {
        downloadSpeed = currentRxBytes - lastRxBytes
        uploadSpeed = currentTxBytes - lastTxBytes
    }

    lastRxBytes = currentRxBytes
    lastTxBytes = currentTxBytes

    val dlKb = (downloadSpeed / 1024).toInt().coerceAtLeast(0)
    val ulKb = (uploadSpeed / 1024).toInt().coerceAtLeast(0)

    networkGraphHandler.update(dlKb, ulKb) {
        navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route
    }
}

fun calculateTopApps(context: Context) {
    if (!checkUsageStatsPermission(context)) {
        hasUsagePermission = false
        return
    }
    hasUsagePermission = true

    val pm = context.packageManager
    val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val totalList = mutableListOf<AppNetInfo>()

    for (app in allApps) {
        if (app.uid == Process.INVALID_UID) continue
        runCatching {
            val rx = TrafficStats.getUidRxBytes(app.uid)
            val tx = TrafficStats.getUidTxBytes(app.uid)
            val total = if (rx > 0 || tx > 0) (rx + tx) else 0L

            if (total > 0L) {
                val appName = pm.getApplicationLabel(app).toString()
                val lastTotal = lastAppBytesMap[app.uid] ?: 0L
                val currentSpeed = if (lastTotal > 0L) (total - lastTotal).coerceAtLeast(0L) else 0L
                lastAppBytesMap[app.uid] = total

                totalList.add(AppNetInfo(appName, total, currentSpeed))
            }
        }
    }

    topTotalApps = totalList.sortedByDescending { it.totalBytes }.take(5)
    topCurrentApps = totalList.sortedByDescending { it.currentSpeed }.take(5)
}

@Composable
fun Network(modifier: Modifier = Modifier) {
    val context = TaskManager.requireContext()
    LaunchedEffect(Unit) {
        networkGraphHandler.refresh()
        hasUsagePermission = checkUsageStatsPermission(context)
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        UsageChart(
            modelProducer = networkGraphHandler.modelProducer,
            lineColors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
            modifier = modifier
        )

        Spacer(modifier = Modifier.padding(vertical = 4.dp))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()

            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoItem(label = "Входящая скорость", value = "${FormatUtils.formatBytes(downloadSpeed)}/с", highlighted = true)
                    InfoItem(label = "Исходящая скорость", value = "${FormatUtils.formatBytes(uploadSpeed)}/с", highlighted = false)
                    InfoItem(label = "Всего получено трафика", value = FormatUtils.formatBytes(lastRxBytes), highlighted = false)
                    InfoItem(label = "Всего отправлено трафика", value = FormatUtils.formatBytes(lastTxBytes), highlighted = false)
                }
            }

            if (!hasUsagePermission) {
                InfoCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Для вывода активности приложений требуется доступ к статистике использования устройства.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }) {
                            Text("Предоставить доступ в настройках")
                        }
                    }
                }
            } else {
                Text(text = "Активность сетевого трафика (Топ-5):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                InfoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (topCurrentApps.isEmpty()) {
                            Text(text = "Активный трафик в данный момент отсутствует", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                        } else {
                            topCurrentApps.forEach { app ->
                                InfoItem(label = app.appName, value = "${FormatUtils.formatBytes(app.currentSpeed)}/с", highlighted = true)
                            }
                        }
                    }
                }

                Text(text = "Суммарное потребление за сессию (Топ-5):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                InfoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (topTotalApps.isEmpty()) {
                            Text(text = "Данные сетевой статистики отсутствуют", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                        } else {
                            topTotalApps.forEach { app ->
                                InfoItem(label = app.appName, value = FormatUtils.formatBytes(app.totalBytes), highlighted = false)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}
