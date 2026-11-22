package com.fix.morvanium

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import java.util.Timer
import java.util.TimerTask

class MainPlugin(context: PluginContext) : SpwPlugin(context) {

    private var watchdogTimer: Timer? = null
    private var lastTrackSignature: String = ""
    private var lastPosition: Long = -1L
    private var lastDuration: Long = -1L
    private var stalledTicks: Int = 0
    private var refreshedForCurrentTrack: Boolean = false

    override fun start() {
        println("[MorvaniumFix] Plugin iniciado.")
        watchdogTimer = Timer("MorvaniumSyncWatchdog", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    checkAndSyncPlayback()
                }
            }, 1500L, 800L)
        }
    }

    override fun stop() {
        watchdogTimer?.cancel()
        watchdogTimer = null
        println("[MorvaniumFix] Plugin encerrado.")
    }

    private fun checkAndSyncPlayback() {
        try {
            val apiClass = Class.forName("com.xuncorp.spw.workshop.api.WorkshopApi")
            val apiInstance = try {
                apiClass.getDeclaredField("INSTANCE").get(null)
            } catch (_: Throwable) {
                null
            }

            val controller = findPlaybackController(apiClass, apiInstance) ?: return

            val currentTrack = invokeGetter(controller, "getCurrentMediaItem", "getMediaItem", "getCurrentTrack", "getPlayingItem")?.toString() ?: ""
            val currentPos = (invokeGetter(controller, "getCurrentPosition", "getPosition", "getProgress") as? Number)?.toLong() ?: -1L
            val totalDur = (invokeGetter(controller, "getDuration", "getTotalDuration") as? Number)?.toLong() ?: -1L
            val isPlaying = (invokeGetter(controller, "isPlaying", "getPlaying") as? Boolean) ?: true

            if (!isPlaying) return

            if (currentTrack.isNotEmpty() && currentTrack != lastTrackSignature) {
                lastTrackSignature = currentTrack
                refreshedForCurrentTrack = false
                stalledTicks = 0

                Timer("MorvaniumTrackRefresh", true).schedule(object : TimerTask() {
                    override fun run() {
                        val durNow = (invokeGetter(controller, "getDuration", "getTotalDuration") as? Number)?.toLong() ?: -1L
                        val posNow = (invokeGetter(controller, "getCurrentPosition", "getPosition", "getProgress") as? Number)?.toLong() ?: -1L
                        if (!refreshedForCurrentTrack && (durNow == lastDuration || posNow <= 500L)) {
                            triggerSeekRefresh(controller, posNow)
                            refreshedForCurrentTrack = true
                        }
                        lastDuration = durNow
                    }
                }, 1200L)
                return
            }

            if (currentPos >= 0 && currentPos == lastPosition) {
                stalledTicks++
                if (stalledTicks >= 2 && !refreshedForCurrentTrack) {
                    triggerSeekRefresh(controller, currentPos)
                    refreshedForCurrentTrack = true
                    stalledTicks = 0
                }
            } else {
                stalledTicks = 0
            }

            lastPosition = currentPos
            if (totalDur > 0) lastDuration = totalDur

        } catch (_: Throwable) {
        }
    }

    private fun triggerSeekRefresh(controller: Any, currentPos: Long) {
        try {
            val target = if (currentPos > 150L) currentPos else 150L
            val seekMethod = controller.javaClass.methods.firstOrNull {
                (it.name == "seekTo" || it.name == "seek") && it.parameterCount == 1
            }
            seekMethod?.invoke(controller, target)
            println("[MorvaniumFix] Playback ressincronizado em ${target}ms")
        } catch (_: Throwable) {
        }
    }

    private fun findPlaybackController(apiClass: Class<*>, apiInstance: Any?): Any? {
        val methods = if (apiInstance != null) apiInstance.javaClass.methods else apiClass.methods
        val getter = methods.firstOrNull {
            it.parameterCount == 0 && (it.name.contains("Playback", true) || it.name.contains("Player", true) || it.name.contains("Controller", true))
        } ?: return null

        return try {
            getter.invoke(apiInstance)
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeGetter(target: Any, vararg names: String): Any? {
        for (name in names) {
            val m = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            if (m != null) {
                return try {
                    m.invoke(target)
                } catch (_: Throwable) {
                    null
                }
            }
        }
        return null
    }
}
