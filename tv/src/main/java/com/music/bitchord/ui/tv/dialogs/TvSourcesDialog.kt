package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvDialogButton
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvTextField
import com.music.bitchord.ui.tv.components.tvInitialFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The phone app's Sources screen, for the remote: the sources BitChord asks for
 * a track, in the order it asks, with each one's on/off switch and — for an addon
 * that offers several — its audio quality (Hi-Res, Lossless, ...).
 *
 * Everything here goes through [SourceRegistry], the same store the phone app
 * and the playback layer use, so a source set up here is the one playback
 * resolves against. Nothing about addon sign-in is handled here: whether an
 * addon trusts this device is [com.music.bitchord.data.sources.addon.AddonAuth]'s
 * business, and that runs underneath exactly as it does on the phone.
 */
@Composable
fun TvSourcesDialog(onDismiss: () -> Unit) {
    val configs by SourceRegistry.configs.collectAsState()
    var opened by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    val target = opened?.let { id -> configs.firstOrNull { it.id == id } }

    when {
        adding -> TvAddAddonDialog(onDismiss = { adding = false })
        target != null -> TvSourceDetailDialog(config = target, onDismiss = { opened = null })
        else -> TvDialog(
            title = "Sources",
            message = "Where BitChord looks for each song, in this order. Select a source to turn it on or off, or to pick its audio quality.",
            width = 560.dp,
            onDismissRequest = onDismiss,
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 304.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(configs, key = { it.id }) { config ->
                    TvListRow(
                        title = config.displayName,
                        subtitle = sourceSubtitle(config),
                        value = if (config.enabled) "On" else "Off",
                        modifier = Modifier.tvInitialFocus(enabled = config.id == configs.firstOrNull()?.id),
                        onClick = { opened = config.id },
                    )
                }
                item(key = "add") {
                    TvListRow(
                        title = "Add Addon",
                        subtitle = "Connect a source by its address.",
                        leadingIcon = Icons.Rounded.Add,
                        onClick = { adding = true },
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            TvDialogButton(text = "Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun TvSourceDetailDialog(config: SourceConfig, onDismiss: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }

    TvDialog(
        title = config.displayName,
        message = sourceSubtitle(config),
        width = 512.dp,
        onDismissRequest = onDismiss,
    ) {
        TvListRow(
            title = if (config.enabled) "Turn Off" else "Turn On",
            modifier = Modifier.tvInitialFocus(),
            onClick = { SourceRegistry.setEnabled(config.id, !config.enabled) },
        )
        if (config.qualityOptions.size > 1) {
            Spacer(modifier = Modifier.height(5.dp))
            // One row per choice, so every option is a single press away and the
            // current one is visible without a second dialog.
            config.qualityOptions.forEach { option ->
                TvListRow(
                    title = option.label,
                    subtitle = "Audio quality",
                    value = if (config.activeQuality?.value == option.value) "Selected" else null,
                    onClick = {
                        // Off the stored config, not the one this dialog opened with:
                        // a probe may have refreshed it since.
                        SourceRegistry.config(config.id)?.let {
                            SourceRegistry.update(it.copy(pickedQuality = option.value))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
        if (config.kind.isUserAdded) {
            if (confirmRemove) {
                TvDialogButton(
                    text = "Remove — Select Again to Confirm",
                    onClick = {
                        SourceRegistry.remove(config.id)
                        onDismiss()
                    },
                )
            } else {
                TvListRow(
                    title = "Remove",
                    destructive = true,
                    onClick = { confirmRemove = true },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        TvDialogButton(text = "Back", onClick = onDismiss)
    }
}

@Composable
private fun TvAddAddonDialog(onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // The same three steps the phone's editor takes: identify what is at the
    // address, refuse a duplicate, check that it answers, then store it. A
    // source whose format is known is kept even if it is asleep right now.
    fun save() {
        if (busy || address.isBlank()) return
        busy = true
        status = null
        scope.launch {
            val identified = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.identify(address.trim()) }
                    .getOrElse { Result.failure(it) }
            }
            val found = identified.getOrNull()
            if (found == null) {
                status = identified.exceptionOrNull()?.message ?: "That address isn't a source BitChord recognises."
                busy = false
                return@launch
            }
            SourceRegistry.duplicateOf(found.baseUrl)?.let { existing ->
                status = "${existing.displayName} is already added."
                busy = false
                return@launch
            }
            val health = withContext(Dispatchers.IO) {
                runCatching { SourceRegistry.probeCandidate(found) }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
            SourceRegistry.add(found)
            busy = false
            if (health.isOk) {
                onDismiss()
            } else {
                // Saved, but say why it isn't answering rather than closing on it.
                status = "Added, but it isn't answering yet: " + when (health) {
                    is SourceHealth.Rejected -> health.reason
                    is SourceHealth.Unreachable -> health.reason
                    else -> "unknown"
                }
            }
        }
    }

    TvDialog(
        title = "Add Addon",
        message = "Enter the address of the addon. Typing uses the TV keyboard; you can also paste it from the phone app.",
        width = 560.dp,
        onDismissRequest = onDismiss,
    ) {
        TvTextField(
            value = address,
            onValueChange = { address = it },
            placeholder = "https://",
            modifier = Modifier.tvInitialFocus(),
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.None,
            onImeAction = ::save,
        )
        status?.let {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Text(text = it, style = com.music.bitchord.ui.tv.theme.TvType.Body)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TvDialogButton(
            text = if (busy) "Checking…" else "Test and Add",
            enabled = !busy && address.isNotBlank(),
            onClick = ::save,
        )
        Spacer(modifier = Modifier.height(6.dp))
        TvDialogButton(text = "Cancel", onClick = onDismiss)
    }
}

/** What a source row says under its name: its kind, chosen quality, and anything stopping it. */
private fun sourceSubtitle(config: SourceConfig): String = listOfNotNull(
    config.kind.label,
    config.activeQuality?.label,
    "Needs a lossless output".takeIf { config.awaitsLosslessOutput },
    "Outdated addon".takeIf { config.needsAddonUpdate },
).joinToString(" · ")
