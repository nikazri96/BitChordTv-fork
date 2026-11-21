package com.music.bitchord.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.session.MediaController
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.QualityUpgrade
import com.music.bitchord.playback.upgradeQuality
import kotlinx.coroutines.delay

/**
 * While the quality ceiling is Lossless, asks for the "Upgrade Quality" step by
 * itself whenever the track playing is still YouTube's own copy: a few seconds
 * after it starts, then again twice more a little later. It is the same request
 * the song menu's button makes, so nothing is forced that the button couldn't.
 */
@Composable
fun TvAutoLossless(mediaController: MediaController?) {
    val wifi by AppSettings.audioQualityWifi.collectAsState()
    val cellular by AppSettings.audioQualityCellular.collectAsState()
    val metered by AppSettings.meteredConnection.collectAsState()
    val wantsLossless = (if (metered == true) cellular else wifi) == AudioQuality.LOSSLESS
    val controller = mediaController ?: return
    if (!wantsLossless) return

    LaunchedEffect(controller) {
        var lastId: String? = null
        while (true) {
            val id = controller.currentMediaItem?.mediaId
            if (id != null && id != lastId) {
                lastId = id
                for (wait in longArrayOf(1_500, 10_000, 25_000)) {
                    delay(wait)
                    if (controller.currentMediaItem?.mediaId != id) break
                    if (id in NerdStats.racingLossless.value) continue
                    if (!controller.playingYouTubesOwn(id)) break
                    controller.upgradeQuality()
                }
            }
            delay(1_500)
        }
    }
}

private fun MediaController.playingYouTubesOwn(videoId: String): Boolean {
    val item = currentMediaItem?.takeIf { it.mediaId == videoId } ?: return false
    val uri = item.localConfiguration?.uri
    return QualityUpgrade.isKnownToBePlayingYouTubesOwn(
        videoId,
        uri?.let(QualityUpgrade::cacheTag),
        uri?.let(AudioCache::originOf),
    )
}
