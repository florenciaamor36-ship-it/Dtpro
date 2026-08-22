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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.PayloadGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadGeneratorDialog(
    onPayloadGenerated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val methods = listOf("CONNECT", "GET", "POST", "HEAD", "PUT", "OPTIONS")
    val injectionTypes = listOf("Normal", "Front Inject", "Back Inject")

    var selectedMethod by remember { mutableStateOf("CONNECT") }
    var selectedInjection by remember { mutableStateOf("Normal") }
    var customHost by remember { mutableStateOf("") }
    var useKeepAlive by remember { mutableStateOf(true) }
    var useUpgrade by remember { mutableStateOf(true) }
    var useOnlineHost by remember { mutableStateOf(true) }

    var isMethodExpanded by remember { mutableStateOf(false) }
    var isInjectionExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GENERADOR DE PAYLOAD HTTP", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Method Dropdown
                ExposedDropdownMenuBox(
                    expanded = isMethodExpanded,
                    onExpandedChange = { isMethodExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Método de Petición", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMethodExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberCard,
                            unfocusedContainerColor = CyberCard
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isMethodExpanded,
                        onDismissRequest = { isMethodExpanded = false },
                        modifier = Modifier.background(CyberCard)
                    ) {
                        methods.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, color = TextPrimary) },
                                onClick = {
                                    selectedMethod = m
                                    isMethodExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Injection Type
                ExposedDropdownMenuBox(
                    expanded = isInjectionExpanded,
                    onExpandedChange = { isInjectionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedInjection,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Modo de Inyección", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInjectionExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberCard,
                            unfocusedContainerColor = CyberCard
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isInjectionExpanded,
                        onDismissRequest = { isInjectionExpanded = false },
                        modifier = Modifier.background(CyberCard)
                    ) {
                        injectionTypes.forEach { inj ->
                            DropdownMenuItem(
                                text = { Text(inj, color = TextPrimary) },
                                onClick = {
                                    selectedInjection = inj
                                    isInjectionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DarkTextField(
                    value = customHost,
                    onValueChange = { customHost = it },
                    label = "Host Bug / URL (opcional, ej: m.facebook.com)"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Checkboxes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useKeepAlive,
                        onCheckedChange = { useKeepAlive = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                    )
                    Text("Connection: Keep-Alive", color = TextPrimary, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useUpgrade,
                        onCheckedChange = { useUpgrade = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                    )
                    Text("Upgrade: websocket", color = TextPrimary, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useOnlineHost,
                        onCheckedChange = { useOnlineHost = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                    )
                    Text("X-Online-Host / Forward-Host", color = TextPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gen = PayloadGenerator.generateCustomPayload(
                        method = selectedMethod,
                        injectionType = selectedInjection,
                        customHost = customHost.trim(),
                        useKeepAlive = useKeepAlive,
                        useUpgrade = useUpgrade,
                        useOnlineHost = useOnlineHost
                    )
                    onPayloadGenerated(gen)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("GENERAR PAYLOAD", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = TextSecondary)
            }
        }
    )
}
