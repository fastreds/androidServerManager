package com.videototem.servermanager.core

import android.content.Context
import com.videototem.servermanager.log.LogStore
import com.videototem.servermanager.model.Pm2Proc
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import kotlinx.coroutines.delay
import org.json.JSONArray

data class Pm2CheckResult(val status: Status, val procs: List<Pm2Proc>)

object Pm2Manager {

    private val ansi = Regex("""\u001B\[[0-9;]*m""")

    private suspend fun jlistRaw(ctx: Context, cfg: Config): String? {
        val t = TermuxEnv(cfg)
        val cmd = "command -v pm2 >/dev/null 2>&1 || export PATH=\$PATH:/usr/local/bin:/usr/bin:/root/.npm-global/bin; " +
            "command -v pm2 >/dev/null 2>&1 || exit 127; pm2 jlist"
        val raw = TermuxCommander.query(ctx, t.ubuntuQuery(cmd))
        LogStore.append("pm2", "jlist raw: ${raw?.take(200) ?: "null"}")
        return raw
    }

    /** null = sin canal / no instalado; lista (posiblemente vacía) = daemon respondió */
    private fun parse(raw: String?): List<Pm2Proc>? {
        if (raw == null) return null
        val clean = raw.replace(ansi, "")
        val arrText = when {
            clean.contains("[{") -> {
                val s = clean.indexOf("[{")
                val e = clean.lastIndexOf(']')
                if (e <= s) return null else clean.substring(s, e + 1)
            }
            clean.contains("[]") -> return emptyList()
            else -> return null
        }
        return try {
            val arr = JSONArray(arrText)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val env = o.optJSONObject("pm2_env")
                val monit = o.optJSONObject("monit")
                Pm2Proc(
                    name = o.optString("name", "?"),
                    status = env?.optString("status") ?: o.optString("status", "?"),
                    restarts = env?.optInt("restart_time", 0) ?: 0,
                    cpu = monit?.optDouble("cpu", 0.0) ?: 0.0,
                    memMb = (monit?.optLong("memory", 0) ?: 0) / (1024 * 1024)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun check(ctx: Context, cfg: Config): Pm2CheckResult {
        if (!cfg.pm2Enabled)
            return Pm2CheckResult(Status(State.UNKNOWN, "supervisión desactivada"), emptyList())
        val raw = jlistRaw(ctx, cfg)
        val procs = parse(raw)
        val status = when {
            procs != null && procs.isEmpty() ->
                Status(State.FAIL, "pm2 sin procesos registrados (daemon caído o sin dump)")
            procs != null -> {
                val bad = procs.filter { it.status != "online" }
                if (bad.isEmpty())
                    Status(State.OK, "${procs.size} servicio(s) online: ${procs.joinToString(", ") { it.name }}")
                else
                    Status(State.FAIL, "caído(s): ${bad.joinToString(", ") { "${it.name}(${it.status})" }}")
            }
            raw == null -> Status(State.WARN, "sin respuesta del canal Termux (¿allow-external-apps?)")
            raw.isBlank() -> Status(State.FAIL, "pm2 sin respuesta (daemon caído)")
            raw.contains("command not found") -> Status(State.WARN, "pm2 no instalado en Ubuntu (npm i -g pm2)")
            else -> Status(State.FAIL, "respuesta pm2 no válida: ${raw.take(80)}")
        }
        return Pm2CheckResult(status, procs ?: emptyList())
    }

    suspend fun ensure(ctx: Context, cfg: Config): Pm2CheckResult {
        val first = check(ctx, cfg)
        if (first.status.state == State.OK || !cfg.autoFix) return first
        val t = TermuxEnv(cfg)
        var fixed = false

        when {
            first.procs.isEmpty() -> {
                TermuxCommander.runCommand(
                    ctx,
                    t.ubuntuBackground("pm2 resurrect 2>/dev/null; pm2 start all 2>/dev/null; sleep 3; pm2 save 2>/dev/null; sleep infinity")
                )
                delay(10000)
                fixed = true
            }
            first.procs.any { it.status != "online" } -> {
                for (p in first.procs.filter { it.status != "online" }) {
                    TermuxCommander.runCommand(
                        ctx,
                        t.ubuntuQuery("pm2 restart ${Cmd.sh(p.name)} || pm2 start ${Cmd.sh(p.name)}; true")
                    )
                    delay(3000)
                }
                fixed = true
            }
            else -> return first
        }

        val after = check(ctx, cfg)
        if (fixed && after.status.state == State.OK) {
            // persistir el estado actual (incluye servicios añadidos) para futuros resurrect
            TermuxCommander.runCommand(ctx, t.ubuntuQuery("pm2 save 2>/dev/null; true"))
        }
        return after
    }

    suspend fun restartAll(ctx: Context, cfg: Config): String {
        val t = TermuxEnv(cfg)
        TermuxCommander.runCommand(ctx, t.ubuntuQuery("pm2 restart all; true"))
        delay(5000)
        return "pm2 restart all → ${check(ctx, cfg).status.detail}"
    }
}
