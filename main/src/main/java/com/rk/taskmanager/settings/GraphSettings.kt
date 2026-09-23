package com.rk.taskmanager.settings

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.commons.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.commons.strings
import kotlin.math.abs

@Composable
fun GraphSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.graph)){
        val minFreq = 150f
        val maxFreq = 1000f

        // Точки жесткого прилипания
        val snapPoints = listOf(150f, 250f, 500f, 750f, 1000f)

        var sliderValue by rememberSaveable {
            mutableFloatStateOf(Settings.updateFrequency.toFloat().coerceIn(minFreq, maxFreq))
        }

        PreferenceGroup {
            PreferenceTemplate(title = { Text(stringResource(strings.graph_update)) }) {
                Text(stringResource(strings.ms_unit, sliderValue.toInt()))
            }
            PreferenceTemplate(title = {}) {
                Slider(
                    value = sliderValue,
                    valueRange = minFreq..maxFreq,
                    onValueChange = { rawValue ->
                        // Ищем ближайшую точку прилипания
                        val closestSnap = snapPoints.minByOrNull { abs(it - rawValue) } ?: rawValue

                        // Если палец близко (в радиусе 25 мс) — магнитим. Если далеко — даем свободный ход (например, 315 мс)
                        sliderValue = if (abs(closestSnap - rawValue) < 25f) closestSnap else rawValue

                        // Сразу обновляем частоту для плавности интерфейса на лету
                        Settings.updateFrequency = sliderValue.toInt()
                    },
                    onValueChangeFinished = {
                        Settings.updateFrequency = sliderValue.toInt()
                    }
                )
            }
        }
    }
}
