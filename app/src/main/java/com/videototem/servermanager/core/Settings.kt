package com.videototem.servermanager.core

import android.content.Context

data class Config(
    val termuxPrefix: String = "/data/data/com.termux/files/usr",
    val distro: String = "ubuntu",
    val serverStartCmd: String = "if [ -x /root/start.sh ]; then exec bash /root/start.sh; else sleep infinity; fi",
    val serverStopPattern: String = "proot.*ubuntu",
    val healthEnabled: Boolean = false,
    val healthHost: String = "127.0.0.1",
    val healthPort: Int = 8080,
    val healthPath: String = "/",
    val timeoutSec: Int = 5,
    val sshEnabled: Boolean = true,
    val sshPorts: String = "8022,22,2222",
    val sshInfoPassword: String = "",
    val pm2Enabled: Boolean = true,
    val tailscaleEnabled: Boolean = true,
    val tailscalePackage: String = "com.tailscale.ipn",
    val intervalSec: Int = 30,
    val autoFix: Boolean = true,
    val autoStartWatchdog: Boolean = true,
    val githubRepo: String = "fastreds/androidServerManager",
    val autoUpdate: Boolean = true
)

object Settings {
    private const val PREFS = "settings"

    fun load(ctx: Context): Config {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("version")) return Config()
        return Config(
            termuxPrefix = p.getString("termuxPrefix", Config().termuxPrefix)!!,
            distro = p.getString("distro", Config().distro)!!,
            serverStartCmd = p.getString("serverStartCmd", Config().serverStartCmd)!!,
            serverStopPattern = p.getString("serverStopPattern", Config().serverStopPattern)!!,
            healthEnabled = p.getBoolean("healthEnabled", false),
            healthHost = p.getString("healthHost", Config().healthHost)!!,
            healthPort = p.getInt("healthPort", 8080),
            healthPath = p.getString("healthPath", Config().healthPath)!!,
            timeoutSec = p.getInt("timeoutSec", 5),
            sshEnabled = p.getBoolean("sshEnabled", true),
            sshPorts = p.getString("sshPorts", Config().sshPorts)!!,
            sshInfoPassword = p.getString("sshInfoPassword", "")!!,
            pm2Enabled = p.getBoolean("pm2Enabled", true),
            tailscaleEnabled = p.getBoolean("tailscaleEnabled", true),
            tailscalePackage = p.getString("tailscalePackage", Config().tailscalePackage)!!,
            intervalSec = p.getInt("intervalSec", 30),
            autoFix = p.getBoolean("autoFix", true),
            autoStartWatchdog = p.getBoolean("autoStartWatchdog", true),
            githubRepo = p.getString("githubRepo", "")!!,
            autoUpdate = p.getBoolean("autoUpdate", false)
        )
    }

    fun save(ctx: Context, c: Config) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("version", 1)
            .putString("termuxPrefix", c.termuxPrefix)
            .putString("distro", c.distro)
            .putString("serverStartCmd", c.serverStartCmd)
            .putString("serverStopPattern", c.serverStopPattern)
            .putBoolean("healthEnabled", c.healthEnabled)
            .putString("healthHost", c.healthHost)
            .putInt("healthPort", c.healthPort)
            .putString("healthPath", c.healthPath)
            .putInt("timeoutSec", c.timeoutSec)
            .putBoolean("sshEnabled", c.sshEnabled)
            .putString("sshPorts", c.sshPorts)
            .putString("sshInfoPassword", c.sshInfoPassword)
            .putBoolean("pm2Enabled", c.pm2Enabled)
            .putBoolean("tailscaleEnabled", c.tailscaleEnabled)
            .putString("tailscalePackage", c.tailscalePackage)
            .putInt("intervalSec", c.intervalSec)
            .putBoolean("autoFix", c.autoFix)
            .putBoolean("autoStartWatchdog", c.autoStartWatchdog)
            .putString("githubRepo", c.githubRepo)
            .putBoolean("autoUpdate", c.autoUpdate)
            .apply()
    }
}
