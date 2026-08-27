package com.videototem.servermanager.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videototem.servermanager.core.EnvReport
import com.videototem.servermanager.core.Uninstaller
import com.videototem.servermanager.model.State

@Composable
fun EnvScreen(
    report: EnvReport?,
    busy: Boolean,
    lastOp: String,
    onAnalyze: () -> Unit,
    onFix: (String) -> Unit,
    onPrepareAll: () -> Unit,
    onUninstall: (Set<String>) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (report != null && !report.rootOk) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    "SIN ROOT: es requisito para todo lo demás. Concede acceso a esta app en Magisk/KernelSU y vuelve a Analizar.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (report == null) {
            Text("Pulsa «Analizar entorno» para verificar root, Termux, Ubuntu, Node, PM2, SSH y Tailscale.")
        }

        report?.items?.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(stateColor(item.state), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.canFix && item.state != State.OK) {
                        OutlinedButton(onClick = { onFix(item.id) }) {
                            Text(if (item.id == "ssh" || item.id == "dump") "Arreglar" else "Instalar")
                        }
                    }
                }
            }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("trabajando... (las instalaciones pueden tardar minutos)")
            }
        }
        if (lastOp.isNotBlank()) {
            Text(lastOp, style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Analizar entorno")
        }
        Button(onClick = onPrepareAll, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Preparar todo (dejar listo para PM2)")
        }
        var showUninstall by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showUninstall = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Desinstalador — limpiar entorno (pruebas)") }
        if (showUninstall) {
            val opts = remember { Uninstaller.options() }
            var sel by remember { mutableStateOf(opts.filter { it.checkedDefault }.map { it.id }.toSet()) }
            AlertDialog(
                onDismissRequest = { showUninstall = false },
                title = { Text("Limpiar entorno (sin factory reset)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Selecciona qué borrar. Equivale a dejar el móvil limpio para probar una instalación desde cero.", style = MaterialTheme.typography.bodySmall)
                        opts.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = sel.contains(o.id), onCheckedChange = { c -> sel = if (c) sel + o.id else sel - o.id })
                                Column {
                                    Text(o.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(o.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showUninstall = false; onUninstall(sel) }, enabled = sel.isNotEmpty()) { Text("Borrar seleccionado") }
                },
                dismissButton = { TextButton(onClick = { showUninstall = false }) { Text("Cancelar") } }
            )
        }
        if (report != null) {
            Text(
                "Compatibilidad: funciona en cualquier móvil Android 8+ con root (Magisk/KernelSU/APatch, arm64/x86, Samsung/Pixel/Xiaomi). " +
                    "Su se busca en varias rutas. Instalación nueva: usa Preparar todo y el entorno queda PM2-ready. " +
                    "Auto-actualización: configura el repo en Ajustes, activa auto-actualizar, publica un release con el APK adjunto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
