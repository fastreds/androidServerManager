package com.videototem.servermanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.videototem.servermanager.core.Config
import com.videototem.servermanager.core.EnvReport
import com.videototem.servermanager.core.EnvSetup
import com.videototem.servermanager.core.HealthChecker
import com.videototem.servermanager.core.Pm2CheckResult
import com.videototem.servermanager.core.Pm2Manager
import com.videototem.servermanager.core.RootShell
import com.videototem.servermanager.core.ServerManager
import com.videototem.servermanager.core.Settings
import com.videototem.servermanager.core.SshManager
import com.videototem.servermanager.core.TailscaleManager
import com.videototem.servermanager.core.TermuxCommander
import com.videototem.servermanager.core.TermuxEnv
import com.videototem.servermanager.log.LogStore
import com.videototem.servermanager.model.SshAccess
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import com.videototem.servermanager.model.UiState
import com.videototem.servermanager.service.WatchdogService
import com.videototem.servermanager.ui.Actions
import com.videototem.servermanager.ui.DashboardScreen
import com.videototem.servermanager.ui.EnvScreen
import com.videototem.servermanager.ui.LogsScreen
import com.videototem.servermanager.ui.SettingsScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val crashHandler = CoroutineExceptionHandler { _, e ->
        LogStore.append("error", "${e.javaClass.simpleName}: ${e.message ?: ""}")
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashHandler)

    private var cfg by mutableStateOf(Config())
    private var ui by mutableStateOf(UiState())
    private var watchdog by mutableStateOf(WatchdogService.running)
    private var tab by mutableStateOf(0)
    private var envReport by mutableStateOf<EnvReport?>(null)
    private var envBusy by mutableStateOf(false)
    private var envLastOp by mutableStateOf("")

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun maybeRequestRunCommandPermission() {
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"), 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Settings.load(this)
        setContent { App() }
        maybeRequestNotifPermission()
        maybeRequestRunCommandPermission()
        ioScope.launch { refresh() }
        handleEnvIntent(intent)
        checkUpdateIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEnvIntent(intent)
    }

    private fun handleEnvIntent(it: Intent?) {
        val action = it?.getStringExtra("env_action") ?: return
        tab = 1
        when (action) {
            "analyze" -> runEnv("Analizar entorno") {
                val r = EnvSetup.analyze(this@MainActivity, cfg)
                envReport = r
                "componentes: " + r.items.count { it.state == State.OK } + "/" + r.items.size + " OK"
            }
            "fix" -> {
                val id = it.getStringExtra("env_id") ?: return
                runEnv("Arreglar $id") { EnvSetup.fix(this@MainActivity, cfg, id) }
            }
            "prepare" -> runEnv("Preparar entorno") { EnvSetup.prepareAll(this@MainActivity, cfg) }
        }
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private suspend fun refresh() {
        LogStore.append("ui", "refresh iniciado")
        ui = ui.copy(busy = true)
        val c = cfg

        val rootOk = runCatching { RootShell.rootAvailable() }.getOrDefault(false)
        LogStore.append("ui", "root check: $rootOk")
        val root = if (rootOk) Status(State.OK, "shell root disponible")
        else Status(State.FAIL, "sin root: concede acceso desde Magisk/KernelSU")

        val t = TermuxEnv(c)
        val prootFound = TermuxCommander.query(this, "test -x ${t.prootDistro} && echo FOUND")
        val termux = when {
            prootFound?.contains("FOUND") == true ->
                Status(State.OK, "proot-distro presente en ${t.prefix}")
            prootFound != null ->
                Status(State.FAIL, "Termux accesible pero falta proot-distro en ${t.prefix}")
            else ->
                Status(State.WARN, "sin canal RUN_COMMAND (verifica allow-external-apps en Termux)")
        }
        LogStore.append("ui", "termux check: ${termux.state}")

        val ubuntu = if (rootOk && termux.state == State.OK) {
            val (alive, detail) = ServerManager.sessionAlive(c)
            Status(if (alive) State.OK else State.FAIL, detail)
        } else Status(State.UNKNOWN, "no comprobable sin root/Termux")

        val pm2Result = when {
            !c.pm2Enabled -> Pm2CheckResult(Status(State.UNKNOWN, "supervisión desactivada"), emptyList())
            else -> Pm2Manager.check(this, c)
        }
        LogStore.append("ui", "pm2 check: ${pm2Result.status.state} ${pm2Result.status.detail}")

        val tailscale = when {
            !c.tailscaleEnabled -> Status(State.UNKNOWN, "supervisión desactivada")
            rootOk -> TailscaleManager.check(c)
            else -> Status(State.UNKNOWN, "requiere root")
        }

        val ssh = when {
            !c.sshEnabled -> Status(State.UNKNOWN, "supervisión desactivada")
            else -> {
                val up = SshManager.listening(c)
                if (up.isNotEmpty()) Status(State.OK, "escuchando en ${up.joinToString(", ")}")
                else Status(State.FAIL, "ningún puerto (${c.sshPorts}) escuchando")
            }
        }

        val sshUser = TermuxCommander.query(this, "whoami")?.trim()?.lines()?.lastOrNull { it.isNotBlank() }
        val tsIp = TailscaleManager.detectIp()
        val sshAccess = SshAccess(
            host = tsIp ?: "sin IP Tailscale",
            port = SshManager.listening(c).firstOrNull()?.toString() ?: "—",
            user = sshUser ?: "u0_a305?",
            password = c.sshInfoPassword
        )


        val health = HealthChecker.check(c)
        watchdog = WatchdogService.running
        ui = UiState(
            root = root,
            termux = termux,
            ubuntu = ubuntu,
            pm2 = pm2Result.status,
            pm2Procs = pm2Result.procs,
            tailscale = tailscale,
            ssh = ssh,
            sshAccess = sshAccess,
            health = health,
            busy = false,
            lastAction = ui.lastAction
        )
    }

    private fun runAction(label: String, block: suspend () -> String) {
        ioScope.launch {
            ui = ui.copy(busy = true, lastAction = "$label...")
            val msg = block()
            LogStore.append("acción", "$label: $msg")
            refresh()
            ui = ui.copy(lastAction = "$label → $msg")
        }
    }

    private val actions = Actions(
        onRefresh = { ioScope.launch { refresh() } },
        onStart = { runAction("Arrancar Ubuntu") { ServerManager.start(this@MainActivity, cfg) } },
        onStop = { runAction("Parar Ubuntu") { ServerManager.stop(cfg) } },
        onRestart = { runAction("Reiniciar Ubuntu") { ServerManager.restart(this@MainActivity, cfg) } },
        onSshFix = { runAction("Arreglar SSH") { SshManager.ensure(this@MainActivity, cfg).detail } },
        onTailscaleApp = { runAction("Lanzar Tailscale") { TailscaleManager.tryStartApp(cfg) } },
        onPm2Fix = { runAction("Arreglar PM2") { Pm2Manager.ensure(this@MainActivity, cfg).status.detail } },
        onPm2RestartAll = { runAction("PM2 restart all") { Pm2Manager.restartAll(this@MainActivity, cfg) } },
        onWatchdogToggle = {
            if (WatchdogService.running) {
                WatchdogService.stop(this)
                watchdog = false
                LogStore.append("acción", "watchdog detenido")
            } else {
                WatchdogService.start(this)
                watchdog = true
                LogStore.append("acción", "watchdog iniciado")
            }
        },
        onBatteryExempt = {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    )

    private fun runEnv(label: String, block: suspend () -> String) {
        ioScope.launch {
            envBusy = true
            envLastOp = "$label..."
            val msg = runCatching { block() }.getOrElse { "${it.message ?: it.javaClass.simpleName}" }
            envLastOp = "$label → $msg"
            envBusy = false
        }
    }

    private fun checkUpdateIfNeeded() {
        if (!cfg.autoUpdate || cfg.githubRepo.isBlank()) return
        ioScope.launch {
            val info = com.videototem.servermanager.core.UpdateManager.checkForUpdate(this@MainActivity, cfg.githubRepo) ?: return@launch
            envLastOp = "Actualización ${info.tag} disponible — descargando…"
            tab = 1
            val ok = com.videototem.servermanager.core.UpdateManager.downloadAndInstall(this@MainActivity, info.apkUrl) { msg -> envLastOp = msg }
            envLastOp = if (ok) "Actualizado a ${info.tag} ✓" else "Fallo al actualizar a ${info.tag}"
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App() {
        val tabs = listOf(
            TabDef("Estado", Icons.Filled.Dashboard),
            TabDef("Entorno", Icons.Filled.Build),
            TabDef("Ajustes", Icons.Filled.Settings),
            TabDef("Logs", Icons.Filled.Assignment)
        )
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Server Manager") })
            }
        ) { padding ->
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(t.label) },
                            icon = { Icon(t.icon, contentDescription = t.label) }
                        )
                    }
                }
                when (tab) {
                    0 -> DashboardScreen(ui, watchdog, actions)
                    1 -> EnvScreen(
                        report = envReport,
                        busy = envBusy,
                        lastOp = envLastOp,
                        onAnalyze = {
                            runEnv("Analizar entorno") { EnvSetup.analyze(this@MainActivity, cfg).let { r -> envReport = r; "componentes: " + r.items.count { it.state == State.OK } + "/" + r.items.size + " OK" } }
                        },
                        onFix = { id -> runEnv("Arreglar $id") { EnvSetup.fix(this@MainActivity, cfg, id) } },
                        onPrepareAll = { runEnv("Preparar entorno") { EnvSetup.prepareAll(this@MainActivity, cfg) } }
                    )
                    2 -> SettingsScreen(
                        cfg,
                        onChange = { cfg = it },
                        onSave = {
                            Settings.save(this@MainActivity, cfg)
                            LogStore.append("ajustes", "configuración guardada")
                            ioScope.launch { refresh() }
                        }
                    )
                    3 -> LogsScreen(LogStore.lines.value)
                }
            }
        }
    }

    private data class TabDef(val label: String, val icon: ImageVector)
}
