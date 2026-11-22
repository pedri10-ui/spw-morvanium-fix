package com.fix.morvanium

import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.PluginContext

class MainPlugin(context: PluginContext) : SpwPlugin(context) {
    override fun start() {
        println("[MorvaniumFix] Plugin iniciado.")
    }

    override fun stop() {
        println("[MorvaniumFix] Plugin encerrado.")
    }
}
