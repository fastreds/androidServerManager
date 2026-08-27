package com.videototem.servermanager.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.videototem.servermanager.log.LogStore
import com.videototem.servermanager.model.State
import kotlinx.coroutines.delay

data class EnvItem(
    val id: String,
    val title: String,
    val state: State,
    val detail: String,
    val canFix: Boolean
)

data class EnvReport(
    val items: List<EnvItem>,
    val sshUser: String? = null,
    val tailscaleIp: String? = null
) {
    fun byId(id: String) = items.firstOrNull { it.id == id }
    val rootOk: Boolean get() = byId("root")?.state == State.OK
}

object EnvSetup {

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    suspend fun analyze(ctx: Context, cfg: Config): EnvReport {
        val items = mutableListOf<EnvItem>()
        val t = TermuxEnv(cfg)

        val rootOk = RootShell.rootAvailable()
        items += EnvItem(
            "root", "Root (Magisk/KernelSU)",
            if (rootOk) State.OK else State.FAIL,
            if (rootOk) "shell root disponible — requisito cumplido"
            else "SIN ROOT: nada más funcionará. Concede acceso a esta app en Magisk/KernelSU",
            canFix = false
        )

        val termuxOk = isInstalled(ctx, "com.termux")
        items += EnvItem(
            "termux", "Termux (app)",
            if (termuxOk) State.OK else State.FAIL,
            if (termuxOk) "instalada" else "no instalada — instala Termux (F-Droid)",
            canFix = rootOk && !termuxOk
        )

        var chanOk = if (!termuxOk) false
        else TermuxCommander.query(ctx, "echo CHANNEL_OK")?.contains("CHANNEL_OK") == true
        if (!chanOk && termuxOk && RootShell.rootAvailable()) {
            RootShell.exec("mkdir -p /data/data/com.termux/files/home/.termux && echo 'allow-external-apps = true' >> /data/data/com.termux/files/home/.termux/termux.properties && chmod 644 /data/data/com.termux/files/home/.termux/termux.properties")
            kotlinx.coroutines.delay(1500)
            chanOk = TermuxCommander.query(ctx, "echo CHANNEL_OK")?.contains("CHANNEL_OK") == true
        }
        items += EnvItem(
            "channel", "Canal RUN_COMMAND",
            when { !termuxOk -> State.UNKNOWN; chanOk -> State.OK; else -> State.FAIL },
            when {
                !termuxOk -> "requiere Termux"
                chanOk -> "allow-external-apps activo, canal operativo"
                else -> "en Termux ejecuta: mkdir -p ~/.termux && echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"
            },
            canFix = false
        )

        val prootOk = chanOk &&
            TermuxCommander.query(ctx, "test -x ${t.prootDistro} && echo PD_OK")?.contains("PD_OK") == true
        items += EnvItem(
            "proot", "proot-distro (Termux)",
            when { !chanOk -> State.UNKNOWN; prootOk -> State.OK; else -> State.FAIL },
            when { !chanOk -> "requiere canal"; prootOk -> "instalado"; else -> "falta (pkg install proot-distro)" },
            canFix = chanOk && !prootOk
        )

        val rootfs = if (chanOk) TermuxCommander.query(
            ctx, "${t.prootDistro} list 2>/dev/null"
        ) else null
        val ubuntuOk = rootfs != null && Regex("""\*\s*${Regex.escape(cfg.distro)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(rootfs)
        items += EnvItem(
            "ubuntu", "Ubuntu (proot-distro)",
            when { !prootOk -> State.UNKNOWN; ubuntuOk -> State.OK; else -> State.FAIL },
            when { !prootOk -> "requiere proot-distro"; ubuntuOk -> "${cfg.distro} instalado"; else -> "falta (proot-distro install ${cfg.distro}, ~100MB)" },
            canFix = prootOk && !ubuntuOk
        )

        val nodeOut = if (ubuntuOk) TermuxCommander.query(ctx, t.ubuntuQuery("command -v node && node -v")) else null
        val nodeOk = nodeOut?.contains("node") == true
        items += EnvItem(
            "node", "Node.js (Ubuntu)",
            when { !ubuntuOk -> State.UNKNOWN; nodeOk -> State.OK; else -> State.FAIL },
            when {
                !ubuntuOk -> "requiere Ubuntu"
                nodeOk -> nodeOut!!.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "instalado"
                else -> "falta (apt install nodejs npm)"
            },
            canFix = ubuntuOk && !nodeOk
        )

        val pm2Ok = if (ubuntuOk) Pm2Manager.check(ctx, cfg).status.state != State.WARN else false
        val pm2Installed = if (ubuntuOk)
            TermuxCommander.query(ctx, t.ubuntuQuery("command -v pm2 && echo PM2_OK"))?.contains("PM2_OK") == true
        else false
        items += EnvItem(
            "pm2", "PM2 (Ubuntu)",
            when { !nodeOk -> State.UNKNOWN; pm2Installed -> State.OK; else -> State.FAIL },
            when { !nodeOk -> "requiere Node.js"; pm2Installed -> "instalado y operativo"; else -> "falta (npm i -g pm2)" },
            canFix = nodeOk && !pm2Installed
        )

        val dumpOk = if (pm2Installed) TermuxCommander.query(
            ctx, t.ubuntuQuery("test -f /root/.pm2/dump.pm2 && echo DUMP_OK")
        )?.contains("DUMP_OK") == true else false
        items += EnvItem(
            "dump", "Dump PM2 (persistencia resurrect)",
            when {
                !pm2Installed -> State.UNKNOWN
                dumpOk -> State.OK
                else -> State.WARN
            },
            when {
                !pm2Installed -> "requiere PM2"
                dumpOk -> "dump.pm2 presente"
                else -> "sin dump: ejecuta pm2 save (la app lo puede crear)"
            },
            canFix = pm2Installed && !dumpOk
        )

        val sshPorts = SshManager.listening(cfg)
        items += EnvItem(
            "ssh", "SSH escuchando",
            if (sshPorts.isNotEmpty()) State.OK else State.FAIL,
            if (sshPorts.isNotEmpty()) "puerto(s) ${sshPorts.joinToString(", ")}"
            else "ningún puerto (${cfg.sshPorts}) escuchando",
            canFix = chanOk && sshPorts.isEmpty()
        )

        val tsInstalled = isInstalled(ctx, cfg.tailscalePackage)
        val tsIp = TailscaleManager.detectIp()
        items += EnvItem(
            "tailscale", "Tailscale (app + conectado)",
            when { !tsInstalled -> State.FAIL; tsIp != null -> State.OK; else -> State.WARN },
            when {
                !tsInstalled -> "app no instalada"
                tsIp != null -> "conectado · $tsIp"
                else -> "app instalada pero sin IP (ábrela y conecta)"
            },
            canFix = !tsInstalled
        )

        val sshUser = if (chanOk)
            TermuxCommander.query(ctx, "whoami")?.trim()?.lines()?.lastOrNull { it.isNotBlank() }
        else null

        val report = EnvReport(items, sshUser, tsIp)
        report.items.forEach { LogStore.append("entorno", "${it.id}: ${it.state} — ${it.detail.take(100)}") }
        return report
    }

    private suspend fun poll(ctx: Context, cmd: String, needle: String, timeoutMs: Long, intervalMs: Long = 15000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            delay(intervalMs)
            if (TermuxCommander.query(ctx, cmd)?.contains(needle) == true) return true
        }
        return false
    }

    suspend fun fix(ctx: Context, cfg: Config, id: String): String {
        val t = TermuxEnv(cfg)
        LogStore.append("entorno", "fix[$id] iniciado")
        return when (id) {
            "termux" -> {
                if (RootShell.rootAvailable()) {
                    try {
                        val apkUrl = "https://f-droid.org/repo/com.termux_118.apk"
                        val tmp = java.io.File(ctx.cacheDir, "termux.apk")
                        val okHttp = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                        val req = okhttp3.Request.Builder().url(apkUrl).header("User-Agent", "androidServerManager").build()
                        okHttp.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful && resp.body != null) {
                                resp.body!!.byteStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                            }
                        }
                        if (tmp.exists() && tmp.length() > 1024 * 500) {
                            val pub = java.io.File("/data/local/tmp/termux.apk")
                            RootShell.exec("cp ${Cmd.sh(tmp.absolutePath)} ${Cmd.sh(pub.absolutePath)} && chmod 644 ${Cmd.sh(pub.absolutePath)}")
                            val r = RootShell.exec("pm install -r ${Cmd.sh(pub.absolutePath)} 2>&1")
                            if (r.isSuccess || r.out.any { it.contains("Success", ignoreCase = true) }) {
                                return "Termux instalado automáticamente — abre Termux una vez y vuelve a Analizar"
                            }
                        }
                    } catch (_: Exception) {}
                }
                openUrl(ctx, "https://f-droid.org/es/packages/com.termux/")
                "abierta la página de Termux (F-Droid). Si falla la auto-instalación, instálala manualmente y vuelve a Analizar"
            }
            "tailscale" -> {
                if (RootShell.rootAvailable()) {
                    try {
                        val apkUrl = "https://f-droid.org/repo/com.tailscale.ipn_180.apk"
                        val tmp = java.io.File(ctx.cacheDir, "tailscale.apk")
                        val okHttp = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                        val req = okhttp3.Request.Builder().url(apkUrl).build()
                        okHttp.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful && resp.body != null) resp.body!!.byteStream().use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                        }
                        if (tmp.exists() && tmp.length() > 1024 * 500) {
                            val pub = java.io.File("/data/local/tmp/tailscale.apk")
                            RootShell.exec("cp ${Cmd.sh(tmp.absolutePath)} ${Cmd.sh(pub.absolutePath)} && chmod 644 ${Cmd.sh(pub.absolutePath)}")
                            val r = RootShell.exec("pm install -r ${Cmd.sh(pub.absolutePath)} 2>&1")
                            if (r.isSuccess || r.out.any { it.contains("Success", ignoreCase = true) }) return "Tailscale instalado automáticamente"
                        }
                    } catch (_: Exception) {}
                }
                openUrl(ctx, "https://play.google.com/store/apps/details?id=${cfg.tailscalePackage}")
                "abierta la página de Tailscale en Play Store"
            }
            "proot" -> {
                TermuxCommander.runCommand(ctx, "pkg install -y proot-distro curl")
                val ok = poll(ctx, "test -x ${t.prootDistro} && echo PD_OK", "PD_OK", 120000)
                if (ok) "proot-distro instalado" else "instalación en curso o fallida; revisa Logs y re-analiza"
            }
            "ubuntu" -> {
                TermuxCommander.runCommand(
                    ctx,
                    "${t.envPrelude()} nohup ${t.prootDistro} install ${cfg.distro} >> ${t.logDir}/distro-install.log 2>&1 &"
                )
                val ok = poll(
                    ctx,
                    "${t.prootDistro} list 2>/dev/null",
                    "* ${cfg.distro}", 480000, 20000
                )
                if (ok) "Ubuntu instalado" else "instalación en curso (puede tardar varios minutos); re-analiza luego"
            }
            "node" -> {
                TermuxCommander.runCommand(
                    ctx,
                    t.ubuntuBackground("apt-get update -y; DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs npm")
                )
                val ok = poll(ctx, t.ubuntuQuery("command -v node && echo NODE_OK"), "NODE_OK", 360000, 20000)
                if (ok) "Node.js instalado en Ubuntu" else "instalación apt en curso; re-analiza luego"
            }
            "pm2" -> {
                TermuxCommander.runCommand(ctx, t.ubuntuBackground("npm install -g pm2"))
                val ok = poll(ctx, t.ubuntuQuery("command -v pm2 && echo PM2_OK"), "PM2_OK", 240000, 15000)
                if (ok) "PM2 instalado en Ubuntu" else "instalación npm en curso; re-analiza luego"
            }
            "dump" -> {
                TermuxCommander.runCommand(ctx, t.ubuntuBackground("pm2 ping >/dev/null 2>&1; pm2 save"))
                val ok = poll(ctx, t.ubuntuQuery("test -f /root/.pm2/dump.pm2 && echo DUMP_OK"), "DUMP_OK", 90000, 10000)
                if (ok) "dump.pm2 creado — PM2 listo para resurrect" else "no se pudo confirmar el dump"
            }
            "ssh" -> {
                TermuxCommander.runCommand(
                    ctx,
                    "${t.envPrelude()} pkg install -y openssh; sleep 2; command -v sshd && sshd; true"
                )
                val ok = poll(ctx, "true", "__NEVER__", 0, 1000).let { // placeholder no usado
                    false
                }
                // esperar puerto con sondeo local
                val start = System.currentTimeMillis()
                var up = false
                while (System.currentTimeMillis() - start < 90000 && !up) {
                    delay(5000)
                    up = SshManager.listening(cfg).isNotEmpty()
                }
                if (up) "sshd iniciado en ${SshManager.listening(cfg).first()}" else "sshd no arrancó; instala openssh en Termux"
            }
            else -> "paso desconocido: $id"
        }.also { LogStore.append("entorno", "fix[$id] → $it") }
    }

    /** Deja el entorno listo para PM2: instala lo que falte, en orden */
    suspend fun prepareAll(ctx: Context, cfg: Config): String {
        val report = analyze(ctx, cfg)
        if (!report.rootOk) return "BLOQUEADO: sin root. Concede acceso en Magisk y reintenta."
        val results = mutableListOf<String>()

        report.byId("termux")?.takeIf { it.state != State.OK }?.let {
            results += "termux: " + fix(ctx, cfg, "termux")
            return@prepareAll results.joinToString(" | ") + " — instala Termux manualmente y vuelve a ejecutar"
        }
        report.byId("channel")?.takeIf { it.state == State.FAIL }?.let {
            return "BLOQUEADO: canal RUN_COMMAND inactivo. " + it.detail
        }
        for (step in listOf("proot", "ubuntu", "node", "pm2", "dump", "ssh")) {
            val item = report.byId(step) ?: continue
            if (item.state == State.OK) continue
            if (!item.canFix) continue
            results += "$step: " + fix(ctx, cfg, step)
        }
        val final = analyze(ctx, cfg)
        val pending = final.items.filter { it.state == State.FAIL }.map { it.id }
        return if (pending.isEmpty())
            "ENTORNO LISTO PARA PM2 ✓ (" + results.joinToString(" | ").ifBlank { "todo ya estaba instalado" } + ")"
        else
            "completado con pendientes: ${pending.joinToString(", ")} — " + results.joinToString(" | ")
    }

    private fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            LogStore.append("entorno", "no se pudo abrir $url")
        }
    }
}
