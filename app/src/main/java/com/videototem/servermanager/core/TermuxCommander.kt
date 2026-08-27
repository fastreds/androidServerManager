package com.videototem.servermanager.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout

object TermuxCommander {

    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val SERVICE = "com.termux/com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"

    fun runCommand(ctx: Context, command: String, background: Boolean = true): Boolean {
        val intent = Intent(ACTION).apply {
            component = ComponentName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }
        return try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
            true
        } catch (e: Exception) {
            try {
                ctx.startService(intent)
                true
            } catch (e2: Exception) {
                amFallback(command, background)
            }
        }
    }

    /** Ejecuta como root via am (no le afecta SELinux) */
    private fun amFallback(command: String, background: Boolean): Boolean = try {
        val b64 = Base64.getEncoder().encodeToString(command.toByteArray())
        val arg = "echo $b64 | base64 -d | bash"
        val p = ProcessBuilder("su", "-c",
            "am startservice --user 0 -n $SERVICE -a $ACTION " +
                "--es com.termux.RUN_COMMAND_PATH $BASH " +
                "--esa com.termux.RUN_COMMAND_ARGUMENTS '-c,$arg' " +
                "--ez com.termux.RUN_COMMAND_BACKGROUND $background"
        ).start()
        p.waitFor()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Canal de consulta: la app abre un socket en localhost, Termux ejecuta el comando
     * y devuelve su salida via bash /dev/tcp. Sin permisos de almacenamiento.
     */
    suspend fun query(ctx: Context, command: String, timeoutMs: Long = 25000): String? =
        withContext(Dispatchers.IO) {
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress("127.0.0.1", 0))
                    val port = server.localPort
                    val job = async {
                        Thread.sleep(600)
                        runCommand(ctx, "($command) > /dev/tcp/127.0.0.1/$port 2>&1")
                    }
                    server.soTimeout = timeoutMs.toInt()
                    try {
                        val sock: Socket = server.accept()
                        val txt = sock.getInputStream().bufferedReader().use { it.readText() }
                        txt
                    } finally {
                        job.cancel()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
}
