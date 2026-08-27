package com.videototem.servermanager.core

import android.content.Context
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

object SshManager {

    fun ports(cfg: Config): List<Int> =
        cfg.sshPorts.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }

    fun probe(port: Int, host: String = "127.0.0.1"): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 400) }
        true
    } catch (_: Exception) {
        false
    }

    fun listening(cfg: Config, host: String = "127.0.0.1"): List<Int> =
        ports(cfg).filter { probe(it, host) }

    suspend fun ensure(ctx: Context, cfg: Config): Status {
        if (!cfg.sshEnabled) return Status(State.UNKNOWN, "supervisión desactivada")
        val up = listening(cfg)
        if (up.isNotEmpty()) return Status(State.OK, "escuchando en puerto(s) ${up.joinToString(", ")}")

        if (!cfg.autoFix) return Status(State.FAIL, "SSH caído (ningún puerto escuchando)")

        val t = TermuxEnv(cfg)
        // 1) sshd de Termux (puerto 8022)
        TermuxCommander.runCommand(ctx, "${t.envPrelude()} command -v sshd >/dev/null && sshd; true")
        delay(3000)
        val up2 = listening(cfg)
        if (up2.isNotEmpty()) return Status(State.OK, "sshd de Termux iniciado :${up2.first()}")

        // 2) sshd de Ubuntu (22) en sesión persistente
        TermuxCommander.runCommand(
            ctx,
            t.ubuntuBackground("service ssh start 2>/dev/null || /usr/sbin/sshd 2>/dev/null; sleep infinity")
        )
        delay(5000)
        val up3 = listening(cfg)
        if (up3.isNotEmpty()) return Status(State.OK, "sshd de Ubuntu iniciado :${up3.first()}")

        return Status(
            State.FAIL,
            "ningún sshd disponible. Instala: Termux 'pkg install openssh' o Ubuntu 'apt install openssh-server'"
        )
    }
}
