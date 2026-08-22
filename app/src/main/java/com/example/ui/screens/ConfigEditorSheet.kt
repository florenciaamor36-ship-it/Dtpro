package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorSheet(
    config: TunnelConfig?,
    onSave: (TunnelConfig) -> Unit,
    onDismiss: () -> Unit,
    onOpenPayloadGenerator: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(config) { mutableStateOf(config?.name ?: "Nuevo Servidor") }
    var mode by remember(config) { mutableStateOf(config?.mode ?: TunnelMode.SSH_WEBSOCKET_SSL) }
    var host by remember(config) { mutableStateOf(config?.serverHost ?: "") }
    var portText by remember(config) { mutableStateOf(config?.serverPort?.toString() ?: mode.defaultPort.toString()) }
    var username by remember(config) { mutableStateOf(config?.username ?: "") }
    var password by remember(config) { mutableStateOf(config?.password ?: "") }
    var sniHost by remember(config) { mutableStateOf(config?.sniHost ?: "") }
    var payload by remember(config) { mutableStateOf(config?.customPayload ?: "") }
    var proxyHost by remember(config) { mutableStateOf(config?.proxyHost ?: "") }
    var proxyPortText by remember(config) { mutableStateOf(config?.proxyPort?.toString() ?: "8080") }
    var autoReconnect by remember(config) { mutableStateOf(config?.autoReconnect ?: true) }
    var udpForwarding by remember(config) { mutableStateOf(config?.isUdpForwarding ?: true) }

    var isModeDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberNavy
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config?.id != null && config.id > 0) "EDITAR SERVIDOR / CONFIG" else "NUEVA CONFIGURACIÓN",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profile Name
            DarkTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre del Perfil",
                testTag = "config_name_input"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tunnel Mode Dropdown
            ExposedDropdownMenuBox(
                expanded = isModeDropdownExpanded,
                onExpandedChange = { isModeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = mode.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de Túnel / Protocolo", color = TextSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModeDropdownExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberCard,
                        unfocusedContainerColor = CyberCard
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isModeDropdownExpanded,
                    onDismissRequest = { isModeDropdownExpanded = false },
                    modifier = Modifier.background(CyberCard)
                ) {
                    TunnelMode.values().forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = m.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(text = m.description, color = TextSecondary, fontSize = 11.sp)
                                }
                            },
                            onClick = {
                                mode = m
                                if (portText.isBlank() || portText == "22" || portText == "80" || portText == "443" || portText == "8080") {
                                    portText = m.defaultPort.toString()
                                }
                                isModeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Server Host & Port
            Row(modifier = Modifier.fillMaxWidth()) {
                DarkTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = "Host / IP del Servidor",
                    modifier = Modifier.weight(2.5f),
                    testTag = "config_host_input"
                )
                Spacer(modifier = Modifier.width(8.dp))
                DarkTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = "Puerto",
                    modifier = Modifier.weight(1f),
                    testTag = "config_port_input"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SSH Credentials
            Row(modifier = Modifier.fillMaxWidth()) {
                DarkTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Usuario SSH",
                    modifier = Modifier.weight(1f),
                    testTag = "config_user_input"
                )
                Spacer(modifier = Modifier.width(8.dp))
                DarkTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña SSH",
                    modifier = Modifier.weight(1f),
                    testTag = "config_pass_input"
                )
            }

            if (mode.requiresSni || mode == TunnelMode.SSH_SSL || mode == TunnelMode.SSH_WEBSOCKET_SSL) {
                Spacer(modifier = Modifier.height(12.dp))
                DarkTextField(
                    value = sniHost,
                    onValueChange = { sniHost = it },
                    label = "SNI / Host SSL / Bug Host (ej: cloudflare.com)",
                    testTag = "config_sni_input"
                )
            }

            if (mode.requiresPayload || mode == TunnelMode.SSH_PAYLOAD || mode == TunnelMode.SSH_WEBSOCKET || mode == TunnelMode.SSH_WEBSOCKET_SSL) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Custom HTTP Payload", color = TextSecondary, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = onOpenPayloadGenerator,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = NeonCyan, modifier = Modifier.padding(end = 4.dp))
                        Text("Generador", color = NeonCyan, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DarkTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = "Payload HTTP",
                    minLines = 3,
                    testTag = "config_payload_input"
                )
            }

            if (mode == TunnelMode.SSH_PAYLOAD || mode == TunnelMode.DIRECT_PROXY) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DarkTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = "Proxy IP / Host Remoto",
                        modifier = Modifier.weight(2.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DarkTextField(
                        value = proxyPortText,
                        onValueChange = { proxyPortText = it },
                        label = "Puerto Proxy",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Reconexión Automática", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(text = "Reintentar si se pierde la conexión", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = autoReconnect,
                    onCheckedChange = { autoReconnect = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberBorder)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Reenvío UDP / Juegos", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(text = "Habilitar soporte para tráfico UDP", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = udpForwarding,
                    onCheckedChange = { udpForwarding = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = CyberBorder)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: mode.defaultPort
                    val pPort = proxyPortText.toIntOrNull() ?: 8080
                    val updated = (config ?: TunnelConfig(name = name)).copy(
                        name = name.ifBlank { "Servidor DTunnel" },
                        mode = mode,
                        serverHost = host.trim(),
                        serverPort = port,
                        username = username.trim(),
                        password = password,
                        sniHost = sniHost.trim(),
                        customPayload = payload.trim(),
                        proxyHost = proxyHost.trim(),
                        proxyPort = pPort,
                        autoReconnect = autoReconnect,
                        isUdpForwarding = udpForwarding
                    )
                    onSave(updated)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_config_button")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GUARDAR CONFIGURACIÓN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    testTag: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary, fontSize = 12.sp) },
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = CyberBorder,
            focusedContainerColor = CyberCard,
            unfocusedContainerColor = CyberCard
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
