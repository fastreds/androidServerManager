package com.videototem.servermanager.core

import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object HealthChecker {

    fun check(cfg: Config): Status {
        if (!cfg.healthEnabled || cfg.healthPort <= 0) {
            return Status(State.UNKNOWN, "health-check desactivado (solo supervisión de proceso)")
        }
        return try {
            if (cfg.healthPath.isBlank()) tcp(cfg) else http(cfg)
        } catch (e: Exception) {
            Status(State.FAIL, "sin respuesta: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun tcp(cfg: Config): Status {
        Socket().use { s ->
            s.connect(InetSocketAddress(cfg.healthHost, cfg.healthPort), cfg.timeoutSec * 1000)
        }
        return Status(State.OK, "puerto ${cfg.healthPort} abierto")
    }

    private fun http(cfg: Config): Status {
        val client = OkHttpClient.Builder()
            .connectTimeout(cfg.timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(cfg.timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
        val url = "http://${cfg.healthHost}:${cfg.healthPort}${cfg.healthPath}"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        resp.use {
            return if (it.code in 200..499) Status(State.OK, "HTTP ${it.code} · $url")
            else Status(State.WARN, "HTTP ${it.code} · $url")
        }
    }
}
