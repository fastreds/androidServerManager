package com.videototem.servermanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videototem.servermanager.model.UiState

data class Actions(
    val onRefresh: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRestart: () -> Unit,
    val onSshFix: () -> Unit,
    val onTailscaleApp: () -> Unit,
    val onPm2Fix: () -> Unit,
    val onPm2RestartAll: () -> Unit,
    val onWatchdogToggle: () -> Unit,
    val onBatteryExempt: () -> Unit
)

@Composable
fun DashboardScreen(state: UiState, watchdogRunning: Boolean, actions: Actions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusCard("Root", state.root, Cards.Root)
        StatusCard("Termux / proot-distro", state.termux, Cards.Termux)
        StatusCard("Ubuntu (proot)", state.ubuntu, Cards.Ubuntu)
        StatusCard("PM2 (servicios)", state.pm2, Cards.Pm2)
        if (state.pm2Procs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Servicios PM2 (${state.pm2Procs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.pm2Procs.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(pm2StateColor(p.status), CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${p.name} · ${p.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "↻${p.restarts}  ${p.cpu}%  ${p.memMb}MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        StatusCard("Tailscale", state.tailscale, Cards.Tailscale)
        StatusCard("SSH", state.ssh, Cards.Ssh)
        SshAccessCard(state.sshAccess)
        StatusCard("Server health", state.health, Cards.Health)

        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text("comprobando...")
            }
        }
        if (state.lastAction.isNotBlank()) {
            Text(state.lastAction, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = actions.onRefresh, modifier = Modifier.weight(1f)) { Text("Revisar") }
            Button(onClick = actions.onStart, modifier = Modifier.weight(1f)) { Text("Arrancar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = actions.onStop, modifier = Modifier.weight(1f)) { Text("Parar") }
            OutlinedButton(onClick = actions.onRestart, modifier = Modifier.weight(1f)) { Text("Reiniciar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = actions.onSshFix, modifier = Modifier.weight(1f)) { Text("Arreglar SSH") }
            OutlinedButton(onClick = actions.onTailscaleApp, modifier = Modifier.weight(1f)) { Text("Abrir Tailscale") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = actions.onPm2Fix, modifier = Modifier.weight(1f)) { Text("Arreglar PM2") }
            OutlinedButton(onClick = actions.onPm2RestartAll, modifier = Modifier.weight(1f)) { Text("Reiniciar todos (PM2)") }
        }
        Button(
            onClick = actions.onWatchdogToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (watchdogRunning) "Detener watchdog" else "Iniciar watchdog (supervisión continua)")
        }
        OutlinedButton(onClick = actions.onBatteryExempt, modifier = Modifier.fillMaxWidth()) {
            Text("Eximir de optimización de batería (recomendado)")
        }
    }
}
