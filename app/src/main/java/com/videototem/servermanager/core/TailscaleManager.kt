package com.videototem.servermanager.core

import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status

object TailscaleManager {

    private val cgnatRegex =
        Regex("""inet (100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.[0-9]+\.[0-9]+)/""")

    suspend fun detectIp(): String? {
        val r = RootShell.exec("ip -o -4 addr show 2>/dev/null")
        return r.out.firstNotNullOfOrNull { line ->
            cgnatRegex.find(line)?.groupValues?.get(1)
        }
    }

    suspend fun check(cfg: Config): Status {
        if (!cfg.tailscaleEnabled) return Status(State.UNKNOWN, "supervisión desactivada")
        val ip = detectIp()
            ?: return Status(State.FAIL, "interfaz Tailscale inactiva (la app no está conectada)")
        val ping = RootShell.exec("ping -c 1 -W 3 $ip >/dev/null 2>&1")
        return if (ping.isSuccess) {
            Status(State.OK, "conectado · $ip")
        } else {
            Status(State.WARN, "IP $ip asignada pero sin respuesta")
        }
    }

    suspend fun tryStartApp(cfg: Config): String {
        val r = RootShell.exec("am start -n ${cfg.tailscalePackage}/.IPNActivity 2>&1")
        return if (r.isSuccess) "app de Tailscale relanzada" else "no se pudo lanzar: ${RootShell.resultText(r)}"
    }

    suspend fun ensure(cfg: Config): Status {
        val s = check(cfg)
        if (s.state == State.OK || !cfg.autoFix) return s
        tryStartApp(cfg)
        kotlinx.coroutines.delay(8000)
        return check(cfg)
    }
}
