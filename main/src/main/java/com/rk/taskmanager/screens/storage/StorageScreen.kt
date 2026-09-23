package com.rk.taskmanager.screens.storage

import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.utils.FormatUtils
import java.io.File

private data class FileSystemInfo(
    val device: String,
    val mountPoint: String,
    val fileSystem: String,
    val total: Long,
    val used: Long,
    val free: Long
)

private fun readFileSystems(): List<FileSystemInfo> {
    val ignored = setOf("proc", "sysfs", "tmpfs", "devtmpfs", "devpts", "cgroup", "cgroup2", "selinuxfs", "overlay")
    return runCatching {
        File("/proc/mounts").readLines().mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size < 3 || parts[2] in ignored) return@mapNotNull null
            val mount = parts[1].replace("\\040", " ").replace("\\011", "\t")
            val stat = runCatching { StatFs(mount) }.getOrNull() ?: return@mapNotNull null
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            if (total <= 0L) return@mapNotNull null
            FileSystemInfo(parts[0], mount, parts[2], total, total - free, free)
        }.distinctBy { it.mountPoint }
    }.getOrDefault(emptyList())
}


@Composable
fun StorageScreen(modifier: Modifier = Modifier) {
    val fileSystems = remember { readFileSystems() }

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

        if (fileSystems.isEmpty()) {
            InfoCard { InfoItem("Состояние", "Блочные устройства недоступны") }
        } else {
            fileSystems.forEach { fs ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = fs.mountPoint,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${fs.device} · ${fs.fileSystem}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        InfoItem("Использовано", FormatUtils.formatBytes(fs.used), highlighted = fs.used > fs.total * 0.9)
                        InfoItem("Свободно", FormatUtils.formatBytes(fs.free), highlighted = false)
                        InfoItem("Всего", FormatUtils.formatBytes(fs.total), highlighted = false)
                    }
                }
            }
        }
    }
}
