package com.videototem.servermanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.videototem.servermanager.core.Settings
import com.videototem.servermanager.log.LogStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val cfg = Settings.load(ctx)
            if (cfg.autoStartWatchdog) {
                LogStore.append("boot", "arranque del sistema: iniciando watchdog")
                WatchdogService.start(ctx)
            }
        }
    }
}
