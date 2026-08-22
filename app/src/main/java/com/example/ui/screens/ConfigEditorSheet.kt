package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardLight
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigEditorSheet(
    config: TunnelConfig?,
    onSave: (TunnelConfig) -> Unit,
    onDismiss: () -> Unit,
    onOpenPayloadGenerator: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    var name by remember(config) { mutableStateOf(config?.name ?: "Nuevo Servidor") }
    var mode by remember(config) { mutableStateOf(config?.mode ?: TunnelMode.SSH_PAYLOAD) }
    var host by remember(config) { mutableStateOf(config?.serverHost ?: "") }
    var portText by remember(config) { mutableStateOf(config?.serverPort?.toString() ?: mode.defaultPort.toString()) }
    var username by remember(config) { mutableStateOf(config?.username ?: "") }
    var password by remember(config) { mutableStateOf(config?.password ?: "") }
    var sniHost by remember(config) { mutableStateOf(config?.sniHost ?: "") }
    var payload by remember(config) { mutableStateOf(config?.customPayload ?: "") }
    var proxyHost by remember(config) { mutableStateOf(config?.proxyHost ?: "") }
    var proxyPortText by remember(config) { mutableStateOf(config?.proxyPort?.toString() ?: "80") }
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
                    text = if (config?.id != null && config.id > 0) "EDITAR PERFIL / SERVIDOR" else "NUEVA CONFIGURACIÓN",
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
                    label = { Text("Modo de Conexión / Protocolo", color = TextSecondary) },
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

            Spacer(modifier = Modifier.height(14.dp))

            // Campos específicos para el modo SSH + HTTP Payload (Puerto 80)
            if (mode == TunnelMode.SSH_PAYLOAD) {
                // Host Frontal o Proxy
                Row(modifier = Modifier.fillMaxWidth()) {
                    DarkTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = "Host frontal o Proxy",
                        modifier = Modifier.weight(2.5f),
                        testTag = "config_proxy_host_input"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DarkTextField(
                        value = proxyPortText,
                        onValueChange = { proxyPortText = it },
                        label = "Puerto frontal",
                        modifier = Modifier.weight(1f),
                        testTag = "config_proxy_port_input"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Campo Multilínea de Payload Completo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payload completo",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                payload = clipText
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PEGAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    placeholder = {
                        Text(
                            text = "GET / HTTP/1.1[crlf]\nHost: [host][crlf]\nConnection: Keep-Alive[crlf]\n[crlf]",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("config_payload_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    ),
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberCard,
                        unfocusedContainerColor = CyberCard
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Marcadores rápidos
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("[crlf]", "[host]", "[port]", "[host_port]").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(CyberCardLight, RoundedCornerShape(6.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                                .clickable { payload += tag }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tag,
                                color = NeonGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Host real del servidor SSH (si es diferente del frontal)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DarkTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = "Host real del servidor SSH (opcional)",
                        modifier = Modifier.weight(2.5f),
                        testTag = "config_host_input"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DarkTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = "Puerto SSH",
                        modifier = Modifier.weight(1f),
                        testTag = "config_port_input"
                    )
                }
            } else {
                // Modos Directo, SSL o WebSocket
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

                if (mode.requiresSni || mode == TunnelMode.SSH_SSL || mode == TunnelMode.SSH_WEBSOCKET_SSL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DarkTextField(
                        value = sniHost,
                        onValueChange = { sniHost = it },
                        label = "SNI / Host SSL (ej: midominio.com)",
                        testTag = "config_sni_input"
                    )
                }

                if (mode.requiresPayload || mode == TunnelMode.SSH_WEBSOCKET || mode == TunnelMode.SSH_WEBSOCKET_SSL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Payload completo",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    DarkTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = "Payload HTTP",
                        minLines = 3,
                        testTag = "config_payload_input"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Credenciales SSH
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

            Spacer(modifier = Modifier.height(14.dp))

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Reconexión Automática", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(text = "Reintentar conexión si se pierde la red", color = TextMuted, fontSize = 11.sp)
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
                    val pPort = proxyPortText.toIntOrNull() ?: 80
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
