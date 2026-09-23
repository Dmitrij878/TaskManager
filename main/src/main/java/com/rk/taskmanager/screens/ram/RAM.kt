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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import java.io.File

val ramGraphHandler = GraphDataHandler(seriesCount = 3)

var RamUsage by mutableIntStateOf(0)
var usedRam by mutableLongStateOf(0L)
var totalRam by mutableLongStateOf(0L)

var SwapUsage by mutableIntStateOf(0)
var usedSwap by mutableLongStateOf(0L)
var totalSwap by mutableLongStateOf(0L)
var ZramUsage by mutableIntStateOf(0)
var usedZram by mutableLongStateOf(0L)
var totalZram by mutableLongStateOf(0L)

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

suspend fun updateRamAndSwapGraph(
    zramUsagePercent: Int,
    zramUsageBytes: Long,
    zramTotalBytes: Long,
    swapUsagePercent: Int,
    swapUsageBytes: Long,
    swapTotalBytes: Long
) {
    val ramUsage = getSystemRamUsage(TaskManager.requireContext())

    RamUsage = ramUsage
    usedZram = zramUsageBytes
    totalZram = zramTotalBytes
    ZramUsage = zramUsagePercent
    usedSwap = swapUsageBytes
    totalSwap = swapTotalBytes
    SwapUsage = swapUsagePercent

    ramGraphHandler.update(ramUsage, zramUsagePercent, swapUsagePercent) {
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

fun ensureF2fsIo(): String {
    return runSuCommand("""
                target=/data/local/tmp/f2fs_io
                if [ ! -x ${'$'}target ]; then
                    for source in /data/local/tmp/f2fs_io /data/data/com.rk.taskmanager/files/f2fs_io /data/user/0/com.rk.taskmanager/files/f2fs_io; do
                        if [ -f ${'$'}source ]; then cp ${'$'}source ${'$'}target; chmod 755 ${'$'}target; break; fi
          done
        fi
                if [ -x ${'$'}target ]; then echo ${'$'}target; else echo unavailable; fi
    """.trimIndent())
}

fun prepareF2fsIo(context: Context): String {
    runCatching {
        val bundled = File(context.filesDir, "f2fs_io")
        if (!bundled.exists()) {
            context.assets.open("f2fs_io").use { input ->
                bundled.outputStream().use { output -> input.copyTo(output) }
            }
        }
        bundled.setExecutable(true)
    }
    return ensureF2fsIo()
}

private fun createSwapFile(sizeMb: Long): String {
    val sizeBytes = sizeMb * 1024L * 1024L
    val f2fs = ensureF2fsIo()
    val allocate = if (f2fs.startsWith("/data/local/tmp/f2fs_io")) {
        "$f2fs fallocate 0 $sizeBytes /data/swapfile"
    } else {
        "dd if=/dev/zero of=/data/swapfile bs=1M count=$sizeMb"
    }
    return runSuCommand("swapoff /data/swapfile 2>/dev/null; rm -f /data/swapfile; $allocate && chmod 600 /data/swapfile && mkswap /data/swapfile && swapon /data/swapfile")
}

private fun readSwapFileStatus(): String {
    val result = runSuCommand("cat /proc/swaps; if [ -f /data/swapfile ]; then stat -c '%s' /data/swapfile; else echo missing; fi")
    if (result.startsWith("Ошибка")) return "Требуется Root"
    val fileSize = result.lines().lastOrNull()?.trim()
    val active = result.lines().any { it.startsWith("/data/swapfile") }
    return when {
        fileSize == "missing" -> "Файл не создан"
        fileSize?.toLongOrNull() != null -> {
            val sizeMb = fileSize.toLong() / 1024 / 1024
            if (active) "Включен · $sizeMb МБ" else "Создан, но отключен · $sizeMb МБ"
        }
        else -> "Состояние неизвестно"
    }
}

@Composable
fun RAM(modifier: Modifier = Modifier, viewModel: ProcessViewModel) {
    var swappinessValue by remember { mutableStateOf("Чтение...") }
    var swappinessInput by remember { mutableStateOf("60") }
    var zramSizeInput by remember { mutableStateOf("4096") }
    var swapFileSizeInput by remember { mutableStateOf("1024") }
    var swapFileStatus by remember { mutableStateOf("Чтение...") }
    var rootLogStatus by remember { mutableStateOf("Ожидание действий") }

    // Асинхронно считываем текущий swappiness через Root при открытии вкладки
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val result = runSuCommand("cat /proc/sys/vm/swappiness")
            if (result.startsWith("Ошибка") || result.toIntOrNull() == null) {
                swappinessValue = "Требуется Root"
            } else {
                swappinessValue = result
                swappinessInput = result
            }
            swapFileStatus = readSwapFileStatus()
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        val ramColor = MaterialTheme.colorScheme.primary
        val swapColor = MaterialTheme.colorScheme.tertiary

        UsageChart(
            modelProducer = ramGraphHandler.modelProducer,
            lineColors = listOf(ramColor, MaterialTheme.colorScheme.secondary, swapColor),
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
            Text(
                "ZRAM: ${FormatUtils.formatBytes(usedZram)} / ${FormatUtils.formatBytes(totalZram)} ($ZramUsage%)",
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
            Text("Параметры подкачки и ZRAM:", style = MaterialTheme.typography.titleSmall, color = ramColor)
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
                    if (input.matches(Regex("\\d{0,3}")) && (input.isEmpty() || input.toInt() in 0..200)) {
                        swappinessInput = input
                    }
                },
                label = { Text("Интенсивность Swappiness (0-200)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (swappinessInput.isNotEmpty()) {
                        rootLogStatus = runSuCommand("echo $swappinessInput > /proc/sys/vm/swappiness && cat /proc/sys/vm/swappiness")
                        if (!rootLogStatus.startsWith("Ошибка")) {
                            swappinessValue = rootLogStatus.lines().last().trim()
                            swappinessInput = swappinessValue
                        }
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
                onValueChange = { input -> if (input.matches(Regex("\\d{0,3}"))) zramSizeInput = input },
                label = { Text("Размер ZRAM (МБ)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val sizeMb = zramSizeInput.toLongOrNull()
                    if (sizeMb != null && sizeMb > 0) {
                        val sizeInBytes = sizeMb * 1024L * 1024L
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

            OutlinedTextField(
                value = swapFileSizeInput,
                onValueChange = { input ->
                    if (input.matches(Regex("\\d{0,6}"))) swapFileSizeInput = input
                },
                label = { Text("Размер /data/swapfile (МБ)") },
                supportingText = { Text("Файл создаётся непрерывным через f2fs_io, если доступен") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            InfoCard {
                InfoItem(label = "Состояние /data/swapfile", value = swapFileStatus, highlighted = swapFileStatus.startsWith("Включен"))
            }

            Button(
                onClick = {
                    val sizeMb = swapFileSizeInput.toLongOrNull()
                    rootLogStatus = if (sizeMb != null && sizeMb >= 128) {
                        val result = createSwapFile(sizeMb)
                        swapFileStatus = readSwapFileStatus()
                        result
                    } else "Минимальный размер: 128 МБ"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать заново и включить swapfile")
            }

            Button(
                onClick = {
                    rootLogStatus = runSuCommand("swapon /data/swapfile")
                    swapFileStatus = readSwapFileStatus()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Включить существующий swapfile")
            }

            Button(
                onClick = {
                    rootLogStatus = runSuCommand("swapoff /data/swapfile")
                    swapFileStatus = readSwapFileStatus()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отключить swapfile")
            }

            Button(
                onClick = {
                    rootLogStatus = runSuCommand("swapoff /data/swapfile 2>/dev/null; rm -f /data/swapfile")
                    swapFileStatus = readSwapFileStatus()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Удалить swapfile")
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
