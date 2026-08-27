package com.videototem.servermanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.videototem.servermanager.model.State
import com.videototem.servermanager.model.Status
import com.videototem.servermanager.model.pm2State

fun stateColor(s: State): Color = when (s) {
    State.OK -> Color(0xFF16A34A)
    State.WARN -> Color(0xFFF59E0B)
    State.FAIL -> Color(0xFFDC2626)
    State.UNKNOWN -> Color(0xFF64748B)
}

fun pm2StateColor(status: String): Color = stateColor(pm2State(status))

object Cards {
    val Root = Icons.Filled.VerifiedUser
    val Termux = Icons.Filled.Terminal
    val Ubuntu = Icons.Filled.Memory
    val Pm2 = Icons.Filled.Loop
    val Tailscale = Icons.Filled.Lan
    val Ssh = Icons.Filled.VpnKey
    val Health = Icons.Filled.MonitorHeart
    val Battery = Icons.Filled.BatteryFull
}

@Composable
fun SshAccessCard(a: com.videototem.servermanager.model.SshAccess?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Acceso SSH", style = MaterialTheme.typography.titleMedium)
            if (a == null) {
                Text("sin datos todavía", style = MaterialTheme.typography.bodySmall)
            } else {
                Row {
                    Text("Usuario: ", style = MaterialTheme.typography.bodyMedium)
                    Text(a.user, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(16.dp))
                    Text("Puerto: ", style = MaterialTheme.typography.bodyMedium)
                    Text(a.port, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text("Host: ", style = MaterialTheme.typography.bodyMedium)
                    Text(a.host, style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text("Contraseña: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        a.password.ifBlank { "(ponla en Ajustes)" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    a.command,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "pwd: cámbiala en Termux con: passwd",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusCard(title: String, status: Status, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = stateColor(status.state))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    status.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
