package com.rk.taskmanager.screens.ram

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.utils.FormatUtils
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import com.rk.commons.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rk.taskmanager.ProcessViewModel

val ramGraphHandler = GraphDataHandler(seriesCount = 2)

var RamUsage by mutableIntStateOf(0)
var usedRam by mutableLongStateOf(0L)
var totalRam by mutableLongStateOf(0L)

var SwapUsage by mutableIntStateOf(0)
var usedSwap by mutableLongStateOf(0L)
var totalSwap by mutableLongStateOf(0L)

suspend fun getSystemRamUsage(context: Context): Int = withContext(Dispatchers.IO) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)

    totalRam = info.totalMem
    val available = info.availMem
    usedRam = totalRam - available

    ((usedRam.toDouble() / totalRam.toDouble()) * 100)
        .toInt()
        .coerceIn(0, 100)
}

suspend fun updateRamAndSwapGraph(usagePercent: Int, usageBytes: Long, totalBytes: Long) {
    val ramUsage = getSystemRamUsage(TaskManager.requireContext())

    RamUsage = ramUsage
    usedSwap = usageBytes
    totalSwap = totalBytes
    SwapUsage = usagePercent

    ramGraphHandler.update(ramUsage, usagePercent) {
        selectedscreen.intValue == 0 && navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route
    }
}

// Вспомогательная функция выполнения Root оболочки (su)
fun runSuCommand(cmd: String): String {
    return runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        process.outputStream.write("\nexit\n".toByteArray())
        process.outputStream.flush()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotEmpty()) output else "Успешно"
        } else {
            val error = process.errorStream.bufferedReader().readText().trim()
            "Ошибка ($exitCode): $error"
        }
    }.getOrElse { "Ошибка: Root-доступ не предоставлен" }
}

@Composable
fun RAM(modifier: Modifier = Modifier, viewModel: ProcessViewModel) {
    var swappinessValue by remember { mutableStateOf("Чтение...") }
    var swappinessInput by remember { mutableStateOf("60") }
    var zramSizeInput by remember { mutableStateOf("4") } // Размер ZRAM в ГБ по умолчанию
    var rootLogStatus by remember { mutableStateOf("Ожидание действий") }

    // Асинхронно считываем текущий swappiness через Root при открытии вкладки
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val result = runSuCommand("cat /proc/sys/vm/swappiness")
            if (result.startsWith("Ошибка")) {
                swappinessValue = "Требуется Root"
            } else {
                swappinessValue = result
                swappinessInput = result
            }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        val ramColor = MaterialTheme.colorScheme.primary
        val swapColor = MaterialTheme.colorScheme.tertiary

        UsageChart(
            modelProducer = ramGraphHandler.modelProducer,
            lineColors = listOf(ramColor, swapColor),
            modifier = modifier
        )

        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                stringResource(
                    strings.ram_label,
                    FormatUtils.formatBytes(usedRam),
                    FormatUtils.formatBytes(totalRam),
                    RamUsage
                ),
                color = ramColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    strings.swap_label,
                    FormatUtils.formatBytes(usedSwap),
                    FormatUtils.formatBytes(totalSwap),
                    SwapUsage
                ),
                color = swapColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()

            // ИНТЕРФЕЙС КАРТОЧКИ СИСТЕМНОГО СТАТУСА SWAP
            Text("Параметры ядра подкачки:", style = MaterialTheme.typography.titleSmall, color = ramColor)
            InfoCard {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoItem(label = "Интенсивность подкачки (Swappiness)", value = swappinessValue, highlighted = swappinessValue != "Требуется Root")
                    InfoItem(label = "Результат выполнения Root-команд", value = rootLogStatus, highlighted = rootLogStatus != "Ожидание действий")
                }
            }

            HorizontalDivider()

            // ПАНЕЛЬ Root-УПРАВЛЕНИЯ SWAP И ZRAM
            Text("Управление файлом подкачки (Root):", style = MaterialTheme.typography.titleMedium, color = ramColor)

            // 1. Поле для ручного ввода Swappiness
            OutlinedTextField(
                value = swappinessInput,
                onValueChange = { input ->
                    // Проверяем диапазон от 10 до 100 или пустое значение для ввода
                    if (input.isEmpty() || (input.toIntOrNull() != null && input.toInt() in 10..100)) {
                        swappinessInput = input
                    }
                },
                label = { Text("Интенсивность Swappiness (10-100%)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (swappinessInput.isNotEmpty()) {
                        rootLogStatus = runSuCommand("echo $swappinessInput > /proc/sys/vm/swappiness")
                        if (!rootLogStatus.startsWith("Ошибка")) swappinessValue = swappinessInput
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить выбранный Swappiness")
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))

            // 2. Поле для принудительного ресайза ZRAM
            OutlinedTextField(
                value = zramSizeInput,
                onValueChange = { zramSizeInput = it.filter { char -> char.isDigit() } },
                label = { Text("Принудительный размер ZRAM (в ГБ)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val gbValue = zramSizeInput.toLongOrNull()
                    if (gbValue != null && gbValue > 0) {
                        val sizeInBytes = gbValue * 1024L * 1024L * 1024L
                        // Пошаговый Root-скрипт переразметки блочного устройства zram0 ядра Linux
                        rootLogStatus = runSuCommand("swapoff /dev/block/zram0 && echo 1 > /sys/block/zram0/reset && echo $sizeInBytes > /sys/block/zram0/disksize && mkswap /dev/block/zram0 && swapon /dev/block/zram0")
                    } else {
                        rootLogStatus = "Некорректный размер"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Пересоздать и очистить ZRAM")
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))

            // 3. Широкая, безопасная кнопка полного отключения
            Button(
                onClick = {
                    rootLogStatus = runSuCommand("swapoff -a")
                    if (!rootLogStatus.startsWith("Ошибка")) swappinessValue = "Отключен"
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Полностью отключить все разделы Swap/ZRAM")
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}
