package com.example.util

object PayloadGenerator {
    /**
     * Reemplaza únicamente los marcadores estándar soportados en el payload
     * sin agregar métodos, encabezados ni modificar el texto escrito por el usuario.
     */
    fun parsePayload(rawPayload: String, targetHost: String, targetPort: Int): String {
        val hostPort = if (targetPort > 0 && targetPort != 80 && targetPort != 443) "$targetHost:$targetPort" else targetHost
        val explicitHostPort = "$targetHost:$targetPort"

        return rawPayload
            .replace("[host_port]", explicitHostPort, ignoreCase = true)
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

    /**
     * Payload de ejemplo estándar para SSH sobre HTTP Proxy / Frontal puerto 80
     */
    const val DEFAULT_HTTP_PAYLOAD = "GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
    const val DEFAULT_CONNECT_PAYLOAD = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
}

