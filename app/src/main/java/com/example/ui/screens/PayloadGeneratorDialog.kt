package com.example.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardLight
import com.example.ui.theme.CyberNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayloadGeneratorDialog(
    initialPayload: String = "",
    onPayloadGenerated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var payloadText by remember { mutableStateOf(initialPayload) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberNavy,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EDITAR PAYLOAD",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Ingresa o pega el payload HTTP completo. Se preservará exactamente como lo escribas.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo Multilínea Principal
                OutlinedTextField(
                    value = payloadText,
                    onValueChange = { payloadText = it },
                    label = { Text("Payload completo", color = NeonCyan, fontWeight = FontWeight.SemiBold) },
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
                        .height(180.dp)
                        .testTag("payload_full_textfield"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary
                    ),
                    maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberCard,
                        unfocusedContainerColor = CyberCard
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Barra de Acciones Rápidas (Pegar / Limpiar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                payloadText = clipText
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PEGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { payloadText = "" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LIMPIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chips para insertar marcadores
                Text(
                    text = "Marcadores automáticos compatibles:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("[crlf]", "[host]", "[port]", "[host_port]", "[protocol]").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(CyberCardLight, RoundedCornerShape(6.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                                .clickable { payloadText += tag }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tag,
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPayloadGenerated(payloadText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_payload_dialog_button")
            ) {
                Text("GUARDAR PAYLOAD", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = TextSecondary)
            }
        }
    )
}
