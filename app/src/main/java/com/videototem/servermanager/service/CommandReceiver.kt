package com.videototem.servermanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.videototem.servermanager.core.Settings
import com.videototem.servermanager.core.TermuxCommander
import com.videototem.servermanager.core.TermuxEnv
import com.videototem.servermanager.log.LogStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * API externa de la app (para adb/automatizaciones):
 *   am broadcast -a com.videototem.servermanager.RUN \
 *     --es mode ubuntu_bg --es cmd "pm2 start ..."      (dentro de Ubuntu, background)
 *   am broadcast -a com.videototem.servermanager.RUN \
 *     --es mode raw --es cmd "termux-wake-lock"         (directo en Termux)
 */
class CommandReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != "com.videototem.servermanager.RUN") return
        var cmd = intent.getStringExtra("cmd")
        val mode = intent.getStringExtra("mode") ?: "ubuntu_bg"
        val b64 = intent.getStringExtra("b64")
        if (cmd.isNullOrBlank() && !b64.isNullOrBlank()) {
            cmd = try {
                String(java.util.Base64.getDecoder().decode(b64.trim()))
            } catch (_: Exception) {
                null
            }
        }
        LogStore.append("api", "recibido mode=$mode cmd=${cmd?.take(100) ?: "null"}")
        if (cmd.isNullOrBlank()) return
        val cfg = Settings.load(ctx)
        val t = TermuxEnv(cfg)
        val full = when (mode) {
            "raw" -> cmd
            else -> t.ubuntuBackground(cmd)
        }
        val pending = goAsync()
        GlobalScope.launch {
            try {
                when (mode) {
                    "raw" -> {
                        val ok = TermuxCommander.runCommand(ctx, cmd)
                        LogStore.append("api", if (ok) "enviado" else "fallo al enviar")
                    }
                    "raw_q" -> {
                        val out = TermuxCommander.query(ctx, cmd)
                        LogStore.append("api", "salida: ${out?.take(400) ?: "null"}")
                    }
                    "ubuntu_q" -> {
                        val out = TermuxCommander.query(ctx, t.ubuntuQuery(cmd))
                        LogStore.append("api", "salida: ${out?.take(400) ?: "null"}")
                    }
                    "env_analyze" -> {
                        try {
                            val cfg = Settings.load(ctx)
                            val r = com.videototem.servermanager.core.EnvSetup.analyze(ctx, cfg)
                            r.items.forEach { LogStore.append("entorno", "${it.id}: ${it.state} — ${it.detail.take(90)}") }
                            LogStore.append("entorno", "sshUser=${r.sshUser} ip=${r.tailscaleIp}")
                        } catch (e: Exception) {
                            LogStore.append("entorno", "ERROR: ${e.message}")
                        }
                    }
                    "env_fix" -> {
                        val id = intent.getStringExtra("id") ?: return@launch
                        val i = Intent(ctx, com.videototem.servermanager.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("env_action", "fix")
                            .putExtra("env_id", id)
                        ctx.startActivity(i)
                    }
                    "env_prepare" -> {
                        val i = Intent(ctx, com.videototem.servermanager.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("env_action", "prepare")
                        ctx.startActivity(i)
                    }
                    else -> {
                        val ok = TermuxCommander.runCommand(ctx, t.ubuntuBackground(cmd))
                        LogStore.append("api", if (ok) "enviado" else "fallo al enviar")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
