package com.example.data.model

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Authenticating : ConnectionStatus()
    object Connected : ConnectionStatus()
    object Reconnecting : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

data class TunnelState(
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val currentConfig: TunnelConfig? = null,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val downloadSpeedBps: Long = 0,
    val uploadSpeedBps: Long = 0,
    val pingMs: Long = -1,
    val publicIp: String = "---",
    val ipLocation: String = "---",
    val connectedSinceTimestamp: Long = 0,
    val isLocalProxyRunning: Boolean = false,
    val localSocksPort: Int = 1080,
    val localHttpPort: Int = 8080
)
