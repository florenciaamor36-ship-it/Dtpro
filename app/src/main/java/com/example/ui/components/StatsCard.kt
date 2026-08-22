package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.TunnelState
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardLight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun StatsCard(
    state: TunnelState,
    modifier: Modifier = Modifier
) {
    val isConnected = state.status is ConnectionStatus.Connected

    var durationFormatted by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(isConnected, state.connectedSinceTimestamp) {
        while (isConnected && state.connectedSinceTimestamp > 0) {
            val elapsedSeconds = (System.currentTimeMillis() - state.connectedSinceTimestamp) / 1000
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            durationFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
        if (!isConnected) {
            durationFormatted = "00:00:00"
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Speed Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SpeedMetric(
                    label = "DESCARGA",
                    speedBps = if (isConnected) state.downloadSpeedBps else 0L,
                    totalBytes = state.bytesIn,
                    icon = Icons.Default.ArrowDownward,
                    color = NeonGreen,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                SpeedMetric(
                    label = "SUBIDA",
                    speedBps = if (isConnected) state.uploadSpeedBps else 0L,
                    totalBytes = state.bytesOut,
                    icon = Icons.Default.ArrowUpward,
                    color = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CyberBorder))
            Spacer(modifier = Modifier.height(14.dp))

            // Lower Info Row: Ping, Duration, Public IP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoTag(
                    icon = Icons.Default.Speed,
                    label = "PING",
                    value = if (state.pingMs >= 0) "${state.pingMs} ms" else "---",
                    valueColor = if (state.pingMs in 0..120) NeonGreen else if (state.pingMs > 120) NeonOrange else TextSecondary
                )

                InfoTag(
                    icon = Icons.Default.Timer,
                    label = "TIEMPO",
                    value = durationFormatted,
                    valueColor = TextPrimary
                )

                InfoTag(
                    icon = Icons.Default.Language,
                    label = "IP PÚBLICA",
                    value = if (state.publicIp.isNotBlank()) state.publicIp else "---",
                    valueColor = NeonCyan
                )
            }
        }
    }
}

@Composable
private fun SpeedMetric(
    label: String,
    speedBps: Long,
    totalBytes: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val speedText = formatSpeed(speedBps)
    val totalText = formatBytes(totalBytes)

    Row(
        modifier = modifier
            .background(CyberCardLight, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(text = label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                text = speedText,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "Total: $totalText", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoTag(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
