package com.videototem.servermanager.core

import android.content.Context
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import kotlinx.coroutines.delay

object ServerManager {

    suspend fun sessionAlive(cfg: Config): Pair<Boolean, String> {
        val r = RootShell.exec("pgrep -f ${Cmd.sh(cfg.serverStopPattern)}")
        return if (r.isSuccess) {
            val pids = r.out.joinToString(",").trim()
            true to "sesión Ubuntu activa (PID $pids)"
        } else {
            false to "sin sesión Ubuntu activa"
        }
    }

    suspend fun start(ctx: Context, cfg: Config): String {
        val t = TermuxEnv(cfg)
        val inner = cfg.serverStartCmd.ifBlank { "sleep infinity" }
        val ok = TermuxCommander.runCommand(ctx, t.ubuntuBackground(inner))
        delay(6000)
        val alive = sessionAlive(cfg).first
        return when {
            alive -> "Ubuntu lanzado (log: ${t.logDir}/server.log)"
            ok -> "comando enviado pero la sesión no aparece; revisa Termux"
            else -> "no se pudo enviar el comando a Termux (¿allow-external-apps?)"
        }
    }

    suspend fun stop(cfg: Config): String {
        val innerPattern = cfg.serverStartCmd.ifBlank { "sleep infinity" }.take(40)
        RootShell.exec(
            "pkill -9 -f ${Cmd.sh(cfg.serverStopPattern)}; sleep 1; " +
                "pkill -9 -f ${Cmd.sh(innerPattern)}; true"
        )
        return if (sessionAlive(cfg).first) "la sesión sigue activa, revisa el patrón de stop"
        else "sesión Ubuntu detenida"
    }

    suspend fun restart(ctx: Context, cfg: Config): String {
        stop(cfg)
        delay(1500)
        return start(ctx, cfg)
    }
}
