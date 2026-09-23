package com.rk.taskmanager.screens.storage

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.utils.FormatUtils


@Composable
fun StorageScreen(modifier: Modifier = Modifier) {
    // Используем встроенный StatFs для мгновенного чтения разделов без лагов
    val stat = remember { StatFs(Environment.getDataDirectory().path) }

    val totalBytes = remember(stat) { stat.blockCountLong * stat.blockSizeLong }
    val freeBytes = remember(stat) { stat.availableBlocksLong * stat.blockSizeLong }
    val usedBytes = remember(totalBytes, freeBytes) { totalBytes - freeBytes }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Мониторинг внутреннего накопителя:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        InfoCard {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoItem(
                    label = "Всего встроенной памяти",
                    value = FormatUtils.formatBytes(totalBytes),
                    highlighted = false
                )
                InfoItem(
                    label = "Использовано пространства",
                    value = FormatUtils.formatBytes(usedBytes),
                    highlighted = true
                )
                InfoItem(
                    label = "Свободно места на диске",
                    value = FormatUtils.formatBytes(freeBytes),
                    highlighted = false
                )
            }
        }
    }
}
