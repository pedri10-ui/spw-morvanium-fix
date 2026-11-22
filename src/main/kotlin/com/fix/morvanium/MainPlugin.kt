package com.fix.morvanium

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.WorkshopApi
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
        println("[MorvaniumFix] Plugin iniciado. Inspecionando API...")
        logAvailableApiMethods()

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

    private fun logAvailableApiMethods() {
        try {
            val apiClass = WorkshopApi::class.java
            println("[MorvaniumFix] WorkshopApi methods: " + apiClass.methods.joinToString { it.name })
        } catch (e: Exception) {
            println("[MorvaniumFix] Erro ao inspecionar WorkshopApi: ${e.message}")
        }
    }

    private fun checkAndSyncPlayback() {
        try {
            val apiInstance = getWorkshopApiInstance() ?: return
            val controller = findPlaybackController(apiInstance) ?: return

            val currentTrack = invokeGetter(controller, "getCurrentMediaItem", "getMediaItem", "getCurrentTrack", "getPlayingItem")?.toString() ?: ""
            val currentPos = (invokeGetter(controller, "getCurrentPosition", "getPosition", "getProgress") as? Number)?.toLong() ?: -1L
            val totalDur = (invokeGetter(controller, "getDuration", "getTotalDuration") as? Number)?.toLong() ?: -1L
            val isPlaying = (invokeGetter(controller, "isPlaying", "getPlaying") as? Boolean) ?: true

            if (!isPlaying) return

            // Detecta mudança de faixa (inclusive via Morvanium)
            if (currentTrack.isNotEmpty() && currentTrack != lastTrackSignature) {
                lastTrackSignature = currentTrack
                refreshedForCurrentTrack = false
                stalledTicks = 0

                // Aguarda 1.2s após a troca e força sincronização caso a duração tenha ficado presa na faixa anterior
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

            // Detecta se os segundos congelaram durante reprodução ativa
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

        } catch (_: Exception) {
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
        } catch (e: Exception) {
            println("[MorvaniumFix] Falha no seekTo: ${e.message}")
        }
    }

    private fun getWorkshopApiInstance(): Any? {
        return try {
            WorkshopApi.INSTANCE
        } catch (_: Throwable) {
            try {
                WorkshopApi::class.java.getDeclaredField("INSTANCE").get(null)
            } catch (_: Throwable) {
                WorkshopApi::class.java
            }
        }
    }

    private fun findPlaybackController(api: Any): Any? {
        if (api is Class<*>) {
            val getter = api.methods.firstOrNull { it.name.contains("Playback", true) || it.name.contains("Player", true) }
            return getter?.invoke(null)
        }
        val getter = api.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.name.contains("Playback", true) || it.name.contains("Player", true) || it.name.contains("Controller", true))
        }
        return getter?.invoke(api) ?: api
    }

    private fun invokeGetter(target: Any, vararg names: String): Any? {
        for (name in names) {
            val m = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            if (m != null) return m.invoke(target)
        }
        return null
    }
}
