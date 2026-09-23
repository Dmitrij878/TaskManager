package com.rk.taskmanager.screens.cpu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CpuCoresScreen(modifier: Modifier = Modifier) {
    var coreFreqs by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            coreFreqs = withContext(Dispatchers.IO) {
                val freqs = mutableListOf<String>()
                var coreId = 0
                while (true) {
                    val file = File("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
                    if (file.exists()) {
                        runCatching {
                            val freqKhz = file.readText().trim().toLong()
                            freqs.add("${freqKhz / 1000} МГц")
                        }.onFailure { freqs.add("Офлайн") }
                        coreId++
                    } else {
                        break
                    }
                }
                freqs
            }
            delay(500)
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Мониторинг ядер процессора:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        InfoCard {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                coreFreqs.forEachIndexed { index, freq ->
                    InfoItem(label = "Ядро #$index", value = freq, highlighted = freq != "Офлайн")
                }
            }
        }
    }
}
