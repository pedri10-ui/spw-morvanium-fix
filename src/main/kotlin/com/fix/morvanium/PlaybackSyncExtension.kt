package com.fix.morvanium

import com.xuncorp.spw.workshop.api.PlaybackExtension
import com.xuncorp.spw.workshop.api.WorkshopApi
import org.pf4j.Extension
import java.util.Timer
import java.util.TimerTask

@Extension
class PlaybackSyncExtension : PlaybackExtension {

    private var lastTrackKey: String? = null
    private var lastReportedDuration: Long = -1L
    private var syncTimer: Timer? = null

    override fun onMediaChanged(mediaItem: PlaybackExtension.MediaItem?) {
        val currentKey = "${mediaItem?.title}-${mediaItem?.artist}-${mediaItem?.path}"
        if (currentKey == lastTrackKey) return
        lastTrackKey = currentKey

        syncTimer?.cancel()
        syncTimer = Timer("MorvaniumSyncWatchdog", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    forcePlaybackStateRefresh()
                }
            }, 1200L)
        }
    }

    private fun forcePlaybackStateRefresh() {
        try {
            val controller = WorkshopApi.playbackController
            val currentPos = controller.currentPosition
            val totalDur = controller.duration

            if (totalDur > 0 && (totalDur == lastReportedDuration || currentPos < 500L)) {
                val targetPos = if (currentPos > 100L) currentPos else 100L
                controller.seekTo(targetPos)
            }
            lastReportedDuration = totalDur
        } catch (e: Exception) {
            println("[MorvaniumFix] Falha ao ressincronizar estado: ${e.message}")
        }
    }
}
