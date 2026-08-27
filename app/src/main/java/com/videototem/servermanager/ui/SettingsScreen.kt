package com.videototem.servermanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videototem.servermanager.core.Config

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(cfg: Config, onChange: (Config) -> Unit, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Termux / Ubuntu", style = MaterialTheme.typography.titleMedium)
        Field("Prefijo de Termux", cfg.termuxPrefix) { onChange(cfg.copy(termuxPrefix = it)) }
        Field("Distribución proot", cfg.distro) { onChange(cfg.copy(distro = it)) }
        OutlinedTextField(
            value = cfg.serverStartCmd,
            onValueChange = { onChange(cfg.copy(serverStartCmd = it)) },
            label = { Text("Comando dentro de Ubuntu (bash -lc)") },
            modifier = Modifier.fillMaxWidth()
        )
        Field("Patrón de proceso (stop/pgrep)", cfg.serverStopPattern) { onChange(cfg.copy(serverStopPattern = it)) }

        Text("Watchdog", style = MaterialTheme.typography.titleMedium)
        Field("Intervalo (segundos)", cfg.intervalSec.toString()) { v ->
            v.toIntOrNull()?.let { onChange(cfg.copy(intervalSec = it.coerceAtLeast(10))) }
        }
        SwitchRow("Auto-corrección (relanzar si cae)", cfg.autoFix) { onChange(cfg.copy(autoFix = it)) }
        SwitchRow("Iniciar watchdog al arrancar el móvil", cfg.autoStartWatchdog) { onChange(cfg.copy(autoStartWatchdog = it)) }

        Text("SSH", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Supervisar SSH", cfg.sshEnabled) { onChange(cfg.copy(sshEnabled = it)) }
        Field("Puertos a comprobar (orden)", cfg.sshPorts) { onChange(cfg.copy(sshPorts = it)) }
        Field("Contraseña SSH (solo se muestra en dashboard)", cfg.sshInfoPassword) { onChange(cfg.copy(sshInfoPassword = it)) }
        SwitchRow("Supervisar PM2 en Ubuntu", cfg.pm2Enabled) { onChange(cfg.copy(pm2Enabled = it)) }

        Text("Tailscale", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Supervisar Tailscale", cfg.tailscaleEnabled) { onChange(cfg.copy(tailscaleEnabled = it)) }
        Field("Paquete de la app", cfg.tailscalePackage) { onChange(cfg.copy(tailscalePackage = it)) }

        Text("Health-check HTTP/TCP (opcional)", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Activar health-check", cfg.healthEnabled) { onChange(cfg.copy(healthEnabled = it)) }
        Field("Host", cfg.healthHost) { onChange(cfg.copy(healthHost = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Puerto", cfg.healthPort.toString(), Modifier.weight(1f)) { v ->
                v.toIntOrNull()?.let { onChange(cfg.copy(healthPort = it)) }
            }
            Field("Timeout (s)", cfg.timeoutSec.toString(), Modifier.weight(1f)) { v ->
                v.toIntOrNull()?.let { onChange(cfg.copy(timeoutSec = it.coerceAtLeast(1))) }
            }
        }
        Field("Ruta HTTP (vacío = solo TCP)", cfg.healthPath) { onChange(cfg.copy(healthPath = it)) }

        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        Field("Repo GitHub (owner/repo)", cfg.githubRepo) { onChange(cfg.copy(githubRepo = it)) }
        SwitchRow("Auto-actualizar desde GitHub", cfg.autoUpdate) { onChange(cfg.copy(autoUpdate = it)) }
        Text("Requiere repo público con releases que adjunten el APK. Ej: usuario/androidServerManager", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar configuración") }
    }
}
