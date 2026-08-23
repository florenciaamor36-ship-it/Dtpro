package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tunnel_configs")
data class TunnelConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val mode: TunnelMode = TunnelMode.SSH_DIRECT,
    val serverHost: String = "",
    val serverPort: Int = 22,
    val username: String = "",
    val password: String = "",
    val customPayload: String = "[method] [host_port] [protocol][crlf]Host: [host][crlf]Connection: Upgrade[crlf]Upgrade: websocket[crlf][crlf]",
    val sniHost: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val isUdpForwarding: Boolean = true,
    val dnsPrimary: String = "8.8.8.8",
    val dnsSecondary: String = "8.8.4.4",
    val autoReconnect: Boolean = true,
    val isDefault: Boolean = false,

    // Opciones de Bloqueo y Seguridad del Archivo (.dtun)
    val isLocked: Boolean = false,
    val expiryTimestamp: Long = 0L,
    val allowedHwids: String = "",
    val vpsAuthUrl: String = "",
    val creatorNote: String = "",

    // Bloqueos Avanzados de Archivos (Estilo HTTP Injector / HTTP Custom / TLS Tunnel)
    val blockRoot: Boolean = false,
    val allowedCarriers: String = "",
    val blockWifi: Boolean = false,
    val blockMobileData: Boolean = false,
    val blockSniffers: Boolean = false,
    val blockHotshare: Boolean = false,
    val lockPassword: String = "",
    val showToastOnConnect: String = ""
)
