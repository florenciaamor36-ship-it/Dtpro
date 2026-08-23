package com.example.data.model

enum class TunnelMode(
    val title: String,
    val description: String,
    val requiresPayload: Boolean,
    val requiresSni: Boolean,
    val defaultPort: Int
) {
    SSH_PAYLOAD(
        title = "SSH + HTTP Custom Payload",
        description = "Inyección de cabeceras HTTP mediante proxy / puerto 80",
        requiresPayload = true,
        requiresSni = false,
        defaultPort = 80
    ),
    SSH_DIRECT(
        title = "SSH Direct (TCP)",
        description = "Conexión SSH TCP directa al servidor",
        requiresPayload = false,
        requiresSni = false,
        defaultPort = 22
    ),
    SSH_SSL(
        title = "SSH + SSL / TLS (SNI)",
        description = "Túnel encriptado SSL/TLS con Host SNI / Bug Host",
        requiresPayload = false,
        requiresSni = true,
        defaultPort = 443
    ),
    SSH_WEBSOCKET(
        title = "SSH + WebSocket (HTTP)",
        description = "Túnel WebSocket con cabeceras personalizadas",
        requiresPayload = true,
        requiresSni = false,
        defaultPort = 80
    ),
    SSH_WEBSOCKET_SSL(
        title = "SSH + WebSocket (WSS/SSL)",
        description = "Túnel WebSocket seguro sobre TLS con SNI",
        requiresPayload = true,
        requiresSni = true,
        defaultPort = 443
    ),
    V2RAY_VMESS(
        title = "V2Ray (VMess / VLESS)",
        description = "Túnel V2Ray VMess / VLESS con transporte WS y TLS",
        requiresPayload = false,
        requiresSni = true,
        defaultPort = 443
    ),
    UDP_HYSTERIA(
        title = "UDP Custom / Hysteria / BadVPN",
        description = "Túnel UDP de baja latencia para juegos y llamadas",
        requiresPayload = false,
        requiresSni = false,
        defaultPort = 7300
    ),
    DIRECT_PROXY(
        title = "Proxy SOCKS5 / HTTP Directo",
        description = "Enrutamiento directo mediante servidor Proxy",
        requiresPayload = false,
        requiresSni = false,
        defaultPort = 8080
    )
}
