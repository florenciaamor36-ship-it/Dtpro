package com.example.util

object PayloadGenerator {
    /**
     * Reemplaza únicamente los marcadores estándar soportados en el payload
     */
    fun parsePayload(rawPayload: String, targetHost: String, targetPort: Int): String {
        val explicitHostPort = if (targetPort > 0) "$targetHost:$targetPort" else targetHost

        return rawPayload
            .replace("[host_port]", explicitHostPort, ignoreCase = true)
            .replace("[host]", targetHost, ignoreCase = true)
            .replace("[port]", targetPort.toString(), ignoreCase = true)
            .replace("[protocol]", "HTTP/1.1", ignoreCase = true)
            .replace("[method]", "CONNECT", ignoreCase = true)
            .replace("[crlf]", "\r\n", ignoreCase = true)
            .replace("[cr]", "\r", ignoreCase = true)
            .replace("[lf]", "\n", ignoreCase = true)
            .replace("[raw]", "", ignoreCase = true)
            .replace("[real_raw]", "", ignoreCase = true)
            .replace("[netData]", "", ignoreCase = true)
            .replace("[ua]", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", ignoreCase = true)
    }

    /**
     * Divide el payload en partes si contiene [split], [instant_split] o [delay_split]
     */
    fun splitPayload(rawPayload: String, targetHost: String, targetPort: Int): List<Pair<String, Long>> {
        val parsed = parsePayload(rawPayload, targetHost, targetPort)
        val splitDelimiters = listOf("[split]", "[instant_split]", "[delay_split]")
        
        var hasSplit = false
        var delayMs = 0L
        for (delim in splitDelimiters) {
            if (rawPayload.contains(delim, ignoreCase = true)) {
                hasSplit = true
                if (delim.equals("[delay_split]", ignoreCase = true)) {
                    delayMs = 250L
                }
                break
            }
        }

        if (!hasSplit) {
            return listOf(Pair(parsed, 0L))
        }

        // Split real por los marcadores
        val parts = parsed.split(Regex("\\[split\\]|\\[instant_split\\]|\\[delay_split\\]", RegexOption.IGNORE_CASE))
        return parts.mapIndexed { index, part ->
            Pair(part, if (index > 0) delayMs else 0L)
        }.filter { it.first.isNotEmpty() }
    }

    /**
     * Generador interactivo de payloads según las opciones típicas de HTTP Injector y HTTP Custom
     */
    fun generateInteractivePayload(
        urlOrHost: String,
        reqMethod: String = "CONNECT",
        injectionMethod: String = "Normal", // Normal, Front Inject, Back Inject
        onlineHost: Boolean = true,
        forwardHost: Boolean = false,
        keepAlive: Boolean = true,
        userAgent: Boolean = true,
        split: Boolean = false,
        dualConnect: Boolean = false
    ): String {
        val sb = StringBuilder()
        val target = if (urlOrHost.isNotBlank()) urlOrHost else "[host_port]"

        if (injectionMethod == "Front Inject") {
            sb.append("GET http://$target/ HTTP/1.1[crlf]")
            sb.append("Host: $target[crlf]")
            if (userAgent) sb.append("User-Agent: [ua][crlf]")
            if (keepAlive) sb.append("Connection: Keep-Alive[crlf]")
            sb.append("[crlf]")
            if (split) sb.append("[split]")
        }

        sb.append("$reqMethod [host_port] HTTP/1.1[crlf]")
        sb.append("Host: $target[crlf]")
        if (onlineHost) sb.append("X-Online-Host: $target[crlf]")
        if (forwardHost) sb.append("X-Forward-Host: $target[crlf]")
        if (userAgent) sb.append("User-Agent: [ua][crlf]")
        if (keepAlive) sb.append("Connection: Keep-Alive[crlf]")
        
        if (dualConnect) {
            sb.append("[crlf]CONNECT [host_port] HTTP/1.1[crlf]")
            sb.append("Host: $target[crlf]")
        }

        sb.append("[crlf][crlf]")
        return sb.toString()
    }

    const val DEFAULT_HTTP_PAYLOAD = "GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
    const val DEFAULT_CONNECT_PAYLOAD = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
}
