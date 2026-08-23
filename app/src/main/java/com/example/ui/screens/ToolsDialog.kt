package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TunnelEngine
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ToolsDialog(
    currentIp: String,
    ipLocation: String,
    pingResult: String?,
    isTesting: Boolean,
    onRunPing: (String, Int) -> Unit,
    onRefreshIp: () -> Unit,
    onDismiss: () -> Unit
) {
    var hostToPing by remember { mutableStateOf("1.1.1.1") }
    var portToPingText by remember { mutableStateOf("443") }
    var hotshareEnabled by remember { mutableStateOf(TunnelEngine.instance.isHotshareActive()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("HERRAMIENTAS DE RED Y DIAGNÓSTICO", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // IP Diagnostics Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("IP PÚBLICA ACTUAL", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onRefreshIp, modifier = Modifier.height(24.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualizar IP", tint = NeonCyan)
                        }
                    }
                    Text(
                        text = if (currentIp.isNotBlank()) currentIp else "Consultando...",
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Ubicación: $ipLocation",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hotshare / Tethering (Compartir VPN vía WiFi Hotspot)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, tint = NeonOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Hotshare / Compartir VPN", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Proxy HTTP en puerto 8080", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = hotshareEnabled,
                            onCheckedChange = { active ->
                                hotshareEnabled = active
                                TunnelEngine.instance.toggleHotshare(active, 8080)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonOrange, checkedTrackColor = CyberBorder)
                        )
                    }
                    if (hotshareEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✓ Activo en 192.168.43.1:8080. Configura el proxy en tu PC/Consola.",
                            color = NeonGreen,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ping / Host Latency Tester
                Text("TEST DE PING Y PUERTO (TCP HANDSHAKE)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    DarkTextField(
                        value = hostToPing,
                        onValueChange = { hostToPing = it },
                        label = "Host / IP / Bug Host",
                        modifier = Modifier.weight(2.5f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    DarkTextField(
                        value = portToPingText,
                        onValueChange = { portToPingText = it },
                        label = "Puerto",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val p = portToPingText.toIntOrNull() ?: 80
                        onRunPing(hostToPing.trim(), p)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(color = CyberNavy, modifier = Modifier.height(18.dp).width(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PROBANDO...")
                    } else {
                        Icon(imageVector = Icons.Default.NetworkPing, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TEST DE LATENCIA REAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (pingResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberCard, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = pingResult,
                            color = if (pingResult.startsWith("✓")) NeonGreen else TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR", color = NeonCyan)
            }
        }
    )
}
