package com.example.util

object PayloadGenerator {
    fun parsePayload(rawPayload: String, targetHost: String, targetPort: Int): String {
        return rawPayload
            .replace("[host_port]", "$targetHost:$targetPort", ignoreCase = true)
            .replace("[host]", targetHost, ignoreCase = true)
            .replace("[port]", targetPort.toString(), ignoreCase = true)
            .replace("[protocol]", "HTTP/1.1", ignoreCase = true)
            .replace("[method]", "CONNECT", ignoreCase = true)
            .replace("[crlf]", "\r\n", ignoreCase = true)
            .replace("[cr]", "\r", ignoreCase = true)
            .replace("[lf]", "\n", ignoreCase = true)
            .replace("[instant_split]", "", ignoreCase = true)
            .replace("[delay_split]", "", ignoreCase = true)
    }

    fun generateCustomPayload(
        method: String = "CONNECT",
        injectionType: String = "Normal",
        hostTag: String = "[host_port]",
        customHost: String = "",
        useKeepAlive: Boolean = true,
        useUpgrade: Boolean = true,
        useOnlineHost: Boolean = true,
        userAgent: String = "DTunnel/2.0"
    ): String {
        val sb = StringBuilder()
        val effectiveHost = if (customHost.isNotBlank()) customHost else "[host]"

        when (injectionType) {
            "Front Inject" -> {
                sb.append("GET http://$effectiveHost/ HTTP/1.1[crlf]")
                sb.append("Host: $effectiveHost[crlf]")
                sb.append("Connection: Keep-Alive[crlf][crlf]")
                sb.append("$method $hostTag [protocol][crlf]")
                sb.append("Host: $effectiveHost[crlf]")
            }
            "Back Inject" -> {
                sb.append("$method $hostTag [protocol][crlf]")
                sb.append("Host: $effectiveHost[crlf][crlf]")
                sb.append("GET http://$effectiveHost/ HTTP/1.1[crlf]")
                sb.append("Host: $effectiveHost[crlf]")
            }
            else -> {
                sb.append("$method $hostTag [protocol][crlf]")
                sb.append("Host: $effectiveHost[crlf]")
            }
        }

        if (useOnlineHost) {
            sb.append("X-Online-Host: $effectiveHost[crlf]")
            sb.append("X-Forward-Host: $effectiveHost[crlf]")
        }
        if (useKeepAlive) {
            sb.append("Connection: Keep-Alive[crlf]")
        }
        if (useUpgrade) {
            sb.append("Upgrade: websocket[crlf]")
            sb.append("Sec-WebSocket-Version: 13[crlf]")
        }
        if (userAgent.isNotBlank()) {
            sb.append("User-Agent: $userAgent[crlf]")
        }
        sb.append("[crlf]")

        return sb.toString()
    }
}
