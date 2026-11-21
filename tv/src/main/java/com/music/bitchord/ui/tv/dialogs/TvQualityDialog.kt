package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.tvInitialFocus

/**
 * Streaming quality ceiling for one connection type: Lossless, High, Medium or
 * Low. Lossless is the default and asks the addons first on every play.
 */
@Composable
fun TvQualityDialog(wifi: Boolean, onDismiss: () -> Unit) {
    val wifiQuality by AppSettings.audioQualityWifi.collectAsState()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsState()
    val current = if (wifi) wifiQuality else cellularQuality

    TvDialog(
        title = if (wifi) "Quality on Wi-Fi" else "Quality on Mobile Data",
        message = "Lossless asks your addons first on every song. Lower choices skip them to save data.",
        width = 560.dp,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AudioQuality.entries.reversed().forEach { quality ->
                TvListRow(
                    title = quality.label,
                    subtitle = quality.detail,
                    trailingIcon = if (quality == current) Icons.Rounded.Check else null,
                    modifier = if (quality == current) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        if (wifi) AppSettings.setAudioQualityWifi(quality)
                        else AppSettings.setAudioQualityCellular(quality)
                        onDismiss()
                    },
                )
            }
        }
    }
}
