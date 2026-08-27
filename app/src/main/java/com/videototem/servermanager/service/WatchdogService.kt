package com.videototem.servermanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.videototem.servermanager.R
import com.videototem.servermanager.core.EnvSetup
import com.videototem.servermanager.core.HealthChecker
import com.videototem.servermanager.core.Pm2Manager
import com.videototem.servermanager.core.RootShell
import com.videototem.servermanager.core.ServerManager
import com.videototem.servermanager.core.Settings
import com.videototem.servermanager.core.SshManager
import com.videototem.servermanager.core.TailscaleManager
import com.videototem.servermanager.core.TermuxEnv
import com.videototem.servermanager.log.LogStore
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchdogService : Service() {

    companion object {
        const val CHANNEL_ID = "watchdog"
        const val NOTIF_ID = 42
        var running = false
            private set

        private const val EXTRA_ENV_MODE = "env_mode"
        private const val EXTRA_ENV_ID = "env_id"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, WatchdogService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WatchdogService::class.java))
        }

        fun runEnvTask(ctx: Context, mode: String, id: String? = null) {
            val i = Intent(ctx, WatchdogService::class.java).apply {
                putExtra(EXTRA_ENV_MODE, mode)
                if (id != null) putExtra(EXTRA_ENV_ID, id)
            }
            ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notifManager: NotificationManager
    private var lastPm2Names: Set<String>? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(svcIntent: Intent?, flags: Int, startId: Int): Int {
        val eMode = svcIntent?.getStringExtra(EXTRA_ENV_MODE)
        if (eMode != null) {
            val eId = svcIntent.getStringExtra(EXTRA_ENV_ID)
            scope.launch { runEnv(mode = eMode, id = eId) }
            return START_NOT_STICKY
        }
        if (!running) {
            createChannel()
            startForeground(NOTIF_ID, buildNotification("iniciando watchdog..."))
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ServerManager::Watchdog")
                .also { it.acquire() }
            running = true
            LogStore.append("watchdog", "servicio iniciado")
            scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
        LogStore.append("watchdog", "servicio detenido")
        super.onDestroy()
    }

    private suspend fun runEnv(mode: String, id: String?) {
        try {
            val cfg = Settings.load(this)
            when (mode) {
                "env_analyze" -> {
                    val r = EnvSetup.analyze(this, cfg)
                    r.items.forEach { LogStore.append("entorno", "${it.id}: ${it.state} — ${it.detail.take(90)}") }
                    LogStore.append("entorno", "sshUser=${r.sshUser} ip=${r.tailscaleIp}")
                }
                "env_fix" -> {
                    if (id != null) LogStore.append("env", "fix[$id] → ${EnvSetup.fix(this, cfg, id)}")
                }
                "env_prepare" -> {
                    LogStore.append("env", EnvSetup.prepareAll(this, cfg))
                }
            }
        } catch (e: Exception) {
            LogStore.append("env", "ERROR: ${e.message}")
        } finally {
            if (!running) stopSelf()
        }
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val cfg = Settings.load(this)
            runCycle(cfg)
            delay((cfg.intervalSec.coerceAtLeast(10) * 1000L))
        }
    }

    private suspend fun runCycle(cfg: com.videototem.servermanager.core.Config) {
        val ctx = this
        val parts = mutableListOf<String>()

        if (!RootShell.rootAvailable()) {
            update("root NO disponible", State.FAIL)
            LogStore.append("watchdog", "root no disponible")
            return
        }
        parts.add("root ok")

        val ts = TailscaleManager.ensure(cfg)
        parts.add("tailscale:${ts.state}")
        LogStore.append("tailscale", ts.detail)

        val ssh = SshManager.ensure(ctx, cfg)
        parts.add("ssh:${ssh.state}")
        LogStore.append("ssh", ssh.detail)

        val (alive, _) = ServerManager.sessionAlive(cfg)
        if (!alive && cfg.autoFix) {
            val res = ServerManager.start(ctx, cfg)
            LogStore.append("server", "auto-arranque: $res")
            delay(5000)
        }
        val (alive2, detail2) = ServerManager.sessionAlive(cfg)
        parts.add("ubuntu:${if (alive2) "OK" else "FAIL"}")
        LogStore.append("server", detail2)

        if (alive2 || cfg.autoFix) {
            val pm2 = Pm2Manager.ensure(ctx, cfg)
            parts.add("pm2:${pm2.status.state}")
            LogStore.append("pm2", pm2.status.detail)
            val names = pm2.procs.map { it.name }.toSet()
            lastPm2Names?.let { prev ->
                (names - prev).forEach { LogStore.append("pm2", "servicio añadido: $it") }
                (prev - names).forEach { LogStore.append("pm2", "servicio eliminado: $it") }
            }
            lastPm2Names = names
        } else {
            parts.add("pm2:WARN")
        }

        val health = HealthChecker.check(cfg)
        if (health.state != State.UNKNOWN) {
            parts.add("health:${health.state}")
            LogStore.append("health", health.detail)
        }

        val worst = parts.map { it.substringAfter(":") }
        val global = when {
            worst.contains("FAIL") -> State.FAIL
            worst.contains("WARN") -> State.WARN
            else -> State.OK
        }
        update(parts.joinToString("  "), global)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Watchdog del servidor",
                NotificationManager.IMPORTANCE_LOW
            )
            notifManager = getSystemService(NotificationManager::class.java)
            notifManager.createNotificationChannel(ch)
        } else {
            notifManager = getSystemService(NotificationManager::class.java)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle("Server Manager · watchdog")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun update(text: String, state: State) {
        notifManager.notify(NOTIF_ID, buildNotification(text))
    }
}
