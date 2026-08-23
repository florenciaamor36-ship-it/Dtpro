package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TunnelConfig
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
import com.example.util.BatteryManagerHelper
import com.example.util.SoundEffectHelper

data class DnsPreset(val name: String, val primary: String, val secondary: String)

@Composable
fun SettingsDialog(
    currentConfig: TunnelConfig?,
    onSaveDns: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val presets = listOf(
        DnsPreset("Google DNS", "8.8.8.8", "8.8.4.4"),
        DnsPreset("Cloudflare DNS", "1.1.1.1", "1.0.0.1"),
        DnsPreset("AdGuard DNS (Anti-Ads)", "94.140.14.14", "94.140.15.15"),
        DnsPreset("OpenDNS", "208.67.222.222", "208.67.220.220"),
        DnsPreset("Personalizado", "", "")
    )

    var selectedDnsName by remember {
        val currentP = currentConfig?.dnsPrimary ?: "8.8.8.8"
        val found = presets.firstOrNull { it.primary == currentP }
        mutableStateOf(found?.name ?: "Personalizado")
    }

    var customDns1 by remember { mutableStateOf(currentConfig?.dnsPrimary ?: "8.8.8.8") }
    var customDns2 by remember { mutableStateOf(currentConfig?.dnsSecondary ?: "8.8.4.4") }

    var isWakeLockOn by remember { mutableStateOf(BatteryManagerHelper.isWakeLockEnabled(context)) }
    var isBatterySaverOn by remember { mutableStateOf(BatteryManagerHelper.isBatterySaverEnabled(context)) }
    var isSoundOn by remember { mutableStateOf(SoundEffectHelper.isSoundEnabled(context)) }
    var isHotshareOn by remember { mutableStateOf(TunnelEngine.instance.isHotshareActive()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AJUSTES DE CONEXIÓN", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Sección Hotshare / Tethering (Compartir VPN a PC/WiFi)
                Text("HOTSHARE / TETHERING (COMPARTIR VPN)", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Compartir VPN vía Proxy (Puerto 8080)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Permite a tu PC o celular en tu zona Wi-Fi usar la VPN de tu teléfono", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = isHotshareOn,
                        onCheckedChange = {
                            isHotshareOn = it
                            TunnelEngine.instance.toggleHotshare(it, 8080)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonOrange,
                            checkedTrackColor = CyberNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sección Batería y WakeLock
                Text("RENDIMIENTO Y ENERGÍA", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Mantener Conexión (WakeLock)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Evita que Android desconecte el túnel con la pantalla apagada", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = isWakeLockOn,
                        onCheckedChange = {
                            isWakeLockOn = it
                            BatteryManagerHelper.setWakeLockEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CyberNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.BatterySaver, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Modo Ahorro de Batería", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Reduce sondeos y diagnósticos innecesarios en segundo plano", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = isBatterySaverOn,
                        onCheckedChange = {
                            isBatterySaverOn = it
                            BatteryManagerHelper.setBatterySaverEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CyberNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCard, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Efectos de Sonido", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Emite tonos de aviso al conectar, desconectar o en error", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = isSoundOn,
                        onCheckedChange = {
                            isSoundOn = it
                            SoundEffectHelper.setSoundEnabled(context, it)
                            if (it) {
                                SoundEffectHelper.playConnectSound(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CyberNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sección Servidor DNS
                Text("SERVIDOR DNS (SISTEMA DE NOMBRES)", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDnsName = preset.name
                                if (preset.name != "Personalizado") {
                                    customDns1 = preset.primary
                                    customDns2 = preset.secondary
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDnsName == preset.name),
                            onClick = {
                                selectedDnsName = preset.name
                                if (preset.name != "Personalizado") {
                                    customDns1 = preset.primary
                                    customDns2 = preset.secondary
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(preset.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (preset.primary.isNotBlank()) {
                                Text("${preset.primary} • ${preset.secondary}", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (selectedDnsName == "Personalizado") {
                    Spacer(modifier = Modifier.height(8.dp))
                    DarkTextField(value = customDns1, onValueChange = { customDns1 = it }, label = "DNS Primario")
                    Spacer(modifier = Modifier.height(6.dp))
                    DarkTextField(value = customDns2, onValueChange = { customDns2 = it }, label = "DNS Secundario")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveDns(customDns1.trim(), customDns2.trim())
                    onDismiss()
                }
            ) {
                Text("GUARDAR", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR", color = TextSecondary)
            }
        }
    )
}
