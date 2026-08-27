package com.videototem.servermanager.core

import android.content.Context
import com.videototem.servermanager.log.LogStore
import kotlinx.coroutines.delay

data class UninstallOption(
    val id: String,
    val title: String,
    val description: String,
    val checkedDefault: Boolean = true
)

object Uninstaller {

    fun options(): List<UninstallOption> = listOf(
        UninstallOption("pm2_services", "Servicios PM2", "pm2 delete all + pm2 kill (detiene dashboard/monitor/etc)", true),
        UninstallOption("pm2_dump", "Dump PM2", "borra /root/.pm2 y dump.pm2 (resurrect vacío)", true),
        UninstallOption("ubuntu", "Ubuntu (proot-distro)", "borra el contenedor entero — requiere reinstalar Ubuntu después", false),
        UninstallOption("logs", "Logs y temporales", "~/server-manager, /tmp/srv.js, /sdcard/pdl.txt", true),
        UninstallOption("ssh", "Detener SSH", "pkill sshd (puerto 8022/22)", false),
        UninstallOption("node_pm2", "Node + PM2 (Ubuntu)", "apt remove nodejs y npm uninstall pm2 (opcional)", false),
    )

    suspend fun uninstall(ctx: Context, cfg: Config, ids: Set<String>): String {
        val t = TermuxEnv(cfg)
        val results = mutableListOf<String>()
        LogStore.append("uninstall", "iniciado: ${ids.joinToString(",")}")

        if ("pm2_services" in ids) {
            TermuxCommander.runCommand(ctx, t.ubuntuBackground("pm2 delete all 2>/dev/null; pm2 kill 2>/dev/null; sleep 2; true"))
            delay(4000)
            val still = TermuxCommander.query(ctx, t.ubuntuQuery("pm2 jlist 2>/dev/null | head -c 100"))
            results += if (still?.contains("[]") == true || still?.contains("online") == false) "PM2 detenido" else "PM2 delete enviado"
        }
        if ("pm2_dump" in ids) {
            TermuxCommander.runCommand(ctx, t.ubuntuBackground("rm -rf /root/.pm2 2>/dev/null; true"))
            delay(3000)
            results += "dump PM2 borrado"
        }
        if ("logs" in ids) {
            TermuxCommander.runCommand(ctx, "rm -rf ${t.logDir} 2>/dev/null; rm -rf /tmp/srv.js /sdcard/pdl.txt /sdcard/srv.log /sdcard/jlist.txt 2>/dev/null; true")
            RootShell.exec("rm -rf /data/local/tmp/termux.apk /data/local/tmp/tailscale.apk 2>/dev/null; true")
            results += "logs limpiados"
        }
        if ("ssh" in ids) {
            RootShell.exec("pkill -9 sshd 2>/dev/null; true")
            TermuxCommander.runCommand(ctx, "pkill -9 sshd 2>/dev/null; true")
            delay(2000)
            results += "sshd detenido"
        }
        if ("node_pm2" in ids) {
            TermuxCommander.runCommand(ctx, t.ubuntuBackground("npm uninstall -g pm2 2>/dev/null; apt-get remove -y nodejs npm 2>/dev/null; true"))
            delay(8000)
            results += "Node/PM2 desinstalados de Ubuntu"
        }
        if ("ubuntu" in ids) {
            TermuxCommander.runCommand(ctx, "proot-distro remove ubuntu 2>&1 | head -n 20")
            delay(5000)
            val gone = TermuxCommander.query(ctx, "${t.prootDistro} list 2>/dev/null")?.contains("* ${cfg.distro}") != true
            if (!gone) {
                TermuxCommander.runCommand(ctx, "rm -rf ${t.prefix}/var/lib/proot-distro/installed-rootfs/${cfg.distro} 2>/dev/null; true")
                delay(3000)
            }
            results += "Ubuntu eliminado (verifica con Analizar)"
        }

        LogStore.append("uninstall", results.joinToString(" | "))
        return results.joinToString(" | ").ifBlank { "nada seleccionado" }
    }
}
