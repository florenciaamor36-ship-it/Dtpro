package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TunnelConfig
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardLight
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ConfigExporter
import com.example.util.FileHandlerHelper
import com.example.util.HwidManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportExportDialog(
    currentConfig: TunnelConfig?,
    onImportSuccess: (TunnelConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Export States
    var customExportName by remember(currentConfig) { mutableStateOf(currentConfig?.name ?: "Mi Configuración") }
    var isLocked by remember { mutableStateOf(false) }
    var enableExpiry by remember { mutableStateOf(false) }
    var expiryDaysSelected by remember { mutableIntStateOf(7) }
    var customExpiryTimestamp by remember {
        mutableLongStateOf(System.currentTimeMillis() + 7L * 24 * 3600 * 1000)
    }
    var enableHwidLock by remember { mutableStateOf(false) }
    var targetHwidInput by remember { mutableStateOf("") }
    var enableVpsAuth by remember { mutableStateOf(false) }
    var vpsUrlInput by remember { mutableStateOf("") }
    var creatorNoteInput by remember { mutableStateOf("") }
    var exportedResultString by remember { mutableStateOf("") }
    var saveSuccessMessage by remember { mutableStateOf("") }

    // Advanced Lock Options (Root, Carrier, Network, Anti-Sniffer, Connect Message)
    var enableRootBlock by remember { mutableStateOf(false) }
    var enableCarrierLock by remember { mutableStateOf(false) }
    var allowedCarriersInput by remember { mutableStateOf("") }
    var networkLockType by remember { mutableIntStateOf(0) } // 0: Cualquiera, 1: Solo Datos, 2: Solo Wi-Fi
    var enableSnifferBlock by remember { mutableStateOf(false) }
    var welcomeToastInput by remember { mutableStateOf("") }

    // Saved Files on Device State
    var savedFilesList by remember { mutableStateOf(FileHandlerHelper.getSavedConfigsList(context)) }

    // Import States
    var importInputText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<ConfigExporter.ImportResult?>(null) }

    val myHwid = remember { HwidManager.getHwid(context) }

    // File Picker Launcher para abrir archivos .dtun o .ehi
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = FileHandlerHelper.readConfigFromUri(context, uri)
            if (!content.isNullOrBlank()) {
                importInputText = content.trim()
                importResult = ConfigExporter.importConfigDetailed(content.trim())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COMPARTIR Y GESTIONAR ARCHIVOS",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CyberCard,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("EXPORTAR", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("IMPORTAR", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            savedFilesList = FileHandlerHelper.getSavedConfigsList(context)
                            selectedTab = 2
                        },
                        text = { Text("MIS ARCHIVOS (${savedFilesList.size})", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    if (currentConfig == null) {
                        Text(
                            text = "No hay ningún perfil seleccionado para exportar.",
                            color = NeonOrange,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Exportando: ${currentConfig.name}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Elige el nombre del archivo y si deseas compartirlo abierto o cerrado con protección.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom Name Field for Export/Locking
                        DarkTextField(
                            value = customExportName,
                            onValueChange = { customExportName = it },
                            label = "Nombre del Archivo / Perfil de Exportación",
                            testTag = "custom_export_name_field"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Modalidad Rápida de Exportación (4 Tipos)
                        Text(
                            text = "MODALIDAD DE PROTECCIÓN:",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = !isLocked,
                                onClick = {
                                    isLocked = false
                                    enableExpiry = false
                                    enableHwidLock = false
                                    enableVpsAuth = false
                                },
                                label = { Text("🔓 Abierto", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonGreen
                                )
                            )
                            FilterChip(
                                selected = isLocked && enableExpiry && !enableHwidLock && !enableVpsAuth,
                                onClick = {
                                    isLocked = true
                                    enableExpiry = true
                                    enableHwidLock = false
                                    enableVpsAuth = false
                                },
                                label = { Text("⏳ Por Fecha", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonOrange
                                )
                            )
                            FilterChip(
                                selected = isLocked && enableHwidLock && !enableVpsAuth,
                                onClick = {
                                    isLocked = true
                                    enableHwidLock = true
                                    enableVpsAuth = false
                                    if (targetHwidInput.isBlank()) targetHwidInput = myHwid
                                },
                                label = { Text("📱 Por HWID", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                            FilterChip(
                                selected = isLocked && enableVpsAuth,
                                onClick = {
                                    isLocked = true
                                    enableVpsAuth = true
                                },
                                label = { Text("🌐 VPS Colectivo", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Switch: Bloquear / Cerrar archivo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isLocked) NeonCyan else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isLocked) "Archivo Bloqueado / Cerrado" else "Archivo Abierto (Editable)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = if (isLocked) "Oculta y cifra el payload y credenciales" else "Los usuarios podrán ver y editar los datos",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = isLocked,
                                onCheckedChange = { isLocked = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberBorder)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Expiry Lock Options
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = if (enableExpiry) NeonOrange else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bloqueo por Fecha y Hora",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Vence automáticamente tras el plazo",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableExpiry,
                                onCheckedChange = { enableExpiry = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonOrange, checkedTrackColor = CyberBorder)
                            )
                        }

                        if (enableExpiry) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 3, 7, 15, 30).forEach { days ->
                                    FilterChip(
                                        selected = expiryDaysSelected == days,
                                        onClick = {
                                            expiryDaysSelected = days
                                            customExpiryTimestamp = System.currentTimeMillis() + days * 24L * 3600 * 1000
                                        },
                                        label = { Text("+$days d", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonOrange.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonOrange
                                        )
                                    )
                                }
                            }
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            Text(
                                text = "Expira el: ${sdf.format(Date(customExpiryTimestamp))}",
                                color = NeonOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // HWID Lock Options
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (enableHwidLock) NeonGreen else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bloqueo por HWID Único",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Solo los HWIDs indicados podrán conectar",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableHwidLock,
                                onCheckedChange = { enableHwidLock = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = CyberBorder)
                            )
                        }

                        if (enableHwidLock) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DarkTextField(
                                value = targetHwidInput,
                                onValueChange = { targetHwidInput = it },
                                label = "HWIDs Permitidos (separados por coma)",
                                minLines = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotBlank()) targetHwidInput = clip
                                }) {
                                    Text("Pegar HWID", color = NeonCyan, fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    targetHwidInput = if (targetHwidInput.isBlank()) myHwid else "$targetHwidInput,$myHwid"
                                }) {
                                    Text("+ Mi HWID", color = NeonGreen, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // VPS Online Auth Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = if (enableVpsAuth) NeonCyan else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Validación Remota en VPS",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Comprueba si el HWID está activo en tu VPS",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableVpsAuth,
                                onCheckedChange = { enableVpsAuth = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberBorder)
                            )
                        }

                        if (enableVpsAuth) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DarkTextField(
                                value = vpsUrlInput,
                                onValueChange = { vpsUrlInput = it },
                                label = "URL Endpoint VPS (ej: http://mi-vps.com/check_hwid.php)"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ================= BLOQUEOS AVANZADOS ADICIONALES =================
                        Text(
                            text = "BLOQUEOS DE SEGURIDAD AVANZADOS:",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // 1. Bloqueo Root (Anti-Root)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (enableRootBlock) NeonRed else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bloquear Dispositivos Root",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Prohíbe el uso en equipos con Root / Magisk / KernelSU",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableRootBlock,
                                onCheckedChange = { enableRootBlock = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonRed, checkedTrackColor = CyberBorder)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Bloqueo por Operadora / Carrier Lock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = if (enableCarrierLock) NeonCyan else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bloqueo por Operadora Móvil",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Permitir solo operadoras específicas (SIM celular)",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableCarrierLock,
                                onCheckedChange = { enableCarrierLock = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberBorder)
                            )
                        }

                        if (enableCarrierLock) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DarkTextField(
                                value = allowedCarriersInput,
                                onValueChange = { allowedCarriersInput = it },
                                label = "Operadoras Permitidas (ej: Claro, Movistar, Personal, Tigo, Telcel)",
                                minLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Claro", "Movistar", "Personal", "Tigo", "Telcel", "Vivo", "Entel").forEach { carrier ->
                                    FilterChip(
                                        selected = allowedCarriersInput.contains(carrier, ignoreCase = true),
                                        onClick = {
                                            if (allowedCarriersInput.contains(carrier, ignoreCase = true)) {
                                                allowedCarriersInput = allowedCarriersInput.split(",")
                                                    .map { it.trim() }
                                                    .filter { !it.equals(carrier, ignoreCase = true) }
                                                    .joinToString(",")
                                            } else {
                                                allowedCarriersInput = if (allowedCarriersInput.isBlank()) carrier else "$allowedCarriersInput,$carrier"
                                            }
                                        },
                                        label = { Text(carrier, fontSize = 9.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Bloqueo por Tipo de Red (Wi-Fi vs Datos Móviles)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (networkLockType == 1) Icons.Default.SignalCellularAlt else if (networkLockType == 2) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = if (networkLockType != 0) NeonOrange else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bloqueo por Tipo de Conexión",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = when (networkLockType) {
                                            1 -> "📱 Obligatorio Datos Móviles (Wi-Fi Bloqueado)"
                                            2 -> "📡 Obligatorio Wi-Fi (Datos Móviles Bloqueados)"
                                            else -> "🌐 Permitir cualquier tipo de red"
                                        },
                                        color = if (networkLockType != 0) NeonOrange else TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = networkLockType == 0,
                                    onClick = { networkLockType = 0 },
                                    label = { Text("🌐 Cualquier Red", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberBorder,
                                        selectedLabelColor = TextPrimary
                                    )
                                )
                                FilterChip(
                                    selected = networkLockType == 1,
                                    onClick = { networkLockType = 1 },
                                    label = { Text("📱 Solo Datos", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonOrange.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonOrange
                                    )
                                )
                                FilterChip(
                                    selected = networkLockType == 2,
                                    onClick = { networkLockType = 2 },
                                    label = { Text("📡 Solo Wi-Fi", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Bloqueo Anti-Sniffer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (enableSnifferBlock) NeonRed else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Protección Anti-Sniffer & Anti-Debug",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Bloquea si hay apps de captura (HttpCanary, PacketCapture)",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = enableSnifferBlock,
                                onCheckedChange = { enableSnifferBlock = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonRed, checkedTrackColor = CyberBorder)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Mensaje Emergente al Conectar
                        DarkTextField(
                            value = welcomeToastInput,
                            onValueChange = { welcomeToastInput = it },
                            label = "Mensaje Emergente / Aviso al Conectar (Opcional)"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Creator Note
                        DarkTextField(
                            value = creatorNoteInput,
                            onValueChange = { creatorNoteInput = it },
                            label = "Nota del Creador (Se muestra al importar)"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Copiar Texto y Compartir como Archivo Físico .dtun
                        val preparedConfig = currentConfig.copy(
                            name = customExportName.trim().ifBlank { currentConfig.name },
                            isLocked = isLocked,
                            expiryTimestamp = if (enableExpiry) customExpiryTimestamp else 0L,
                            allowedHwids = if (enableHwidLock) targetHwidInput.trim() else "",
                            vpsAuthUrl = if (enableVpsAuth) vpsUrlInput.trim() else "",
                            creatorNote = creatorNoteInput.trim(),
                            blockRoot = enableRootBlock,
                            allowedCarriers = if (enableCarrierLock) allowedCarriersInput.trim() else "",
                            blockWifi = networkLockType == 1,
                            blockMobileData = networkLockType == 2,
                            blockSniffers = enableSnifferBlock,
                            showToastOnConnect = welcomeToastInput.trim()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val code = ConfigExporter.exportConfig(preparedConfig)
                                    exportedResultString = code
                                    saveSuccessMessage = ""
                                    clipboardManager.setText(AnnotatedString(code))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPIAR TEXTO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    saveSuccessMessage = ""
                                    val shareIntent = FileHandlerHelper.shareConfigFile(context, preparedConfig)
                                    if (shareIntent != null) {
                                        context.startActivity(Intent.createChooser(shareIntent, "Enviar archivo .dtun"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COMPARTIR .DTUN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botón para Guardar en el Teléfono
                        Button(
                            onClick = {
                                val saveResult = FileHandlerHelper.saveConfigFileToStorage(context, preparedConfig)
                                if (saveResult.first) {
                                    saveSuccessMessage = "✓ ¡Archivo '${preparedConfig.name}.dtun' guardado en tu teléfono! Puedes compartirlo más tarde desde la pestaña 'MIS ARCHIVOS'."
                                    savedFilesList = FileHandlerHelper.getSavedConfigsList(context)
                                } else {
                                    saveSuccessMessage = "Error al guardar: ${saveResult.second}"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCardLight, contentColor = NeonCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GUARDAR EN EL TELÉFONO (PARA DESPUÉS)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        if (exportedResultString.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberCardLight, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "✓ ¡Código copiado al portapapeles! Compártelo con quien desees.",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (saveSuccessMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberCardLight, RoundedCornerShape(8.dp))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = saveSuccessMessage,
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    // TAB IMPORTAR
                    Text(
                        text = "Importa desde archivo .dtun o pega un enlace 'dtunnel://...':",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón para seleccionar archivo desde el almacenamiento
                    OutlinedButton(
                        onClick = {
                            openFileLauncher.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SELECCIONAR ARCHIVO .DTUN / .EHI", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DarkTextField(
                        value = importInputText,
                        onValueChange = {
                            importInputText = it
                            importResult = null
                        },
                        label = "dtunnel://... o contenido del archivo",
                        minLines = 4
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text ?: ""
                                if (clip.isNotBlank()) {
                                    importInputText = clip
                                    importResult = ConfigExporter.importConfigDetailed(clip.trim())
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pegar", color = NeonCyan, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                importResult = ConfigExporter.importConfigDetailed(importInputText.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ANALIZAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Reporte de importación
                    if (importResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        when (val res = importResult!!) {
                            is ConfigExporter.ImportResult.Success -> {
                                val conf = res.config
                                val expiryCheck = HwidManager.checkExpiry(conf)
                                val hwidCheck = HwidManager.checkHwidPermission(context, conf)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberCard, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (expiryCheck.first && hwidCheck.first) NeonGreen else NeonOrange, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                 ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = NeonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = conf.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Protocolo: ${conf.mode.title} • ${conf.serverHost}:${conf.serverPort}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )

                                    if (conf.isLocked) {
                                        Text(
                                            text = "🔒 Archivo Bloqueado por el Creador",
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.expiryTimestamp > 0) {
                                        Text(
                                            text = "📅 ${expiryCheck.second}",
                                            color = if (expiryCheck.first) NeonGreen else NeonRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.allowedHwids.isNotBlank()) {
                                        Text(
                                            text = "📱 HWID: ${hwidCheck.second}",
                                            color = if (hwidCheck.first) NeonGreen else NeonRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.blockRoot) {
                                        Text(
                                            text = "🚫 Bloqueo Root Activo (No permitido en dispositivos con Root)",
                                            color = NeonRed,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.allowedCarriers.isNotBlank()) {
                                        Text(
                                            text = "📶 Solo Operadoras: ${conf.allowedCarriers}",
                                            color = NeonCyan,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.blockWifi) {
                                        Text(
                                            text = "📱 Restringido a Datos Móviles (Wi-Fi no permitido)",
                                            color = NeonOrange,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.blockMobileData) {
                                        Text(
                                            text = "📡 Restringido a Red Wi-Fi (Datos móviles no permitidos)",
                                            color = NeonCyan,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.blockSniffers) {
                                        Text(
                                            text = "🛡️ Protección Anti-Sniffer / Anti-Debug activa",
                                            color = NeonGreen,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (conf.creatorNote.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Nota: \"${conf.creatorNote}\"",
                                            color = NeonCyan,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            onImportSuccess(conf)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberNavy),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("GUARDAR Y USAR ESTE PERFIL", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is ConfigExporter.ImportResult.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberCard, RoundedCornerShape(10.dp))
                                        .border(1.dp, NeonRed, RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = NeonRed)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "Error de Importación", color = NeonRed, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = res.reason, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: MIS ARCHIVOS GUARDADOS (.DTUN)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Archivos guardados en el almacenamiento:",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        IconButton(
                            onClick = {
                                savedFilesList = FileHandlerHelper.getSavedConfigsList(context)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Recargar lista", tint = NeonCyan, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (savedFilesList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCard, RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No hay archivos .dtun guardados aún",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "En la pestaña 'EXPORTAR', presiona 'GUARDAR EN EL TELÉFONO'. Tus archivos quedarán almacenados aquí para compartirlos o usarlos en cualquier momento sin volver a configurarlos.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedFilesList.forEach { savedItem ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberCard, RoundedCornerShape(10.dp))
                                        .border(1.dp, CyberCardLight, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = savedItem.name,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${savedItem.formattedDate} • ${savedItem.sizeKb}",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Botón Compartir
                                        Button(
                                            onClick = {
                                                val shareIntent = FileHandlerHelper.shareExistingFile(context, savedItem)
                                                context.startActivity(Intent.createChooser(shareIntent, "Compartir ${savedItem.name}"))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberNavy),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("COMPARTIR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Botón Importar / Usar
                                        Button(
                                            onClick = {
                                                val content = FileHandlerHelper.readConfigFromUri(context, savedItem.uri)
                                                if (!content.isNullOrBlank()) {
                                                    importInputText = content.trim()
                                                    val res = ConfigExporter.importConfigDetailed(content.trim())
                                                    importResult = res
                                                    if (res is ConfigExporter.ImportResult.Success) {
                                                        onImportSuccess(res.config)
                                                        onDismiss()
                                                    } else {
                                                        selectedTab = 1
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("USAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Botón Eliminar
                                        IconButton(
                                            onClick = {
                                                FileHandlerHelper.deleteSavedFile(savedItem.file)
                                                savedFilesList = FileHandlerHelper.getSavedConfigsList(context)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", tint = NeonRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
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
